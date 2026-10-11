# Auction CQRS — Architecture

High-level view of the auction system: **Command side** (write) and **Query side** (read),
connected by the **Transactional Outbox** and an **asynchronous event pipeline** running on
the local AWS emulator (Floci).

---

## 1. Overview

```mermaid
flowchart TB
    %% ---------- Client ----------
    subgraph CLIENT["Client"]
        direction LR
        C1["POST /auctions<br/>POST /auctions/:id/bids<br/><i>commands</i>"]
        C2["GET /queries/auctions/:id/summary<br/>GET /queries/auctions/hot<br/><i>queries</i>"]
    end

    %% ---------- WRITE SIDE ----------
    subgraph WRITE["WRITE SIDE — strong consistency"]
        direction TB
        WC["AuctionController<br/><i>REST / HTTP</i>"]
        WAS["AuctionApplicationService<br/><i>@Transactional</i><br/>load → decide → persist"]
        AGG["<b>Auction</b> aggregate<br/><i>domain rules</i><br/>min increment · anti-sniping<br/>reserve · buy-now · seller≠bidder"]
        WDB[("PostgreSQL<br/><b>auction</b> · <b>bid</b><br/><b>outbox_event</b><br/>version · aggregate_version")]

        WC --> WAS --> AGG --> WDB
    end

    %% ---------- EVENT PIPELINE ----------
    subgraph PIPE["EVENT PIPELINE — at-least-once"]
        direction TB
        PUB["OutboxEventPublisher<br/><i>@Scheduled 5s</i><br/>reads unpublished rows"]
        SNS[["SNS topic<br/>auction-events<br/><i>fan-out</i>"]]
        SQS[["SQS queue<br/>auction-events-queue"]]
        DLQ[["DLQ<br/>auction-events-dlq<br/><i>after 3 fails</i>"]]
        CONS["AuctionEventsConsumer<br/><i>@Scheduled 3s</i><br/>long poll · 10 msgs"]

        PUB -->|"publish event_type"| SNS --> SNS --> SQS --> CONS
        SQS -.->|"failed"| DLQ
    end

    %% ---------- READ SIDE ----------
    subgraph READ["READ SIDE — eventual consistency"]
        direction TB
        PROJ["AuctionSummaryProjector<br/><i>idempotent by version</i><br/>ignores event if version ≤ stored"]
        RDB[("PostgreSQL<br/><b>auction_summary</b><br/><i>read model</i><br/>idx: status · bidder · bid_count")]
        QS["AuctionQueryService<br/><i>reads the read model only</i>"]
        QC["AuctionQueryController<br/>GET /queries/**"]

        PROJ --> RDB --> QS --> QC
    end

    %% ---------- INFRA ----------
    subgraph INFRA["Local infrastructure — docker compose"]
        FLO["Floci — AWS emulator :4566<br/><i>SNS · SQS</i>"]
        PG["PostgreSQL 17 :5433"]
    end

    %% ---------- FLOWS ----------
    C1 ==>|"1. synchronous<br/>immediate 200/201"| WC
    C2 -->|"3. read"| QC

    WDB ==>|"2. same transaction<br/>state + event"| PUB
    SQS --> CONS --> PROJ
    FLO -.-> SNS
    FLO -.-> SQS
    PG -.-> WDB
    PG -.-> RDB

    %% ---------- STYLES ----------
    classDef write fill:#1f4e79,stroke:#5b9bd5,color:#fff
    classDef pipe fill:#7f6000,stroke:#ffc000,color:#fff
    classDef read fill:#375623,stroke:#70ad47,color:#fff
    classDef infra fill:#3f3f3f,stroke:#a6a6a6,color:#fff
    classDef client fill:#e7e6e6,stroke:#808080,color:#000

    class WC,WAS,AGG,WDB write
    class PUB,SNS,SQS,DLQ,CONS pipe
    class PROJ,RDB,QS,QC read
    class FLO,PG infra
    class C1,C2 client
```

### The flow in three steps

1. **Write** — the command is validated by the `Auction` aggregate and the new state plus the
   domain event are committed **in the same transaction**. The client gets its response immediately.
2. **Propagate** — a scheduled publisher drains `outbox_event` rows into an SNS topic, which
   fans out to SQS. Delivery is *at-least-once*.
3. **Read** — a consumer applies each event to the `auction_summary` read model, and queries
   serve that model — never the write tables.

> The system is **eventually consistent**: the write side is immediately correct, the read side
> converges a few seconds later. This is the deliberate trade-off that makes reads cheap and
> independently scalable.

---

## 2. Detailed sequence — placing a bid

```mermaid
sequenceDiagram
    autonumber
    actor U as Bidder
    participant API as AuctionController
    participant SVC as AuctionApplicationService
    participant AGG as Auction aggregate
    participant PG as PostgreSQL (write)
    participant PUB as OutboxEventPublisher
    participant SNS as SNS auction-events
    participant SQS as SQS queue
    participant CON as Consumer
    participant PRJ as AuctionSummaryProjector
    participant RM as auction_summary (read)

    U->>API: POST /auctions/{id}/bids
    API->>SVC: placeBid(PlaceBidCommand)
    SVC->>PG: SELECT auction WHERE id = ?
    PG-->>SVC: AuctionEntity
    SVC->>AGG: reconstitute(entity)

    Note over AGG: rules are checked here,<br/>never in the read layer

    alt auction not OPEN / below minimum / seller bidding
        AGG-->>SVC: throws AuctionDomainException
        SVC-->>API: 4xx rejected
    else bid accepted
        AGG->>AGG: applyBid → BidPlaced(v n+1)
        SVC->>PG: UPDATE auction (status, current_price, ends_at)
        SVC->>PG: INSERT bid
        SVC->>PG: INSERT outbox_event (BidPlaced)
        Note over PG: ONE transaction —<br/>state and event are atomic
        PG-->>SVC: commit
        SVC-->>API: 200 OK
        API-->>U: bid accepted

        PUB->>PG: SELECT … WHERE published_at IS NULL
        PUB->>SNS: publish(event_type = BidPlaced)
        SNS-->>SQS: deliver to subscriber
        SQS-->>CON: message (may repeat)
        CON->>PRJ: handle(event)
        alt version ≤ stored version
            PRJ-->>CON: ignored (idempotent)
        else newer version
            PRJ->>RM: UPDATE summary (price, bidder, bid_count)
            CON->>SQS: deleteMessage
        end
    end
```

**Guarantees**

| Concern | How it is handled |
|---|---|
| Lost event | Outbox row is written in the same transaction as the state |
| Duplicate event | Projector ignores any version ≤ `last_event_version` |
| Out-of-order event | Same version check discards stale updates |
| Poison message | SQS retries 3×, then the message lands in the DLQ |
| Concurrent bids | JPA `@Version` raises `OptimisticLockingFailureException` |
| Event ordering per auction | Sequential `aggregate_version`; SQS FIFO is the upgrade path |

---

## 3. Aggregate state machine

```mermaid
stateDiagram-v2
    [*] --> DRAFT : AuctionCreated
    DRAFT --> OPEN : AuctionOpened
    OPEN --> OPEN : BidPlaced / AuctionExtended
    OPEN --> SOLD : close (reserve met) / buy-now
    OPEN --> UNSOLD : close (no bids or reserve not met)
    DRAFT --> CANCELLED : AuctionCancelled
    OPEN --> CANCELLED : AuctionCancelled
    SOLD --> [*]
    UNSOLD --> [*]
    CANCELLED --> [*]
```

---

## 4. Read models served from one event stream

```mermaid
flowchart LR
    EV(("BidPlaced<br/>R$ 5.200.000")) --> M1["auction_summary<br/><i>current price · status</i>"]
    EV --> M2["bid_history<br/><i>full bid timeline</i>"]
    EV --> M3["my_bids<br/><i>user is winning / outbid</i>"]
    EV --> M4["hot_auctions<br/><i>bid_count ranking</i>"]
    EV --> M5["notifications<br/><i>'you were outbid'</i>"]

    M1 --> Q1["GET /summary"]
    M2 --> Q2["GET /history"]
    M3 --> Q3["GET /mine"]
    M4 --> Q4["GET /hot"]
    M5 --> Q5["email / push"]
```

Only `auction_summary` is implemented today. Each additional model would get its **own SQS
queue**, so a failing projection never blocks the others — and this fan-out is what the SNS
topic exists for.

---

## 5. Packages

```mermaid
flowchart TB
    subgraph SRC["com.auction"]
        A["api<br/><i>controllers</i>"]
        B["application<br/><i>service · query · projection · dto</i>"]
        C["domain.auction<br/><i>aggregate · commands · events<br/>exceptions · Money · Bid</i>"]
        D["domain.shared<br/><i>AggregateRoot · DomainEvent</i>"]
        E["infrastructure.persistence<br/><i>JPA entities · repositories</i>"]
        F["infrastructure.read<br/><i>read model entity · repository</i>"]
        G["infrastructure.messaging<br/><i>publisher · consumer · provisioner</i>"]
        H["infrastructure.config<br/><i>AWS clients → Floci</i>"]
    end

    A --> B
    B --> C
    C --> D
    B --> E
    G --> F
    B --> G
    G --> H
```

The dependency rule holds: **domain knows nothing** about JPA, HTTP or AWS.

---

## 6. Local infrastructure

| Container | Port | Purpose |
|---|---|---|
| `auction-postgres` | 5433 | write side + read model |
| `auction-floci` | 4566 | SNS + SQS emulator (`/_floci/ui`) |

> Port 5433 instead of 5432 because a native PostgreSQL already occupies 5432 on this machine.