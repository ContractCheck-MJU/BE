package ac.mju.contractcheck.repository;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 저장소. 영속화 시 V1__init.sql의 {@code legal_documents}, {@code legal_chunks},
 * {@code kftc_cases} 테이블에 매핑한다. 검색은 {@code ORDER BY embedding <=> ?}와 HNSW 인덱스를 사용한다.
 * 적재 전에는 빈 결과를 반환한다.
 */
@Repository
public class PrecedentRepository {

    public List<Map<String, Object>> searchPrecedents(String query, String type, int k) {
        return List.of();
    }

    public List<Map<String, Object>> searchCases(String query, Long clauseId, String unfairType, int k) {
        return List.of();
    }
}
