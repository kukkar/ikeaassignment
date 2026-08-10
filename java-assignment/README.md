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