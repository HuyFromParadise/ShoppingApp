-- Development seed data: categories and products with variants and images.
--
-- Opt-in, never automatic. Flyway applies it only when the "demo" profile adds
--   spring.flyway.locations=classpath:db/migration,classpath:db/seed
-- The test profile does NOT include it, so integration tests never see these
-- rows. It sits in db/seed rather than db/migration so a real deployment
-- cannot pick it up by accident.
--
-- Safe to re-run: every insert is guarded by NOT EXISTS on BOTH the fixed id
-- and the row's natural key (slug, sku, code, email). Matching on the natural
-- key as well matters because the rows may already exist without this history
-- row — for example if they were inserted by a manual run and the history was
-- then cleared. A second run inserts nothing and does not touch data an
-- operator has since edited.
--
-- Prices are VND minor units (ADR-008): 499000 is 499,000 ₫.
-- Images point at local placeholder paths; an operator uploads real photos
-- through /admin/products/{id}/images.

SET @now = UTC_TIMESTAMP(6);

-- ---------------------------------------------------------------------------
-- Categories (one parent with two children, to exercise the hierarchy)
-- ---------------------------------------------------------------------------
INSERT INTO categories (id, name, slug, description, parent_id, is_active, sort_order, created_at, updated_at)
SELECT * FROM (
    SELECT 'c1000000-0000-4000-8000-000000000001' AS id, 'Áo' AS name, 'ao' AS slug,
           'Shirts, tops and knitwear.' AS description, CAST(NULL AS CHAR(36)) AS parent_id,
           TRUE AS is_active, 1 AS sort_order, @now AS created_at, @now AS updated_at
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000002', 'Quần', 'quan',
           'Trousers, denim and shorts.', NULL, TRUE, 2, @now, @now
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000003', 'Áo sơ mi', 'ao-so-mi',
           'Shirts for work and weekends.', 'c1000000-0000-4000-8000-000000000001', TRUE, 1, @now, @now
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000004', 'Áo len', 'ao-len',
           'Knitwear in merino and cotton.', 'c1000000-0000-4000-8000-000000000001', TRUE, 2, @now, @now
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000005', 'Quần tây', 'quan-tay',
           'Tailored trousers.', 'c1000000-0000-4000-8000-000000000002', TRUE, 1, @now, @now
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000006', 'Phụ kiện', 'phu-kien',
           'Bags, belts and small leather goods.', NULL, TRUE, 3, @now, @now
    -- A hidden category: proves the storefront hides it and the admin shows it.
    UNION ALL SELECT 'c1000000-0000-4000-8000-000000000007', 'Lưu trữ', 'luu-tru',
           'Retired line, kept for order history.', NULL, FALSE, 99, @now, @now
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = seed.id OR c.slug = seed.slug);

-- ---------------------------------------------------------------------------
-- Products
-- ---------------------------------------------------------------------------
INSERT INTO products (id, name, slug, short_description, description, category_id, base_price,
                      currency, status, material, fit, care_instructions, featured, created_at, updated_at)
SELECT * FROM (
    SELECT 'p1000000-0000-4000-8000-000000000001' AS id, 'Áo sơ mi Oxford' AS name,
           'ao-so-mi-oxford' AS slug,
           'A crisp cotton Oxford that softens with every wash.' AS short_description,
           'Cut from long-staple cotton with a button-down collar and a single chest pocket. The weave is dense enough to hold its shape, open enough to breathe.' AS description,
           'c1000000-0000-4000-8000-000000000003' AS category_id, 499000 AS base_price,
           'VND' AS currency, 'ACTIVE' AS status, '100% cotton' AS material,
           'Regular' AS fit, 'Machine wash cold, hang dry.' AS care_instructions,
           TRUE AS featured, @now AS created_at, @now AS updated_at
    UNION ALL SELECT 'p1000000-0000-4000-8000-000000000002', 'Áo sơ mi linen', 'ao-so-mi-linen',
           'Washed linen for hot days.', 'Washed linen with a relaxed collar. Wrinkles are part of the fabric, not a fault.',
           'c1000000-0000-4000-8000-000000000003', 649000, 'VND', 'ACTIVE', '100% linen', 'Relaxed',
           'Machine wash cold, warm iron.', FALSE, @now, @now
    UNION ALL SELECT 'p1000000-0000-4000-8000-000000000003', 'Áo len merino', 'ao-len-merino',
           'Fine-gauge merino, worn alone or layered.', 'Knitted from extra-fine merino so it sits flat under a jacket without bulk.',
           'c1000000-0000-4000-8000-000000000004', 899000, 'VND', 'ACTIVE', '100% merino wool', 'Slim',
           'Hand wash cold, dry flat.', TRUE, @now, @now
    UNION ALL SELECT 'p1000000-0000-4000-8000-000000000004', 'Quần tây wool', 'quan-tay-wool',
           'Tailored trousers with a clean break.', 'A mid-rise trouser in tropical wool with a pressed crease and a hidden hook closure.',
           'c1000000-0000-4000-8000-000000000005', 1090000, 'VND', 'ACTIVE', '98% wool, 2% elastane', 'Straight',
           'Dry clean.', FALSE, @now, @now
    UNION ALL SELECT 'p1000000-0000-4000-8000-000000000005', 'Túi tote canvas', 'tui-tote-canvas',
           'A canvas tote that survives the week.', 'Heavy canvas with a leather base and an inside pocket for the small things.',
           'c1000000-0000-4000-8000-000000000006', 359000, 'VND', 'ACTIVE', 'Cotton canvas, leather trim', 'One size',
           'Spot clean.', TRUE, @now, @now
    -- A draft: must never appear in the storefront.
    UNION ALL SELECT 'p1000000-0000-4000-8000-000000000006', 'Áo khoác chưa ra mắt', 'ao-khoac-nhap',
           'Not published yet.', 'Autumn jacket still in review.', 'c1000000-0000-4000-8000-000000000001',
           1290000, 'VND', 'DRAFT', 'Cotton twill', 'Regular', NULL, FALSE, @now, @now
    -- An archived product with an order history in a real deployment.
    UNION ALL SELECT 'p1000000-0000-4000-8000-000000000007', 'Quần short cũ', 'quan-short-cu',
           'Retired short.', 'Sold in the previous season, kept for order history.',
           'c1000000-0000-4000-8000-000000000002', 329000, 'VND', 'ARCHIVED', 'Cotton', 'Regular', NULL,
           FALSE, @now, @now
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = seed.id OR p.slug = seed.slug);

-- ---------------------------------------------------------------------------
-- Variants: sizes per product, one out-of-stock and one low-stock case so the
-- storefront and the admin low-stock list have something real to show.
-- ---------------------------------------------------------------------------
INSERT INTO product_variants (id, product_id, sku, size, color, price_override, stock_quantity, is_active, created_at, updated_at)
SELECT * FROM (
    SELECT 'v1000000-0000-4000-8000-000000000001' AS id, 'p1000000-0000-4000-8000-000000000001' AS product_id, 'OXF-WHITE-S' AS sku, 'S' AS size, 'White' AS color, CAST(NULL AS SIGNED) AS price_override, 12 AS stock_quantity, TRUE AS is_active, @now AS created_at, @now AS updated_at
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000002', 'p1000000-0000-4000-8000-000000000001', 'OXF-WHITE-M', 'M', 'White', NULL, 20, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000003', 'p1000000-0000-4000-8000-000000000001', 'OXF-WHITE-L', 'L', 'White', NULL, 8, TRUE, @now, @now
    -- out of stock
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000004', 'p1000000-0000-4000-8000-000000000001', 'OXF-BLUE-M', 'M', 'Blue', NULL, 0, TRUE, @now, @now
    -- a reduced line: exercises the sale badge and compare-at price
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000005', 'p1000000-0000-4000-8000-000000000002', 'LIN-SAND-M', 'M', 'Sand', 549000, 10, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000006', 'p1000000-0000-4000-8000-000000000002', 'LIN-SAND-L', 'L', 'Sand', 549000, 4, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000007', 'p1000000-0000-4000-8000-000000000003', 'MER-GREY-M', 'M', 'Grey', NULL, 6, TRUE, @now, @now
    -- low stock, for the admin low-stock list
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000008', 'p1000000-0000-4000-8000-000000000003', 'MER-GREY-L', 'L', 'Grey', NULL, 2, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-000000000009', 'p1000000-0000-4000-8000-000000000004', 'TRO-NAVY-30', '30', 'Navy', NULL, 5, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-00000000000a', 'p1000000-0000-4000-8000-000000000004', 'TRO-NAVY-32', '32', 'Navy', NULL, 7, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-00000000000b', 'p1000000-0000-4000-8000-000000000005', 'TOTE-NAT-OS', 'One size', 'Natural', NULL, 30, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-00000000000c', 'p1000000-0000-4000-8000-000000000006', 'JKT-DRAFT-M', 'M', 'Olive', NULL, 3, TRUE, @now, @now
    UNION ALL SELECT 'v1000000-0000-4000-8000-00000000000d', 'p1000000-0000-4000-8000-000000000007', 'SHORT-ARCH-M', 'M', 'Stone', NULL, 0, FALSE, @now, @now
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.id = seed.id OR v.sku = seed.sku);

-- ---------------------------------------------------------------------------
-- Images: placeholder storage keys, one primary per product.
-- ---------------------------------------------------------------------------
INSERT INTO product_images (id, product_id, storage_key, alt_text, sort_order, is_primary, created_at, updated_at)
SELECT * FROM (
    SELECT 'i1000000-0000-4000-8000-000000000001' AS id, 'p1000000-0000-4000-8000-000000000001' AS product_id, '/images/demo/oxford-shirt-1.svg' AS storage_key, 'Áo sơ mi Oxford in white cotton' AS alt_text, 0 AS sort_order, TRUE AS is_primary, @now AS created_at, @now AS updated_at
    UNION ALL SELECT 'i1000000-0000-4000-8000-000000000002', 'p1000000-0000-4000-8000-000000000001', '/images/demo/oxford-shirt-2.svg', 'Collar and pocket detail', 1, FALSE, @now, @now
    UNION ALL SELECT 'i1000000-0000-4000-8000-000000000003', 'p1000000-0000-4000-8000-000000000002', '/images/demo/linen-shirt.svg', 'Áo sơ mi linen in sand', 0, TRUE, @now, @now
    UNION ALL SELECT 'i1000000-0000-4000-8000-000000000004', 'p1000000-0000-4000-8000-000000000003', '/images/demo/merino-knit.svg', 'Áo len merino in grey', 0, TRUE, @now, @now
    UNION ALL SELECT 'i1000000-0000-4000-8000-000000000005', 'p1000000-0000-4000-8000-000000000004', '/images/demo/wool-trouser.svg', 'Quần tây wool in navy', 0, TRUE, @now, @now
    UNION ALL SELECT 'i1000000-0000-4000-8000-000000000006', 'p1000000-0000-4000-8000-000000000005', '/images/demo/canvas-tote.svg', 'Canvas tote in natural', 0, TRUE, @now, @now
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM product_images i WHERE i.id = seed.id
  OR (i.product_id = seed.product_id AND i.storage_key = seed.storage_key));

-- ---------------------------------------------------------------------------
-- Promotions (P1) so checkout has a working code out of the box.
-- ---------------------------------------------------------------------------
INSERT INTO promotions (id, code, name, description, type, value, min_order, max_discount, starts_at, ends_at, is_active, created_at, updated_at)
SELECT * FROM (
    SELECT 'r1000000-0000-4000-8000-000000000001' AS id, 'SAVE10' AS code, 'Ten percent off' AS name,
           'Ten percent off any order.' AS description, 'PERCENT' AS type, 10 AS value, 0 AS min_order,
           CAST(200000 AS SIGNED) AS max_discount, CAST(NULL AS DATETIME(6)) AS starts_at,
           CAST(NULL AS DATETIME(6)) AS ends_at, TRUE AS is_active, @now AS created_at, @now AS updated_at
    UNION ALL SELECT 'r1000000-0000-4000-8000-000000000002', 'WELCOME50K', 'Welcome voucher',
           'Fifty thousand off orders over five hundred thousand.', 'FIXED', 50000, 500000, NULL, NULL, NULL,
           TRUE, @now, @now
    -- expired on purpose: proves the server refuses it rather than applying it
    UNION ALL SELECT 'r1000000-0000-4000-8000-000000000003', 'SUMMER24', 'Last summer',
           'Expired.', 'PERCENT', 20, 0, NULL, NULL, '2025-09-01 00:00:00', FALSE, @now, @now
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM promotions p WHERE p.id = seed.id OR p.code = seed.code);

-- ---------------------------------------------------------------------------
-- Demo accounts. Password for both: DemoPass!234  (BCrypt cost 12)
-- Created only if absent, so an operator who changed them keeps their values.
-- ---------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, first_name, last_name, phone, role, status, created_at, updated_at)
SELECT * FROM (
    SELECT 'u1000000-0000-4000-8000-000000000001' AS id, 'admin@demo.local' AS email,
           '$2a$12$jH3/a0BCR6Tf6lMZ.cECheG2jZYA2MWpcdnb8bgB0EX9UmFCmVHry' AS password_hash,
           'Store' AS first_name, 'Admin' AS last_name, CAST(NULL AS CHAR(30)) AS phone,
           'ADMIN' AS role, 'ACTIVE' AS status, @now AS created_at, @now AS updated_at
    UNION ALL SELECT 'u1000000-0000-4000-8000-000000000002', 'customer@demo.local',
           '$2a$12$jH3/a0BCR6Tf6lMZ.cECheG2jZYA2MWpcdnb8bgB0EX9UmFCmVHry',
           'An', 'Nguyen', '0901234567', 'CUSTOMER', 'ACTIVE', @now, @now
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = seed.id OR u.email = seed.email);

-- ---------------------------------------------------------------------------
-- A default address for the demo customer, so checkout can be reached at once.
-- ---------------------------------------------------------------------------
INSERT INTO addresses (id, user_id, recipient_name, phone, address_line1, address_line2, ward, district,
                       city_or_province, postal_code, is_default, created_at, updated_at)
SELECT * FROM (
    SELECT 'a1000000-0000-4000-8000-000000000001' AS id, 'u1000000-0000-4000-8000-000000000002' AS user_id,
           'An Nguyen' AS recipient_name, '0901234567' AS phone, '12 Le Loi' AS address_line1,
           -- MySQL CAST only accepts CHAR (not VARCHAR) for a null literal.
           CAST(NULL AS CHAR(255)) AS address_line2, 'Ben Thanh' AS ward, 'District 1' AS district,
           'Ho Chi Minh City' AS city_or_province, '700000' AS postal_code, TRUE AS is_default,
           @now AS created_at, @now AS updated_at
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM addresses a WHERE a.id = seed.id OR (a.user_id = seed.user_id AND a.is_default))
  AND EXISTS (SELECT 1 FROM users u WHERE u.id = 'u1000000-0000-4000-8000-000000000002');
