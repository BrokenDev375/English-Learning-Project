-- Preserve the current published journey, then number unpublished rounds after it.
ALTER TABLE units ADD COLUMN round_number INT NULL;
CREATE TEMPORARY TABLE round_number_backfill AS
SELECT u.id, ROW_NUMBER() OVER (ORDER BY
    CASE WHEN EXISTS (SELECT 1 FROM lessons l WHERE l.unit_id=u.id
        AND l.published_version_id IS NOT NULL AND l.retired_at IS NULL) THEN 0 ELSE 1 END,
    u.stage,u.sort_order,u.id) AS round_number
FROM units u;
UPDATE units u JOIN round_number_backfill b ON b.id=u.id SET u.round_number=b.round_number;
DROP TEMPORARY TABLE round_number_backfill;
ALTER TABLE units MODIFY COLUMN round_number INT NOT NULL,
    ADD CONSTRAINT ck_units_round_number CHECK (round_number >= 1),
    ADD CONSTRAINT uk_units_round_number UNIQUE (round_number);
