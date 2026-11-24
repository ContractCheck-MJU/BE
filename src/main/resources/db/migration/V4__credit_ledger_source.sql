-- FR-CRD-02: 이용권 차감·복구 출처(FREE/PAID)를 원장에 남겨 분석 실패 시 스택(LIFO) 순으로 원래 잔여에 복구한다.
ALTER TABLE credit_ledger
    ADD COLUMN credit_source VARCHAR(10);

CREATE INDEX credit_ledger_user_ref_idx
    ON credit_ledger (user_id, type, ref_type, ref_id);

CREATE UNIQUE INDEX credit_ledger_unique_restore_idx
    ON credit_ledger (user_id, ref_type, ref_id)
    WHERE type = 'RESTORE' AND ref_type IS NOT NULL AND ref_id IS NOT NULL;
