-- Separates the domain aggregate version (event ordering) from the JPA @Version
-- column, which is only an optimistic locking counter starting at 0 on insert.
ALTER TABLE auction ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 0;

UPDATE auction SET aggregate_version = version + 1 WHERE version > 0;