# Java Code Assignment

Quarkus application implementing Location lookup, Store synchronization, Warehouse lifecycle management, and the optional fulfilment-assignment feature.

See [CODE_ASSIGNMENT.md](CODE_ASSIGNMENT.md) for the original requirements and [QUESTIONS.md](QUESTIONS.md) for the design discussion.

## Requirements

- JDK 17+
- Docker or PostgreSQL

## Build and run

Run the test suite:

```sh
./mvnw clean test
```

Run all verification, including packaged integration tests:

```sh
./mvnw clean verify
```

Start Quarkus development mode:

```sh
./mvnw quarkus:dev
```

The application is available at <http://localhost:8080>. Quarkus Dev Services starts PostgreSQL automatically when Docker is available.

To build and run the packaged application:

```sh
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

The project targets Java 17. JDK 24 compatibility is enabled through the Byte Buddy experimental-support setting in `.mvn/jvm.config` and the Maven test configuration.

## Architecture

Warehouse and fulfilment functionality follow a ports-and-adapters structure:

```text
REST adapter
  -> operation port
  -> use case
  -> persistence port
  -> Panache/PostgreSQL adapter
```

Business rules and transaction boundaries live in use cases. REST adapters handle HTTP mapping, while repositories contain persistence queries and PostgreSQL-specific locking.

Product and Store retain the simpler persistence styles from the starter application. Store integration logic was extracted into immutable CDI events and an after-commit listener.

## Warehouse behavior

Supported operations:

- Create a Warehouse.
- List active Warehouses.
- Retrieve an active Warehouse by business-unit code.
- Archive a Warehouse without deleting its history.
- Replace an active Warehouse atomically.

Creation validates:

- Required fields and positive capacity/non-negative stock.
- Stock does not exceed capacity.
- Business-unit code has no active Warehouse.
- Location exists.
- Location Warehouse-count limit.
- Aggregate active capacity at the Location.

Replacement:

1. Locks and loads the active Warehouse.
2. Validates the replacement Location and capacity limits.
3. Requires replacement stock to match existing stock.
4. Archives the old row.
5. Inserts a new active row using the same business-unit code.
6. Commits both changes atomically.

Only active rows (`archivedAt IS NULL`) appear in normal queries and count toward Location limits.

## Key Warehouse decisions

### Public identifier

`businessUnitCode` is the public Warehouse identifier. Database row IDs remain internal. This resolves the starter code's conflict between `/warehouse/{id}` and the domain model, which had no public ID.

### Historical uniqueness

Archived and active Warehouse versions may share a business-unit code, but only one may be active. This is enforced by application validation and a PostgreSQL partial unique index:

```sql
UNIQUE (businessUnitCode) WHERE archivedAt IS NULL
```

### Seed data

The original `MWH.001` fixture exceeded `ZWOLLE-001`'s capacity limit. Its capacity was corrected from 100 to 40 so the initial state satisfies the implemented rules.

### Repeated archive

A repeated archive returns 404 because no active Warehouse remains for that code.

## Store synchronization

Store create and update operations publish immutable events inside the database transaction. `StoreLegacySyncListener` observes them using:

```java
@Observes(during = TransactionPhase.AFTER_SUCCESS)
```

The legacy gateway is therefore called only after a successful commit and never after rollback. Tests verify commit visibility, rollback behavior, and delivery of final persisted values.

This guarantees ordering, not durable delivery. A production integration should use a transactional outbox with retries, idempotency, monitoring, and dead-letter handling.

`PATCH /store/{id}` uses a dedicated request DTO so omitted stock can be distinguished from an explicit value of zero.

Store deletion is not synchronized because the supplied legacy gateway has no delete operation.

## Concurrency

The implementation uses database coordination because process-local Java locks would not protect multiple application instances.

- A partial unique index prevents multiple active Warehouses with one business-unit code.
- Pessimistic row locks protect Warehouse archive and replacement.
- Transaction-scoped advisory locks serialize Location count/capacity calculations, including Locations with no existing rows to lock.
- A business-unit-code activation lock coordinates Warehouse creation/reactivation with fulfilment limit evaluation.

These guarantees assume a single PostgreSQL database. Highly contended keys serialize operations, favoring correctness over write throughput.

## Bonus: fulfilment assignments

The bonus associates a Warehouse with a Product for a Store:

```text
Store + Product + Warehouse business-unit code
```

It enforces:

1. Maximum 2 distinct Warehouses per Product per Store.
2. Maximum 3 distinct Warehouses per Store.
3. Maximum 5 distinct Product types per Warehouse.

All limits count distinct identifiers rather than association rows.

### API

Create an assignment:

```http
POST /stores/{storeId}/fulfilment-assignments
Content-Type: application/json

{
  "productId": 1,
  "warehouseBusinessUnitCode": "MWH.001"
}
```

List assignments, optionally filtered by Product:

```http
GET /stores/{storeId}/fulfilment-assignments
GET /stores/{storeId}/fulfilment-assignments?productId=1
```

Delete an assignment:

```http
DELETE /stores/{storeId}/fulfilment-assignments/{assignmentId}
```

The standalone contract is documented in `src/main/resources/openapi/fulfilment-assignment-openapi.yaml`. The endpoint is handwritten, consistent with the existing Product and Store APIs. Contract-shape tests verify its runtime statuses and JSON structure; the YAML itself is not used for code generation.

### Persistence

`fulfilment_assignment` stores:

- Assignment ID.
- Store ID.
- Product ID.
- Warehouse business-unit code.
- Creation timestamp.

The table has foreign keys to Store and Product, indexes for the three query dimensions, and a unique constraint on Store + Product + Warehouse code.

There is no foreign key to Warehouse business-unit code because historical Warehouse rows intentionally share that code. Active-Warehouse existence is enforced through `WarehouseStore`.

### Assignment lifecycle

Assignments reference the Warehouse business identity rather than a particular Warehouse row. Replacement therefore preserves assignments automatically because the new active Warehouse reuses the same code.

For operational limit calculations:

- Rules 1 and 2 count only currently active Warehouse codes. An archived-only Warehouse no longer consumes a Store fulfilment slot.
- Rule 3 remains associated with the business-unit code, so a replacement inherits the Product footprint of its predecessor.
- Assignment history is preserved and listing exposes `warehouseActive`.
- Exact duplicate assignments return 409 even if an older Warehouse version is archived.

### Assignment concurrency

Assignment creation acquires transaction-scoped advisory locks in a fixed order:

```text
Store -> Store/Product -> Warehouse
```

After locking, the use case re-reads the relevant distinct sets, checks for duplicates, validates all limits, and inserts in the same transaction. The database unique constraint provides a final duplicate backstop.

Warehouse row and activation locks coordinate assignment creation with concurrent Warehouse archive, replacement, and code reactivation.

### Deleting Stores and Products

A Store or Product referenced by an assignment cannot be deleted. PostgreSQL foreign-key violation `23503` is translated into 409 Conflict rather than exposing a persistence error or silently cascading deletion. The assignment must be removed first.

## Error responses

Warehouse and fulfilment APIs use a shared error structure:

```json
{
  "code": "CONFLICT",
  "message": "Human-readable explanation",
  "details": null
}
```

Typical statuses:

- `400` — malformed or invalid input.
- `404` — Store, Product, Location, Warehouse, or assignment not found.
- `409` — duplicate, business-limit conflict, or referenced resource cannot be deleted.

## Health checks

MicroProfile Health (`quarkus-smallrye-health`) exposes:

- `GET /q/health/live` — `heap-memory`: DOWN if free heap drops below 5% of max. Cheap, no I/O, no external dependency, so a struggling database doesn't trigger a pointless restart.
- `GET /q/health/ready` — `database-connection`: DOWN if a `SELECT 1` against PostgreSQL fails, plus Quarkus's own auto-registered Agroal datasource check.
- `GET /q/health` — both combined.

## Tests

The suite contains:

- Plain unit tests for Location and use-case business rules.
- Store transaction/after-commit tests.
- PostgreSQL repository and constraint tests.
- REST tests for happy paths and 400/404/409 responses.
- Concurrency tests for Warehouse and fulfilment invariants.
- Health endpoint tests.
- Packaged-application integration tests executed by `mvn verify`.

Docker must be running for PostgreSQL-backed Quarkus tests.

## Coverage

`./mvnw clean verify` runs JaCoCo and enforces a minimum of **80% instruction coverage**, bundle-wide (`jacoco-maven-plugin`'s `check` goal, `INSTRUCTION`/`COVEREDRATIO` ≥ `0.80`, checked against the unit-test run). Reports:

- HTML: `target/site/jacoco/index.html`
- XML: `target/site/jacoco/jacoco.xml`

Excluded from the count: the generated OpenAPI client (`com/warehouse/api/**`, no business logic), and a documented list of classes (Panache entities/repositories, `@Transactional` REST resources) that a Quarkus/JaCoCo javaagent interaction prevents this setup from instrumenting - see the `jacoco-maven-plugin` comment in `pom.xml` for exactly which classes and why; each is still exercised by real, passing tests. Packaged-jar integration tests (`WarehouseEndpointIT`) run in a separate process and aren't reflected in this number.

## CI

`.github/workflows/ci.yml` runs `./mvnw clean verify` (JDK 17, Maven wrapper, Docker-backed Postgres via Dev Services) on every push and pull request to `main`, and uploads the JaCoCo report as a build artifact.

## Production considerations

For a production system, I would additionally:

- Manage schema changes with Flyway or Liquibase instead of `import.sql`.
- Replace best-effort legacy synchronization with a transactional outbox.
- Add effective dates/status to fulfilment assignments if full historical reporting is required.
- Add metrics and alerting for conflicts, lock waits, and failed external synchronization.
