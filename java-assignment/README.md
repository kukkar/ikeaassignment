# Java Code Assignment

This is a short code assignment that explores various aspects of software development, including API implementation, documentation, persistence layer handling, and testing.

## About the assignment

You will find the tasks of this assignment on [CODE_ASSIGNMENT](CODE_ASSIGNMENT.md) file

## About the code base

This is based on https://github.com/quarkusio/quarkus-quickstarts

### Requirements

To compile and run this demo you will need:

- JDK 17+

In addition, you will need either a PostgreSQL database, or Docker to run one.

### Configuring JDK 17+

Make sure that `JAVA_HOME` environment variables has been set, and that a JDK 17+ `java` command is on the path.

## Building the demo

Execute the Maven build on the root of the project:

```sh
./mvnw package
```

## Running the demo

### Live coding with Quarkus

The Maven Quarkus plugin provides a development mode that supports
live coding. To try this out:

```sh
./mvnw quarkus:dev
```

In this mode you can make changes to the code and have the changes immediately applied, by just refreshing your browser.

    Hot reload works even when modifying your JPA entities.
    Try it! Even the database schema will be updated on the fly.

## (Optional) Run Quarkus in JVM mode

When you're done iterating in developer mode, you can run the application as a conventional jar file.

First compile it:

```sh
./mvnw package
```

Next we need to make sure you have a PostgreSQL instance running (Quarkus automatically starts one for dev and test mode). To set up a PostgreSQL database with Docker:

```sh
docker run -it --rm=true --name quarkus_test -e POSTGRES_USER=quarkus_test -e POSTGRES_PASSWORD=quarkus_test -e POSTGRES_DB=quarkus_test -p 15432:5432 postgres:13.3
```

Connection properties for the Agroal datasource are defined in the standard Quarkus configuration file,
`src/main/resources/application.properties`.

Then run it:

```sh
java -jar ./target/quarkus-app/quarkus-run.jar
```
    Have a look at how fast it boots.
    Or measure total native memory consumption...


## See the demo in your browser

Navigate to:

<http://localhost:8080/index.html>

Have fun, and join the team of contributors!

## Troubleshooting

Using **IntelliJ**, in case the generated code is not recognized and you have compilation failures, you may need to add `target/.../jaxrs` folder as "generated sources".

### Running on JDK 24

This project's Hibernate ORM version bundles a Byte Buddy release that doesn't yet officially
support JDK 24's class file version, and fails proxy generation at build/test time
(`IllegalArgumentException: Java 24 (68) is not supported by ... Byte Buddy`) unless Byte Buddy's
experimental-JDK-support flag is set. `.mvn/jvm.config` and the `maven-surefire-plugin`
configuration in `pom.xml` both set `net.bytebuddy.experimental=true` so `./mvnw clean test` and
`./mvnw clean verify` work out of the box on JDK 24 without any extra flags. On JDK 17-21 this
setting is a no-op.

---

## Assumptions, contradictions, and design decisions

This section documents every ambiguity in the assignment brief that required a judgment call, the
resolution chosen, and why - per the assignment's instruction to record assumptions rather than
resolve them silently.

### 1. Warehouse public identifier: `id` vs `businessUnitCode`

The original OpenAPI spec exposed `/warehouse/{id}`, an `id` field on the response schema that the
mapper never populated, and a commented-out test calling `DELETE /warehouse/1` - while the business
description, the domain model (`Warehouse` has no database id), and the replacement endpoint
(`/warehouse/{businessUnitCode}/replacement`) all treat `businessUnitCode` as the real identifier.

**Resolution (as the assignment's preferred option):** `businessUnitCode` is the sole public
identifier. The OpenAPI path parameters for `GET`/`DELETE /warehouse/{businessUnitCode}` were
renamed accordingly (regenerating `com.warehouse.api.WarehouseResource`,
`archiveTheActiveWarehouseUnitByBusinessUnitCode`/`getTheActiveWarehouseUnitByBusinessUnitCode`),
and the `id` field was removed from the `Warehouse` schema entirely rather than left unpopulated.
Database row ids (`DbWarehouse.id`) stay purely internal to the persistence adapter and never
appear in the domain model, any port, or the API. Two read-only fields, `createdAt`/`archivedAt`,
were added to the response schema instead, since they're the natural way to surface a warehouse's
lifecycle state (active vs. archived, and since when) - directly useful given the domain is built
around archive-and-replace, and requires no domain/persistence changes since both fields already
existed on the model.

### 2. Seed data violating the location capacity rule

`import.sql` created `MWH.001` at `ZWOLLE-001` with `capacity=100`, while `LocationGateway` defines
`ZWOLLE-001` with `maxCapacity=40`. Nothing in the domain models a "grandfathered" or
"over-capacity" warehouse status - every rule (replacement, aggregate capacity checks) assumes
active warehouses are within limits - so grandfathering would require inventing state the domain
doesn't have. **Resolution:** the fixture was corrected to `capacity=40` (the maximum a single
active warehouse could hold at that location) rather than adding an unmodelled exception.

### 3. "Business unit code must not exist" (creation) vs. "reuse the code" (replacement)

**Resolution:** interpreted as "at most one *active* warehouse per business unit code"; archived
(historical) rows may freely share a code. Enforced at three levels: the creation use case checks
`findActiveByBusinessUnitCode(...) == null`; `WarehouseRepository.findActiveByBusinessUnitCode`
always filters `archivedAt IS NULL` (never an unrestricted "first result", since replacement can
leave several historical rows with the same code); and a PostgreSQL **partial unique index**
(`ux_warehouse_active_business_unit_code ... WHERE archivedAt IS NULL`, added in `import.sql`)
enforces the same invariant at the database level, independent of the application code.

### 4. `Location.maxCapacity` as an aggregate

The model comment says "maximum capacity of the location summing all the warehouse capacities."
**Resolution:** implemented literally as an aggregate: creation and replacement both sum the
capacity of every *active* warehouse at the target location and reject if
`existing active capacity + requested capacity > maxCapacity`. Replacement excludes the warehouse
being replaced from that sum (and from the active-count check) when it stays at the same location,
per the assignment's explicit instruction.

### 5. Replacement validation ordering: why `InsufficientCapacityException` needed its own path

The basic field validation rule "stock must not exceed capacity" and the replacement rules "new
stock must equal old stock" + "new capacity must accommodate the old stock" overlap by
construction: if new stock == old stock and new stock <= new capacity, then new capacity >= old
stock always holds - so naively applying the generic cross-field check to a replacement request
first would make `InsufficientCapacityException` unreachable dead code. `WarehouseValidator`
therefore has two entry points: `validateBasicFields` (creation - includes the cross-field check,
since there's no prior state to compare against) and `validateShape` (replacement - field-level
checks only), leaving the cross-field comparison to the replacement-specific `StockMismatchException`
and `InsufficientCapacityException` checks, which run against the warehouse being replaced. This
was caught by `ReplaceWarehouseUseCaseTest` failing with the wrong exception type - see the class
Javadoc on `WarehouseValidator` for the full reasoning.

### 6. Repeated archive requests

Archiving looks up "the active warehouse for this code" and marks it archived. A second archive
call finds nothing active (the first call already archived it) and returns **404**, identical to
archiving a business unit code that never existed. This was a deliberate choice over inventing a
"why 404" distinction (already-archived vs. never-existed): the domain has no notion of "the archived
record for this code" as an addressable resource, only "the active one," so once nothing is active,
there is nothing more specific to report.

### 7. Should `DELETE /store/{id}` sync to the legacy system?

**No, deliberately.** `LegacyStoreManagerGateway` only exposes `createStoreOnLegacySystem` and
`updateStoreOnLegacySystem` - there is no delete operation to call, and inventing one wasn't part of
the assignment's scope. If a future requirement needs it, the extension is mechanical: add a
`StoreDeletedEvent` record, fire it from `StoreResource.delete` (inside the existing
`@Transactional` boundary), and add an `onStoreDeleted` observer to `StoreLegacySyncListener` with
`@Observes(during = TransactionPhase.AFTER_SUCCESS)`, exactly like the two existing observers.

### 8. Store after-commit mechanism: CDI events, not a transactional outbox

`StoreResource` fires immutable `StoreCreatedEvent`/`StoreUpdatedEvent` records (id, name, final
stock) which `StoreLegacySyncListener` observes with
`@Observes(during = TransactionPhase.AFTER_SUCCESS)`. This guarantees the legacy gateway is only
ever called after the originating transaction has committed, and never on rollback (see
`StoreResourceAfterCommitTest`, which proves ordering by having the mocked gateway open a *second*,
independent transaction and check the row is already visible there).

This is **not** a durability guarantee, and is explicitly not meant to be: if the legacy call itself
fails (the observer catches and logs it, rather than swallowing it via `printStackTrace` or letting
it surface as a misleading 500 for an already-successful write), that failure is currently only
visible in logs. The production-grade evolution of this is a **transactional outbox**: write the
"pending legacy sync" record to an outbox table in the *same* transaction as the `Store` change
(so it's atomic with the write, not a best-effort post-commit hook), then have a separate
poller/dispatcher deliver it with retries, exponential backoff, and idempotency (so a redelivery
after a crash mid-dispatch doesn't double-apply), moving failed deliveries to a dead-letter queue
for monitoring/alerting instead of a log line. The CDI-event approach here is the right-sized
solution for this assignment's scope; the outbox is what I'd build before this integration carried
real operational weight.

### 9. Concurrency: what's protected, and what isn't

- **Duplicate active business unit code:** protected at both the application layer (existence check
  before insert) and the database layer (the partial unique index), so even a race that slips past
  the application check is rejected by the database.
- **Same-warehouse replace/archive races:** `ReplaceWarehouseUseCase` looks up the warehouse being
  replaced via `WarehouseStore.lockActiveByBusinessUnitCode`, a `SELECT ... FOR UPDATE`. Under
  PostgreSQL's read-committed semantics, if a concurrent transaction commits an archive of that same
  row while this call is blocked waiting for the lock, the query re-evaluates its `WHERE archivedAt
  IS NULL` clause against the just-committed row once the lock is granted - so the second caller
  correctly observes "no longer active" instead of operating on a stale snapshot.
- **Location count/capacity races:** `WarehouseStore.lockActiveUsageByLocation` takes a
  transaction-scoped PostgreSQL advisory lock keyed by the location identifier
  (`pg_advisory_xact_lock(hashtext(...))`) before computing the active count/capacity. A plain
  row-level lock can't protect this check when a location currently has zero active warehouses (there's
  no row to lock), which is exactly the case an advisory lock closes.
- **What's still a known gap:** this is optimistic-adjacent locking scoped to a single Postgres
  instance; it doesn't extend across a future multi-region or sharded deployment, and a location
  with very high write contention would see requests serialize (correct, but not necessarily fast)
  rather than fail fast. `WarehouseResourceTest#testConcurrentReplacementsOnTheSameWarehouseNeverCorruptState`
  documents the actual guarantee under test: not "exactly one of two concurrent requests wins" (HTTP-level
  test timing can't force true transaction overlap), but the invariant that must hold regardless of
  interleaving - never a lost update, never more than one active row for a code.

---

## Test suite overview

- `LocationGatewayTest` - existing/unknown/blank/null identifier resolution (Task 1).
- `StoreResourceAfterCommitTest` - proves the legacy gateway is called only after commit (with an
  independent-transaction visibility check, not just call-order), never on rollback, receives the
  final persisted state, and that PATCH applies only explicitly-supplied fields (Task 2).
- `CreateWarehouseUseCaseTest`, `ReplaceWarehouseUseCaseTest`, `ArchiveWarehouseUseCaseTest` - fast,
  Quarkus-free unit tests (Mockito) covering every validation branch called out in the assignment.
- `WarehouseRepositoryTest` - `@QuarkusTest` + real PostgreSQL (via Dev Services): active queries
  never return archived rows, capacity/count queries exclude archived rows, multiple historical rows
  may share a business unit code, and the partial unique index rejects a second active row.
- `WarehouseResourceTest` - `@QuarkusTest` + RestAssured with structured JSON assertions (not
  `containsString`): happy paths and representative 400/404/409 responses, replacement's atomic
  rollback (state verified directly via the repository, not just the HTTP status), and the
  concurrency invariant described above.
- `WarehouseEndpointIT` - `@QuarkusIntegrationTest` smoke test against the packaged application.
  Note: this class is only executed by `./mvnw verify` (via the Failsafe plugin binding added to the
  main build - previously Failsafe was only configured inside the `native` profile, so `*IT.java`
  classes were never run by either `mvn test` or a plain `mvn verify`).

Run the fast suite with `./mvnw test`; run everything, including the packaged-jar smoke test, with
`./mvnw verify`. Both require Docker (for Quarkus Dev Services' PostgreSQL container) unless a
PostgreSQL instance is already reachable at the configured URL.