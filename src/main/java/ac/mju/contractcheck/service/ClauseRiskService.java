package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.InferenceClient;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.ClauseText;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.domain.RiskAssessment;
import ac.mju.contractcheck.domain.RiskLevel;
import ac.mju.contractcheck.domain.RiskLevelResolver;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * FR-ANL-01 조항 위험 분류의 판정 단계. 조항을 추론 서버에 배치로 보내 위험 여부·유형·확률과 임베딩을 받고,
 * 위험 후보만 pgvector에서 근거를 찾아 {@link RiskLevelResolver}로 HIGH/CAUTION/SAFE를 정한다.
 *
 * <p>트랜잭션을 열지 않는다. 외부(추론 서버) 호출이 있어 호출하는 쪽도 트랜잭션 밖에서 불러야 한다.
 * 이용권 차감, 분석 상태 전이, 결과 저장, 비동기 실행은 {@link AnalysisService}의 몫이고 이 서비스는 판정만 한다.
 * 추론 서버는 내부망이라 마스킹 전 조항도 받을 수 있지만, 스펙상 저장된 조항은 마스킹본이다(FR-UPL-04).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClauseRiskService {

    @NonNull
    private final InferenceClient inference;
    @NonNull
    private final EmbeddingModel embeddingModel;
    @NonNull
    private final EvidenceRetriever evidenceRetriever;
    @NonNull
    private final RiskLevelResolver resolver;

    /**
     * FR-ANL-01 조항 위험 분류
     *
     * 1. /classify(InferenceClient)와 /embed(Spring AI EmbeddingModel 어댑터)를 조항 전체에 대해 배치로 한 번씩 부른다(단건 루프 없음).
     * 2. 위험으로 분류된 조항만 임베딩으로 근거를 검색하고(TC-ANL-018), 비위험은 검색하지 않는다.
     * 3. 위험 확률이 임계값 미만이면 HIGH로 확정하지 않고 CAUTION + lowConfidence다(TC-ANL-017).
     *
     * TODO(FR-ANL-01): kftc_cases(공정위 시정 사례) 검색과 similar_case_ids. 적재 데이터가 생기면 추가한다.
     * 스펙 미정: 임베딩 실패 시 "검색 생략" 처리(지금은 InferenceException을 그대로 올려 분석 실패로 본다),
     * 리랭크 점수(지금은 코사인 유사도), 비위험 조항의 probability 의미.
     *
     * @return 입력과 같은 순서의 판정 결과
     * @throws ac.mju.contractcheck.client.InferenceException 추론 서버 호출이 실패했을 때
     */
    public List<ClauseRisk> assess(@NonNull List<Clause> clauses) {
        if (clauses.isEmpty()) {
            return List.of();
        }
        List<ClauseClassification> classifications = inference.classify(
                clauses.stream().map(clause -> new ClauseText(String.valueOf(clause.id()), clause.text())).toList());
        List<float[]> embeddings = embeddingModel.embed(clauses.stream().map(Clause::text).toList());

        List<ClauseRisk> risks = new ArrayList<>(clauses.size());
        int riskyCount = 0;
        for (int index = 0; index < clauses.size(); index++) {
            ClauseClassification result = classifications.get(index);
            float[] embedding = embeddings.get(index);
            List<EvidenceHit> hits = List.of();
            if (result.classification().risky()) {
                riskyCount++;
                hits = evidenceRetriever.search(embedding, EvidenceRetriever.DEFAULT_LIMIT);
            }
            RiskAssessment assessment = result.classification().risky()
                    ? resolver.resolve(result.classification(), hits.stream().map(EvidenceHit::similarity).toList())
                    : new RiskAssessment(RiskLevel.SAFE, false, false);
            risks.add(new ClauseRisk(
                    clauses.get(index).id(),
                    result.riskType(),
                    result.classification().probability(),
                    assessment,
                    result.modelVersion(),
                    embedding,
                    hits));
        }
        log.info("조항 위험 판정 완료 clauses={} risky={}", clauses.size(), riskyCount);
        return risks;
    }
}
