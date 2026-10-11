CREATE TABLE auction_summary (
    auction_id        UUID PRIMARY KEY,
    seller_id         UUID        NOT NULL,
    title             VARCHAR(255) NOT NULL,
    category          VARCHAR(50) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    current_price     NUMERIC(15,2),
    highest_bidder_id UUID,
    bid_count         INTEGER     NOT NULL DEFAULT 0,
    ends_at           TIMESTAMPTZ NOT NULL,
    last_event_version BIGINT     NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auction_summary_status ON auction_summary (status);

CREATE INDEX idx_auction_summary_highest_bidder ON auction_summary (highest_bidder_id);

CREATE INDEX idx_auction_summary_hot ON auction_summary (bid_count DESC);