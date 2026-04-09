-- Add owner username snapshot + rating aggregates on documents.
ALTER TABLE public.documents
ADD COLUMN IF NOT EXISTS username character varying NOT NULL DEFAULT '';

ALTER TABLE public.documents
ADD COLUMN IF NOT EXISTS rating_avg numeric(4,2) NOT NULL DEFAULT 0;

ALTER TABLE public.documents
ADD COLUMN IF NOT EXISTS rating_count bigint NOT NULL DEFAULT 0;

-- Backfill username snapshot from users table.
UPDATE public.documents d
SET username = COALESCE(u.username, '')
FROM public.users u
WHERE d.owner_id = u.id
  AND COALESCE(d.username, '') = '';

-- Per-user rating for each document.
CREATE TABLE IF NOT EXISTS public.document_ratings (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  document_id uuid NOT NULL,
  user_id integer NOT NULL,
  rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT document_ratings_pkey PRIMARY KEY (id)
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'uq_document_ratings_document_user'
  ) THEN
    ALTER TABLE public.document_ratings
      ADD CONSTRAINT uq_document_ratings_document_user UNIQUE (document_id, user_id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'document_ratings_document_id_fkey'
  ) THEN
    ALTER TABLE public.document_ratings
      ADD CONSTRAINT document_ratings_document_id_fkey FOREIGN KEY (document_id)
      REFERENCES public.documents(id);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'document_ratings_user_id_fkey'
  ) THEN
    ALTER TABLE public.document_ratings
      ADD CONSTRAINT document_ratings_user_id_fkey FOREIGN KEY (user_id)
      REFERENCES public.users(id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_document_ratings_document_id
ON public.document_ratings (document_id);

CREATE INDEX IF NOT EXISTS idx_document_ratings_user_id
ON public.document_ratings (user_id);

-- Backfill aggregate ratings from existing data (if any).
UPDATE public.documents d
SET rating_avg = COALESCE(src.avg_rating, 0),
    rating_count = COALESCE(src.total_count, 0)
FROM (
  SELECT
    dr.document_id,
    ROUND(AVG(dr.rating)::numeric, 2) AS avg_rating,
    COUNT(*)::bigint AS total_count
  FROM public.document_ratings dr
  GROUP BY dr.document_id
) src
WHERE d.id = src.document_id;

UPDATE public.documents d
SET rating_avg = 0,
    rating_count = 0
WHERE NOT EXISTS (
  SELECT 1
  FROM public.document_ratings dr
  WHERE dr.document_id = d.id
);
