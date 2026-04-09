-- Add role column for owner/admin authorization.
ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS role character varying NOT NULL DEFAULT 'USER';

-- Normalize existing rows.
UPDATE public.users
SET role = 'USER'
WHERE role IS NULL OR trim(role) = '';

-- Comments table (soft delete enabled).
CREATE TABLE IF NOT EXISTS public.document_comments (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  document_id uuid NOT NULL,
  user_id integer NOT NULL,
  username_snapshot character varying NOT NULL,
  content text NOT NULL,
  rating_snapshot integer NOT NULL CHECK (rating_snapshot BETWEEN 1 AND 5),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  deleted_at timestamp with time zone,
  CONSTRAINT document_comments_pkey PRIMARY KEY (id),
  CONSTRAINT document_comments_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id),
  CONSTRAINT document_comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);

CREATE INDEX IF NOT EXISTS idx_document_comments_document_id_created_at
ON public.document_comments (document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_document_comments_user_id
ON public.document_comments (user_id);
