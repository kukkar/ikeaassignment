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

### Build hygiene

`pom.xml` has a single `<maven.compiler.release>17</maven.compiler.release>` - no contradictory
`source`/`target` settings alongside it. Every plugin/dependency version that's referenced more
than once (Surefire/Failsafe share `surefire-plugin.version`; the OpenAPI generator's `2.4.7` is
now `openapi-generator.version`) is a `<properties>` entry rather than a repeated literal, so
bumping a version is a one-line change. `target/`, `.DS_Store`, and this repo's `.claude/` Claude
Code session directory are all git-ignored and untracked - no generated or editor/IDE-local state
is committed.

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

10. **Deleting a Store or Product that still has fulfilment assignments.** The bonus feature's
    `fulfilment_assignment` table has plain foreign keys (default `ON DELETE NO ACTION`) to
    `store(id)` and `product(id)` - see "Database" under "Bonus" below. Two policies were
    considered for what `DELETE /store/{id}` and `DELETE /product/{id}` should do when rows still
    reference them:
    - **Cascade the delete** (also remove the referencing assignments). Rejected: it silently
      destroys fulfilment history the moment an unrelated resource is deleted, and makes deletion's
      blast radius depend on a table the caller of `DELETE /store/{id}` may not know exists.
    - **Reject the delete** (chosen). `StoreResource`/`ProductResource` attempt the delete and force
      it to flush inside the request's transaction (`Panache.flush()` / repository `flush()`, since
      Hibernate would otherwise defer the `DELETE` statement past the point this code can still
      react to it); a caught `PersistenceException` (the FK violation, SQLState `23503`) is
      translated to **409 Conflict** via a `WebApplicationException`, rendered by each resource's
      existing `ErrorMapper` into its standard JSON error body (`exceptionType`/`code`/`error`)
      instead of surfacing as a raw, unmapped 500. Nothing is deleted - the failed statement's
      transaction rolls back the whole request.

    This is deliberately a **reactive** check (attempt-then-translate-the-constraint-failure), not a
    **proactive** one (query `fulfilment_assignment` before attempting the delete): a proactive
    check would require `stores`/`products` to depend on the `fulfilment` module purely to ask "do
    any assignments reference me," recreating in the opposite direction the exact cross-module
    coupling problem fixed for `DomainErrorType`/`ApiError` (see "Module boundaries" under "Bonus").
    Letting the database - which already knows the true referential state authoritatively - reject
    the statement, and translating that into a clean domain-level response, keeps `stores`/`products`
    ignorant of `fulfilment`'s existence while still returning a correct, non-500 status. The caller
    must explicitly delete the blocking assignment(s) first (`DELETE
    .../fulfilment-assignments/{id}`) before the Store or Product delete will succeed. Both resources
    behave identically by construction (same FK shape, same catch-and-translate pattern). Proven by
    `StoreDeletionTest` and `ProductDeletionTest`.

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
`FulfilmentAssignmentContractShapeTest` (`@QuarkusTest` + RestAssured) walks every status/shape
combination the YAML documents - the 201 body's exact required keys, the 400/404/409 `ApiError`
shape including the `details` map, the 204-with-empty-body on delete - as literal assertions
checked against the real running server.

**Preventing drift - what this test does and doesn't guarantee.** The test's assertions are
transcribed by hand from the YAML, not parsed from it, so it is endpoint-shape coverage, not a
mechanical drift check: editing the YAML without updating the test (or the runtime without
updating either) will not by itself fail anything here. A real drift guarantee would need the test
to load and validate against the YAML itself (an OpenAPI schema-validation library) or to generate
the contract from the runtime (or vice versa). Neither was introduced for this single, small,
hand-maintained file - the library would be a new build dependency for one endpoint, and
generating a spec this project doesn't otherwise generate risks the working single-spec Warehouse
codegen setup for no clear gain. The name and this note are the honest alternative: say what the
test actually checks, not what it would take to fully guarantee.

**Module boundaries.** `DomainErrorType` and `ApiError` are transport-agnostic (`DomainErrorType` is
a bare enum; `ApiError` is a plain record with no JAX-RS/Jakarta annotations) and were always meant
to be shared, but originally lived under `warehouses.domain.exceptions` / `warehouses.adapters.restapi`
respectively - so reusing them here meant `fulfilment` importing from `warehouses` for something
that isn't actually a warehouse concept, the wrong direction for two sibling feature modules under
ports-and-adapters. Both now live in `com.fulfilment.application.monolith.common`, a small package
with no other content and no dependencies of its own; both feature modules import from it, neither
imports the other. This is a placement fix, not a redesign: the types themselves, the JSON they
produce, and every exception class that extends `WarehouseDomainException`/`FulfilmentDomainException`
are unchanged - only the `import` lines and the two files' package declarations moved.

**Errors.** Reuses `DomainErrorType` (VALIDATION/NOT_FOUND/CONFLICT) and the `ApiError` response
shape via a new `FulfilmentDomainException` base and `FulfilmentDomainExceptionMapper` that mirror
`WarehouseDomainException`/`WarehouseDomainExceptionMapper` exactly. Both types now live in
`com.fulfilment.application.monolith.common` (moved out of the `warehouses` package - see "Module
boundaries" below) precisely so this reuse doesn't create a dependency on a sibling feature module.
Named exceptions:
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

**Concurrency: warehouse reactivation race.** A subtler, distinct gap: `AssignWarehouseToProductForStoreUseCase#activeOnly`
(used by rules 1 and 2, see "Assignment lifecycle policy" below) decides whether each of a store's
*other* historical codes currently counts as active via `WarehouseStore.findActiveByBusinessUnitCode`
- a plain read. That read is safe against a concurrent *archive* of one of those codes (PostgreSQL's
read-committed snapshot means it observes either the pre- or post-commit state, and both are values
the row genuinely held), but not against a concurrent *reactivation*: `CreateWarehouseUseCase`
reusing an archived-only code (see "Code must not exist" vs. "reuse the code" in "Assumptions") is a
brand new `INSERT`, and a plain read taken before that insert commits can return "not active" even
after the code truly becomes active moments later - there is no row yet at read time for a lock to
protect. Left alone: a store with a historical assignment against archived code A, sitting at 2 of
its 3-warehouse limit, could have a concurrent reactivation of A (from an unrelated request) and a
concurrent new assignment for a different warehouse D both pass their checks and commit, leaving the
store fulfilled by 4 distinct active warehouses - exceeding the limit neither transaction's existing
locks caught, since `assign()` only row-locks its own assignment target and `CreateWarehouseUseCase`
took no lock at all before its insert.

Closed with a new `WarehouseStore.lockForActivation(code)` - a transaction-scoped advisory lock
keyed by the business unit code, deliberately *not* a row lock (there may be no row yet), following
the same "lock a key that might have zero matching rows" pattern `lockActiveUsageByLocation` already
established for location capacity. Both sides now take it for the same code before acting: `activeOnly`
takes it for every historical code it is about to evaluate, right before the read; `CreateWarehouseUseCase`
takes it before its duplicate check and insert. Whichever side gets there first, the other blocks
until it commits, so "is this code active" is never answered from a snapshot a concurrent
reactivation can invalidate later - once unblocked, the read sees the code's true, final state.

No new deadlock risk: `CreateWarehouseUseCase` only ever holds this one lock before its insert - it
never simultaneously holds anything `assign()` could be waiting on (it also takes the location lock,
but `assign()`/`activeOnly` never touch that), so it can only ever be a wait target, never part of a
cycle. And concurrent `assign()` calls for the *same* store can never contend on this new lock with
each other in the first place: they already fully serialize on the store-level advisory lock
(acquired earlier, in the fixed lock order above) before ever reaching `activeOnly`. Verified two
ways: `WarehouseRepositoryTest#testLockForActivationSerializesConcurrentAcquisitionsForTheSameCode`
proves the lock primitive itself blocks a concurrent acquisition, with explicit transaction control.
`FulfilmentAssignmentResourceTest#testReactivatingAnArchivedWarehouseCodeBlocksAConcurrentStoreLimitEvaluation`
proves the real scenario end-to-end: a real reactivation (manually paused mid-transaction, the same
technique the lock tests use) blocks a real `assign()` HTTP call already evaluating that code, and
once unblocked, the store's limit is correctly re-evaluated against the now-active code and the 4th
warehouse is rejected with 409 - never silently exceeding the limit. `CreateWarehouseUseCaseTest`/
`AssignWarehouseToProductForStoreUseCaseTest` add fast unit tests confirming both use cases actually
call this lock, and where in each method's sequence.

**Assignment lifecycle policy (archived warehouses vs. the three limits).** A preserved assignment
row can reference a warehouse in any of three states: still active, archived without a replacement,
or archived-with-replacement (i.e. transparently resolving to a new active row under the same
code - see "Identifiers" above). The question this raises: does a historical assignment against an
archived-without-replacement warehouse still occupy a slot against rules 1/2 (warehouses per
product/store), and does it still count against rule 3 (products per warehouse)? Two options were
considered:

- **Count every row regardless of warehouse status.** Simplest to implement (no extra query per
  candidate code), but means a store that legitimately used its 3 warehouses, one of which is later
  decommissioned with no replacement, can *never* gain a genuine replacement slot without first
  deleting the old assignment row by hand - silently punishing normal warehouse lifecycle churn and
  contradicting "archived-without-replacement is a real, supported end state," not an error.
- **Count only rows whose warehouse is currently active** (the chosen option). Archiving a warehouse
  without a replacement correctly frees the capacity it used to occupy, matching how "fulfilled by"
  reads in the brief (a store *is* fulfilled by its currently-active warehouses); the historical row
  itself is never touched, so a store's assignment history is never lost, only excluded from the
  live count. Implemented by `AssignWarehouseToProductForStoreUseCase#activeOnly`, which filters the
  distinct-warehouse sets used for rules 1 and 2 through `WarehouseStore#findActiveByBusinessUnitCode`
  before comparing against the limit.

Rule 3 (products per warehouse) is deliberately **not** filtered the same way: it is scoped by
`warehouseBusinessUnitCode` identity, not by a specific row's lifecycle state, and a replacement is
meant to inherit its predecessor's product footprint under that same code (the same continuity the
"Identifiers" resolution above already relies on) - filtering it here would silently reset a
replaced warehouse's product-type count to zero, which the brief never asks for.

Duplicate detection (`FulfilmentAssignmentStore#existsExact`) is unfiltered on purpose, and
independently of the above: it must catch a repeat of the exact triple against *any* historical row
(e.g. a business-unit code that was archived and later reused by a fresh `CreateWarehouseUseCase`
call, not a replacement), otherwise the database's unique constraint - not this check - would be
the first thing to reject it, surfacing as a raw constraint violation instead of a clean 409.

No assignment row is ever deleted, hidden, or rewritten as a side effect of a warehouse lifecycle
operation, and archiving/replacing a warehouse is never rejected because assignments reference it -
both were ruled out as out of scope for this bonus feature (see "Archive without replacement"
below), and neither is needed for the limits to behave correctly, since the *counting* logic, not
the data, is what changes. `GET .../fulfilment-assignments` and the `warehouseActive` flag continue
to return every row (see "Archive without replacement" below) - the active-only filtering is scoped
to *limit evaluation*, not to what history is visible.

Proven by `AssignWarehouseToProductForStoreUseCaseTest#testArchivedWarehouseInTheSetDoesNotCountTowardTheProductLimit`
/ `...TheStoreLimit` (an archived warehouse in an otherwise-full set frees a slot) and
`...testDuplicateDetectionIgnoresWarehouseActiveStatus` (a duplicate against an archived-referencing
row is still rejected) at the unit level, and end-to-end by
`FulfilmentAssignmentResourceTest#testArchivingAWarehouseFreesItsStoreSlotForAGenuinelyNewWarehouse`.

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
  fast, Quarkus-free Mockito unit tests covering every validation branch in the assignment;
  `CreateWarehouseUseCaseTest` additionally verifies (bonus-driven) that `lockForActivation` is
  taken before the duplicate check and insert - see "Concurrency: warehouse reactivation race".
- `WarehouseRepositoryTest` - `@QuarkusTest` + real Postgres: active queries exclude archived rows,
  capacity/count queries exclude archived rows, historical rows may share a code, the partial
  unique index rejects a second active row, and (bonus-driven) `lockActiveByBusinessUnitCode`
  deterministically blocks a concurrent `archiveActive` **and** a concurrent `ReplaceWarehouseOperation.replace`
  on the same row, while `lockForActivation` deterministically serializes two concurrent
  acquisitions for the same code - all proven with explicit, concurrently-open transactions
  (`QuarkusTransaction.requiringNew` + `ExecutorService`/`CountDownLatch`), not request timing, so
  the block is a real database-level guarantee rather than an artifact of test scheduling.
- `WarehouseResourceTest` - `@QuarkusTest` + RestAssured, structured JSON assertions: happy paths,
  representative 400/404/409s, replacement's atomic rollback (state verified via the repository),
  the concurrency invariant above.
- `WarehouseEndpointIT` - `@QuarkusIntegrationTest` smoke test against the packaged jar, run by
  `./mvnw verify` (Failsafe was previously only wired in the `native` profile, so `*IT.java` never
  actually ran under `test` or a plain `verify` - fixed as part of this change).
- `AssignWarehouseToProductForStoreUseCaseTest` / `RemoveFulfilmentAssignmentUseCaseTest` /
  `ListStoreFulfilmentAssignmentsUseCaseTest` (bonus) - fast Mockito unit tests: existence checks,
  duplicate detection (including that it ignores warehouse active status - see "Assignment lifecycle
  policy"), all three limits including the "reusing an already-counted warehouse/product doesn't
  trip the limit" branch and the two archived-warehouse-frees-a-slot cases for rules 1 and 2, the
  `warehouseActive` flag/caching, and that `activeOnly` takes `lockForActivation` for every
  historical code it evaluates (see "Concurrency: warehouse reactivation race").
- `FulfilmentAssignmentRepositoryTest` (bonus) - `@QuarkusTest` + real Postgres: distinct-set
  queries collapse duplicate rows correctly, `existsExact` matches only the precise triple (not a
  partial match on any one field), and the unique constraint and both foreign keys reject
  violations at the database level.
- `FulfilmentAssignmentResourceTest` (bonus) - `@QuarkusTest` + RestAssured: happy paths,
  representative 400/404/409s, the warehouse-replacement and archive-without-replacement
  interactions, and five concurrency tests - identical concurrent requests yield exactly one
  persisted row, racing distinct warehouses against the rule-1 limit never exceeds it regardless of
  which request wins, a concurrent archive-vs-assign smoke test, (Problem 3) archiving a warehouse
  mid-flight frees its store slot for a genuinely new warehouse, and (the warehouse reactivation
  race) a real, explicitly-paused warehouse reactivation blocks a real concurrent `assign()` call
  already evaluating that code, which then correctly counts it and rejects a 4th warehouse for the
  store instead of silently exceeding the limit - and a dedicated unit test confirming the
  active-warehouse check uses the locking read, not the plain one.
- `FulfilmentAssignmentContractShapeTest` (bonus) - `@QuarkusTest` + RestAssured: every status/shape
  the standalone OpenAPI document promises (201 body keys, 400/404/409 `ApiError` shape,
  204-with-no-body on delete), transcribed by hand and checked against the real running server -
  see "Preventing drift" under "Bonus" for exactly what this does and does not guarantee.
- `StoreDeletionTest` / `ProductDeletionTest` - `@QuarkusTest` + RestAssured: deleting a Store/Product
  that still has a fulfilment assignment returns 409 and destroys neither the resource nor the
  assignment; deleting one with no assignments still succeeds normally. See "Deleting a Store or
  Product that still has fulfilment assignments" above.
- `ProductEndpointTest` - `@QuarkusTest` + RestAssured, Task 1's original CRUD test: deletes a
  scratch Product it creates itself, never the seeded TONSTAD/KALLAX/BESTÅ rows. This was a real
  bug, not just a style choice: `@QuarkusTest` classes here have no per-test transaction rollback,
  so a permanently-deleted seeded row is visible to every other `@QuarkusTest` class that runs
  afterward in the same Surefire JVM. Several bonus fulfilment-assignment tests hardcode seeded
  Store id 1 / Product id 1 / Warehouse `MWH.001`, and originally this test deleted Product id 1 as
  part of its own assertions - harmless only because of the specific class order Quarkus's test
  runner happened to produce, never guaranteed by JUnit/Surefire. Fixed by having it operate on its
  own scratch Product instead.

**Test isolation note.** Besides the fix above, every other test that references seeded Store 1 /
Product 1 / `MWH.001` only *reads* them or creates/deletes its own rows alongside them - audited by
grepping the whole suite for every `delete(...)` call against a literal (not dynamically-created)
id or business-unit code. The handful of warehouse tests that do archive `MWH.001` (`WarehouseRepositoryTest`)
are all `@TestTransaction`, so Quarkus rolls the row back at the end of each test regardless. Note
that `-Dsurefire.runOrder` (tried as `reversealphabetical` and `random` while diagnosing this) has
no effect on the actual class execution order here: Quarkus's own JUnit5 extension batches all
`@QuarkusTest` classes together ahead of plain unit-test classes to avoid redundant application
restarts, overriding whatever order Surefire hands it. That made forcing a different order
impractical as a *verification* technique - the guarantee instead comes from the audit above (no
test permanently mutates a seeded row other than through its own scratch data), not from having
observed the suite pass under multiple orderings.

Run the fast suite with `./mvnw test`; run everything, including the packaged-jar smoke test, with
`./mvnw verify`. Both require Docker (for Quarkus Dev Services' PostgreSQL container) unless a
PostgreSQL instance is already reachable at the configured URL.