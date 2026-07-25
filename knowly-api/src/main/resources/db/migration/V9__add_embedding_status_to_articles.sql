ALTER TABLE articles
  ADD COLUMN embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN embedding_failure_reason VARCHAR(500);

ALTER TABLE articles_aud
  ADD COLUMN embedding_status VARCHAR(20),
  ADD COLUMN embedding_failure_reason VARCHAR(500);
