INSERT INTO store(id, name, quantityProductsInStock) VALUES (1, 'TONSTAD', 10);
INSERT INTO store(id, name, quantityProductsInStock) VALUES (2, 'KALLAX', 5);
INSERT INTO store(id, name, quantityProductsInStock) VALUES (3, 'BESTÅ', 3);
ALTER SEQUENCE store_seq RESTART WITH 4;

INSERT INTO product(id, name, stock) VALUES (1, 'TONSTAD', 10);
INSERT INTO product(id, name, stock) VALUES (2, 'KALLAX', 5);
INSERT INTO product(id, name, stock) VALUES (3, 'BESTÅ', 3);
ALTER SEQUENCE product_seq RESTART WITH 4;

-- ZWOLLE-001 has maxCapacity=40 (see LocationGateway); the original fixture set MWH.001's
-- capacity to 100, which violates that limit. Corrected to 40 (capacity == location.maxCapacity,
-- the maximum a single active warehouse could hold there) rather than grandfathering the
-- inconsistency, since nothing in the domain models an "over-capacity" or "legacy" status for a
-- warehouse - every other rule assumes active warehouses are within limits.
INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt)
VALUES (1, 'MWH.001', 'ZWOLLE-001', 40, 10, '2024-07-01', null);
INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt)
VALUES (2, 'MWH.012', 'AMSTERDAM-001', 50, 5, '2023-07-01', null);
INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt)
VALUES (3, 'MWH.023', 'TILBURG-001', 30, 27, '2021-02-01', null);
ALTER SEQUENCE warehouse_seq RESTART WITH 4;

-- Enforce "only one active row per business unit code" at the database level too, on top of the
-- application-level check. Historical (archived) rows are free to share a businessUnitCode, so
-- this is a partial index, not a plain unique constraint.
CREATE UNIQUE INDEX ux_warehouse_active_business_unit_code
    ON warehouse (businessUnitCode)
    WHERE archivedAt IS NULL;

-- BONUS: fulfilment_assignment (Store + Product + Warehouse). The table itself, its indexes, and
-- its (store_id, product_id, warehouse_business_unit_code) unique constraint are all generated
-- from @Table on DbFulfilmentAssignment - only the two foreign keys are added here, since a bare
-- Long column has no JPA association for @JoinColumn to attach to.
--
-- No FK to warehouse(businessUnitCode): PostgreSQL requires an FK's target column(s) to be backed
-- by a full UNIQUE or PRIMARY KEY constraint, and businessUnitCode only has the *partial* unique
-- index above (active rows only) - attempting a plain FK against it fails at DDL time with
-- "there is no unique constraint matching given keys for referenced table". Active-warehouse
-- existence is validated at the application layer instead, via the existing WarehouseStore port.
ALTER TABLE fulfilment_assignment
    ADD CONSTRAINT fk_fulfilment_assignment_store FOREIGN KEY (store_id) REFERENCES store (id);
ALTER TABLE fulfilment_assignment
    ADD CONSTRAINT fk_fulfilment_assignment_product FOREIGN KEY (product_id) REFERENCES product (id);
