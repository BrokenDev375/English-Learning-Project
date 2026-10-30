-- Optional, versioned activity layout. NULL preserves all existing lessons and attempts.
ALTER TABLE lesson_versions ADD COLUMN activities_json JSON NULL;
