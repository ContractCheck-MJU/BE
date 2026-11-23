-- 계약을 지우면 계약에 딸린 하위 데이터를 DB가 함께 정리한다(스펙시트 '컬럼 정의서'의 ON DELETE 제안값).
-- 앱 코드(ContractRepository)가 하위 테이블을 하나씩 알고 지우던 구조를 없애, 분석·비교·설명이 영속화돼도 삭제 코드를 고치지 않게 한다.
--
-- 범위: 계약·조항을 뿌리로 하는 하위 테이블만 바꾼다. 회원(users)을 가리키는 FK는 그대로 둔다.
--       회원 탈퇴는 원본 파일(S3) 삭제가 먼저여야 해서 서비스가 순서를 조율한다(FR-AUTH-03).
-- 이미 적용된 V1은 고치지 않고 FK를 새 규칙으로 다시 만든다. FK 이름은 PostgreSQL 기본 규칙(<테이블>_<컬럼>_fkey)이다.

ALTER TABLE clauses
    DROP CONSTRAINT clauses_contract_id_fkey,
    ADD CONSTRAINT clauses_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES contracts (id) ON DELETE CASCADE;

ALTER TABLE analyses
    DROP CONSTRAINT analyses_contract_id_fkey,
    ADD CONSTRAINT analyses_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES contracts (id) ON DELETE CASCADE;

ALTER TABLE risk_results
    DROP CONSTRAINT risk_results_analysis_id_fkey,
    DROP CONSTRAINT risk_results_clause_id_fkey,
    ADD CONSTRAINT risk_results_analysis_id_fkey FOREIGN KEY (analysis_id) REFERENCES analyses (id) ON DELETE CASCADE,
    ADD CONSTRAINT risk_results_clause_id_fkey FOREIGN KEY (clause_id) REFERENCES clauses (id) ON DELETE CASCADE;

ALTER TABLE explanations
    DROP CONSTRAINT explanations_clause_id_fkey,
    ADD CONSTRAINT explanations_clause_id_fkey FOREIGN KEY (clause_id) REFERENCES clauses (id) ON DELETE CASCADE;

ALTER TABLE explanation_citations
    DROP CONSTRAINT explanation_citations_explanation_id_fkey,
    ADD CONSTRAINT explanation_citations_explanation_id_fkey FOREIGN KEY (explanation_id) REFERENCES explanations (id) ON DELETE CASCADE;

ALTER TABLE comparisons
    DROP CONSTRAINT comparisons_before_contract_id_fkey,
    DROP CONSTRAINT comparisons_after_contract_id_fkey,
    ADD CONSTRAINT comparisons_before_contract_id_fkey FOREIGN KEY (before_contract_id) REFERENCES contracts (id) ON DELETE CASCADE,
    ADD CONSTRAINT comparisons_after_contract_id_fkey FOREIGN KEY (after_contract_id) REFERENCES contracts (id) ON DELETE CASCADE;

ALTER TABLE comparison_pairs
    DROP CONSTRAINT comparison_pairs_comparison_id_fkey,
    ADD CONSTRAINT comparison_pairs_comparison_id_fkey FOREIGN KEY (comparison_id) REFERENCES comparisons (id) ON DELETE CASCADE;

-- 일정은 계약이 없어져도 사용자가 남겨 둘 수 있어 행은 두고 계약 연결만 끊는다.
ALTER TABLE milestones
    DROP CONSTRAINT milestones_contract_id_fkey,
    ADD CONSTRAINT milestones_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES contracts (id) ON DELETE SET NULL;
