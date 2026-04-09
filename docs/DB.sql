-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.document_downloads (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id integer NOT NULL,
  document_id uuid NOT NULL,
  downloaded_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT document_downloads_pkey PRIMARY KEY (id),
  CONSTRAINT document_downloads_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id),
  CONSTRAINT document_downloads_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);
CREATE TABLE public.document_ratings (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  document_id uuid NOT NULL,
  user_id integer NOT NULL,
  rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT document_ratings_pkey PRIMARY KEY (id),
  CONSTRAINT uq_document_ratings_document_user UNIQUE (document_id, user_id),
  CONSTRAINT document_ratings_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id),
  CONSTRAINT document_ratings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);
CREATE TABLE public.document_comments (
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
CREATE TABLE public.document_tags (
  document_id uuid NOT NULL,
  tag_id integer NOT NULL,
  CONSTRAINT document_tags_pkey PRIMARY KEY (document_id, tag_id),
  CONSTRAINT document_tags_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id),
  CONSTRAINT document_tags_tag_id_fkey FOREIGN KEY (tag_id) REFERENCES public.tags(id)
);
CREATE TABLE public.documents (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  bucket_name character varying NOT NULL,
  content_type character varying NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  object_key character varying NOT NULL UNIQUE,
  original_name character varying NOT NULL,
  owner_id integer NOT NULL,
  username character varying NOT NULL,
  size bigint NOT NULL CHECK (size >= 0 AND size <= 20971520),
  visible boolean NOT NULL,
  uploaded_at timestamp with time zone,
  title character varying,
  description text,
  fts tsvector DEFAULT to_tsvector('simple'::regconfig, ((((f_unaccent((COALESCE(title, ''::character varying))::text) || ' '::text) || f_unaccent(COALESCE(description, ''::text))) || ' '::text) || f_unaccent((original_name)::text))),
  download_count bigint NOT NULL DEFAULT 0,
  rating_avg numeric(4,2) NOT NULL DEFAULT 0,
  rating_count bigint NOT NULL DEFAULT 0,
  page_count integer NOT NULL DEFAULT 0,
  processing_status character varying NOT NULL DEFAULT 'READY'::character varying,
  CONSTRAINT documents_pkey PRIMARY KEY (id),
  CONSTRAINT documents_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id)
);
CREATE TABLE public.tags (
  id integer NOT NULL DEFAULT nextval('tags_id_seq'::regclass),
  name character varying NOT NULL UNIQUE,
  CONSTRAINT tags_pkey PRIMARY KEY (id)
);
CREATE TABLE public.users (
  id integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  email character varying NOT NULL,
  password character varying NOT NULL,
  username character varying NOT NULL UNIQUE,
  role character varying NOT NULL DEFAULT 'USER'::character varying,
  upload_count integer NOT NULL DEFAULT 0,
  download_count integer NOT NULL DEFAULT 0,
  status character varying NOT NULL DEFAULT 'ACTIVE'::character varying,
  token_version integer NOT NULL DEFAULT 0,
  banned_at timestamp with time zone,
  CONSTRAINT users_pkey PRIMARY KEY (id)
);