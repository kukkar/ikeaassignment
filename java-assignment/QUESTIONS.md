# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
The code base currently mixes two persistence styles:

- Active Record, via Panache: `Product` and `Store` extend/use `PanacheEntity`/`PanacheRepository`
  directly. The JPA entity IS the domain object, and `StoreResource`/`ProductResource` call
  `Store.findById(...)`, `store.persist()`, `entity.delete()` straight from the JAX-RS layer.
- Ports-and-adapters (repository pattern), for Warehouse: a plain `Warehouse` domain model, a
  `WarehouseStore` port, a `WarehouseRepository` Panache adapter implementing it, and use cases
  (`CreateWarehouseUseCase`, `ReplaceWarehouseUseCase`, ...) that depend on the port, not on
  Hibernate/Panache types.

Trade-offs, not a verdict on "one true pattern":

- Active Record is genuinely less code for simple CRUD with little business logic (Product today).
  It reads top to bottom, there's no mapping layer, and for a small entity that's a real win in
  velocity and reviewability. Its cost shows up exactly where Store already is: `StoreResource`
  mixes HTTP concerns, transaction boundaries, and the "when do we call the legacy system" business
  rule in one class, and unit-testing that logic means either standing up Quarkus or testing through
  HTTP - there's no seam to substitute a fake persistence layer.
- Ports-and-adapters costs more upfront (a model, a port, an adapter, a use case for what might be a
  three-line query) but pays off once there's real business logic to protect and test: the Warehouse
  use cases are plain constructor-injected classes, tested with Mockito in milliseconds, with zero
  Quarkus/Hibernate bootstrap - see `CreateWarehouseUseCaseTest`, `ReplaceWarehouseUseCaseTest`. The
  domain never imports `jakarta.persistence.*`.

Would I refactor? Incrementally, not a wholesale rewrite, and not uniformly:

- `Store` is the one I'd move first, and this assignment already started that move: the after-commit
  legacy-sync rule (Task 2) and the PATCH partial-update rule are business logic that used to live in
  `StoreResource`, tangled with the JPA entity. I kept `Store` as a Panache entity (rewriting the
  persistence layer wasn't the ask, and the entity is trivial), but pulled the "when does the legacy
  system get called, with what data" decision out into `StoreLegacySyncListener` + immutable events, so
  it's independently reasoned about and testable without asserting on `System.out` or temp files.
- `Product` I would leave as Active Record. It has no business rules beyond CRUD today; introducing a
  port/use-case layer for it would be ceremony with no payoff - "don't overengineer" cuts both ways.
  If `Product` grows real rules (pricing, stock reservations), that's the trigger to extract it, not a
  calendar date.
- Warehouse is the reference implementation now: `WarehouseResourceImpl` never touches
  `WarehouseRepository` directly, only the use-case ports, so the REST layer, the business rules, and
  the SQL/locking details can each change independently and be tested at their own level.

The one thing I'd standardize regardless of pattern: transaction ownership. Right now `@Transactional`
sits on JAX-RS resource methods for Store/Product and on use-case methods for Warehouse. I put it on
the Warehouse use cases deliberately (a REST resource shouldn't need to know that "archive + insert" is
one atomic unit - that's a domain fact), and I'd migrate Store's transaction boundary the same
direction over time, rather than leaving "where does a transaction start" undocumented and
pattern-dependent.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
Contract-first (Warehouse):
+ The YAML is the single, explicit source of truth for the wire contract - types, required fields,
  status codes are declared, not inferred from reading Java. Anyone integrating (another team, a
  frontend, a partner) can generate a client or just read the spec without reading the Java at all.
+ It forces the contract to be decided before the implementation exists, which is exactly what caught
  the `id`/`businessUnitCode` inconsistency this assignment asks to resolve: the schema had an `id`
  field the mapper never populated, and a path parameter (`{id}`) that didn't match how the domain
  actually identifies a warehouse (`businessUnitCode`). That mismatch is much easier to spot staring at
  a YAML file than buried across a resource class and a domain model.
+ Compatibility can be checked mechanically in CI (e.g. `openapi-diff` against the previous spec) before
  a breaking change ships, which isn't really possible for a handwritten interface without inventing
  the same schema by another name.
- Generator/build complexity is real: this project needed the `quarkus-openapi-generator-server`
  extension, a Maven build step, and a "regenerate + read the generated interface before implementing"
  workflow (I had to build the project once just to see the exact generated method signatures and
  bean shape before writing `WarehouseResourceImpl`). Debugging is one layer removed - a wrong status
  code isn't a typo in your resource method, it's a mismatch between what the generator emitted and
  what you assumed it would emit (the generated interface here returns the DTO directly rather than
  `Response`, for example, so getting a 201 instead of the JAX-RS default 200 needed a small
  `ContainerResponseFilter`, not just a return-type change).
- Renaming a path parameter or field means editing YAML, not just Java, and remembering to regenerate;
  that's one more thing to forget mid-refactor.

Handwritten (Product, Store today):
+ Fastest to write and change for something small and internal - no generator step, no generated
  package to jump into, the resource method signature IS the contract.
+ Full control: you're not constrained by what the generator can express or how it names methods.
- The contract only exists as Java code. There's nothing to diff for breaking changes, nothing to hand
  a frontend team except "read the resource class," and it's easy for entity and DTO to silently be the
  same class (which is exactly what happened: `Store`/`Product` are JPA entities AND the JSON request/
  response body, which is how bugs like "PATCH can't tell an omitted field from an explicit zero" and
  "the legacy gateway receives the raw request object instead of the persisted entity" crept in - there
  was no separate request DTO forcing that distinction to be made explicit).
- Nothing stops the implementation and the (nonexistent) contract from drifting silently.

My choice: contract-first for anything crossing a team or system boundary - which is exactly what
Warehouse is here (Task 3 describes it as the piece other systems/teams would integrate against) - and
handwritten is fine for small, internal, low-churn resources, with the caveat that "internal" entities
should still get a dedicated request/response DTO once they need partial updates or non-trivial
validation (as I did for `Store`'s PATCH), rather than reusing the JPA entity as the wire type.
Practically: I'd add a CI check (contract-diff on the YAML against the previous release) the moment
Warehouse has an external consumer, so a breaking change becomes a visible, blocked diff rather than a
support ticket.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
Priority order, cheapest/fastest/highest-signal first:

1. Domain/use-case unit tests, no Quarkus, no database: `CreateWarehouseUseCaseTest`,
   `ReplaceWarehouseUseCaseTest`, `ArchiveWarehouseUseCaseTest`, `LocationGatewayTest`. These are
   plain objects with mocked ports, run in milliseconds, and are where the actual business rules
   live (duplicate code, location limits, stock/capacity math, replacement's exclude-self accounting).
   This is where I put the most test *cases* - every branch in the assignment's validation list
   (null/blank/zero/negative, count limit, capacity limit, stock mismatch, insufficient capacity,
   missing active warehouse) gets its own test here, because a wrong branch here is a wrong business
   decision, and finding it costs a few milliseconds instead of a container boot.
2. Repository/persistence integration tests against a real Postgres (`WarehouseRepositoryTest`, backed
   by Quarkus Dev Services + Testcontainers): these exist specifically to prove things a mock can't -
   that the partial unique index actually rejects a second active row for the same business unit code,
   that the "active" query really excludes archived rows, that historical rows sharing a code coexist.
   SQL and constraints are exactly the kind of thing that looks right in Java and is wrong in the
   database, so these need a real engine, not a fake.
3. Focused REST/contract tests (`WarehouseResourceTest`, `@QuarkusTest` + RestAssured): one happy path
   and a representative 400/404/409 per endpoint, asserted with structured JSON matchers
   (`body("code", equalTo("CONFLICT"))`, not `containsString`), plus request/response mapping (no
   leaked `id` field, timestamps present). This layer exists to catch wiring mistakes - wrong status
   code, wrong field name, exception not mapped - not to re-verify business rules already covered at
   layer 1.
4. After-commit integration tests (`StoreResourceAfterCommitTest`): these need the real transaction
   manager, so they're `@QuarkusTest`, but they're narrowly scoped to one question each - does the
   legacy gateway see committed data, does it get skipped on rollback, does PATCH apply only the
   supplied fields. I proved "after commit" concretely by having the mocked gateway open a *second*,
   independent transaction and check the row is visible there - if the call happened before commit,
   that assertion would fail regardless of what Mockito recorded about call order.
5. A handful of end-to-end tests (`WarehouseEndpointIT`, `@QuarkusIntegrationTest` against the packaged
   jar): list + archive against real seed data. Deliberately small in number - they're the slowest and
   least specific about *why* something broke, so they exist as a smoke test that the packaged app
   still boots and wires together, not as a place to enumerate business rules.

Coverage, as a target: I care about branch/behavioral coverage of the rules enumerated in the
assignment (every validation rule, every status code, both outcomes of "does the DB row end up
archived or not"), not a line-coverage percentage. A percentage rewards exercising code, not verifying
outcomes - the concurrent-replacement test in `WarehouseResourceTest` is the clearest example: it adds
no new lines to "cover" but is the only test that actually proves the pessimistic-lock/partial-unique-
index combination prevents two concurrent replacements from both winning.

Keeping it effective over time: gate merges on `mvn test` (fast layer, runs on every push) and treat
`mvn verify` (adds the Postgres-backed and packaged-jar tests) as the pre-merge/CI gate rather than
something developers run on every save - the split exists so the fast feedback loop stays fast.
Regressions get a test at the lowest layer that can reproduce them (a wrong business decision gets a
use-case test, not a new REST test) so the suite gets more precise, not just bigger, over time. And
when a rule changes, its test should fail before its implementation changes, not after - I'd rather
see a red test at the domain layer than a red integration test three layers up with no clue which of
five rules broke.
```
