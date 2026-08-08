# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
The code base mixes two styles: Active Record for Product/Store (the JPA entity IS the domain
object; JAX-RS calls Store.findById/persist/delete directly), and ports-and-adapters for
Warehouse (a plain domain model, a WarehouseStore port, use cases that depend on the port, never
on Hibernate/Panache types).

Trade-offs, not a verdict on one true pattern:
- Active Record is genuinely less code for simple CRUD - fine for Product today, which has no
  business rules beyond CRUD. Its cost shows up once real logic appears: StoreResource mixed HTTP
  concerns, transactions, and the "when do we call the legacy system" rule in one class, with no
  seam to unit-test that logic without booting Quarkus.
- Ports-and-adapters costs more upfront but pays off once there's real logic to test: the
  Warehouse use cases are plain constructor-injected classes tested with Mockito in milliseconds,
  with the domain never importing jakarta.persistence.*.

Would I refactor? Incrementally, not uniformly:
- Store is what I'd move first - and this assignment already did: the after-commit legacy-sync
  rule and the PATCH partial-update rule were business logic tangled with the JPA entity; I pulled
  them into StoreLegacySyncListener + immutable events, independently testable.
- Product I'd leave as Active Record - introducing a use-case layer for pure CRUD would be
  ceremony with no payoff. Extract it if/when it grows real rules, not on a calendar.
- Warehouse is the reference now: WarehouseResourceImpl never touches WarehouseRepository
  directly, only use-case ports.

One thing I'd standardize regardless of pattern: transaction ownership. @Transactional sits on
JAX-RS methods for Store/Product and on use-case methods for Warehouse (deliberately - "archive +
insert is one atomic unit" is a domain fact, not a REST concern). I'd migrate Store the same
direction over time rather than leave transaction boundaries pattern-dependent.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
Contract-first (Warehouse):
+ The YAML is the explicit source of truth - types, required fields, status codes are declared,
  not inferred from Java. It caught exactly the kind of bug this assignment asked to fix: an `id`
  field the mapper never populated, and a path parameter that didn't match how the domain actually
  identifies a warehouse.
+ Compatibility is mechanically checkable in CI (e.g. openapi-diff) before a breaking change ships.
- Generator/build complexity is real: needed a build step and a "regenerate, then read the
  generated interface" workflow. Debugging is one layer removed - getting a 201 instead of the
  default 200 needed a small ContainerResponseFilter, since the generated interface returns the
  DTO directly rather than Response.
- Renaming a field means editing YAML *and* remembering to regenerate.

Handwritten (Product, Store today):
+ Fastest for something small and internal - no generator step, the method signature IS the
  contract.
- The contract only exists as Java code - nothing to diff for breaking changes, nothing to hand a
  frontend team. Entity and DTO silently being the same class is how bugs like "PATCH can't tell
  omitted from zero" and "legacy gateway gets the raw request instead of the persisted entity"
  crept in - there was no dedicated request DTO forcing that distinction.

My choice: contract-first for anything crossing a team/system boundary (Warehouse fits, per the
brief), handwritten for small internal resources - with the caveat that even internal entities
should get a dedicated request/response DTO once they need partial updates or real validation,
rather than reusing the JPA entity as the wire type. I'd add a CI contract-diff check the moment
Warehouse gets an external consumer.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
Priority order, cheapest/highest-signal first:

1. Domain/use-case unit tests, no Quarkus, no database (CreateWarehouseUseCaseTest,
   ReplaceWarehouseUseCaseTest, ArchiveWarehouseUseCaseTest, LocationGatewayTest). Plain objects
   with mocked ports, milliseconds to run - this is where the actual business rules live, so most
   test *cases* live here: every branch from the assignment's validation list gets its own test.
2. Repository/persistence integration tests against real Postgres (WarehouseRepositoryTest, via
   Dev Services/Testcontainers) - proves things a mock can't: the partial unique index actually
   rejects a duplicate active row, the "active" query excludes archived rows. SQL/constraints look
   right in Java and are wrong in the database often enough to need a real engine.
3. Focused REST/contract tests (WarehouseResourceTest, RestAssured, structured JSON assertions -
   not containsString): one happy path plus representative 400/404/409 per endpoint. Exists to
   catch wiring mistakes, not to re-verify business rules already covered at layer 1.
4. After-commit integration tests (StoreResourceAfterCommitTest) - need the real transaction
   manager. I proved "after commit" concretely by having the mocked gateway open a *second*,
   independent transaction and check the row is visible there, not just by asserting call order.
5. A handful of end-to-end tests (WarehouseEndpointIT, @QuarkusIntegrationTest against the packaged
   jar) - deliberately few, since they're slowest and least specific about *why* something broke.

Coverage target: branch/behavioral coverage of the rules the assignment enumerates, not a line
percentage - a percentage rewards exercising code, not verifying outcomes. The concurrent-
replacement test is the clearest example: it adds no new lines to "cover" but is the only test
proving the locking scheme actually prevents state corruption under contention.

Keeping it effective over time: `mvn test` (fast layer) gates every push; `mvn verify` (adds
Postgres-backed and packaged-jar tests) is the pre-merge/CI gate. Regressions get a test at the
lowest layer that can reproduce them - a wrong business decision gets a use-case test, not a new
REST test - so the suite gets more precise, not just bigger, over time.
```
