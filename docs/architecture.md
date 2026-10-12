# Auction CQRS — Architecture

An auction system built with **Domain-Driven Design**, **hexagonal architecture** (ports &
adapters) and **CQRS**, with the write and read sides connected by the **Transactional Outbox**
and an asynchronous event pipeline running on the local AWS emulator (Floci).

---

## 1. Overview

```mermaid
flowchart TB
    subgraph CLIENT["Client"]
        direction LR
        C1["POST /auctions<br/>/:id/open · /:id/bids · /:id/close · /:id/cancel<br/><i>commands</i>"]
        C2["GET /auctions/:id<br/>GET /queries/auctions/:id/summary<br/>/hot · /mine<br/><i>queries</i>"]
    end

    subgraph ADAPTERS_IN["Driving adapters"]
        WC["AuctionController<br/><i>HTTP</i>"]
        QC["AuctionQueryController<br/><i>HTTP</i>"]
    end

    subgraph APP["<b>Application</b> — use cases"]
        WAS["AuctionApplicationService<br/><i>implements AuctionCommandUseCase</i><br/>@Transactional · orchestrates"]
        QS["AuctionQueryService<br/><i>implements AuctionQueryUseCase</i>"]
        PRJ["AuctionSummaryProjector<br/><i>applies events to the read model</i>"]
    end

    subgraph PORTS["<b>Ports</b> — interfaces owned by the application"]
        PIN["Driving ports<br/>AuctionCommandUseCase<br/>AuctionQueryUseCase"]
        POUT["Driven ports<br/>AuctionStorePort · BidStorePort<br/>OutboxStorePort · EventPublisherPort<br/>AuctionSummaryStorePort"]
    end

    subgraph DOMAIN["<b>Domain</b> — pure business rules"]
        AGG["<b>Auction</b> aggregate root<br/>min increment · seller≠bidder · reserve<br/>buy-now · anti-sniping"]
        DOMV["Money (value object)<br/>Bid (entity)<br/>AuctionStatus · AuctionCategory"]
        DEVT["Domain events<br/>AuctionCreated · AuctionOpened · BidPlaced<br/>AuctionExtended · AuctionClosed · AuctionCancelled"]
    end

    subgraph ADAPTERS_OUT["Driven adapters — technology"]
        JPA["JpaAuctionStoreAdapter<br/>JpaBidStoreAdapter<br/>JpaOutboxStoreAdapter<br/>JpaAuctionSummaryStoreAdapter"]
        SQSAD["SqsEventPublisherAdapter"]
        CONS["AuctionEventsConsumer<br/><i>@Scheduled 3s</i>"]
    end

    subgraph DATA["Storage"]
        WDB[("PostgreSQL<br/><b>auction</b> · <b>bid</b><br/><b>outbox_event</b><i>write side</i>")]
        RDB[("PostgreSQL<br/><b>auction_summary</b><i>read model</i>")]
    end

    subgraph BROKER["Broker — Floci :4566"]
        SQS[["SQS · auction-events-queue"]]
        DLQ[["DLQ · auction-events-dlq<br/><i>after 3 fails</i>"]]
    end

    C1 ==> WC
    C2 --> QC
    WC -.->|"implements"| PIN
    QC -.->|"implements"| PIN
    PIN --> WAS
    PIN --> QS
    WAS --> AGG
    WAS --> POUT
    QS --> POUT
    POUT -.->|"implemented by"| JPA
    POUT -.->|"implemented by"| SQSAD
    AGG --> DOMV
    AGG --> DEVT
    JPA --> WDB
    JPA --> RDB
    SQSAD --> SQS
    SQS -.-> DLQ
    SQS --> CONS
    CONS --> PRJ
    PRJ --> POUT
    C1 ==>|"1 · immediate"| WC
    WDB ==>|"2 · same transaction"| JPA
    SQSAD ==>|"3 · publish"| SQS
    PRJ ==>|"4 · update"| RDB
    C2 -->|"5 · read"| QC

    classDef client fill:#eceff1,stroke:#607d8b,color:#111
    classDef app fill:#1a3d5c,stroke:#4a90c2,color:#fff
    classDef ports fill:#7a5200,stroke:#e0a800,color:#fff
    classDef domain fill:#0d5c4a,stroke:#3fae8f,color:#fff
    classDef adapter fill:#4a235a,stroke:#9b59b6,color:#fff
    classDef data fill:#2d5016,stroke:#7cb342,color:#fff
    classDef broker fill:#7f6000,stroke:#ffc000,color:#fff

    class C1,C2 client
    class WAS,QS,PRJ app
    class PIN,POUT ports
    class AGG,DOMV,DEVT domain
    class WC,QC,JPA,SQSAD,CONS adapter
    class WDB,RDB data
    class SQS,DLQ broker
```

### The flow in five steps

1. **Command** — the HTTP adapter calls a driving port; the application service loads the
   aggregate through a driven port.
2. **Decide** — the `Auction` aggregate validates the invariants and emits domain events.
3. **Persist atomically** — the write state and the outbox row are committed **in the same
   transaction**. The client gets its response immediately.
4. **Propagate** — a scheduled publisher drains `outbox_event` into SQS (at-least-once).
5. **Project** — a consumer applies each event to `auction_summary`; queries serve that model
   only, never the write tables.

> The system is **eventually consistent**: the write side is immediately correct, the read side
> converges a few seconds later.

### Why no SNS (yet)

An SNS topic only earns its place when **more than one consumer** needs the same event stream —
that is what fan-out means. With a single read model, publishing straight to SQS removes a moving
part without losing anything. When the second read model (`bid_history`) appears, the publisher is
the only class that changes:

```mermaid
flowchart LR
    OUT[("outbox_event")] --> SNS[["SNS topic"]]
    SNS --> Q1["SQS summary-q<br/>→ auction_summary"]
    SNS --> Q2["SQS history-q<br/>→ bid_history"]
    SNS --> Q3["SQS notify-q<br/>→ notifications"]
```

Each queue then gets its own retry and DLQ, so one failing projection cannot stall the others.

---

## 2. Hexagonal layers

```mermaid
flowchart TB
    subgraph CORE["Core — depends on nothing"]
        D1["domain/auction<br/>Auction · Money · Bid<br/>commands · events · exceptions"]
        D2["domain/shared<br/>AggregateRoot · DomainEvent"]
    end

    subgraph INNER["Inner — depends on the core"]
        A1["application/port/in<br/><b>driving ports</b><br/>AuctionCommandUseCase<br/>AuctionQueryUseCase"]
        A2["application/port/out<br/><b>driven ports</b><br/>AuctionStorePort · BidStorePort<br/>OutboxStorePort · EventPublisherPort<br/>AuctionSummaryStorePort"]
        A3["application<br/>service · query · projection · dto"]
    end

    subgraph OUTER["Outer — depends inward"]
        O1["api<br/>controllers · ApiExceptionHandler"]
        O2["infrastructure/persistence/adapter<br/>Jpa*Adapter"]
        O3["infrastructure/read/adapter<br/>JpaAuctionSummaryStoreAdapter"]
        O4["infrastructure/messaging<br/>publisher · consumer · provisioner"]
        O5["infrastructure/config<br/>AwsConfig"]
    end

    O1 --> A1
    A3 --> A1
    A3 --> A2
    A3 --> D1
    O2 -.->|"implements"| A2
    O3 -.->|"implements"| A2
    O4 -.->|"implements"| A2
    O5 --> O4
    D1 --> D2

    classDef core fill:#0d5c4a,stroke:#3fae8f,color:#fff
    classDef inner fill:#1a3d5c,stroke:#4a90c2,color:#fff
    classDef outer fill:#4a235a,stroke:#9b59b6,color:#fff
    class D1,D2 core
    class A1,A2,A3 inner
    class O1,O2,O3,O4,O5 outer
```

**The dependency rule:** arrows point inward only. `domain` and `application` never import from
`infrastructure` — this is enforced by the compiler, not by convention.

**Why it matters here:** the ports are why `AuctionApplicationServiceTest` can run without a
database, and why the read model could later move to DynamoDB by swapping a single adapter.

---

## 3. Detailed sequence — placing a bid

```mermaid
sequenceDiagram
    autonumber
    actor U as Bidder
    participant C as AuctionController
    participant S as AuctionApplicationService
    participant P as AuctionStorePort
    participant A as Auction aggregate
    participant DB as PostgreSQL (write)
    participant PUB as OutboxEventPublisher
    participant EP as EventPublisherPort
    participant Q as SQS queue
    participant K as Consumer
    participant PJ as AuctionSummaryProjector
    participant RM as auction_summary (read)

    U->>C: POST /auctions/{id}/bids
    C->>S: placeBid(PlaceBidCommand)
    S->>P: findById(id)
    P->>DB: SELECT auction
    DB-->>P: AuctionRecord
    P-->>S: AuctionRecord
    S->>A: reconstitute(record)
    Note over A: rules are checked here,<br/>never in the read layer

    alt not OPEN, below increment,<br/>seller bidding, or ended
        A-->>S: AuctionDomainException
        S-->>C: exception
        C-->>U: 422 Business rule violated
    else accepted
        A->>A: placeBid → BidPlaced(v n+1)
        S->>P: save(updated record)
        S->>DB: UPDATE auction
        S->>DB: INSERT bid
        S->>DB: INSERT outbox_event
        Note over DB: ONE transaction —<br/>state and event are atomic
        S-->>C: void
        C-->>U: 200 OK

        PUB->>DB: SELECT ... WHERE published_at IS NULL
        PUB->>EP: publish(event_type, payload)
        EP->>Q: SendMessage
        Q-->>K: message (may repeat)
        K->>PJ: handle(event)
        alt version <= stored version
            PJ-->>PJ: ignored (idempotent)
        else newer version
            PJ->>RM: UPDATE price, bidder, bid_count
            K->>Q: deleteMessage
        end
    end
```

### Guarantees

| Concern | How it is handled |
|---|---|
| Lost event | Outbox row is written in the same transaction as the state |
| Duplicate event | Projector ignores any version ≤ `last_event_version` |
| Out-of-order event | Same version check discards stale updates |
| Poison message | SQS retries 3×, then the message lands in the DLQ |
| Concurrent bids | JPA `@Version` raises `OptimisticLockingFailureException` → HTTP 409 |
| Event ordering per auction | Sequential `aggregate_version`; SQS FIFO is the upgrade path |

### HTTP contract

| Situation | Status | Payload |
|---|---|---|
| Business rule violated | **422** | `{"title":"Business rule violated","detail":"..."}` |
| Unknown auction id | **404** | `{"title":"Resource not found"}` |
| Concurrent modification | **409** | `{"title":"Concurrent modification"}` |
| Malformed payload | **400** | `{"title":"Invalid request"}` |

---

## 4. Aggregate state machine

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

## 5. Read models served from one event stream

```mermaid
flowchart LR
    EV(("BidPlaced<br/>R$ 5.200.000")) --> M1["auction_summary<br/><i>implemented</i>"]
    EV --> M2["bid_history<br/><i>planned</i>"]
    EV --> M3["my_bids<br/><i>planned</i>"]
    EV --> M4["hot_auctions<br/><i>planned</i>"]
    EV --> M5["notifications<br/><i>planned</i>"]
```

Only `auction_summary` exists today. It serves **three screens** from a single denormalized row,
which is the practical payoff of the read model: the ranking query is an index scan instead of a
`COUNT(*) + GROUP BY` over the bid table.

---

## 6. Data model

One database, four tables — the separation is logical, not physical.

| Table | Side | Purpose |
|---|---|---|
| `auction` | write | Aggregate state. `version` = JPA lock, `aggregate_version` = event order |
| `bid` | write | Full bid history |
| `outbox_event` | write | Events not yet published (JSONB payload) |
| `auction_summary` | read | Denormalized projection + indexes on status, bidder, bid_count |

---

## 7. Running it

```bash
docker compose up -d          # Postgres :5433 + Floci :4566
powershell -ExecutionPolicy Bypass -File scripts/run.ps1
```

The queue and DLQ are created automatically by `AwsProvisioner` on startup (idempotent).
Floci UI: `http://localhost:4566/_floci/ui`.

> Port **5433** because a native PostgreSQL already occupies 5432 on this machine.

### Testing

```bash
./mvnw test
powershell -ExecutionPolicy Bypass -File scripts/api-test.ps1
```

| Suite | Count | Scope |
|---|---|---|
| `AuctionTest` | 21 | Invariants, transitions, anti-sniping, reconstitution |
| `AuctionApplicationServiceTest` | 6 | Orchestration with mocked ports (no DB, no broker) |

> **Build note:** Maven builds into `build/` rather than `target/`. The VS Code Java extension
> compiles into `target/classes` with Eclipse JDT and can leave broken class files behind, which
> Maven would then treat as up to date. Keeping the directories apart makes the tools independent.