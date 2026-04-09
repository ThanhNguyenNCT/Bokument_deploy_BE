-- Add user-level download counter for quota checks.
ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS download_count integer NOT NULL DEFAULT 0;

-- Backfill once from current download history.
UPDATE public.users u
SET download_count = COALESCE(src.downloads, 0)
FROM (
  SELECT user_id, COUNT(*)::integer AS downloads
  FROM public.document_downloads
  GROUP BY user_id
) src
WHERE u.id = src.user_id;

-- Keep upload_count synchronized with existing document ownership snapshot.
UPDATE public.users u
SET upload_count = COALESCE(src.uploads, 0)
FROM (
  SELECT owner_id, COUNT(*)::integer AS uploads
  FROM public.documents
  GROUP BY owner_id
) src
WHERE u.id = src.owner_id;
