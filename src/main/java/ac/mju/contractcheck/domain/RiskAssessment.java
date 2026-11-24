package ac.mju.contractcheck.domain;

import ac.mju.contractcheck.domain.enums.RiskLevel;

public record RiskAssessment(RiskLevel level, boolean lowConfidence, boolean evidenceInsufficient) {
}
