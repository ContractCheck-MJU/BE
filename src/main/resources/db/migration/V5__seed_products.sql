-- FR-PAY-01: 판매할 이용권 상품의 초기 데이터. 상품 구성(이름·횟수·가격)은 스펙에 없어 인메모리 시절의 임시 값을 그대로 옮겼다.
-- 이미 상품이 있는 DB에는 넣지 않는다.
INSERT INTO products (name, credits, price, active)
SELECT v.name, v.credits, v.price, v.active
FROM (VALUES
    ('분석 5회', 5, 4900, TRUE),
    ('분석 20회', 20, 14900, TRUE)
) AS v (name, credits, price, active)
WHERE NOT EXISTS (SELECT 1 FROM products);
