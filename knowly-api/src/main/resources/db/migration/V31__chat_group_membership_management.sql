-- chat-group-membership-management: group admin flag, visibility/archival, soft-delete extension
-- to the three chat entities, and a new join-requests lifecycle table. See PLAN.md's "Data schema".

-- Group admin: a boolean on the existing per-(conversation, user) row.
ALTER TABLE chat_participants ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_participants_aud ADD COLUMN is_admin BOOLEAN;

-- Visibility mode + archival state, PEER_GROUP-only in practice (unenforced by CHECK,
-- consistent with title/owner_user_id's existing kind-conditional-meaning precedent).
ALTER TABLE chat_conversations ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE chat_conversations ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE chat_conversations_aud ADD COLUMN visibility VARCHAR(20);
ALTER TABLE chat_conversations_aud ADD COLUMN archived_at TIMESTAMPTZ;

-- Soft-delete, extending the existing generic SoftDeleteFilter mechanism
-- (soft-delete-default-filter) to the three chat entities it doesn't yet cover.
ALTER TABLE chat_conversations ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_conversations_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_participants ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_participants_aud ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE chat_messages ADD COLUMN deleted_at TIMESTAMPTZ;
-- chat_messages has no _aud table (internal-team-chat's PLAN: message content is deliberately
-- not Envers-audited) -- deleted_at is added to the base table only, consistent with that.

CREATE INDEX ix_chat_conversations_discovery
  ON chat_conversations (visibility, archived_at)
  WHERE kind = 'PEER_GROUP' AND deleted_at IS NULL;

-- Join requests: own lifecycle table, mirrors support_tickets' shape.
CREATE TABLE chat_join_requests (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES chat_conversations (id),
  requester_user_id BIGINT NOT NULL REFERENCES users (id),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
  decided_by_user_id BIGINT REFERENCES users (id),
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by VARCHAR(255) NOT NULL
);

-- REQ-34: at most one PENDING request per (conversation, requester) at a time.
CREATE UNIQUE INDEX ux_chat_join_requests_pending
  ON chat_join_requests (conversation_id, requester_user_id)
  WHERE status = 'PENDING';

CREATE INDEX ix_chat_join_requests_conversation ON chat_join_requests (conversation_id);

CREATE TABLE chat_join_requests_aud (
  id BIGINT NOT NULL,
  rev INTEGER NOT NULL REFERENCES revinfo (rev),
  revtype SMALLINT,
  conversation_id BIGINT,
  requester_user_id BIGINT,
  status VARCHAR(20),
  decided_by_user_id BIGINT,
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  created_by VARCHAR(255),
  updated_at TIMESTAMPTZ,
  updated_by VARCHAR(255),
  PRIMARY KEY (id, rev)
);
