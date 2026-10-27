-- Version the new level curve without changing earned XP, claims or League snapshots.
INSERT INTO gamification_policies(version, activated_at, effective_at, policy_json)
SELECT 'm05-p0-v3', activated_at, CURRENT_TIMESTAMP(6),
       JSON_SET(JSON_REMOVE(policy_json, '$.xpPerActivityLevel'),
                '$.activityLevelCurve', JSON_OBJECT('type', 'INCREASING',
                    'firstStepXp', 10, 'stepIncreaseXp', 10))
FROM gamification_policies WHERE version = 'm05-p0-v2';
