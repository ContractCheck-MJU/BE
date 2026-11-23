#!/usr/bin/env python3
"""DATA 폴더의 RAG 데이터(법령·판례 임베딩)를 PostgreSQL(pgvector)에 반복 적재한다.

여러 번 돌려도 안전하다(멱등). 문서는 UNIQUE(doc_type, source_id)로 찾아 내용이 같으면 건너뛰고(unchanged),
바뀌었으면 문서와 청크를 갱신(updated), 없으면 추가(inserted)한다. 실행마다 ingest_jobs에 이력(RUNNING→DONE/FAILED,
처리·실패 건수)을 남긴다. 문서 하나가 실패해도 그 문서만 되돌리고 나머지는 계속한다. 실패가 있으면 종료 코드가 1이다.

소스 추가 방법: Source(target, read)를 만들어 SOURCES에 넣는다. read(data_dir)는 문서 목록(dict)을 돌려준다.
DATA에 새 파일이 생기면(예: 법령 추가 수집, 판례 추가) 파일만 갱신하고 같은 명령을 다시 실행하면 된다.
kftc_cases(공정위 시정 사례)는 DATA에 데이터가 없어 아직 소스가 없다. 생기면 kftc 전용 Source와 쓰기 함수를 추가한다.

벡터는 이미 L2 정규화돼 있어 그대로 넣는다(코사인 유사도 = 내적). 적재 전에 차원·정규화·행 정렬·해시를 검증한다.
실행은 scripts/ingest.sh를 쓰면 가상환경과 접속 정보를 알아서 처리한다.
"""
import argparse
import csv
import hashlib
import json
import os
import sys
from collections import OrderedDict
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Callable

import numpy as np
import psycopg

DIM = 1024


# ---------- 검증·변환 ----------

def vector_literal(vector):
    return "[" + ",".join(f"{float(x):.9g}" for x in vector) + "]"  # float32는 유효숫자 9자리면 정확히 복원된다


def check_vectors(name, matrix):
    if matrix.ndim != 2 or matrix.shape[1] != DIM:
        raise SystemExit(f"{name}: 차원이 {DIM}이 아닙니다 {matrix.shape}")
    if not np.isfinite(matrix).all():
        raise SystemExit(f"{name}: NaN/Inf가 있습니다")
    norms = np.linalg.norm(matrix, axis=1)
    if not np.allclose(norms, 1.0, atol=1e-3):
        raise SystemExit(f"{name}: L2 정규화되지 않은 벡터가 있습니다 (min={norms.min():.4f}, max={norms.max():.4f})")


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


# ---------- 소스: 읽기 ----------

def read_laws(data_dir):
    base = data_dir / "law-embedding" / "data"
    chunks = read_jsonl(base / "법령_임베딩.jsonl")
    originals = {d["id"]: d for d in read_jsonl(base / "법령_원문95건.jsonl")}
    matrix = np.asarray([c["embedding"] for c in chunks], dtype=np.float32)
    check_vectors("법령 jsonl", matrix)
    npy = np.load(base / "법령_벡터.npy")
    if not np.allclose(npy[np.asarray([c["vector_row"] for c in chunks])], matrix, atol=1e-6):
        raise SystemExit("법령: jsonl 임베딩과 법령_벡터.npy 행이 일치하지 않습니다")

    documents = OrderedDict()
    for index, chunk in enumerate(chunks):
        key = chunk["document_key"]
        if key not in originals:
            raise SystemExit(f"법령: 원문에 없는 document_key {key}")
        documents.setdefault(key, {
            "doc_type": "LAW", "source_id": key, "title": originals[key]["title"], "ref": chunk["article"],
            "court": None, "decided_at": None, "content": originals[key]["source_text"],
            "url": chunk["source_url"], "fetched_at": date.fromisoformat(chunk["collected_date"]), "chunks": [],
        })["chunks"].append((chunk["chunk_no"], chunk["text"], matrix[index]))  # 인용용 원문 text (embedding_text 아님)
    return list(documents.values())


def read_precedents(data_dir):
    base = data_dir / "precedent-search" / "data" / "processed"
    csv_path = base / "precedent_chunks_reviewed_with_urls.csv"
    rows = list(csv.DictReader(csv_path.open(encoding="utf-8-sig", newline="")))
    npz = np.load(base / "precedent_embeddings_reviewed.npz", allow_pickle=False)
    if str(npz["csv_sha256"]) != hashlib.sha256(csv_path.read_bytes()).hexdigest():
        raise SystemExit("판례: NPZ가 가리키는 CSV 해시가 현재 CSV와 다릅니다(임베딩이 낡았을 수 있음)")
    vectors = npz["vectors"].astype(np.float32)
    check_vectors("판례 npz", vectors)
    position = {record_id: i for i, record_id in enumerate(npz["record_ids"].tolist())}
    if len(position) != len(rows) or any(r["record_id"] not in position for r in rows):
        raise SystemExit("판례: CSV의 record_id와 NPZ의 record_ids가 일치하지 않습니다")

    documents = OrderedDict()
    for row in rows:
        documents.setdefault(row["source_id"], {
            "doc_type": "PRECEDENT", "source_id": row["source_id"], "title": row["source_title"],
            "ref": row["case_no"], "court": row["court"] or None,
            "decided_at": date.fromisoformat(row["decided_at"]) if row["decided_at"] else None,
            "content": None, "url": row["source_url"] or None, "fetched_at": None, "chunks": [],
        })["chunks"].append((int(row["chunk_no"]), row["text"], vectors[position[row["record_id"]]]))
    for document in documents.values():
        document["chunks"].sort(key=lambda c: c[0])
        document["content"] = "\n\n".join(text for _, text, _ in document["chunks"])
    return list(documents.values())


@dataclass(frozen=True)
class Source:
    target: str  # ingest_jobs.target (LAW / PRECEDENT / KFTC)
    read: Callable[[Path], list]


SOURCES = {
    "laws": Source("LAW", read_laws),
    "precedents": Source("PRECEDENT", read_precedents),
}

# ---------- 쓰기: legal_documents / legal_chunks ----------

DOC_FIELDS = ("title", "ref", "court", "decided_at", "content", "url")


def write_document(cursor, document, force):
    """inserted / updated / unchanged 중 하나를 돌려준다."""
    cursor.execute(
        "SELECT id, title, ref, court, decided_at, content, url FROM legal_documents WHERE doc_type = %s AND source_id = %s",
        (document["doc_type"], document["source_id"]),
    )
    existing = cursor.fetchone()
    if existing and not force and same_document(cursor, existing, document):
        return "unchanged"

    cursor.execute(
        """
        INSERT INTO legal_documents (doc_type, source_id, title, ref, court, decided_at, content, url, fetched_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, COALESCE(%s::date, CURRENT_DATE)::timestamptz)
        ON CONFLICT (doc_type, source_id) DO UPDATE SET
            title = EXCLUDED.title, ref = EXCLUDED.ref, court = EXCLUDED.court, decided_at = EXCLUDED.decided_at,
            content = EXCLUDED.content, url = EXCLUDED.url, fetched_at = EXCLUDED.fetched_at
        RETURNING id
        """,
        (document["doc_type"], document["source_id"], *(document[f] for f in DOC_FIELDS), document["fetched_at"]),
    )
    document_id = cursor.fetchone()[0]
    cursor.execute("DELETE FROM legal_chunks WHERE document_id = %s", (document_id,))
    cursor.executemany(
        "INSERT INTO legal_chunks (document_id, chunk_no, text, embedding) VALUES (%s, %s, %s, %s::vector)",
        [(document_id, no, text, vector_literal(vector)) for no, text, vector in document["chunks"]],
    )
    return "updated" if existing else "inserted"


def same_document(cursor, existing, document):
    document_id, *stored = existing
    if tuple(stored) != tuple(document[f] for f in DOC_FIELDS):
        return False
    cursor.execute("SELECT count(*) FROM legal_chunks WHERE document_id = %s", (document_id,))
    if cursor.fetchone()[0] != len(document["chunks"]):
        return False
    for no, text, vector in document["chunks"]:
        cursor.execute(
            "SELECT 1 FROM legal_chunks WHERE document_id = %s AND chunk_no = %s AND text = %s AND embedding = %s::vector",
            (document_id, no, text, vector_literal(vector)),
        )
        if cursor.fetchone() is None:
            return False
    return True


# ---------- 실행 ----------

def ingest(connection, name, source, documents, force):
    with connection.cursor() as cursor:
        cursor.execute(
            "INSERT INTO ingest_jobs (target, status, processed, failed, started_at) VALUES (%s, 'RUNNING', 0, 0, now()) RETURNING id",
            (source.target,),
        )
        job_id = cursor.fetchone()[0]
    connection.commit()

    counts = {"inserted": 0, "updated": 0, "unchanged": 0, "failed": 0}
    for document in documents:
        try:
            with connection.transaction():  # 문서 단위 savepoint: 실패한 문서만 되돌린다
                with connection.cursor() as cursor:
                    counts[write_document(cursor, document, force)] += 1
        except Exception as error:  # noqa: BLE001 - 문서 하나의 실패가 전체를 멈추지 않게 한다
            counts["failed"] += 1
            print(f"  실패 {document['source_id']}: {type(error).__name__}", file=sys.stderr)

    with connection.cursor() as cursor:
        cursor.execute(
            "UPDATE ingest_jobs SET status = %s, processed = %s, failed = %s, finished_at = now() WHERE id = %s",
            ("FAILED" if counts["failed"] else "DONE", len(documents) - counts["failed"], counts["failed"], job_id),
        )
    connection.commit()
    print(f"{name}: 추가 {counts['inserted']}, 갱신 {counts['updated']}, 변경 없음 {counts['unchanged']}, 실패 {counts['failed']} (job #{job_id})")
    return counts["failed"]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--source", action="append", choices=sorted(SOURCES), help="적재할 소스(여러 번 지정 가능, 기본 전체)")
    parser.add_argument("--data-dir", default=str(Path(__file__).resolve().parents[2] / "DATA"))
    parser.add_argument("--dry-run", action="store_true", help="검증만 하고 DB에는 쓰지 않는다")
    parser.add_argument("--force", action="store_true", help="변경이 없어도 다시 쓴다")
    args = parser.parse_args()

    names = args.source or list(SOURCES)
    data_dir = Path(args.data_dir)
    loaded = {}
    for name in names:
        loaded[name] = SOURCES[name].read(data_dir)
        chunks = sum(len(d["chunks"]) for d in loaded[name])
        print(f"검증 통과 {name}: 문서 {len(loaded[name])}건, 청크 {chunks}개")
    if args.dry_run:
        print("dry-run: DB에 쓰지 않았습니다")
        return 0

    password = os.environ.get("DB_PASSWORD")
    if not password:
        sys.exit("DB_PASSWORD 환경변수가 필요합니다")
    failed = 0
    with psycopg.connect(
        host=os.environ.get("DB_HOST", "localhost"), port=int(os.environ.get("DB_PORT", "5432")),
        dbname=os.environ.get("DB_NAME", "contractcheck"), user=os.environ.get("DB_USERNAME", "contractcheck"),
        password=password,
    ) as connection:
        for name in names:
            failed += ingest(connection, name, SOURCES[name], loaded[name], args.force)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
