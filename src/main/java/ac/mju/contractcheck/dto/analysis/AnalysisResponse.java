package ac.mju.contractcheck.dto.analysis;

import java.util.List;

/** 분석 결과 조회 응답. 진행 중이면 status=ANALYZING이고 results는 비어 있다. */
public record AnalysisResponse(String status, double overallScore, String summary, List<ClauseResultResponse> results) {}
