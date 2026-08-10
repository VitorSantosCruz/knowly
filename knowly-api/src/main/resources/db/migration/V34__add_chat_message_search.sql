ALTER TABLE chat_messages
    ADD COLUMN content_tsv_pt tsvector
        GENERATED ALWAYS AS (to_tsvector('portuguese', content)) STORED,
    ADD COLUMN content_tsv_en tsvector
        GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_chat_messages_content_tsv_pt
    ON chat_messages USING GIN (content_tsv_pt);

CREATE INDEX idx_chat_messages_content_tsv_en
    ON chat_messages USING GIN (content_tsv_en);
