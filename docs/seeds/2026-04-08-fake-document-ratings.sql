-- DEV/DEMO seed only: regenerate fake ratings for existing documents.
-- Requirement:
--   - Each document has random 1..30 ratings (capped by number of users).
--   - Each rating is random 1..5 stars.
-- Notes:
--   - This script overwrites current document_ratings data to enforce the range.
--   - Keep for local/dev/demo, not production.

BEGIN;

CREATE TEMP TABLE tmp_fake_document_ratings (
  document_id uuid NOT NULL,
  user_id integer NOT NULL,
  rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT tmp_fake_document_ratings_pk PRIMARY KEY (document_id, user_id)
) ON COMMIT DROP;

WITH user_pool AS (
  SELECT u.id
  FROM public.users u
),
user_count AS (
  SELECT COUNT(*)::int AS total
  FROM user_pool
),
doc_targets AS (
  SELECT
    d.id AS document_id,
    CASE
      WHEN uc.total = 0 THEN 0
      ELSE LEAST(
        uc.total,
        (ABS(HASHTEXT('target:' || d.id::text || ':' || clock_timestamp()::text)) % 30) + 1
      )
    END::int AS target_count
  FROM public.documents d
  CROSS JOIN user_count uc
)
INSERT INTO tmp_fake_document_ratings (document_id, user_id, rating)
SELECT
  dt.document_id,
  pick.id AS user_id,
  (ABS(HASHTEXT('rating:' || dt.document_id::text || ':' || pick.id::text || ':' || clock_timestamp()::text)) % 5) + 1 AS rating
FROM doc_targets dt
JOIN LATERAL (
  SELECT up.id
  FROM user_pool up
  ORDER BY random()
  LIMIT dt.target_count
) pick ON dt.target_count > 0;

-- Remove stale pairs so final counts are exactly in the target range.
DELETE FROM public.document_ratings dr
WHERE NOT EXISTS (
  SELECT 1
  FROM tmp_fake_document_ratings t
  WHERE t.document_id = dr.document_id
    AND t.user_id = dr.user_id
);

-- Insert/update fake rating values.
INSERT INTO public.document_ratings (document_id, user_id, rating)
SELECT t.document_id, t.user_id, t.rating
FROM tmp_fake_document_ratings t
ON CONFLICT (document_id, user_id)
DO UPDATE SET
  rating = EXCLUDED.rating,
  updated_at = now();

-- Re-sync aggregate columns on documents.
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

COMMIT;

-- Optional quick checks:
-- SELECT MIN(cnt) AS min_rating_count, MAX(cnt) AS max_rating_count
-- FROM (SELECT document_id, COUNT(*) AS cnt FROM public.document_ratings GROUP BY document_id) x;
-- SELECT MIN(rating) AS min_star, MAX(rating) AS max_star FROM public.document_ratings;
