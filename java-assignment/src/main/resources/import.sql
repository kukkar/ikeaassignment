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
