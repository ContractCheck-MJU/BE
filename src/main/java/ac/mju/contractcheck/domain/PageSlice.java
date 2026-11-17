package ac.mju.contractcheck.domain;

import java.util.List;

/** 목록 한 페이지와 전체 건수. */
public record PageSlice<T>(List<T> items, long total) {}
