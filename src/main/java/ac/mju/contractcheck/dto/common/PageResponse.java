package ac.mju.contractcheck.dto.common;

import java.util.List;

/** Jackson이 {"items":[...],"page":n} 로 직렬화한다. */
public record PageResponse<T>(List<T> items, int page) {}
