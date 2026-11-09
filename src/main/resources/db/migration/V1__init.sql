-- 스펙시트 'DB 스키마' 탭. 임베딩은 BGE-M3 1024차원(pgvector).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) UNIQUE,
    nickname    VARCHAR(50),
    role        VARCHAR(20) NOT NULL,
    status      VARCHAR(20),
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ
);

CREATE TABLE oauth_accounts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users (id),
    provider          VARCHAR(20),
    provider_user_id  VARCHAR(100),
    created_at        TIMESTAMPTZ,
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE contracts (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT REFERENCES users (id),
    title          VARCHAR(200),
    contract_type  VARCHAR(20),
    s3_key         VARCHAR(500),
    status         VARCHAR(20),
    masked_text    TEXT,
    ocr_raw        JSONB,
    created_at     TIMESTAMPTZ
);

CREATE TABLE clauses (
    id           BIGSERIAL PRIMARY KEY,
    contract_id  BIGINT REFERENCES contracts (id),
    clause_no    INT,
    title        VARCHAR(200),
    text         TEXT,
    embedding    VECTOR(1024)
);

CREATE TABLE analyses (
    id             BIGSERIAL PRIMARY KEY,
    contract_id    BIGINT REFERENCES contracts (id),
    model_version  VARCHAR(50),
    overall_score  NUMERIC(5, 2),
    summary        TEXT,
    status         VARCHAR(20),
    created_at     TIMESTAMPTZ
);

CREATE TABLE risk_results (
    id                BIGSERIAL PRIMARY KEY,
    analysis_id       BIGINT REFERENCES analyses (id),
    clause_id         BIGINT REFERENCES clauses (id),
    is_risky          BOOLEAN,
    risk_level        VARCHAR(10),
    risk_type         VARCHAR(50),
    probability       NUMERIC(5, 4),
    similar_case_ids  BIGINT[]
);

CREATE TABLE comparisons (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT REFERENCES users (id),
    before_contract_id  BIGINT REFERENCES contracts (id),
    after_contract_id   BIGINT REFERENCES contracts (id),
    created_at          TIMESTAMPTZ
);

CREATE TABLE comparison_pairs (
    id                BIGSERIAL PRIMARY KEY,
    comparison_id     BIGINT REFERENCES comparisons (id),
    before_clause_id  BIGINT,
    after_clause_id   BIGINT,
    similarity        NUMERIC(5, 4),
    change_type       VARCHAR(10),
    risk_delta        INT
);

CREATE TABLE explanations (
    id              BIGSERIAL PRIMARY KEY,
    clause_id       BIGINT REFERENCES clauses (id),
    text            TEXT,
    suggestion      TEXT,
    llm_model       VARCHAR(50),
    prompt_version  VARCHAR(20),
    created_at      TIMESTAMPTZ
);

CREATE TABLE explanation_citations (
    id              BIGSERIAL PRIMARY KEY,
    explanation_id  BIGINT REFERENCES explanations (id),
    source_type     VARCHAR(20),
    source_id       BIGINT,
    score           NUMERIC(5, 4)
);

CREATE TABLE milestones (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT REFERENCES users (id),
    contract_id  BIGINT REFERENCES contracts (id),
    type         VARCHAR(30),
    title        VARCHAR(200),
    due_date     DATE,
    remind_days  INT[],
    done         BOOLEAN
);

CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT REFERENCES users (id),
    milestone_id  BIGINT REFERENCES milestones (id),
    channel       VARCHAR(10),
    title         TEXT,
    body          TEXT,
    scheduled_at  TIMESTAMPTZ,
    sent_at       TIMESTAMPTZ,
    status        VARCHAR(10),
    read_at       TIMESTAMPTZ
);

CREATE TABLE notification_settings (
    user_id        BIGINT PRIMARY KEY REFERENCES users (id),
    email_enabled  BOOLEAN,
    push_enabled   BOOLEAN,
    remind_days    INT[]
);

CREATE TABLE push_subscriptions (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT REFERENCES users (id),
    endpoint  TEXT UNIQUE,
    p256dh    VARCHAR(255),
    auth      VARCHAR(255)
);

CREATE TABLE products (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(100),
    credits  INT,
    price    INT,
    active   BOOLEAN
);

CREATE TABLE payments (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT REFERENCES users (id),
    product_id   BIGINT REFERENCES products (id),
    order_id     VARCHAR(64) UNIQUE,
    payment_key  VARCHAR(200) UNIQUE,
    amount       INT,
    status       VARCHAR(20),
    method       VARCHAR(30),
    approved_at  TIMESTAMPTZ,
    raw          JSONB
);

CREATE TABLE payment_webhook_events (
    id           BIGSERIAL PRIMARY KEY,
    event_type   VARCHAR(50),
    payment_key  VARCHAR(200),
    payload      JSONB,
    processed    BOOLEAN,
    received_at  TIMESTAMPTZ
);

CREATE TABLE credit_wallets (
    user_id         BIGINT PRIMARY KEY REFERENCES users (id),
    free_remaining  INT,
    paid_remaining  INT,
    version         BIGINT
);

CREATE TABLE credit_ledger (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users (id),
    type        VARCHAR(10),
    amount      INT,
    ref_type    VARCHAR(20),
    ref_id      BIGINT,
    created_at  TIMESTAMPTZ
);

CREATE TABLE legal_documents (
    id          BIGSERIAL PRIMARY KEY,
    doc_type    VARCHAR(20),
    source_id   VARCHAR(100),
    title       VARCHAR(500),
    ref         VARCHAR(200),
    court       VARCHAR(100),
    decided_at  DATE,
    content     TEXT,
    url         VARCHAR(500),
    fetched_at  TIMESTAMPTZ,
    UNIQUE (doc_type, source_id)
);

CREATE TABLE legal_chunks (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT REFERENCES legal_documents (id),
    chunk_no     INT,
    text         TEXT,
    embedding    VECTOR(1024)
);
CREATE INDEX legal_chunks_embedding_idx
    ON legal_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE kftc_cases (
    id                 BIGSERIAL PRIMARY KEY,
    press_date         DATE,
    press_title        VARCHAR(300),
    source_url         VARCHAR(500),
    business           VARCHAR(500),
    industry           VARCHAR(50),
    unfair_type        VARCHAR(50),
    law_article        VARCHAR(200),
    before_text        TEXT,
    after_text         TEXT,
    after_is_deletion  SMALLINT,
    note               TEXT,
    embedding          VECTOR(1024)
);
CREATE INDEX kftc_cases_embedding_idx
    ON kftc_cases USING hnsw (embedding vector_cosine_ops);

CREATE TABLE ingest_jobs (
    id           BIGSERIAL PRIMARY KEY,
    target       VARCHAR(30),
    status       VARCHAR(20),
    processed    INT,
    failed       INT,
    started_at   TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ
);
