-- Initial schema of the spending limit control service.

CREATE TABLE limits
(
    id                       BIGSERIAL PRIMARY KEY,
    account_number           BIGINT                   NOT NULL,
    limit_sum                NUMERIC(19, 2)           NOT NULL,
    limit_currency_shortname VARCHAR(3)               NOT NULL DEFAULT 'USD',
    expense_category         VARCHAR(16)              NOT NULL,
    limit_datetime           TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_limits_sum_positive CHECK (limit_sum > 0),
    CONSTRAINT chk_limits_category CHECK (expense_category IN ('product', 'service'))
);

COMMENT ON TABLE limits IS 'Monthly spending limits; rows are immutable';
COMMENT ON COLUMN limits.limit_datetime IS 'Establishment date, stamped by the service';

-- Lookup of the limit in force at the moment of an operation.
CREATE INDEX idx_limits_lookup ON limits (account_number, expense_category, limit_datetime DESC);

CREATE TABLE transactions
(
    id                 BIGSERIAL PRIMARY KEY,
    account_from       BIGINT                   NOT NULL,
    account_to         BIGINT                   NOT NULL,
    currency_shortname VARCHAR(3)               NOT NULL,
    sum                NUMERIC(19, 2)           NOT NULL,
    sum_usd            NUMERIC(19, 2)           NOT NULL,
    expense_category   VARCHAR(16)              NOT NULL,
    datetime           TIMESTAMP WITH TIME ZONE NOT NULL,
    limit_exceeded     BOOLEAN                  NOT NULL DEFAULT FALSE,
    limit_id           BIGINT                   REFERENCES limits (id),

    CONSTRAINT chk_transactions_sum_positive CHECK (sum > 0),
    CONSTRAINT chk_transactions_category CHECK (expense_category IN ('product', 'service'))
);

COMMENT ON TABLE transactions IS 'Client spending operations';
COMMENT ON COLUMN transactions.sum_usd IS 'Amount in USD at the closing rate of the spending date';
COMMENT ON COLUMN transactions.limit_exceeded IS 'Technical flag marking a monthly limit breach';
COMMENT ON COLUMN transactions.limit_id IS 'Applied limit; NULL when the default limit was in force';

-- Aggregation of the accumulated monthly spend per category.
CREATE INDEX idx_transactions_spend ON transactions (account_from, expense_category, datetime);

-- Selection of the transactions of a client that breached their limit.
CREATE INDEX idx_transactions_exceeded ON transactions (account_from, limit_exceeded);
