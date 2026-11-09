package ac.mju.contractcheck.dto.common;

import java.util.List;

/** Jackson이 {"items":[...]} 로 직렬화한다. */
public record ItemsResponse<T>(List<T> items) {}
