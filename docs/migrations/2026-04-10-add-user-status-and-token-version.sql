-- Add auth lifecycle columns for account moderation and token revocation.
ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS status character varying NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS token_version integer NOT NULL DEFAULT 0;

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS banned_at timestamp with time zone;

UPDATE public.users
SET status = 'ACTIVE'
WHERE status IS NULL OR trim(status) = '';

UPDATE public.users
SET token_version = 0
WHERE token_version IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_status
ON public.users (status);
