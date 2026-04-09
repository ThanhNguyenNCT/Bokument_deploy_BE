-- DEV/DEMO rerun script: regenerate ratings with strict 1..30 votes per document.
-- Prerequisite: database must have at least 30 users.
-- If not enough users, this script raises an error so you can run API seed users first.

BEGIN;

DO $$
DECLARE
  user_total integer;
BEGIN
  SELECT COUNT(*)::int INTO user_total FROM public.users;
  IF user_total < 30 THEN
    RAISE EXCEPTION 'Need at least 30 users, found %. Run bulk-upload/register-seed-users.mjs first.', user_total;
  END IF;
END $$;

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
doc_targets AS (
  SELECT
    d.id AS document_id,
    (FLOOR(random() * 30) + 1)::int AS target_count
  FROM public.documents d
)
INSERT INTO tmp_fake_document_ratings (document_id, user_id, rating)
SELECT
  dt.document_id,
  picked.id AS user_id,
  (FLOOR(random() * 5) + 1)::int AS rating
FROM doc_targets dt
JOIN LATERAL (
  SELECT up.id
  FROM user_pool up
  ORDER BY random()
  LIMIT dt.target_count
) picked ON true;

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

-- Quick checks:
-- 1) Count range per document
-- SELECT MIN(cnt) AS min_rating_count, MAX(cnt) AS max_rating_count
-- FROM (
--   SELECT document_id, COUNT(*) AS cnt
--   FROM public.document_ratings
--   GROUP BY document_id
-- ) x;
--
-- 2) Star range
-- SELECT MIN(rating) AS min_star, MAX(rating) AS max_star
-- FROM public.document_ratings;
