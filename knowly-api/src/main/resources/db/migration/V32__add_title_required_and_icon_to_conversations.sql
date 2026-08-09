-- Backfill existing NULL titles before the NOT NULL constraint can be added
-- (pre-amendment rows never had a title set).
UPDATE conversations SET title = 'Conversa sem título' WHERE title IS NULL;

ALTER TABLE conversations
  ALTER COLUMN title SET NOT NULL,
  ADD COLUMN icon VARCHAR(50);

ALTER TABLE conversations_aud ADD COLUMN icon VARCHAR(50);
