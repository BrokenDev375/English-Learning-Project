-- Preserve v1 and all awarded XP, claims, review evidence and frozen results.
-- Only the activity-level pace and small-group movement thresholds change.
INSERT INTO gamification_policies(version, activated_at, effective_at, policy_json)
SELECT 'm05-p0-v2', activated_at, CURRENT_TIMESTAMP(6),
       JSON_SET(policy_json, '$.xpPerActivityLevel', 50,
                '$.minimumMembers', 2, '$.minimumActive', 2,
                '$.minimumMovementSlots', 1)
FROM gamification_policies WHERE version = 'm05-p0-v1';

-- Apply the requested easier rules now to the current OPEN week only.
-- Ended/closing/finalized weeks retain their original rule snapshots and results.
UPDATE league_seasons
SET policy_version = 'm05-p0-v2',
    rules_json = JSON_SET(rules_json, '$.minimumMembers', 2,
                         '$.minimumActive', 2, '$.minimumMovementSlots', 1),
    revision = revision + 1
WHERE status = 'OPEN' AND start_at <= CURRENT_TIMESTAMP(6)
  AND end_at > CURRENT_TIMESTAMP(6);
