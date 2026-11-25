-- 결제 대행사(토스) 호출 결과를 호출마다 한 행씩 남기는 이력. payments.raw는 승인 성공 응답 하나만 담아 실패·재시도 응답이 사라지므로
-- 응답을 로그 파일이 아니라 DB에 보존한다. 상태 전이와 같은 트랜잭션으로 INSERT하며 수정·삭제하지 않는다(append-only).
CREATE TABLE payment_gateway_logs (
    id           BIGSERIAL PRIMARY KEY,
    payment_id   BIGINT       NOT NULL REFERENCES payments (id),
    action       VARCHAR(20)  NOT NULL,
    outcome      VARCHAR(20)  NOT NULL,
    http_status  INT,
    response     JSONB,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX payment_gateway_logs_payment_idx ON payment_gateway_logs (payment_id, id);
