-- GitHub Actions only: an empty PostgreSQL database with minimal Supabase Auth primitives.
-- Never run against a live Supabase database.
create role anon nologin;
create role authenticated nologin;
create schema auth;
create table auth.users (id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
grant usage on schema auth to authenticated;
grant usage on schema public to anon, authenticated;
grant execute on function auth.uid() to authenticated;
