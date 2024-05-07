-- Schema of the exchange rate store.
-- The application creates the keyspace and the table at startup; this script exists
-- for manual provisioning and as documentation of the schema.

CREATE KEYSPACE IF NOT EXISTS transactions
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
     AND durable_writes = true;

USE transactions;

-- One partition per currency pair, clustered by date in descending order: the query
-- "latest rate not after date X" reads a single row from the head of the partition,
-- which is what gives the previous close on weekends and holidays.
CREATE TABLE IF NOT EXISTS exchange_rates
(
    pair        text,
    rate_date   date,
    close_value decimal,
    PRIMARY KEY ((pair), rate_date)
) WITH CLUSTERING ORDER BY (rate_date DESC);
