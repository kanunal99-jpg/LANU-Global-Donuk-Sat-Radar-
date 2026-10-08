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
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  business_id uuid,
  stage text not null default 'PROSPECT',
  source text,
  source_id text,
  name text not null,
  signboard_name text,
  city text,
  district text,
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
  tags_csv text not null default '',
  merged_into_customer_id uuid references public.lanu_crm_customers(id) on delete set null,
  sync_version bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint lanu_crm_customers_tax_or_national_id_format
    check (tax_or_national_id is null or tax_or_national_id ~ '^[0-9]{10,11}$')
);

create index if not exists lanu_crm_customers_owner_idx on public.lanu_crm_customers(owner_user_id);
create index if not exists lanu_crm_customers_region_idx on public.lanu_crm_customers(owner_user_id, city, district);
create index if not exists lanu_crm_customers_source_idx on public.lanu_crm_customers(owner_user_id, source_id);
create index if not exists lanu_crm_customers_merged_idx
  on public.lanu_crm_customers(owner_user_id, merged_into_customer_id);
drop trigger if exists lanu_crm_customers_touch on public.lanu_crm_customers;
create trigger lanu_crm_customers_touch before update on public.lanu_crm_customers
for each row execute function public.lanu_touch_updated_at();

create table if not exists public.lanu_crm_activities (
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  type text not null,
  note text,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists lanu_crm_activities_owner_time_idx on public.lanu_crm_activities(owner_user_id, created_at);
create index if not exists lanu_crm_activities_customer_idx on public.lanu_crm_activities(customer_id, occurred_at);

create table if not exists public.lanu_crm_next_actions (
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
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
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  title text not null,
  status text not null default 'OPEN',
  amount numeric(14,2),
  currency text,
  amount_origin text not null default 'UNKNOWN',
  expected_close_at timestamptz,
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
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  from_stage text,
  to_stage text not null,
  changed_at timestamptz not null default now(),
  changed_by_user_id uuid,
  client_version bigint not null default 1
);
create index if not exists lanu_crm_stage_transitions_owner_idx on public.lanu_crm_stage_transitions(owner_user_id, changed_at);
create index if not exists lanu_crm_stage_transitions_customer_idx on public.lanu_crm_stage_transitions(customer_id, changed_at);

create table if not exists public.lanu_crm_contacts (
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
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
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  opportunity_id uuid references public.lanu_crm_opportunities(id) on delete set null,
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
  id uuid primary key,
  quote_id uuid not null references public.lanu_crm_quotes(id) on delete cascade,
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
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  quote_id uuid references public.lanu_crm_quotes(id) on delete set null,
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
  id uuid primary key,
  order_id uuid not null references public.lanu_crm_orders(id) on delete cascade,
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

create table if not exists public.lanu_crm_sync_operations (
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  entity_type text not null,
  entity_id uuid not null,
  operation text not null,
  idempotency_key text not null,
  state text not null,
  attempts integer not null default 0,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists lanu_crm_sync_operations_idempotency_idx
  on public.lanu_crm_sync_operations(owner_user_id, idempotency_key);
create index if not exists lanu_crm_sync_operations_state_idx
  on public.lanu_crm_sync_operations(owner_user_id, state, created_at);

create table if not exists public.lanu_crm_applied_mutations (
  operation_id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  entity_type text not null,
  entity_id uuid not null,
  payload_version bigint not null,
  applied_at timestamptz not null default now()
);
create index if not exists lanu_crm_applied_mutations_owner_idx
  on public.lanu_crm_applied_mutations(owner_user_id, applied_at);

alter table public.lanu_crm_sync_operations enable row level security;
alter table public.lanu_crm_applied_mutations enable row level security;

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
    execute format('create policy %I on public.%I for select to authenticated using (owner_user_id = (select auth.uid()))', t || '_owner_select', t);
    execute format('create policy %I on public.%I for insert to authenticated with check (owner_user_id = (select auth.uid()))', t || '_owner_insert', t);
    execute format('create policy %I on public.%I for update to authenticated using (owner_user_id = (select auth.uid())) with check (owner_user_id = (select auth.uid()))', t || '_owner_update', t);
    execute format('create policy %I on public.%I for delete to authenticated using (owner_user_id = (select auth.uid()))', t || '_owner_delete', t);
  end loop;
end $$;

-- Child/commercial tables derive ownership through their parent customer.
drop policy if exists lanu_crm_contacts_owner_all on public.lanu_crm_contacts;
create policy lanu_contacts_owner_select on public.lanu_crm_contacts for select to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_contacts_owner_insert on public.lanu_crm_contacts for insert to authenticated
with check (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_contacts_owner_update on public.lanu_crm_contacts for update to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())))
with check (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_contacts_owner_delete on public.lanu_crm_contacts for delete to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));

drop policy if exists lanu_crm_quotes_owner_all on public.lanu_crm_quotes;
create policy lanu_quotes_owner_select on public.lanu_crm_quotes for select to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_quotes_owner_insert on public.lanu_crm_quotes for insert to authenticated
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (opportunity_id is null or exists (
    select 1 from public.lanu_crm_opportunities o
    where o.id = opportunity_id and o.customer_id = customer_id and o.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_quotes_owner_update on public.lanu_crm_quotes for update to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())))
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (opportunity_id is null or exists (
    select 1 from public.lanu_crm_opportunities o
    where o.id = opportunity_id and o.customer_id = customer_id and o.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_quotes_owner_delete on public.lanu_crm_quotes for delete to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));

drop policy if exists lanu_crm_quote_lines_owner_all on public.lanu_crm_quote_lines;
create policy lanu_quote_lines_owner_select on public.lanu_crm_quote_lines for select to authenticated
using (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_quote_lines_owner_insert on public.lanu_crm_quote_lines for insert to authenticated
with check (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_quote_lines_owner_update on public.lanu_crm_quote_lines for update to authenticated
using (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
))
with check (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_quote_lines_owner_delete on public.lanu_crm_quote_lines for delete to authenticated
using (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));

drop policy if exists lanu_crm_orders_owner_all on public.lanu_crm_orders;
create policy lanu_orders_owner_select on public.lanu_crm_orders for select to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_orders_owner_insert on public.lanu_crm_orders for insert to authenticated
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (quote_id is null or exists (
    select 1 from public.lanu_crm_quotes q
    join public.lanu_crm_customers c on c.id = q.customer_id
    where q.id = quote_id and q.customer_id = customer_id and c.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_orders_owner_update on public.lanu_crm_orders for update to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())))
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (quote_id is null or exists (
    select 1 from public.lanu_crm_quotes q
    join public.lanu_crm_customers c on c.id = q.customer_id
    where q.id = quote_id and q.customer_id = customer_id and c.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_orders_owner_delete on public.lanu_crm_orders for delete to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));

drop policy if exists lanu_crm_order_lines_owner_all on public.lanu_crm_order_lines;
create policy lanu_order_lines_owner_select on public.lanu_crm_order_lines for select to authenticated
using (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_order_lines_owner_insert on public.lanu_crm_order_lines for insert to authenticated
with check (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_order_lines_owner_update on public.lanu_crm_order_lines for update to authenticated
using (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
))
with check (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_order_lines_owner_delete on public.lanu_crm_order_lines for delete to authenticated
using (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));

create policy lanu_crm_sync_operations_owner_select on public.lanu_crm_sync_operations for select to authenticated
using (owner_user_id = (select auth.uid()));
create policy lanu_crm_sync_operations_owner_insert on public.lanu_crm_sync_operations for insert to authenticated
with check (owner_user_id = (select auth.uid()));
create policy lanu_crm_sync_operations_owner_update on public.lanu_crm_sync_operations for update to authenticated
using (owner_user_id = (select auth.uid())) with check (owner_user_id = (select auth.uid()));
create policy lanu_crm_sync_operations_owner_delete on public.lanu_crm_sync_operations for delete to authenticated
using (owner_user_id = (select auth.uid()));

create policy lanu_crm_applied_mutations_owner_select on public.lanu_crm_applied_mutations for select to authenticated
using (owner_user_id = (select auth.uid()));
create policy lanu_crm_applied_mutations_owner_insert on public.lanu_crm_applied_mutations for insert to authenticated
with check (owner_user_id = (select auth.uid()));

revoke all on public.lanu_crm_customers, public.lanu_crm_activities, public.lanu_crm_next_actions,
  public.lanu_crm_opportunities, public.lanu_crm_stage_transitions, public.lanu_crm_contacts,
  public.lanu_crm_quotes, public.lanu_crm_quote_lines, public.lanu_crm_orders,
  public.lanu_crm_order_lines, public.lanu_crm_sync_operations, public.lanu_crm_applied_mutations
from anon;

grant select, insert, update, delete on public.lanu_crm_customers, public.lanu_crm_activities,
  public.lanu_crm_next_actions, public.lanu_crm_opportunities, public.lanu_crm_stage_transitions,
  public.lanu_crm_contacts, public.lanu_crm_quotes, public.lanu_crm_quote_lines,
  public.lanu_crm_orders, public.lanu_crm_order_lines, public.lanu_crm_sync_operations
to authenticated;
grant select, insert on public.lanu_crm_applied_mutations to authenticated;

)
);
create index if not exists lanu_crm_customers_owner_idx on public.lanu_crm_customers(owner_user_id);
create index if not exists lanu_crm_customers_region_idx on public.lanu_crm_customers(owner_user_id, city, district);
create index if not exists lanu_crm_customers_source_idx on public.lanu_crm_customers(owner_user_id, source_id);
drop trigger if exists lanu_crm_customers_touch on public.lanu_crm_customers;
create trigger lanu_crm_customers_touch before update on public.lanu_crm_customers
for each row execute function public.lanu_touch_updated_at();

create table if not exists public.lanu_crm_activities (
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  type text not null,
  note text,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists lanu_crm_activities_owner_time_idx on public.lanu_crm_activities(owner_user_id, created_at);
create index if not exists lanu_crm_activities_customer_idx on public.lanu_crm_activities(customer_id, occurred_at);

create table if not exists public.lanu_crm_next_actions (
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
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
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  title text not null,
  status text not null default 'OPEN',
  amount numeric(14,2),
  currency text,
  amount_origin text not null default 'UNKNOWN',
  expected_close_at timestamptz,
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
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  from_stage text,
  to_stage text not null,
  changed_at timestamptz not null default now(),
  changed_by_user_id uuid,
  client_version bigint not null default 1
);
create index if not exists lanu_crm_stage_transitions_owner_idx on public.lanu_crm_stage_transitions(owner_user_id, changed_at);
create index if not exists lanu_crm_stage_transitions_customer_idx on public.lanu_crm_stage_transitions(customer_id, changed_at);

create table if not exists public.lanu_crm_contacts (
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
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
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  opportunity_id uuid references public.lanu_crm_opportunities(id) on delete set null,
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
  id uuid primary key,
  quote_id uuid not null references public.lanu_crm_quotes(id) on delete cascade,
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
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  quote_id uuid references public.lanu_crm_quotes(id) on delete set null,
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
  id uuid primary key,
  order_id uuid not null references public.lanu_crm_orders(id) on delete cascade,
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

create table if not exists public.lanu_crm_sync_operations (
  id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  entity_type text not null,
  entity_id uuid not null,
  operation text not null,
  idempotency_key text not null,
  state text not null,
  attempts integer not null default 0,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists lanu_crm_sync_operations_idempotency_idx
  on public.lanu_crm_sync_operations(owner_user_id, idempotency_key);
create index if not exists lanu_crm_sync_operations_state_idx
  on public.lanu_crm_sync_operations(owner_user_id, state, created_at);

create table if not exists public.lanu_crm_applied_mutations (
  operation_id uuid primary key,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  entity_type text not null,
  entity_id uuid not null,
  payload_version bigint not null,
  applied_at timestamptz not null default now()
);
create index if not exists lanu_crm_applied_mutations_owner_idx
  on public.lanu_crm_applied_mutations(owner_user_id, applied_at);

alter table public.lanu_crm_sync_operations enable row level security;
alter table public.lanu_crm_applied_mutations enable row level security;

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
    execute format('create policy %I on public.%I for select to authenticated using (owner_user_id = (select auth.uid()))', t || '_owner_select', t);
    execute format('create policy %I on public.%I for insert to authenticated with check (owner_user_id = (select auth.uid()))', t || '_owner_insert', t);
    execute format('create policy %I on public.%I for update to authenticated using (owner_user_id = (select auth.uid())) with check (owner_user_id = (select auth.uid()))', t || '_owner_update', t);
    execute format('create policy %I on public.%I for delete to authenticated using (owner_user_id = (select auth.uid()))', t || '_owner_delete', t);
  end loop;
end $$;

-- Child/commercial tables derive ownership through their parent customer.
drop policy if exists lanu_crm_contacts_owner_all on public.lanu_crm_contacts;
create policy lanu_contacts_owner_select on public.lanu_crm_contacts for select to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_contacts_owner_insert on public.lanu_crm_contacts for insert to authenticated
with check (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_contacts_owner_update on public.lanu_crm_contacts for update to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())))
with check (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_contacts_owner_delete on public.lanu_crm_contacts for delete to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));

drop policy if exists lanu_crm_quotes_owner_all on public.lanu_crm_quotes;
create policy lanu_quotes_owner_select on public.lanu_crm_quotes for select to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_quotes_owner_insert on public.lanu_crm_quotes for insert to authenticated
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (opportunity_id is null or exists (
    select 1 from public.lanu_crm_opportunities o
    where o.id = opportunity_id and o.customer_id = customer_id and o.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_quotes_owner_update on public.lanu_crm_quotes for update to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())))
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (opportunity_id is null or exists (
    select 1 from public.lanu_crm_opportunities o
    where o.id = opportunity_id and o.customer_id = customer_id and o.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_quotes_owner_delete on public.lanu_crm_quotes for delete to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));

drop policy if exists lanu_crm_quote_lines_owner_all on public.lanu_crm_quote_lines;
create policy lanu_quote_lines_owner_select on public.lanu_crm_quote_lines for select to authenticated
using (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_quote_lines_owner_insert on public.lanu_crm_quote_lines for insert to authenticated
with check (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_quote_lines_owner_update on public.lanu_crm_quote_lines for update to authenticated
using (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
))
with check (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_quote_lines_owner_delete on public.lanu_crm_quote_lines for delete to authenticated
using (exists (
  select 1 from public.lanu_crm_quotes q
  join public.lanu_crm_customers c on c.id = q.customer_id
  where q.id = quote_id and c.owner_user_id = (select auth.uid())
));

drop policy if exists lanu_crm_orders_owner_all on public.lanu_crm_orders;
create policy lanu_orders_owner_select on public.lanu_crm_orders for select to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));
create policy lanu_orders_owner_insert on public.lanu_crm_orders for insert to authenticated
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (quote_id is null or exists (
    select 1 from public.lanu_crm_quotes q
    join public.lanu_crm_customers c on c.id = q.customer_id
    where q.id = quote_id and q.customer_id = customer_id and c.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_orders_owner_update on public.lanu_crm_orders for update to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())))
with check (
  exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid()))
  and (quote_id is null or exists (
    select 1 from public.lanu_crm_quotes q
    join public.lanu_crm_customers c on c.id = q.customer_id
    where q.id = quote_id and q.customer_id = customer_id and c.owner_user_id = (select auth.uid())
  ))
);
create policy lanu_orders_owner_delete on public.lanu_crm_orders for delete to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = customer_id and c.owner_user_id = (select auth.uid())));

drop policy if exists lanu_crm_order_lines_owner_all on public.lanu_crm_order_lines;
create policy lanu_order_lines_owner_select on public.lanu_crm_order_lines for select to authenticated
using (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_order_lines_owner_insert on public.lanu_crm_order_lines for insert to authenticated
with check (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_order_lines_owner_update on public.lanu_crm_order_lines for update to authenticated
using (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
))
with check (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));
create policy lanu_order_lines_owner_delete on public.lanu_crm_order_lines for delete to authenticated
using (exists (
  select 1 from public.lanu_crm_orders o
  join public.lanu_crm_customers c on c.id = o.customer_id
  where o.id = order_id and c.owner_user_id = (select auth.uid())
));

create policy lanu_crm_sync_operations_owner_select on public.lanu_crm_sync_operations for select to authenticated
using (owner_user_id = (select auth.uid()));
create policy lanu_crm_sync_operations_owner_insert on public.lanu_crm_sync_operations for insert to authenticated
with check (owner_user_id = (select auth.uid()));
create policy lanu_crm_sync_operations_owner_update on public.lanu_crm_sync_operations for update to authenticated
using (owner_user_id = (select auth.uid())) with check (owner_user_id = (select auth.uid()));
create policy lanu_crm_sync_operations_owner_delete on public.lanu_crm_sync_operations for delete to authenticated
using (owner_user_id = (select auth.uid()));

create policy lanu_crm_applied_mutations_owner_select on public.lanu_crm_applied_mutations for select to authenticated
using (owner_user_id = (select auth.uid()));
create policy lanu_crm_applied_mutations_owner_insert on public.lanu_crm_applied_mutations for insert to authenticated
with check (owner_user_id = (select auth.uid()));

revoke all on public.lanu_crm_customers, public.lanu_crm_activities, public.lanu_crm_next_actions,
  public.lanu_crm_opportunities, public.lanu_crm_stage_transitions, public.lanu_crm_contacts,
  public.lanu_crm_quotes, public.lanu_crm_quote_lines, public.lanu_crm_orders,
  public.lanu_crm_order_lines, public.lanu_crm_sync_operations, public.lanu_crm_applied_mutations
from anon;

grant select, insert, update, delete on public.lanu_crm_customers, public.lanu_crm_activities,
  public.lanu_crm_next_actions, public.lanu_crm_opportunities, public.lanu_crm_stage_transitions,
  public.lanu_crm_contacts, public.lanu_crm_quotes, public.lanu_crm_quote_lines,
  public.lanu_crm_orders, public.lanu_crm_order_lines, public.lanu_crm_sync_operations
to authenticated;
grant select, insert on public.lanu_crm_applied_mutations to authenticated;

