-- Reproducible production schema for LANU CRM.
-- All client-facing tables are protected with RLS. The publishable key is safe only with these policies.

create or replace function public.lanu_touch_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists public.lanu_crm_customers (
  id text primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  stage text not null default 'PROSPECT',
  source text not null default 'manual',
  source_id text not null,
  name text not null,
  signboard_name text,
  city text not null,
  district text not null,
  neighborhood text,
  address text,
  latitude double precision,
  longitude double precision,
  data_quality text not null default 'UNKNOWN',
  notes text,
  contact_name text,
  business_type text,
  tax_or_national_id text,
  phone text,
  website text,
  registry_status text not null default 'UNVERIFIED',
  registry_source text,
  registry_number text,
  sync_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists lanu_crm_customers_owner_idx on public.lanu_crm_customers(owner_user_id);
create index if not exists lanu_crm_customers_region_idx on public.lanu_crm_customers(owner_user_id, city, district);
create index if not exists lanu_crm_customers_source_idx on public.lanu_crm_customers(owner_user_id, source_id);
drop trigger if exists lanu_crm_customers_touch on public.lanu_crm_customers;
create trigger lanu_crm_customers_touch before update on public.lanu_crm_customers
for each row execute function public.lanu_touch_updated_at();

create table if not exists public.lanu_crm_activities (
  id text primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
  type text not null,
  note text,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists lanu_crm_activities_owner_time_idx on public.lanu_crm_activities(owner_user_id, created_at);
create index if not exists lanu_crm_activities_customer_idx on public.lanu_crm_activities(customer_id, occurred_at);

create table if not exists public.lanu_crm_next_actions (
  id text primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
  type text not null,
  due_at timestamptz not null,
  note text,
  created_by_user_id uuid,
  completed_at timestamptz,
  completed_by_user_id uuid,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists lanu_crm_next_actions_owner_due_idx on public.lanu_crm_next_actions(owner_user_id, due_at);
create index if not exists lanu_crm_next_actions_customer_idx on public.lanu_crm_next_actions(customer_id, due_at);
drop trigger if exists lanu_crm_next_actions_touch on public.lanu_crm_next_actions;
create trigger lanu_crm_next_actions_touch before update on public.lanu_crm_next_actions
for each row execute function public.lanu_touch_updated_at();

create table if not exists public.lanu_crm_opportunities (
  id text primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
  title text not null,
  status text not null default 'OPEN',
  amount numeric(18,2),
  currency text,
  amount_origin text not null default 'UNKNOWN',
  note text,
  version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists lanu_crm_opportunities_owner_idx on public.lanu_crm_opportunities(owner_user_id, updated_at);
create index if not exists lanu_crm_opportunities_customer_idx on public.lanu_crm_opportunities(customer_id, updated_at);
drop trigger if exists lanu_crm_opportunities_touch on public.lanu_crm_opportunities;
create trigger lanu_crm_opportunities_touch before update on public.lanu_crm_opportunities
for each row execute function public.lanu_touch_updated_at();

create table if not exists public.lanu_crm_stage_transitions (
  id text primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
  from_stage text,
  to_stage text not null,
  changed_at timestamptz not null default now(),
  changed_by_user_id uuid,
  client_version bigint not null default 1
);
create index if not exists lanu_crm_stage_transitions_owner_idx on public.lanu_crm_stage_transitions(owner_user_id, changed_at);
create index if not exists lanu_crm_stage_transitions_customer_idx on public.lanu_crm_stage_transitions(customer_id, changed_at);

create table if not exists public.lanu_crm_contacts (
  id text primary key,
  customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
  full_name text not null,
  role text,
  phone text,
  email text,
  is_primary boolean not null default false,
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null default 1
);
create index if not exists lanu_crm_contacts_customer_idx on public.lanu_crm_contacts(customer_id, updated_at_epoch_ms);

create table if not exists public.lanu_crm_quotes (
  id text primary key,
  customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
  opportunity_id text references public.lanu_crm_opportunities(id) on delete set null,
  quote_number text not null,
  status text not null,
  currency text not null,
  total_minor bigint not null default 0,
  valid_until_epoch_ms bigint,
  notes text,
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null default 1
);
create index if not exists lanu_crm_quotes_customer_idx on public.lanu_crm_quotes(customer_id, updated_at_epoch_ms);
create index if not exists lanu_crm_quotes_status_idx on public.lanu_crm_quotes(status);

create table if not exists public.lanu_crm_quote_lines (
  id text primary key,
  quote_id text not null references public.lanu_crm_quotes(id) on delete cascade,
  product_id text,
  product_name text not null,
  unit text not null,
  quantity_milli bigint not null check (quantity_milli > 0),
  unit_price_minor bigint not null check (unit_price_minor >= 0),
  discount_basis_points integer not null default 0 check (discount_basis_points between 0 and 10000),
  line_total_minor bigint not null check (line_total_minor >= 0),
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null default 1
);
create index if not exists lanu_crm_quote_lines_quote_idx on public.lanu_crm_quote_lines(quote_id, updated_at_epoch_ms);
create index if not exists lanu_crm_quote_lines_product_idx on public.lanu_crm_quote_lines(product_id);

create table if not exists public.lanu_crm_orders (
  id text primary key,
  customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
  quote_id text references public.lanu_crm_quotes(id) on delete set null,
  order_number text not null,
  status text not null,
  currency text not null,
  total_minor bigint not null default 0,
  notes text,
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null default 1
);
create index if not exists lanu_crm_orders_customer_idx on public.lanu_crm_orders(customer_id, updated_at_epoch_ms);
create index if not exists lanu_crm_orders_status_idx on public.lanu_crm_orders(status);

create table if not exists public.lanu_crm_order_lines (
  id text primary key,
  order_id text not null references public.lanu_crm_orders(id) on delete cascade,
  product_id text,
  product_name text not null,
  unit text not null,
  quantity_milli bigint not null check (quantity_milli > 0),
  unit_price_minor bigint not null check (unit_price_minor >= 0),
  discount_basis_points integer not null default 0 check (discount_basis_points between 0 and 10000),
  line_total_minor bigint not null check (line_total_minor >= 0),
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null default 1
);
create index if not exists lanu_crm_order_lines_order_idx on public.lanu_crm_order_lines(order_id, updated_at_epoch_ms);
create index if not exists lanu_crm_order_lines_product_idx on public.lanu_crm_order_lines(product_id);

alter table public.lanu_crm_customers enable row level security;
alter table public.lanu_crm_activities enable row level security;
alter table public.lanu_crm_next_actions enable row level security;
alter table public.lanu_crm_opportunities enable row level security;
alter table public.lanu_crm_stage_transitions enable row level security;
alter table public.lanu_crm_contacts enable row level security;
alter table public.lanu_crm_quotes enable row level security;
alter table public.lanu_crm_quote_lines enable row level security;
alter table public.lanu_crm_orders enable row level security;
alter table public.lanu_crm_order_lines enable row level security;

-- Core tables carry owner_user_id directly.
do $$
declare t text;
begin
  foreach t in array array[
    'lanu_crm_customers',
    'lanu_crm_activities',
    'lanu_crm_next_actions',
    'lanu_crm_opportunities',
    'lanu_crm_stage_transitions'
  ] loop
    execute format('drop policy if exists %I on public.%I', t || '_owner_select', t);
    execute format('drop policy if exists %I on public.%I', t || '_owner_insert', t);
    execute format('drop policy if exists %I on public.%I', t || '_owner_update', t);
    execute format('drop policy if exists %I on public.%I', t || '_owner_delete', t);
    execute format('create policy %I on public.%I for select using (owner_user_id = auth.uid())', t || '_owner_select', t);
    execute format('create policy %I on public.%I for insert with check (owner_user_id = auth.uid())', t || '_owner_insert', t);
    execute format('create policy %I on public.%I for update using (owner_user_id = auth.uid()) with check (owner_user_id = auth.uid())', t || '_owner_update', t);
    execute format('create policy %I on public.%I for delete using (owner_user_id = auth.uid())', t || '_owner_delete', t);
  end loop;
end $$;

-- Child/commercial tables derive ownership through their parent customer.
drop policy if exists lanu_crm_contacts_owner_all on public.lanu_crm_contacts;
create policy lanu_crm_contacts_owner_all on public.lanu_crm_contacts
for all using (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = auth.uid())
) with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = auth.uid())
);

drop policy if exists lanu_crm_quotes_owner_all on public.lanu_crm_quotes;
create policy lanu_crm_quotes_owner_all on public.lanu_crm_quotes
for all using (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = auth.uid())
) with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = auth.uid())
);

drop policy if exists lanu_crm_quote_lines_owner_all on public.lanu_crm_quote_lines;
create policy lanu_crm_quote_lines_owner_all on public.lanu_crm_quote_lines
for all using (
  exists (
    select 1 from public.lanu_crm_quotes q
    join public.lanu_crm_customers c on c.id = q.customer_id
    where q.id = quote_id and c.owner_user_id = auth.uid()
  )
) with check (
  exists (
    select 1 from public.lanu_crm_quotes q
    join public.lanu_crm_customers c on c.id = q.customer_id
    where q.id = quote_id and c.owner_user_id = auth.uid()
  )
);

drop policy if exists lanu_crm_orders_owner_all on public.lanu_crm_orders;
create policy lanu_crm_orders_owner_all on public.lanu_crm_orders
for all using (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = auth.uid())
) with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = auth.uid())
);

drop policy if exists lanu_crm_order_lines_owner_all on public.lanu_crm_order_lines;
create policy lanu_crm_order_lines_owner_all on public.lanu_crm_order_lines
for all using (
  exists (
    select 1 from public.lanu_crm_orders o
    join public.lanu_crm_customers c on c.id = o.customer_id
    where o.id = order_id and c.owner_user_id = auth.uid()
  )
) with check (
  exists (
    select 1 from public.lanu_crm_orders o
    join public.lanu_crm_customers c on c.id = o.customer_id
    where o.id = order_id and c.owner_user_id = auth.uid()
  )
);

grant select, insert, update, delete on all tables in schema public to authenticated;
