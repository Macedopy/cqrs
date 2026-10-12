# Auction CQRS

Auction system built with **Domain-Driven Design**, **hexagonal architecture** (ports & adapters)
and **CQRS**, using **Java 25** + **Spring Boot 4.1** on a local **AWS emulator (Floci)** — no AWS
account, no credentials, no cost.

---

## Quick start

Two steps. Docker Desktop must be running.

### 1. Start the infrastructure (Postgres + Floci)

```bash
docker compose up -d
```

This starts two containers:

| Container | Port | Purpose |
|---|---|---|
| `auction-postgres` | **5433** | database — write side and read model |
| `auction-floci` | **4566** | AWS emulator (SQS + DLQ) |

Verify they are healthy:

```bash
docker compose ps
```

You should see `healthy` for both. The first run downloads the images and can take a minute.

> **Why port 5433?** A native PostgreSQL already occupies 5432 on this machine, so the container
> is mapped to 5433 to avoid the conflict.

### 2. Start the application

**Foreground** — occupies the terminal, which is the expected behaviour:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run.ps1
```

**Detached** — the API keeps running after you close the terminal:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-detached.ps1
```

It waits until the API answers on port 8080 before returning, and writes the log to `run.out`:

```
API is up on http://localhost:8080
Started AuctionApiApplication in 15.515 seconds
Provisioned queue=http://localhost:4566/.../auction-events-queue dlq=...
```

Follow the log from another terminal:

```powershell
Get-Content run.out -Wait -Tail 20
```

Wait for these two log lines:

```
Started AuctionApiApplication in 11.1 seconds
Provisioned queue=http://localhost:4566/000000000000/auction-events-queue dlq=...
```

The API is now on **http://localhost:8080**.

---

## Floci is wired automatically

**You do not create any AWS resource by hand.** On startup, `AwsProvisioner` creates the queue
and the dead-letter queue, and wires the redrive policy. It is idempotent, so restarts never
duplicate anything.

To inspect the resources, open the Floci web console:

```
http://localhost:4566/_floci/ui
```

You can also inspect the database directly:

```bash
docker exec -it auction-postgres psql -U auction -d auction
```

```sql
\dt
SELECT event_type, aggregate_version, published_at IS NOT NULL AS published
  FROM outbox_event ORDER BY occurred_at;
SELECT * FROM auction_summary;
```

### Stopping

```powershell
powershell -ExecutionPolicy Bypass -File scripts/stop.ps1              # app only
powershell -ExecutionPolicy Bypass -File scripts/stop.ps1 -WithDocker  # app + containers
```

### Troubleshooting

**The API dies when you close the terminal**

You started it with `run.ps1` in the foreground. Use `start-detached.ps1` instead, which launches
the process independently of the shell.

**Port already in use**

```bash
docker compose ps          # see what is mapped
netstat -ano | findstr ":5433"
netstat -ano | findstr ":4566"
```

**Start over from scratch (destroys the data)**

```bash
docker compose down -v     # -v also removes the Postgres volume
docker compose up -d
```

**`ClassNotFoundException` without a package name, or the app fails on startup**

The VS Code Java extension (Eclipse JDT) compiles into `target/classes` and can leave broken
class files that Maven treats as up to date. Maven builds into `build/` instead, which keeps the
two tools apart. Always use `scripts/run.ps1` (it runs `clean` first). Reopening VS Code also lets
`.vscode/settings.json` take effect.

**Floci cannot reach the Docker socket**

Floci needs the socket to manage container-backed services. If the log shows
`Transient docker I/O error`, check that `/var/run/docker.sock` is mounted in `docker-compose.yml`
(Windows Docker Desktop requires this).

---

## Verify the whole system

```powershell
powershell -ExecutionPolicy Bypass -File scripts/api-test.ps1
```

15 steps covering the create → open → bid → close flow, the buy-now flow, the cancel flow, the
read-side queries and the negative cases (bid below the minimum increment, seller bidding on
their own auction, bid on a closed auction).

On Linux/macOS/Git Bash:

```bash
bash scripts/api-test.sh
```

### Example

```powershell
# create
$id = (curl.exe -s -X POST http://localhost:8080/auctions `
  -H "Content-Type: application/json" -d @auction.json).Trim('"')

# command side
curl.exe -s -X POST "http://localhost:8080/auctions/$id/open"
curl.exe -s -X POST "http://localhost:8080/auctions/$id/bids" `
  -H "Content-Type: application/json" `
  -d '{"bidderId":"22222222-2222-2222-2222-222222222222","amount":"100000.00","currency":"BRL"}'

# read side (after ~10s, once the pipeline has drained the outbox)
curl.exe -s "http://localhost:8080/queries/auctions/$id/summary"
```

---

## Tests

```bash
./mvnw test
```

| Suite | Tests | Scope |
|---|---|---|
| `AuctionTest` | 21 | domain invariants, state transitions, anti-sniping, reconstitution |
| `AuctionApplicationServiceTest` | 6 | orchestration with mocked ports — no database, no broker |

---

## Requirements

| Tool | Version |
|---|---|
| Java | 25 |
| Docker Desktop | any recent |
| Maven | bundled (`./mvnw`) |

Set `JAVA_HOME` to a JDK 25 installation before running:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
```

---

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — hexagonal layers, ports, event pipeline,
  aggregate state machine, data model and the full API contract.

## Project layout

```
├── docker-compose.yml          Postgres :5433 + Floci :4566
├── docs/architecture.md        architecture and diagrams
├── scripts/
│   ├── run.ps1                 clean build + start (foreground)
│   ├── start-detached.ps1      start and wait until the API answers
│   ├── stop.ps1                stop the app (-WithDocker stops containers too)
│   ├── api-test.ps1            end-to-end API walkthrough
│   └── api-test.sh             same, for bash
└── src/
    ├── main/java/com/auction/
    │   ├── domain/             business rules — depends on nothing
    │   ├── application/
    │   │   ├── port/in/        driving ports (use case contracts)
    │   │   ├── port/out/       driven ports (store, broker)
    │   │   ├── service/        command handling
    │   │   ├── query/          read side
    │   │   └── projection/     read model updates
    │   ├── infrastructure/
    │   │   ├── persistence/    JPA adapters
    │   │   ├── read/           read model adapters
    │   │   ├── messaging/      SQS publisher, consumer, provisioner
    │   │   └── config/         AWS clients
    │   └── api/                REST controllers, exception handler
    └── main/resources/db/migration/   Flyway migrations
```