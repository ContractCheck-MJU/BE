package ac.mju.contractcheck.repository.memory;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 임시 저장소. 공정위 시정 사례({@code kftc_cases}) 적재 데이터가 생기면
 * {@code EvidenceRepository} 방식의 pgvector 검색으로 옮긴다. 적재 전에는 빈 결과를 반환한다.
 */
@Repository
public class PrecedentRepository {

    public List<Map<String, Object>> searchCases(String query, Long clauseId, String unfairType, int k) {
        return List.of();
    }
}
