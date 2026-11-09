package ac.mju.contractcheck.dto.analysis;

import java.util.List;

public record AnalysisResponse(double overallScore, String summary, List<Object> results) {}
