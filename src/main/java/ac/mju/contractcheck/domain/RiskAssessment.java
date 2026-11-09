package ac.mju.contractcheck.domain;

public record RiskAssessment(RiskLevel level, boolean lowConfidence, boolean evidenceInsufficient) {
}
