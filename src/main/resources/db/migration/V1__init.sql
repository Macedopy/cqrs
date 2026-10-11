CREATE TABLE auction (
    id                    UUID PRIMARY KEY,
    seller_id             UUID        NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    category              VARCHAR(50) NOT NULL DEFAULT 'SINGLE_ITEM',
    lot_quantity          INTEGER     NOT NULL DEFAULT 1,
    lot_unit              VARCHAR(20) NOT NULL DEFAULT 'unit',
    starting_price        NUMERIC(15,2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    minimum_increment     NUMERIC(15,2) NOT NULL,
    reserve_price         NUMERIC(15,2),
    buy_now_price         NUMERIC(15,2),
    starts_at             TIMESTAMPTZ NOT NULL,
    ends_at               TIMESTAMPTZ NOT NULL,
    anti_sniping_seconds  BIGINT      NOT NULL DEFAULT 120,
    status                VARCHAR(20) NOT NULL,
    current_price         NUMERIC(15,2),
    highest_bidder_id     UUID,
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),
    CHECK (starting_price >= 0),
    CHECK (minimum_increment >= 0)
);

CREATE TABLE bid (
    id         UUID PRIMARY KEY,
    auction_id UUID        NOT NULL REFERENCES auction (id),
    bidder_id  UUID        NOT NULL,
    amount     NUMERIC(15,2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    placed_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bid_auction_id ON bid (auction_id);

CREATE TABLE outbox_event (
    id                UUID PRIMARY KEY,
    aggregate_id      UUID        NOT NULL,
    aggregate_type    VARCHAR(50) NOT NULL,
    event_type        VARCHAR(50) NOT NULL,
    payload           JSONB       NOT NULL,
    aggregate_version BIGINT      NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    published_at      TIMESTAMPTZ,
    attempts          INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at)
    WHERE published_at IS NULL;
