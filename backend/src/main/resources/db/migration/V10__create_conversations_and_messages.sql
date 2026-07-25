CREATE TABLE conversations (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenants (id),
  owner_user_id BIGINT NOT NULL REFERENCES users (id),
  title VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX ix_conversations_tenant ON conversations (tenant_id);
CREATE INDEX ix_conversations_owner ON conversations (owner_user_id);

CREATE TABLE messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversations (id),
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_messages_conversation ON messages (conversation_id);
