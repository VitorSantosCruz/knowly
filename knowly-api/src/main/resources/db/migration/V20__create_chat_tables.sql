CREATE TABLE chat_conversations (
  id BIGSERIAL PRIMARY KEY,
  kind VARCHAR(20) NOT NULL,
  tenant_id BIGINT REFERENCES tenants (id),
  title VARCHAR(255),
  owner_user_id BIGINT REFERENCES users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX ux_chat_conversations_support_channel
  ON chat_conversations (tenant_id, owner_user_id)
  WHERE kind = 'SUPPORT';

CREATE INDEX ix_chat_conversations_tenant ON chat_conversations (tenant_id);

CREATE TABLE chat_participants (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  user_id BIGINT NOT NULL REFERENCES users (id),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (conversation_id, user_id)
);

CREATE INDEX ix_chat_participants_user ON chat_participants (user_id);

CREATE TABLE chat_messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  sender_user_id BIGINT NOT NULL REFERENCES users (id),
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_chat_messages_conversation_cursor
  ON chat_messages (conversation_id, id DESC);

CREATE TABLE support_tickets (
  id BIGSERIAL PRIMARY KEY,
  support_channel_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  assigned_staff_user_id BIGINT REFERENCES users (id),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX ux_support_tickets_one_open_per_channel
  ON support_tickets (support_channel_id)
  WHERE status != 'CLOSED';

CREATE INDEX ix_support_tickets_channel ON support_tickets (support_channel_id);

CREATE TABLE chat_conversations_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  kind VARCHAR(20),
  tenant_id BIGINT,
  title VARCHAR(255),
  owner_user_id BIGINT,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);

CREATE TABLE chat_participants_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  conversation_id BIGINT,
  user_id BIGINT,
  joined_at TIMESTAMPTZ,
  PRIMARY KEY (id, rev)
);

CREATE TABLE support_tickets_aud (
  id BIGINT NOT NULL,
  rev BIGINT NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  support_channel_id BIGINT,
  status VARCHAR(20),
  assigned_staff_user_id BIGINT,
  opened_at TIMESTAMPTZ,
  closed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
