-- Reference schema. The app creates it automatically via Hibernate (ddl-auto: update).
CREATE TABLE payments (
                          id          UUID PRIMARY KEY,
                          merchant_id VARCHAR(255)   NOT NULL,
                          amount      NUMERIC(19, 2) NOT NULL,
                          currency    VARCHAR(3)     NOT NULL,
                          reference   VARCHAR(255)   NOT NULL,
                          status      VARCHAR(20)    NOT NULL,
                          version     BIGINT,
                          created_at  TIMESTAMPTZ    NOT NULL,
                          updated_at  TIMESTAMPTZ    NOT NULL,
                          CONSTRAINT uk_payments_merchant_reference UNIQUE (merchant_id, reference)
);

CREATE TABLE payment_events (
                                id         BIGSERIAL PRIMARY KEY,
                                payment_id  UUID        NOT NULL REFERENCES payments (id),
                                from_status VARCHAR(20),
                                to_status   VARCHAR(20) NOT NULL,
                                source      VARCHAR(20) NOT NULL,
                                created_at  TIMESTAMPTZ NOT NULL
);