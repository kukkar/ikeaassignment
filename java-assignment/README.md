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

Ambiguities in the brief that needed a judgment call, and the resolution chosen:

1. **`id` vs `businessUnitCode` as the warehouse identifier.** The old spec had `/warehouse/{id}`,
   an unpopulated `id` field, and a commented-out `DELETE /warehouse/1` test, while the domain has
   no database id and the replacement endpoint already used `businessUnitCode`. **Resolution:**
   `businessUnitCode` is the sole public identifier - path params renamed, `id` dropped from the
   schema, DB row ids stay internal to the persistence adapter. Added read-only `createdAt`/
   `archivedAt` instead, to surface the active/archived lifecycle state.

2. **Seed data violated the location capacity rule** (`MWH.001` capacity 100 at `ZWOLLE-001`,
   `maxCapacity=40`). Nothing in the domain models a "grandfathered" warehouse, so grandfathering
   would invent unmodelled state. **Resolution:** fixed the fixture to `capacity=40`.

3. **"Code must not exist" (creation) vs. "reuse the code" (replacement).** Interpreted as "at
   most one *active* warehouse per code; archived rows may share it" - enforced at the use case
   (`findActiveByBusinessUnitCode == null`), the repository (`archivedAt IS NULL` always, never an
   unrestricted first result), and a PostgreSQL **partial unique index** as a DB-level backstop.

4. **`Location.maxCapacity`** is implemented literally as an aggregate: active capacity at a
   location + requested capacity must not exceed it. Replacement excludes the warehouse being
   replaced from that sum when it stays at the same location.

5. **Why `InsufficientCapacityException` needed its own validation path.** Given replacement's
   "new stock == old stock" and "new stock <= new capacity", "new capacity >= old stock" always
   holds - so applying the generic cross-field check to a replacement request first would make
   `InsufficientCapacityException` unreachable. `WarehouseValidator` has two entry points:
   `validateBasicFields` (creation, includes the cross-field check) and `validateShape`
   (replacement, field-level only), leaving the comparison to the replacement-specific exceptions.
   Caught by a failing test - see the class Javadoc for the full reasoning.

6. **Repeated archive requests** return 404 both times (nothing active the second time), same as
   archiving an unknown code - the domain has no addressable "archived record for this code" to
   distinguish the two cases.

7. **`DELETE /store/{id}` does not sync to the legacy system**, deliberately -
   `LegacyStoreManagerGateway` has no delete method, and inventing one was out of scope. Extending
   it later is mechanical: a `StoreDeletedEvent` + an `AFTER_SUCCESS` observer, same pattern as the
   two existing ones.

8. **Store after-commit mechanism: CDI events, not a transactional outbox.** Immutable
   `StoreCreatedEvent`/`StoreUpdatedEvent` records are observed via
   `@Observes(during = TransactionPhase.AFTER_SUCCESS)`, guaranteeing the legacy gateway only runs
   post-commit, never on rollback (`StoreResourceAfterCommitTest` proves this by having the mocked
   gateway open an *independent* transaction and check the row is already visible there). This is
   best-effort, not durable: a failed legacy call is logged, not swallowed via `printStackTrace`,
   but isn't retried. A **transactional outbox** - write the sync record in the same transaction as
   the `Store` change, dispatch it separately with retries/idempotency/dead-lettering - is the
   production-grade evolution once this integration carries real operational weight.

9. **Concurrency.** Duplicate active codes are protected at both the application layer and the
   partial unique index. Same-warehouse replace/archive races are closed by
   `WarehouseStore.lockActiveByBusinessUnitCode` (`SELECT ... FOR UPDATE`, which re-evaluates its
   `archivedAt IS NULL` clause against the just-committed row once a competing lock is released, so
   a loser correctly sees "no longer active" instead of a stale snapshot). Location count/capacity
   races are closed by a transaction-scoped Postgres advisory lock keyed by the location
   (`pg_advisory_xact_lock`), which also covers the zero-active-warehouses case a row lock can't
   protect. Known gap: this scales to one Postgres instance, and a hot location serializes requests
   rather than failing fast. `WarehouseResourceTest#testConcurrentReplacementsOnTheSameWarehouseNeverCorruptState`
   documents the actual guarantee (no lost update, never >1 active row) rather than a specific
   winner, since HTTP-level test timing can't force true transaction overlap.

---

## Bonus: fulfilment assignments (Store + Product + Warehouse)

Associates Warehouses as fulfilment units for Products at Stores, enforcing three limits: at most
2 distinct Warehouses per Product per Store, at most 3 distinct Warehouses per Store (across all
its Products), and at most 5 distinct Product types per Warehouse (across all Stores). New
top-level package `com.fulfilment.application.monolith.fulfilment`, following the Warehouse
module's ports-and-adapters style end to end (REST adapter → operation ports → use cases →
persistence port → Panache adapter, `@Transactional` on use-case methods).

**Identifiers.** The assignment's own public id is its database-generated row id - unlike
Warehouse, there's no single natural business key for a three-way association, so a row id is the
simplest stable reference for `DELETE .../fulfilment-assignments/{assignmentId}`. The warehouse
side is referenced by `warehouseBusinessUnitCode` and resolved to the currently active warehouse
via the **existing** `WarehouseStore.findActiveByBusinessUnitCode` port (reused directly, not
duplicated) - the same port `CreateWarehouseUseCase`/`ReplaceWarehouseUseCase` already use. This is
what makes warehouse replacement transparent to assignments for free: replacing a warehouse
archives the old row and inserts a new one with the *same* business unit code, so an assignment
that stored that code keeps resolving correctly with zero changes to any assignment row.

**API** (handwritten, like Product/Store - not OpenAPI-generated like Warehouse; see QUESTIONS.md
Q2 for the reasoning, applied here since this is an internal Store-scoped resource, not a
cross-team contract):

- `POST /stores/{storeId}/fulfilment-assignments` - body `{"productId": 1, "warehouseBusinessUnitCode": "MWH.001"}` → 201 with the created assignment.
- `GET /stores/{storeId}/fulfilment-assignments` (optional `?productId=`) → 200 with an array.
- `DELETE /stores/{storeId}/fulfilment-assignments/{assignmentId}` → 204.

A standalone `src/main/resources/openapi/fulfilment-assignment-openapi.yaml` documents the
contract (required fields, minimum id values, all response statuses, the `ApiError` schema,
examples) but is **not** wired into `quarkus.openapi.generator.spec` - that config still names only
`warehouse-openapi.yaml`. Wiring a second spec into the existing single-spec generator setup for an
internal, handwritten-style endpoint wasn't worth the risk to the working codegen configuration.

**Errors.** Reuses the existing `DomainErrorType` (VALIDATION/NOT_FOUND/CONFLICT) and `ApiError`
response shape from the warehouse module directly - both are already generic, not
warehouse-specific - via a new `FulfilmentDomainException` base and `FulfilmentDomainExceptionMapper`
that mirror `WarehouseDomainException`/`WarehouseDomainExceptionMapper` exactly. Named exceptions:
`StoreNotFoundException`, `ProductNotFoundException`, `WarehouseNotFoundException` (fulfilment's
own, deliberately distinct from the warehouse module's exception of the same simple name, so this
module's error handling depends only on the `WarehouseStore` port, not another module's exception
type), `FulfilmentAssignmentNotFoundException`, `DuplicateFulfilmentAssignmentException`,
`ProductWarehouseLimitExceededException`, `StoreWarehouseLimitExceededException`,
`WarehouseProductTypeLimitExceededException`.

**Distinct-count interpretation.** All three limits count *distinct* warehouse/product identifiers,
never assignment rows: `distinctWarehousesForStore` is `SELECT DISTINCT warehouse_business_unit_code
... WHERE store_id = ?`, so three rows for the same store spread across two warehouses count as 2,
not 3 (matching the assignment's own worked example). The three "distinct" persistence methods
return the actual set, not just a `COUNT` - each limit is small (2/3/5) so the set is always tiny
once the invariant holds, and having the set (not just its size) answers both "is this
warehouse/product already counted" (membership - needed so re-using an existing warehouse/product
never wrongly trips the limit) and "how many are there" (size) from one query instead of two.

**Duplicate handling.** An exact repeat of the same Store+Product+Warehouse triple returns **409**
(`DuplicateFulfilmentAssignmentException`), not the existing row - chosen over silently returning
the existing assignment because a 409 makes the caller's mistaken assumption ("this doesn't exist
yet") visible, whereas a quiet 200 would hide it.

**Concurrency.** Three transaction-scoped PostgreSQL advisory locks, acquired in a fixed order
every time - **store, then store+product, then warehouse** - so concurrent requests touching
overlapping dimensions can never deadlock against each other:

```
SELECT pg_advisory_xact_lock(9001, hashtext(storeId))            -- store
SELECT pg_advisory_xact_lock(9002, hashtext(storeId:productId))  -- store+product
SELECT pg_advisory_xact_lock(9003, hashtext(warehouseCode))      -- warehouse
```

The two-integer-key form `pg_advisory_xact_lock(class, key)` is a separate keyspace from the
single-bigint form `WarehouseRepository`'s location lock already uses (`pg_advisory_xact_lock(hashtext(...))`),
so the two features' locks can never collide with each other by construction - this is documented
PostgreSQL behaviour, not a coincidence of the chosen constants. After acquiring all three locks,
the use case re-reads all three distinct sets, re-checks the exact duplicate, validates all three
limits, and inserts - all inside the one transaction the locks are scoped to. A unique constraint on
`(store_id, product_id, warehouse_business_unit_code)` backstops the duplicate check at the database
level too (mirroring the warehouse partial-index precedent), for defense in depth against anything
that bypasses the use case. Deletion needs no locking: removing an assignment can only free
capacity against a limit, never violate one.

**Concurrency: active-warehouse race.** The three advisory locks above are internal to the
fulfilment module - warehouse archive/replace never acquires them, and never has any reason to.
That's fine for the three business limits (fulfilment-only concerns), but the initial
implementation's active-warehouse *existence* check was a plain, unlocked read
(`WarehouseStore.findActiveByBusinessUnitCode`) taken *before* any lock was acquired. A concurrent
archive of the same warehouse could commit in the window between that check and the eventual
insert, leaving a brand new assignment pointing at a warehouse that was already archived-only by
the time the row was written - directly contradicting "new assignments require an active
warehouse."

The fix: that check now uses `WarehouseStore.lockActiveByBusinessUnitCode` - the same row lock
(`SELECT ... FOR UPDATE`) `ReplaceWarehouseUseCase` already takes on itself for the identical
reason. This closes the race against **both** archive and replace uniformly, since both already
funnel through that one method:
- If the assign transaction gets there first, a concurrent archive/replace blocks until it commits
  - correct, since the assignment was created against a genuinely active, now-locked warehouse;
  archiving it a moment later is the ordinary "archive without replacement" outcome above.
- If archive/replace gets there first, the assign transaction's blocked read re-checks the row's
  committed state once the lock is released and correctly observes "not found" - never a stale
  "active" snapshot.
- One deliberate, documented trade-off: losing a race against a concurrent **replace** specifically
  returns 404 even though a new active row with the same code exists a moment later - PostgreSQL's
  lock-wait re-check only re-evaluates the specific row that was blocked on, not newly-inserted
  rows, so the caller sees "not found" and should retry. This mirrors the identical, pre-existing
  trade-off `ReplaceWarehouseUseCase` already has for concurrent replace-vs-replace.

No new deadlock risk: the only resource assign() and archive/replace share is that single warehouse
row, and archive/replace never touch fulfilment's advisory locks, so no lock-acquisition cycle is
possible between them. Verified two ways: `WarehouseRepositoryTest#testLockActiveByBusinessUnitCodeBlocksAConcurrentArchive`
proves the row lock deterministically blocks a concurrent `archiveActive`, using explicit
transaction control (two real, concurrently-open transactions, no reliance on request timing) -
this is the actual mechanism the fix depends on. `FulfilmentAssignmentResourceTest#testConcurrentArchiveAndAssignAlwaysProducesAValidOutcome`
is a coarser HTTP-level smoke test on top: it cannot force true transaction overlap, and - a real
pitfall hit while writing it - `archivedAt` is computed in application code *before* the
(potentially blocking) update statement runs, not at actual commit time, so comparing it against
the assignment's `createdAt` does not reliably reflect database commit order and was dropped as an
invalid check.

**Database.** `fulfilment_assignment(id, store_id, product_id, warehouse_business_unit_code,
created_at)`, generated from `@Table` on `DbFulfilmentAssignment` with indexes on `store_id`,
`(store_id, product_id)`, and `warehouse_business_unit_code`, plus the unique constraint above.
Foreign keys to `store(id)` and `product(id)` are added via raw DDL in `import.sql` (a bare `Long`
column has no JPA association for `@JoinColumn` to attach to). **No FK to
`warehouse(businessUnitCode)`**: PostgreSQL requires an FK's target column to be backed by a full
UNIQUE or PRIMARY KEY constraint, and `businessUnitCode` only has the *partial* unique index from
the mandatory Warehouse work (active rows only) - a plain FK against it fails at DDL time with
"there is no unique constraint matching given keys". Active-warehouse existence is validated at the
application layer via `WarehouseStore` instead, exactly as the identifier decision above intends.
As with the rest of this project's schema, `import.sql` is a stand-in for a real migration tool;
production should use Flyway or Liquibase, versioned and applied incrementally, rather than a
drop-and-recreate script.

**Warehouse replacement and archive interaction.**
- *Replacement* needs no fulfilment-side changes at all - proven by
  `FulfilmentAssignmentResourceTest#testWarehouseReplacementPreservesAssignmentResolution`, which
  creates an assignment, replaces the warehouse via the existing, unmodified
  `POST /warehouse/{code}/replacement` endpoint, and confirms the assignment still resolves.
- *Archive without replacement*: assignment rows are **never rewritten, hidden, or deleted** because
  their warehouse was archived - archiving is never rejected due to existing assignments either.
  Both directions of that choice were effectively forced by scope control (this bonus must not
  change existing mandatory Warehouse behaviour, so `ArchiveWarehouseUseCase` couldn't be taught a
  new rejection rule). Instead, `ListStoreFulfilmentAssignmentsUseCase` stamps every returned
  assignment with a computed `warehouseActive` flag (bounded to at most 3 extra reads per store,
  per rule 2, and cached per distinct code within one call) - the "indicate" option the brief
  explicitly allows as an alternative to excluding rows, chosen because silently hiding a store's
  fulfilment history seemed like the worse outcome. An archived-only `warehouseBusinessUnitCode`
  cannot be used in a **new** assignment (`WarehouseNotFoundException`, 404) - only existing rows
  are preserved.

**Deletion is physical, not soft.** An assignment has no replacement/versioning concept the way a
Warehouse does, so unlike archiving a Warehouse, there's no historical value in keeping a deleted
assignment row around.

**Deliberate simplifications:** no seed data was added for `fulfilment_assignment` (avoids
depending on Hibernate's exact generated sequence name, which the existing Warehouse/Store/Product
fixtures rely on for their own `ALTER SEQUENCE ... RESTART` calls - not worth the coupling for a
bonus feature); `CatalogGateway`'s existence checks call `Store`/`ProductRepository` directly from
a thin adapter rather than introducing richer Store/Product ports, since existence-checking is all
this bonus needs from those modules.

---

## Test suite overview

- `LocationGatewayTest` - existing/unknown/blank/null identifier resolution (Task 1).
- `StoreResourceAfterCommitTest` - gateway called only after commit, never on rollback, receives
  final persisted state, PATCH applies only supplied fields (Task 2).
- `CreateWarehouseUseCaseTest` / `ReplaceWarehouseUseCaseTest` / `ArchiveWarehouseUseCaseTest` -
  fast, Quarkus-free Mockito unit tests covering every validation branch in the assignment.
- `WarehouseRepositoryTest` - `@QuarkusTest` + real Postgres: active queries exclude archived rows,
  capacity/count queries exclude archived rows, historical rows may share a code, the partial
  unique index rejects a second active row, and (bonus-driven) `lockActiveByBusinessUnitCode`
  deterministically blocks a concurrent `archiveActive` on the same row, proven with explicit,
  concurrently-open transactions rather than request timing.
- `WarehouseResourceTest` - `@QuarkusTest` + RestAssured, structured JSON assertions: happy paths,
  representative 400/404/409s, replacement's atomic rollback (state verified via the repository),
  the concurrency invariant above.
- `WarehouseEndpointIT` - `@QuarkusIntegrationTest` smoke test against the packaged jar, run by
  `./mvnw verify` (Failsafe was previously only wired in the `native` profile, so `*IT.java` never
  actually ran under `test` or a plain `verify` - fixed as part of this change).
- `AssignWarehouseToProductForStoreUseCaseTest` / `RemoveFulfilmentAssignmentUseCaseTest` /
  `ListStoreFulfilmentAssignmentsUseCaseTest` (bonus) - fast Mockito unit tests: existence checks,
  duplicate detection, all three limits including the "reusing an already-counted warehouse/product
  doesn't trip the limit" branch, and the `warehouseActive` flag/caching.
- `FulfilmentAssignmentRepositoryTest` (bonus) - `@QuarkusTest` + real Postgres: distinct-set
  queries collapse duplicate rows correctly, the unique constraint and both foreign keys reject
  violations at the database level.
- `FulfilmentAssignmentResourceTest` (bonus) - `@QuarkusTest` + RestAssured: happy paths,
  representative 400/404/409s, the warehouse-replacement and archive-without-replacement
  interactions, three concurrency tests - identical concurrent requests yield exactly one
  persisted row, racing distinct warehouses against the rule-1 limit never exceeds it regardless of
  which request wins, and a concurrent archive-vs-assign smoke test - and a dedicated unit test
  confirming the active-warehouse check uses the locking read, not the plain one.

Run the fast suite with `./mvnw test`; run everything, including the packaged-jar smoke test, with
`./mvnw verify`. Both require Docker (for Quarkus Dev Services' PostgreSQL container) unless a
PostgreSQL instance is already reachable at the configured URL.