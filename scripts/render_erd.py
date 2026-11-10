#!/usr/bin/env python3
"""docs/jachi-contract-risk.erd.json 을 ERD로 다시 뽑는다.

    python3 scripts/render_erd.py

출력은 docs/ 아래 svg, 벡터 pdf, png 다.
PDF는 Google Chrome이 SVG를 벡터로 인쇄한다. 글자와 선이 이미지로 굽지 않는다.
"""

import heapq
import json
import math
import re
import subprocess
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "jachi-contract-risk.erd.json"
PNG = ROOT / "docs" / "jachi-contract-risk-erd.png"
PDF = ROOT / "docs" / "jachi-contract-risk-erd.pdf"

SCALE = 2
OX, OY = 56, 128

GOTHIC = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
font_title = ImageFont.truetype(GOTHIC, 44, index=6)
font_sub = ImageFont.truetype(GOTHIC, 22, index=0)
font_group = ImageFont.truetype(GOTHIC, 28, index=6)
font_header = ImageFont.truetype(GOTHIC, 26, index=6)
font_name = ImageFont.truetype(GOTHIC, 22, index=0)
font_name_pk = ImageFont.truetype(GOTHIC, 22, index=6)
font_type = ImageFont.truetype(GOTHIC, 20, index=0)
font_comment = ImageFont.truetype(GOTHIC, 16, index=0)
font_badge = ImageFont.truetype(GOTHIC, 15, index=6)
font_legend = ImageFont.truetype(GOTHIC, 20, index=0)
font_card = ImageFont.truetype(GOTHIC, 18, index=2)

OPT_AI = 1
OPT_PK = 2
OPT_UQ = 4
OPT_NN = 8

REL = {2: "01", 4: "0N", 8: "1", 16: "1N"}
REL_LABEL = {2: "0..1", 4: "0..N", 8: "1", 16: "1..N"}

BG = (244, 241, 234, 255)
INK = (28, 25, 23, 255)
MUTED = (120, 113, 108, 255)
LINE = (87, 83, 78, 255)
# 겹친 관계선은 같은 회색이 아니라 농도만 다르게 둔다.
LINE_DENSITIES = (
    (28, 25, 23, 255),
    (96, 90, 82, 255),
    (158, 150, 138, 255),
    (58, 78, 102, 255),
    (122, 74, 62, 255),
    (62, 102, 78, 255),
)
PAPER = (255, 252, 247, 255)
HAIR = (214, 211, 206, 255)
FK = (29, 78, 216, 255)


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def mix(color, other, t):
    return tuple(int(a * (1 - t) + b * t) for a, b in zip(color, other))


def luminance(color):
    r, g, b = color
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255


def text_w(font, text):
    return font.getlength(text)


class SvgDraw:
    """Same calls as ImageDraw, written as SVG so the PDF stays vectors."""

    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.parts = []

    def _color(self, color):
        if color is None:
            return None
        if len(color) == 4 and color[3] == 0:
            return "none"
        if len(color) == 4 and color[3] != 255:
            red, green, blue, alpha = color
            return f"rgba({red},{green},{blue},{alpha / 255:.3f})"
        red, green, blue = color[:3]
        return f"#{red:02x}{green:02x}{blue:02x}"

    def _weight(self, font):
        style = font.getname()[1]
        if style in ("Bold", "SemiBold", "ExtraBold", "Heavy"):
            return "700"
        if style == "Medium":
            return "500"
        return "400"

    def rounded_rectangle(self, xy, radius=0, fill=None, outline=None, width=1):
        x0, y0, x1, y1 = xy
        fill_color = self._color(fill) or "none"
        stroke = self._color(outline) or "none"
        stroke_width = width if outline else 0
        self.parts.append(
            f'<rect x="{x0:.2f}" y="{y0:.2f}" width="{x1 - x0:.2f}" height="{y1 - y0:.2f}" '
            f'rx="{radius}" ry="{radius}" fill="{fill_color}" stroke="{stroke}" stroke-width="{stroke_width}"/>'
        )

    def rectangle(self, xy, fill=None, outline=None, width=1):
        self.rounded_rectangle(xy, 0, fill, outline, width)

    def line(self, points, fill=None, width=1, joint=None):
        coords = " ".join(f"{x:.2f},{y:.2f}" for x, y in points)
        stroke = self._color(fill) or "#000000"
        join = "round" if joint else "miter"
        self.parts.append(
            f'<polyline points="{coords}" fill="none" stroke="{stroke}" stroke-width="{width}" '
            f'stroke-linejoin="{join}" stroke-linecap="round"/>'
        )

    def ellipse(self, xy, outline=None, fill=None, width=1):
        x0, y0, x1, y1 = xy
        stroke = self._color(outline) or "none"
        fill_color = self._color(fill) or "none"
        self.parts.append(
            f'<ellipse cx="{(x0 + x1) / 2:.2f}" cy="{(y0 + y1) / 2:.2f}" '
            f'rx="{(x1 - x0) / 2:.2f}" ry="{(y1 - y0) / 2:.2f}" '
            f'fill="{fill_color}" stroke="{stroke}" stroke-width="{width}"/>'
        )

    def text(self, xy, text, font=None, fill=None):
        x, y = xy
        ascent, _descent = font.getmetrics()
        safe = (
            text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        )
        self.parts.append(
            f'<text x="{x:.2f}" y="{y + ascent:.2f}" '
            f'font-family="Apple SD Gothic Neo, AppleGothic, sans-serif" '
            f'font-size="{font.size}" font-weight="{self._weight(font)}" '
            f'fill="{self._color(fill)}">{safe}</text>'
        )

    def svg(self):
        body = "\n".join(self.parts)
        return (
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" '
            f'viewBox="0 0 {self.width} {self.height}">\n'
            f'<rect width="100%" height="100%" fill="#f4f1ea"/>\n{body}\n</svg>\n'
        )


class TeeDraw:
    def __init__(self, *draws):
        self.draws = draws

    def __getattr__(self, name):
        def call(*args, **kwargs):
            for target in self.draws:
                getattr(target, name)(*args, **kwargs)

        return call


class Box:
    def __init__(self, x, y, w, h):
        self.x = x
        self.y = y
        self.w = w
        self.h = h

    @property
    def right(self):
        return self.x + self.w

    @property
    def bottom(self):
        return self.y + self.h

    @property
    def cx(self):
        return self.x + self.w / 2

    @property
    def cy(self):
        return self.y + self.h / 2

    def inflate(self, pad):
        return Box(self.x - pad, self.y - pad, self.w + 2 * pad, self.h + 2 * pad)

    def edge_point(self, side, t):
        t = min(0.86, max(0.14, t))
        if side == "left":
            return (self.x, self.y + self.h * t)
        if side == "right":
            return (self.right, self.y + self.h * t)
        if side == "top":
            return (self.x + self.w * t, self.y)
        return (self.x + self.w * t, self.bottom)


def load():
    doc = json.loads(SRC.read_text())
    collections = doc["collections"]
    tables = collections["tableEntities"]
    columns = collections["tableColumnEntities"]
    relationships = collections["relationshipEntities"]
    groups = collections["tableGroupEntities"]

    fk_ids = set()
    for rel in relationships.values():
        fk_ids.update(rel["end"]["columnIds"])

    domains = {}
    dictionary = collections.get("domainEntities") or {}
    fields = dictionary.get("fields") or []
    for row in dictionary.get("rows") or []:
        item = dict(zip(fields, row))
        domains[item["id"]] = item

    return doc, tables, columns, relationships, groups, fk_ids, domains


def wrap_text(text, font, max_width):
    lines = []
    line = ""
    for token in re.findall(r"\s+|\S+", text):
        trial = line + token
        if line and text_w(font, trial) > max_width:
            lines.append(line.rstrip())
            line = token.lstrip()
        else:
            line = trial
        while line and text_w(font, line) > max_width and len(line) > 1:
            cut = len(line) - 1
            while cut > 1 and text_w(font, line[:cut]) > max_width:
                cut -= 1
            lines.append(line[:cut])
            line = line[cut:].lstrip()
    if line.strip():
        lines.append(line.rstrip())
    return lines


def column_description(column, domains):
    comment = (column.get("comment") or "").strip()
    domain = domains.get(column.get("domainId") or "")
    domain_text = (domain.get("description") or "").strip() if domain else ""
    if comment and domain_text and domain_text != comment:
        return f"{comment}  ·  {domain_text}"
    return comment or domain_text


def build_tables(doc, tables, columns, fk_ids, domains):
    pad_x = 16
    key_w = 52
    gap = 14
    header_h = 50
    row_h = 36
    comment_line_h = 22
    built = {}
    for table_id in doc["doc"]["tableIds"]:
        table = tables[table_id]
        cols = [columns[cid] for cid in table["columnIds"]]
        name_w = max(text_w(font_name_pk, col["name"]) for col in cols)
        type_w = max(text_w(font_type, col["dataType"]) for col in cols)
        header_need = text_w(font_header, table["name"]) + pad_x * 2
        width = max(header_need, pad_x + key_w + gap + name_w + gap + type_w + pad_x)
        width = int(math.ceil(max(width, 680)))
        text_width = width - (pad_x + key_w + gap) - pad_x
        rows = []
        for col in cols:
            description = column_description(col, domains)
            lines = wrap_text(description, font_comment, text_width) if description else []
            rows.append(
                {
                    "col": col,
                    "lines": lines,
                    "height": row_h + comment_line_h * len(lines),
                }
            )
        height = header_h + sum(row["height"] for row in rows)
        x = OX + table["ui"]["x"] * SCALE
        y = OY + table["ui"]["y"] * SCALE
        built[table_id] = {
            "id": table_id,
            "name": table["name"],
            "group_id": table.get("groupId"),
            "cols": cols,
            "rows": rows,
            "box": Box(x, y, width, height),
            "header_h": header_h,
            "row_h": row_h,
            "key_w": key_w,
            "pad_x": pad_x,
            "gap": gap,
        }
    separate_tables(built)
    return built


def separate_tables(built):
    """Push tables apart when descriptions make the saved canvas boxes overlap."""
    items = list(built.values())
    for _ in range(12):
        moved = False
        items.sort(key=lambda item: (item["box"].y, item["box"].x))
        for left in items:
            for right in items:
                if left is right:
                    continue
                a = left["box"]
                b = right["box"]
                if a.right <= b.x or b.right <= a.x or a.bottom <= b.y or b.bottom <= a.y:
                    continue
                if b.x >= a.x:
                    b.x = a.right + 36
                else:
                    a.x = b.right + 36
                moved = True
        if not moved:
            break


def segment_hits(p, q, box):
    x1, y1 = p
    x2, y2 = q
    if abs(x1 - x2) < 0.5:
        y_lo, y_hi = (y1, y2) if y1 <= y2 else (y2, y1)
        return box.x <= x1 <= box.right and y_hi >= box.y and y_lo <= box.bottom
    if abs(y1 - y2) < 0.5:
        x_lo, x_hi = (x1, x2) if x1 <= x2 else (x2, x1)
        return box.y <= y1 <= box.bottom and x_hi >= box.x and x_lo <= box.right
    return False


def path_clear(points, obstacles):
    for start, end in zip(points, points[1:]):
        for obstacle in obstacles:
            if segment_hits(start, end, obstacle):
                return False
    return True


def elbow(p1, p2, horizontal_first):
    x1, y1 = p1
    x2, y2 = p2
    if horizontal_first:
        return [p1, (x2, y1), p2]
    return [p1, (x1, y2), p2]


def dedupe(points):
    cleaned = [points[0]]
    for point in points[1:]:
        if abs(point[0] - cleaned[-1][0]) > 0.5 or abs(point[1] - cleaned[-1][1]) > 0.5:
            cleaned.append(point)
    return cleaned


CELL = 18
DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def side_pairs(src, dst):
    dx = dst.cx - src.cx
    dy = dst.cy - src.cy
    horizontal = ("right", "left") if dx >= 0 else ("left", "right")
    vertical = ("bottom", "top") if dy >= 0 else ("top", "bottom")
    ordered = [horizontal, vertical] if abs(dx) >= abs(dy) else [vertical, horizontal]
    ordered.append((horizontal[0], vertical[1]))
    ordered.append((vertical[0], horizontal[1]))
    return ordered


def outside_cell(box, side, slot, blocked):
    """Second free grid cell outside the table, leaving room for a cardinality mark."""
    x, y = box.edge_point(side, slot)
    cell = (int(math.floor(x / CELL)), int(math.floor(y / CELL)))
    step = {"left": (-1, 0), "right": (1, 0), "top": (0, -1), "bottom": (0, 1)}[side]
    found = None
    for _ in range(12):
        cell = (cell[0] + step[0], cell[1] + step[1])
        if cell in blocked:
            continue
        if found is None:
            found = cell
            continue
        return cell_center(cell), cell
    if found is not None:
        return cell_center(found), found
    return None


def to_cell(point):
    return (int(round(point[0] / CELL)), int(round(point[1] / CELL)))


def cell_center(cell):
    return (cell[0] * CELL + CELL / 2, cell[1] * CELL + CELL / 2)


def blocked_cells(boxes, bounds):
    blocked = set()
    max_x, max_y = bounds
    for gx in range(-2, int(max_x / CELL) + 3):
        for gy in range(-2, int(OY / CELL) - 1):
            blocked.add((gx, gy))
        blocked.add((gx, int(max_y / CELL) + 2))
    for gy in range(-2, int(max_y / CELL) + 3):
        blocked.add((-2, gy))
        blocked.add((int(max_x / CELL) + 2, gy))
    for box in boxes:
        x0 = int(math.floor(box.x / CELL))
        x1 = int(math.floor(box.right / CELL))
        y0 = int(math.floor(box.y / CELL))
        y1 = int(math.floor(box.bottom / CELL))
        for gx in range(x0, x1 + 1):
            for gy in range(y0, y1 + 1):
                blocked.add((gx, gy))
    return blocked


def astar(start, goal, blocked, used):
    if start == goal:
        return [start]
    if start in blocked or goal in blocked:
        return None

    def heuristic(cell):
        return abs(cell[0] - goal[0]) + abs(cell[1] - goal[1])

    heap = [(heuristic(start), 0, start, -1)]
    best = {start: 0}
    came = {}
    while heap:
        _, cost, node, direction = heapq.heappop(heap)
        if cost != best.get(node):
            continue
        if node == goal:
            path = [node]
            while node in came:
                node = came[node]
                path.append(node)
            path.reverse()
            return path
        x, y = node
        for index, (dx, dy) in enumerate(DIRS):
            nxt = (x + dx, y + dy)
            if nxt in blocked:
                continue
            turn = 0 if direction in (-1, index) else 6
            step = cost + 1 + turn + used.get(nxt, 0) * 5
            if step < best.get(nxt, 10**12):
                best[nxt] = step
                came[nxt] = node
                heapq.heappush(heap, (step + heuristic(nxt), step, nxt, index))
    return None


def naive_sides(src, dst):
    return side_pairs(src, dst)[0]


def column_ratio(item, column_ids):
    column_id = column_ids[0]
    y = item["header_h"]
    for row in item["rows"]:
        if row["col"]["id"] == column_id:
            return (y + row["height"] / 2) / item["box"].h
        y += row["height"]
    return 0.5


def assign_slots(routes, built):
    """Left and right edges meet the key column. Top and bottom edges share the width."""
    src_groups = defaultdict(list)
    dst_groups = defaultdict(list)
    for index, route in enumerate(routes):
        src_groups[(route["src_id"], tuple(route["src_columns"]))].append(index)
        dst_groups[(route["dst_id"], tuple(route["dst_columns"]))].append(index)
    src_fan = {}
    dst_fan = {}
    for indexes in src_groups.values():
        ordered = sorted(indexes, key=lambda i: routes[i]["dst_box"].cy)
        for order, index in enumerate(ordered):
            src_fan[index] = (order, len(ordered))
    for indexes in dst_groups.values():
        ordered = sorted(indexes, key=lambda i: routes[i]["src_box"].cy)
        for order, index in enumerate(ordered):
            dst_fan[index] = (order, len(ordered))

    def place(item, column_ids, fan):
        order, count = fan
        base = column_ratio(item, column_ids)
        if count == 1:
            return base
        delta = (order - (count - 1) / 2) * (item["row_h"] * 0.35 / item["box"].h)
        return min(0.92, max(0.08, base + delta))

    src_spread = {}
    dst_spread = {}
    by_src = defaultdict(list)
    by_dst = defaultdict(list)
    for index, route in enumerate(routes):
        by_src[route["src_id"]].append(index)
        by_dst[route["dst_id"]].append(index)
    for indexes in by_src.values():
        ordered = sorted(indexes, key=lambda i: (routes[i]["dst_box"].cx, routes[i]["dst_box"].cy))
        for order, index in enumerate(ordered):
            src_spread[index] = (order + 1) / (len(ordered) + 1)
    for indexes in by_dst.values():
        ordered = sorted(indexes, key=lambda i: (routes[i]["src_box"].cx, routes[i]["src_box"].cy))
        for order, index in enumerate(ordered):
            dst_spread[index] = (order + 1) / (len(ordered) + 1)

    for index, route in enumerate(routes):
        route["src_column_t"] = place(built[route["src_id"]], route["src_columns"], src_fan[index])
        route["dst_column_t"] = place(built[route["dst_id"]], route["dst_columns"], dst_fan[index])
        route["src_spread"] = src_spread[index]
        route["dst_spread"] = dst_spread[index]
        route["src_t"] = route["src_column_t"]
        route["dst_t"] = route["dst_column_t"]


def build_routes(built, relationships, groups):
    routes = []
    for rel in relationships.values():
        src_id = rel["start"]["tableId"]
        dst_id = rel["end"]["tableId"]
        src_box = built[src_id]["box"]
        dst_box = built[dst_id]["box"]
        src_side, dst_side = naive_sides(src_box, dst_box)
        routes.append(
            {
                "rel": rel,
                "src_id": src_id,
                "dst_id": dst_id,
                "src_name": built[src_id]["name"],
                "dst_name": built[dst_id]["name"],
                "src_box": src_box,
                "dst_box": dst_box,
                "src_side": src_side,
                "dst_side": dst_side,
                "src_columns": rel["start"]["columnIds"],
                "dst_columns": rel["end"]["columnIds"],
                "src_t": 0.5,
                "dst_t": 0.5,
                "kind": REL.get(rel["relationshipType"], "1N"),
                "distance": abs(src_box.cx - dst_box.cx) + abs(src_box.cy - dst_box.cy),
            }
        )
    assign_slots(routes, built)

    max_x = max(item["box"].right for item in built.values()) + 160
    max_y = max(item["box"].bottom for item in built.values()) + 80
    for group in groups.values():
        ui = group["ui"]
        max_x = max(max_x, OX + (ui["x"] + ui["width"]) * SCALE + 40)
        max_y = max(max_y, OY + (ui["y"] + ui["height"]) * SCALE + 40)
    obstacles = []
    for item in built.values():
        obstacles.append(item["box"].inflate(12))
    for group in groups.values():
        ui = group["ui"]
        label = group["name"]
        lw = text_w(font_group, label) + 28
        obstacles.append(Box(OX + ui["x"] * SCALE + 22, OY + ui["y"] * SCALE + 16, lw, 40).inflate(6))
    blocked = blocked_cells(obstacles, (max_x, max_y))
    used = {}

    for route in sorted(routes, key=lambda item: item["distance"]):
        points = None
        for src_side, dst_side in side_pairs(route["src_box"], route["dst_box"]):
            route["src_t"] = route["src_column_t"] if src_side in ("left", "right") else route["src_spread"]
            route["dst_t"] = route["dst_column_t"] if dst_side in ("left", "right") else route["dst_spread"]
            start_hit = outside_cell(route["src_box"], src_side, route["src_t"], blocked)
            goal_hit = outside_cell(route["dst_box"], dst_side, route["dst_t"], blocked)
            if start_hit is None or goal_hit is None:
                continue
            start, start_cell = start_hit
            goal, goal_cell = goal_hit
            cells = astar(start_cell, goal_cell, blocked, used)
            if cells is None:
                continue
            edge_start = route["src_box"].edge_point(src_side, route["src_t"])
            edge_end = route["dst_box"].edge_point(dst_side, route["dst_t"])
            centers = [cell_center(cell) for cell in cells]
            points = dedupe(
                leave_edge(edge_start, centers[0], src_side)
                + centers
                + list(reversed(leave_edge(edge_end, centers[-1], dst_side)))
            )
            others = [
                item["box"].inflate(4)
                for item_id, item in built.items()
                if item_id not in (route["src_id"], route["dst_id"])
            ]
            if not path_clear(points, others):
                points = None
                continue
            route["src_side"] = src_side
            route["dst_side"] = dst_side
            for cell in cells:
                used[cell] = used.get(cell, 0) + 1
            break
        if points is None:
            print(f"unrouted {route['src_name']} -> {route['dst_name']}")
            p1 = route["src_box"].edge_point(route["src_side"], route["src_t"])
            p2 = route["dst_box"].edge_point(route["dst_side"], route["dst_t"])
            points = dedupe(elbow(p1, p2, route["src_side"] in ("left", "right")))
        route["points"] = points
    return routes


def leave_edge(edge, nxt, side):
    """Perpendicular stub so the wire leaves the table before it turns."""
    if side in ("top", "bottom"):
        return [edge, (edge[0], nxt[1]), nxt]
    return [edge, (nxt[0], edge[1]), nxt]


def shorten(points, start_dist, end_dist):
    points = [tuple(p) for p in points]
    if len(points) < 2:
        return points
    points[0] = move_toward(points[0], points[1], start_dist)
    points[-1] = move_toward(points[-1], points[-2], end_dist)
    return points


def move_toward(origin, toward, distance):
    dx = toward[0] - origin[0]
    dy = toward[1] - origin[1]
    length = math.hypot(dx, dy)
    if length < 1:
        return origin
    scale = min(distance, length * 0.45) / length
    return (origin[0] + dx * scale, origin[1] + dy * scale)


def separate_overlapping_wires(wires):
    """겹친 축 위 구간을 나란히 벌리고, 그 선들에만 서로 다른 농도를 준다."""
    records = []
    for route_index, wire in enumerate(wires):
        for seg_index in range(len(wire) - 1):
            key = axis_segment(wire[seg_index], wire[seg_index + 1])
            if key is not None:
                records.append((route_index, seg_index, key))

    groups = defaultdict(list)
    for route_index, seg_index, key in records:
        groups[(key[0], key[1])].append((key[2], key[3], route_index, seg_index))

    offsets = defaultdict(dict)
    edges = set()
    for (orient, _axis), intervals in groups.items():
        overlapping = overlapping_interval_ids(intervals)
        if len(overlapping) < 2:
            continue
        lanes = lane_of(intervals, overlapping)
        lane_values = [lanes[item] for item in overlapping]
        center = (min(lane_values) + max(lane_values)) / 2
        for start, end, route_index, seg_index in intervals:
            if (route_index, seg_index) not in lanes:
                continue
            shift = (lanes[(route_index, seg_index)] - center) * 7
            if orient == "h":
                offsets[route_index][seg_index] = (0, shift)
            else:
                offsets[route_index][seg_index] = (shift, 0)
        active = []
        for start, end, route_index, seg_index in sorted(intervals, key=lambda item: item[0]):
            if (route_index, seg_index) not in lanes:
                continue
            active = [(stop, other) for stop, other in active if stop > start + 1]
            for _stop, other in active:
                if other != route_index:
                    edges.add(tuple(sorted((route_index, other))))
            active.append((end, route_index))

    shade_of = {}
    neighbor = defaultdict(set)
    for left, right in edges:
        neighbor[left].add(right)
        neighbor[right].add(left)
    for route_index in sorted(neighbor, key=lambda item: -len(neighbor[item])):
        used = {shade_of[other] for other in neighbor[route_index] if other in shade_of}
        shade = 0
        while shade in used:
            shade += 1
        shade_of[route_index] = shade
    colors = {
        route_index: LINE_DENSITIES[shade % len(LINE_DENSITIES)]
        for route_index, shade in shade_of.items()
    }

    separated = []
    for route_index, wire in enumerate(wires):
        separated.append(apply_segment_offsets(wire, offsets[route_index]))
    return separated, colors


def axis_segment(start, end):
    x1, y1 = start
    x2, y2 = end
    if abs(y1 - y2) <= 3 and abs(x1 - x2) > 4:
        return ("h", round((y1 + y2) / 2), min(x1, x2), max(x1, x2))
    if abs(x1 - x2) <= 3 and abs(y1 - y2) > 4:
        return ("v", round((x1 + x2) / 2), min(y1, y2), max(y1, y2))
    return None


def overlapping_interval_ids(intervals):
    active = []
    found = set()
    for start, end, route_index, seg_index in sorted(intervals, key=lambda item: item[0]):
        active = [(stop, item) for stop, item in active if stop > start + 1]
        current = (route_index, seg_index)
        for _stop, other in active:
            if other[0] != route_index:
                found.add(current)
                found.add(other)
        active.append((end, current))
    return found


def lane_of(intervals, overlapping):
    lanes = {}
    active = []
    ordered = sorted(
        (start, end, route_index, seg_index)
        for start, end, route_index, seg_index in intervals
        if (route_index, seg_index) in overlapping
    )
    for start, end, route_index, seg_index in ordered:
        active = [(stop, lane) for stop, lane in active if stop > start + 1]
        used = {lane for _stop, lane in active}
        lane = 0
        while lane in used:
            lane += 1
        active.append((end, lane))
        lanes[(route_index, seg_index)] = lane
    return lanes


def apply_segment_offsets(wire, seg_offsets):
    if len(wire) < 2 or not seg_offsets:
        return wire
    points = [wire[0]]
    last = len(wire) - 2
    for index in range(len(wire) - 1):
        start = wire[index]
        end = wire[index + 1]
        dx, dy = seg_offsets.get(index, (0, 0))
        start_shifted = start if index == 0 else (start[0] + dx, start[1] + dy)
        end_shifted = end if index == last else (end[0] + dx, end[1] + dy)
        for nxt in (start_shifted, end_shifted):
            prev = points[-1]
            if abs(prev[0] - nxt[0]) > 0.5 and abs(prev[1] - nxt[1]) > 0.5:
                points.append((nxt[0], prev[1]))
            if abs(points[-1][0] - nxt[0]) > 0.5 or abs(points[-1][1] - nxt[1]) > 0.5:
                points.append(nxt)
    return dedupe(points)


def draw_marker(draw, tip, side, kind, color=LINE):
    """Cardinality outside the table. The table is painted after the wire and covers the border."""
    outward = {"left": (-1, 0), "right": (1, 0), "top": (0, -1), "bottom": (0, 1)}[side]
    ux, uy = -outward[0], -outward[1]
    px, py = -uy, ux

    def at(along, across):
        return (
            tip[0] - ux * along + px * across,
            tip[1] - uy * along + py * across,
        )

    def bar(along):
        draw.line([at(along, -12), at(along, 12)], fill=color, width=3)

    def circle(along):
        center = at(along, 0)
        radius = 7
        draw.ellipse(
            [center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius],
            outline=color,
            width=3,
        )

    def foot(base_along):
        base = at(base_along, 0)
        draw.line([base, at(12, -12)], fill=color, width=3)
        draw.line([base, at(12, 0)], fill=color, width=3)
        draw.line([base, at(12, 12)], fill=color, width=3)

    if kind == "1":
        bar(14)
        bar(24)
    elif kind == "1N":
        bar(12)
        foot(32)
    elif kind == "0N":
        circle(22)
        foot(36)
    elif kind == "01":
        bar(12)
        circle(26)


def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(
        [box.x, box.y, box.right, box.bottom],
        radius=radius,
        fill=fill,
        outline=outline,
        width=width,
    )


def draw_table(draw, item, group_color, fk_ids):
    box = item["box"]
    header_h = item["header_h"]
    shadow = Box(box.x + 5, box.y + 7, box.w, box.h)
    rounded(draw, shadow, 14, (28, 25, 23, 22))
    rounded(draw, box, 14, PAPER)
    header_color = group_color
    draw.rounded_rectangle(
        [box.x, box.y, box.right, box.y + header_h + 12],
        radius=14,
        fill=header_color,
    )
    draw.rectangle([box.x, box.y + header_h - 2, box.right, box.y + header_h + 14], fill=header_color)
    title_color = (255, 255, 255, 255) if luminance(header_color) < 0.62 else INK
    name = item["name"]
    draw.text((box.x + item["pad_x"], box.y + 12), name, font=font_header, fill=title_color)

    y = box.y + header_h
    for index, row in enumerate(item["rows"]):
        col = row["col"]
        row_height = row["height"]
        options = col["options"]
        is_pk = bool(options & OPT_PK)
        is_fk = col["id"] in fk_ids
        is_uq = bool(options & OPT_UQ) and not is_pk
        if is_pk:
            draw.rectangle([box.x + 1, y, box.right - 1, y + row_height], fill=(245, 240, 230, 255))
        if index:
            draw.line([(box.x + 10, y), (box.right - 10, y)], fill=(236, 232, 224, 255), width=1)
        badge = "PK" if is_pk else "FK" if is_fk else "UQ" if is_uq else ""
        if badge:
            bw = text_w(font_badge, badge)
            bx = box.x + 12
            by = y + 8
            fill = INK if badge == "PK" else (255, 255, 255, 0)
            outline = None if badge == "PK" else FK if badge == "FK" else MUTED
            text_fill = (255, 255, 255, 255) if badge == "PK" else outline
            if badge == "PK":
                draw.rounded_rectangle([bx, by, bx + bw + 10, by + 20], radius=4, fill=fill)
            else:
                draw.rounded_rectangle(
                    [bx, by, bx + bw + 10, by + 20], radius=4, outline=outline, width=2
                )
            draw.text((bx + 5, by + 1), badge, font=font_badge, fill=text_fill)
        name_font = font_name_pk if is_pk else font_name
        name_color = FK if is_fk and not is_pk else INK
        name_x = box.x + item["pad_x"] + item["key_w"] + item["gap"]
        draw.text((name_x, y + 6), col["name"], font=name_font, fill=name_color)
        type_text = col["dataType"]
        type_x = box.right - item["pad_x"] - text_w(font_type, type_text)
        draw.text((type_x, y + 8), type_text, font=font_type, fill=MUTED)
        line_y = y + item["row_h"] - 2
        for line in row["lines"]:
            draw.text((name_x, line_y), line, font=font_comment, fill=MUTED)
            line_y += 22
        y += row_height

    rounded(draw, box, 14, None, outline=mix(group_color, (28, 25, 23), 0.25), width=2)


def write_vector_pdf(svg_text, width, height, pdf_path):
    chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    html_path = Path("/tmp/contractcheck-erd.html")
    html_path.write_text(
        "<!doctype html><html><head><meta charset=\"utf-8\">"
        f"<style>@page {{ size: {width}px {height}px; margin: 0; }} "
        f"html, body {{ margin: 0; padding: 0; width: {width}px; height: {height}px; background: #f4f1ea; }} "
        "svg { display: block; }</style></head>"
        f"<body>{svg_text}</body></html>",
        encoding="utf-8",
    )
    subprocess.run(
        [
            chrome,
            "--headless",
            "--disable-gpu",
            "--no-pdf-header-footer",
            f"--print-to-pdf={pdf_path}",
            html_path.as_uri(),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def render():
    doc, tables, columns, relationships, groups, fk_ids, domains = load()
    built = build_tables(doc, tables, columns, fk_ids, domains)
    routes = build_routes(built, relationships, groups)

    group_boxes = []
    for group in groups.values():
        ui = group["ui"]
        box = Box(
            OX + ui["x"] * SCALE,
            OY + ui["y"] * SCALE,
            ui["width"] * SCALE,
            ui["height"] * SCALE,
        )
        group_boxes.append((group, box))

    for group, box in group_boxes:
        members = [item["box"] for item in built.values() if item["group_id"] == group["id"]]
        if not members:
            continue
        pad = 36
        left = min(box.x, min(member.x for member in members) - pad)
        top = min(box.y, min(member.y for member in members) - 56)
        right = max(box.right, max(member.right for member in members) + pad)
        bottom = max(box.bottom, max(member.bottom for member in members) + pad)
        box.x, box.y = left, top
        box.w, box.h = right - left, bottom - top

    max_x = max(box.right for _, box in group_boxes)
    max_y = max(box.bottom for _, box in group_boxes)
    for item in built.values():
        max_x = max(max_x, item["box"].right)
        max_y = max(max_y, item["box"].bottom)
    for route in routes:
        for x, y in route["points"]:
            max_x = max(max_x, x)
            max_y = max(max_y, y)

    width = int(max_x + 80)
    height = int(max_y + 150)
    image = Image.new("RGBA", (width, height), BG)
    svg = SvgDraw(width, height)
    draw = TeeDraw(ImageDraw.Draw(image), svg)

    draw.text((OX, 36), "contractcheck", font=font_title, fill=INK)
    subtitle = "자취방 계약서 위험 분석  ·  PostgreSQL  ·  테이블 24  ·  관계 26"
    draw.text((OX + text_w(font_title, "contractcheck") + 18, 52), subtitle, font=font_sub, fill=MUTED)

    for group, box in group_boxes:
        color = hex_rgb(group["color"])
        fill = mix(color, (255, 252, 247), 0.88) + (255,)
        outline = mix(color, (255, 255, 255), 0.25) + (255,)
        rounded(draw, box, 22, fill, outline=outline, width=3)
        label = group["name"]
        lw = text_w(font_group, label)
        chip = Box(box.x + 22, box.y + 16, lw + 28, 40)
        rounded(draw, chip, 10, color + (255,))
        chip_text = (255, 255, 255, 255) if luminance(color) < 0.72 else INK
        draw.text((chip.x + 14, chip.y + 6), label, font=font_group, fill=chip_text)

    hits = 0
    for route in routes:
        others = [
            item["box"].inflate(6)
            for item_id, item in built.items()
            if item_id not in (route["src_id"], route["dst_id"])
        ]
        if not path_clear(route["points"], others):
            hits += 1
            src_name = built[route["src_id"]]["name"]
            dst_name = built[route["dst_id"]]["name"]
            print(f"cross {src_name} -> {dst_name} via {route['src_side']}->{route['dst_side']}")
        full = route["points"]
        end_gap = {"1": 32, "01": 40, "1N": 42, "0N": 48}.get(route["kind"], 42)
        route["wire"] = shorten(full, 32, end_gap)

    separated, wire_colors = separate_overlapping_wires([route["wire"] for route in routes])
    for index, route in enumerate(routes):
        route["wire"] = separated[index]
        route["wire_color"] = wire_colors.get(index, LINE)
        draw.line(route["wire"], fill=route["wire_color"], width=3, joint="curve")

    colors = {group["id"]: hex_rgb(group["color"]) for group in groups.values()}
    for item in built.values():
        draw_table(draw, item, colors.get(item["group_id"], (79, 140, 255)), fk_ids)

    for route in routes:
        full = route["points"]
        draw_marker(draw, full[0], route["src_side"], "1", route["wire_color"])
        draw_marker(draw, full[-1], route["dst_side"], route["kind"], route["wire_color"])

    legend_y = int(max_y + 36)
    draw.text((OX, legend_y), "범례", font=font_group, fill=INK)
    notes = [
        "PK  기본키",
        "FK  외래키",
        "UQ  고유",
        "부모 쪽은 1, 자식 쪽은 1 / 1..N / 0..N",
        "회색 글은 컬럼 설명 · 도메인 description",
        "겹친 관계선은 농도를 나눠 나란히 그린다",
    ]
    x = OX
    y = legend_y + 44
    for note in notes:
        note_width = text_w(font_legend, note)
        if x > OX and x + note_width > width - OX:
            x = OX
            y += 32
        draw.text((x, y), note, font=font_legend, fill=MUTED)
        x += note_width + 36

    rgb = Image.new("RGB", image.size, (244, 241, 234))
    rgb.paste(image, mask=image.split()[-1])
    rgb.save(PNG, "PNG", optimize=True)
    svg_path = ROOT / "docs" / "jachi-contract-risk-erd.svg"
    svg_path.write_text(svg.svg(), encoding="utf-8")
    write_vector_pdf(svg.svg(), width, height, PDF)
    print(f"svg {svg_path}")
    print(f"png {PNG} {rgb.size}")
    print(f"pdf {PDF}")
    print(f"routes {len(routes)} crossings {hits}")


if __name__ == "__main__":
    render()
