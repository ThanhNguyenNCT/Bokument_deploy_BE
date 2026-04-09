-- DEV/DEMO seed only: generate fake comments from existing ratings.
-- Coverage policy per document:
--   - comment_count <= rating_count
--   - comment_count >= CEIL(rating_count * 0.7)
--   - each fake comment maps to one valid (document_id, user_id) rating pair
-- Notes:
--   - This script removes previous [FAKE] comments and regenerates them.
--   - Existing non-fake comments are kept.

BEGIN;

-- Clean previous fake comments (active and soft-deleted) before regenerating.
DELETE FROM public.document_comments dc
WHERE dc.content LIKE '[FAKE]%';

CREATE TEMP TABLE tmp_existing_comment_pairs ON COMMIT DROP AS
SELECT DISTINCT
  dc.document_id,
  dc.user_id
FROM public.document_comments dc
JOIN public.document_ratings dr
  ON dr.document_id = dc.document_id
 AND dr.user_id = dc.user_id
WHERE dc.deleted_at IS NULL;

CREATE TEMP TABLE tmp_comment_targets ON COMMIT DROP AS
WITH rating_stats AS (
  SELECT
    dr.document_id,
    COUNT(*)::int AS rating_count
  FROM public.document_ratings dr
  GROUP BY dr.document_id
),
existing_stats AS (
  SELECT
    ep.document_id,
    COUNT(*)::int AS existing_pair_count
  FROM tmp_existing_comment_pairs ep
  GROUP BY ep.document_id
),
base AS (
  SELECT
    rs.document_id,
    rs.rating_count,
    LEAST(rs.rating_count, GREATEST(1, CEIL(rs.rating_count * 0.7)::int)) AS min_comment,
    COALESCE(es.existing_pair_count, 0) AS existing_pair_count
  FROM rating_stats rs
  LEFT JOIN existing_stats es ON es.document_id = rs.document_id
)
SELECT
  b.document_id,
  b.rating_count,
  b.min_comment,
  b.existing_pair_count,
  CASE
    WHEN b.rating_count = 0 THEN 0
    ELSE (FLOOR(random() * (b.rating_count - b.min_comment + 1))::int + b.min_comment)
  END AS target_comment_count
FROM base b;

CREATE TEMP TABLE tmp_rating_candidates ON COMMIT DROP AS
SELECT
  dr.document_id,
  dr.user_id,
  dr.rating,
  u.username AS username_snapshot
FROM public.document_ratings dr
JOIN public.users u
  ON u.id = dr.user_id
LEFT JOIN tmp_existing_comment_pairs ep
  ON ep.document_id = dr.document_id
 AND ep.user_id = dr.user_id
WHERE ep.document_id IS NULL;

CREATE TEMP TABLE tmp_fake_comment_source ON COMMIT DROP AS
SELECT
  rc.document_id,
  rc.user_id,
  rc.rating,
  rc.username_snapshot,
  GREATEST(0, tgt.target_comment_count - tgt.existing_pair_count) AS need_fake_count,
  ROW_NUMBER() OVER (PARTITION BY rc.document_id ORDER BY random()) AS pick_order
FROM tmp_rating_candidates rc
JOIN tmp_comment_targets tgt
  ON tgt.document_id = rc.document_id
WHERE tgt.rating_count > 0;

WITH rows_to_insert AS (
  SELECT
    src.document_id,
    src.user_id,
    src.username_snapshot,
    src.rating,
    CASE
      WHEN src.rating = 5 THEN '[FAKE] Tai lieu rat chat luong, de theo doi va rat huu ich cho on tap.'
      WHEN src.rating = 4 THEN '[FAKE] Tai lieu tot, noi dung kha day du va de tham khao.'
      WHEN src.rating = 3 THEN '[FAKE] Tai lieu o muc trung binh, dap ung duoc nhu cau co ban.'
      WHEN src.rating = 2 THEN '[FAKE] Noi dung con kho theo doi, can bo sung vi du va giai thich ro hon.'
      ELSE '[FAKE] Tai lieu chua phu hop voi nhu cau, can cai thien chat luong.'
    END || ' #' || ((ABS(HASHTEXT(src.document_id::text || ':' || src.user_id::text || ':' || clock_timestamp()::text)) % 9000) + 1000)::text AS content,
    (
      now()
      - make_interval(days => (FLOOR(random() * 120)::int))
      - make_interval(hours => (FLOOR(random() * 24)::int))
      - make_interval(mins => (FLOOR(random() * 60)::int))
    ) AS created_at
  FROM tmp_fake_comment_source src
  WHERE src.pick_order <= src.need_fake_count
)
INSERT INTO public.document_comments (
  document_id,
  user_id,
  username_snapshot,
  content,
  rating_snapshot,
  created_at,
  updated_at,
  deleted_at
)
SELECT
  r.document_id,
  r.user_id,
  r.username_snapshot,
  r.content,
  r.rating,
  r.created_at,
  r.created_at + make_interval(hours => (FLOOR(random() * 48)::int), mins => (FLOOR(random() * 60)::int)),
  NULL
FROM rows_to_insert r;

COMMIT;

-- Verify 1: coverage per document (70%..100% of rating_count)
-- WITH rating_stats AS (
--   SELECT dr.document_id, COUNT(*)::int AS rating_count
--   FROM public.document_ratings dr
--   GROUP BY dr.document_id
-- ),
-- comment_stats AS (
--   SELECT dc.document_id, COUNT(DISTINCT dc.user_id)::int AS comment_count
--   FROM public.document_comments dc
--   JOIN public.document_ratings dr
--     ON dr.document_id = dc.document_id
--    AND dr.user_id = dc.user_id
--   WHERE dc.deleted_at IS NULL
--   GROUP BY dc.document_id
-- )
-- SELECT
--   rs.document_id,
--   rs.rating_count,
--   COALESCE(cs.comment_count, 0) AS comment_count,
--   CEIL(rs.rating_count * 0.7)::int AS min_required,
--   CASE
--     WHEN COALESCE(cs.comment_count, 0) BETWEEN CEIL(rs.rating_count * 0.7)::int AND rs.rating_count THEN 'OK'
--     ELSE 'VIOLATION'
--   END AS status
-- FROM rating_stats rs
-- LEFT JOIN comment_stats cs ON cs.document_id = rs.document_id
-- ORDER BY status DESC, rs.document_id;

-- Verify 2: fake comment rating_snapshot must match source rating
-- SELECT COUNT(*) AS mismatch_count
-- FROM public.document_comments dc
-- JOIN public.document_ratings dr
--   ON dr.document_id = dc.document_id
--  AND dr.user_id = dc.user_id
-- WHERE dc.deleted_at IS NULL
--   AND dc.content LIKE '[FAKE]%'
--   AND dc.rating_snapshot <> dr.rating;
