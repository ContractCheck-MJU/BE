package ac.mju.contractcheck.service;

import ac.mju.contractcheck.client.InferenceClient;
import ac.mju.contractcheck.client.InferenceException;
import ac.mju.contractcheck.domain.Classification;
import ac.mju.contractcheck.domain.Clause;
import ac.mju.contractcheck.domain.ClauseClassification;
import ac.mju.contractcheck.domain.ClauseRisk;
import ac.mju.contractcheck.domain.ClauseText;
import ac.mju.contractcheck.domain.EvidenceHit;
import ac.mju.contractcheck.domain.RiskAssessment;
import ac.mju.contractcheck.domain.enums.RiskLevel;
import ac.mju.contractcheck.domain.RiskLevelResolver;
import java.util.List;
import java.util.stream.IntStream;
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
        List<ClauseClassification> classifications = classify(clauses);
        List<float[]> embeddings = embed(clauses);

        List<ClauseRisk> risks = IntStream.range(0, clauses.size())
                .mapToObj(index -> assessOne(clauses.get(index), classifications.get(index), embeddings.get(index)))
                .toList();
        log.info("조항 위험 판정 완료 clauses={} flagged={}", clauses.size(),
                risks.stream().filter(risk -> risk.assessment().level() != RiskLevel.SAFE).count());
        return risks;
    }

    private List<ClauseClassification> classify(List<Clause> clauses) {
        List<ClauseClassification> classifications = inference.classify(
                clauses.stream().map(clause -> new ClauseText(String.valueOf(clause.id()), clause.text())).toList());
        return requireSameSize(classifications, clauses.size(), "분류");
    }

    private List<float[]> embed(List<Clause> clauses) {
        return requireSameSize(embeddingModel.embed(clauses.stream().map(Clause::text).toList()), clauses.size(), "임베딩");
    }

    /** 위험 후보만 근거를 검색한다. 비위험 처리(SAFE)는 RiskLevelResolver가 정하므로 여기서 따로 분기하지 않는다. */
    private ClauseRisk assessOne(Clause clause, ClauseClassification result, float[] embedding) {
        Classification classification = result.classification();
        List<EvidenceHit> hits = classification.risky() ? evidenceRetriever.search(embedding, EvidenceRetriever.DEFAULT_LIMIT) : List.of();
        RiskAssessment assessment = resolver.resolve(classification, hits.stream().map(EvidenceHit::similarity).toList());
        return new ClauseRisk(clause.id(), result.riskType(), classification.probability(), assessment, result.modelVersion(), embedding, hits);
    }

    /** 결과 순서로 조항과 짝을 짓기 때문에 개수가 다르면 잘못된 짝이 생기기 전에 실패시킨다. */
    private static <T> List<T> requireSameSize(List<T> results, int expected, String step) {
        if (results.size() != expected) {
            throw new InferenceException(step + " 결과 개수가 조항 수와 다릅니다. expected=" + expected + " actual=" + results.size());
        }
        return results;
    }
}
