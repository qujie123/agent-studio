ALTER TABLE model_settings ADD COLUMN protocol VARCHAR(20) NOT NULL DEFAULT 'OPENAI';
ALTER TABLE model_settings ADD CONSTRAINT ck_model_protocol CHECK (protocol IN ('OPENAI','ANTHROPIC'));
