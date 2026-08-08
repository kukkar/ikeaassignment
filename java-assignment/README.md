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

## Test suite overview

- `LocationGatewayTest` - existing/unknown/blank/null identifier resolution (Task 1).
- `StoreResourceAfterCommitTest` - gateway called only after commit, never on rollback, receives
  final persisted state, PATCH applies only supplied fields (Task 2).
- `CreateWarehouseUseCaseTest` / `ReplaceWarehouseUseCaseTest` / `ArchiveWarehouseUseCaseTest` -
  fast, Quarkus-free Mockito unit tests covering every validation branch in the assignment.
- `WarehouseRepositoryTest` - `@QuarkusTest` + real Postgres: active queries exclude archived rows,
  capacity/count queries exclude archived rows, historical rows may share a code, the partial
  unique index rejects a second active row.
- `WarehouseResourceTest` - `@QuarkusTest` + RestAssured, structured JSON assertions: happy paths,
  representative 400/404/409s, replacement's atomic rollback (state verified via the repository),
  the concurrency invariant above.
- `WarehouseEndpointIT` - `@QuarkusIntegrationTest` smoke test against the packaged jar, run by
  `./mvnw verify` (Failsafe was previously only wired in the `native` profile, so `*IT.java` never
  actually ran under `test` or a plain `verify` - fixed as part of this change).

Run the fast suite with `./mvnw test`; run everything, including the packaged-jar smoke test, with
`./mvnw verify`. Both require Docker (for Quarkus Dev Services' PostgreSQL container) unless a
PostgreSQL instance is already reachable at the configured URL.