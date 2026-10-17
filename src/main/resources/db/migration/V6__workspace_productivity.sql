ALTER TABLE agents ADD COLUMN temperature DOUBLE PRECISION;
ALTER TABLE agents ADD COLUMN max_output_tokens INTEGER;
ALTER TABLE tasks ADD COLUMN execution_temperature DOUBLE PRECISION;
ALTER TABLE tasks ADD COLUMN execution_max_output_tokens INTEGER;
ALTER TABLE chat_turns ADD COLUMN temperature DOUBLE PRECISION;
ALTER TABLE chat_turns ADD COLUMN max_output_tokens INTEGER;
ALTER TABLE chat_sessions ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_sessions ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_chat_owner_archive_pin ON chat_sessions(owner_id,archived,pinned,updated_at DESC,id DESC);
