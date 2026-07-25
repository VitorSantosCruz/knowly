CREATE TABLE message_article_citations (
  id BIGSERIAL PRIMARY KEY,
  message_id BIGINT NOT NULL REFERENCES messages (id),
  article_id BIGINT NOT NULL REFERENCES articles (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (message_id, article_id)
);

CREATE INDEX ix_message_article_citations_article ON message_article_citations (article_id);
