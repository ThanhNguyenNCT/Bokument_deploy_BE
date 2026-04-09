-- Normalize legacy bytea text-like columns in documents to proper text types.
-- This migration is idempotent and only converts columns currently stored as bytea.

CREATE OR REPLACE FUNCTION public.bokument_safe_bytea_to_text(input bytea)
RETURNS text
LANGUAGE plpgsql
AS $$
BEGIN
  IF input IS NULL THEN
    RETURN NULL;
  END IF;

  BEGIN
    RETURN convert_from(input, 'UTF8');
  EXCEPTION
    WHEN others THEN
      RETURN encode(input, 'escape');
  END;
END;
$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'documents'
      AND column_name = 'title'
      AND data_type = 'bytea'
  ) THEN
    EXECUTE $sql$
      ALTER TABLE public.documents
      ALTER COLUMN title TYPE character varying
      USING public.bokument_safe_bytea_to_text(title)
    $sql$;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'documents'
      AND column_name = 'description'
      AND data_type = 'bytea'
  ) THEN
    EXECUTE $sql$
      ALTER TABLE public.documents
      ALTER COLUMN description TYPE text
      USING public.bokument_safe_bytea_to_text(description)
    $sql$;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'documents'
      AND column_name = 'username'
      AND data_type = 'bytea'
  ) THEN
    EXECUTE $sql$
      ALTER TABLE public.documents
      ALTER COLUMN username TYPE character varying
      USING public.bokument_safe_bytea_to_text(username)
    $sql$;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'documents'
      AND column_name = 'original_name'
      AND data_type = 'bytea'
  ) THEN
    EXECUTE $sql$
      ALTER TABLE public.documents
      ALTER COLUMN original_name TYPE character varying
      USING public.bokument_safe_bytea_to_text(original_name)
    $sql$;
  END IF;
END $$;

-- Keep schema artifacts clean after conversion.
DROP FUNCTION IF EXISTS public.bokument_safe_bytea_to_text(bytea);
