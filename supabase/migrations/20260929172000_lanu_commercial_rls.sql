-- LANU commercial CRM cloud schema.
-- Safe-by-default: RLS is enabled and rows are reachable only through an owned customer.
-- Production lanu_crm_customers.id and lanu_crm_opportunities.id are UUID; all cloud relational
-- identifiers below therefore use UUID as well. Android keeps IDs as String and serializes UUIDs.
-- Apply to canonical project jolfbmwxmsamzqtxassg only after review.

create table if not exists public.lanu_crm_contacts (
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  full_name text not null check (length(trim(full_name)) between 1 and 160),
  role text,
  phone text,
  email text,
  is_primary boolean not null default false,
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null check (version >= 0)
);

create table if not exists public.lanu_crm_quotes (
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  opportunity_id uuid references public.lanu_crm_opportunities(id) on delete set null,
  quote_number text not null,
  status text not null,
  currency text not null check (currency ~ '^[A-Z]{3}$'),
  total_minor bigint not null check (total_minor >= 0),
  valid_until_epoch_ms bigint,
  notes text,
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null check (version >= 0)
);

create table if not exists public.lanu_crm_quote_lines (
  id uuid primary key,
  quote_id uuid not null references public.lanu_crm_quotes(id) on delete cascade,
  product_id text,
  product_name text not null check (length(trim(product_name)) > 0),
  unit text not null check (length(trim(unit)) > 0),
  quantity_milli bigint not null check (quantity_milli > 0),
  unit_price_minor bigint not null check (unit_price_minor >= 0),
  discount_basis_points integer not null check (discount_basis_points between 0 and 10000),
  line_total_minor bigint not null check (line_total_minor >= 0),
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null check (version >= 0)
);

create table if not exists public.lanu_crm_orders (
  id uuid primary key,
  customer_id uuid not null references public.lanu_crm_customers(id) on delete cascade,
  quote_id uuid references public.lanu_crm_quotes(id) on delete set null,
  order_number text not null,
  status text not null,
  currency text not null check (currency ~ '^[A-Z]{3}$'),
  total_minor bigint not null check (total_minor >= 0),
  notes text,
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null check (version >= 0)
);

create table if not exists public.lanu_crm_order_lines (
  id uuid primary key,
  order_id uuid not null references public.lanu_crm_orders(id) on delete cascade,
  product_id text,
  product_name text not null check (length(trim(product_name)) > 0),
  unit text not null check (length(trim(unit)) > 0),
  quantity_milli bigint not null check (quantity_milli > 0),
  unit_price_minor bigint not null check (unit_price_minor >= 0),
  discount_basis_points integer not null check (discount_basis_points between 0 and 10000),
  line_total_minor bigint not null check (line_total_minor >= 0),
  created_at_epoch_ms bigint not null,
  updated_at_epoch_ms bigint not null,
  version bigint not null check (version >= 0)
);

create index if not exists lanu_crm_contacts_customer_updated_idx on public.lanu_crm_contacts(customer_id, updated_at_epoch_ms);
create index if not exists lanu_crm_quotes_customer_updated_idx on public.lanu_crm_quotes(customer_id, updated_at_epoch_ms);
create index if not exists lanu_crm_quote_lines_quote_updated_idx on public.lanu_crm_quote_lines(quote_id, updated_at_epoch_ms);
create index if not exists lanu_crm_orders_customer_updated_idx on public.lanu_crm_orders(customer_id, updated_at_epoch_ms);
create index if not exists lanu_crm_order_lines_order_updated_idx on public.lanu_crm_order_lines(order_id, updated_at_epoch_ms);

alter table public.lanu_crm_contacts enable row level security;
alter table public.lanu_crm_quotes enable row level security;
alter table public.lanu_crm_quote_lines enable row level security;
alter table public.lanu_crm_orders enable row level security;
alter table public.lanu_crm_order_lines enable row level security;

-- Parent customer ownership is the single authorization source. This avoids duplicating owner ids
-- across transactional snapshots and prevents a client from reassigning ownership on child rows.
create policy lanu_contacts_owner_all on public.lanu_crm_contacts for all to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = lanu_crm_contacts.customer_id and c.owner_user_id = auth.uid()))
with check (exists (select 1 from public.lanu_crm_customers c where c.id = lanu_crm_contacts.customer_id and c.owner_user_id = auth.uid()));

create policy lanu_quotes_owner_all on public.lanu_crm_quotes for all to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = lanu_crm_quotes.customer_id and c.owner_user_id = auth.uid()))
with check (exists (select 1 from public.lanu_crm_customers c where c.id = lanu_crm_quotes.customer_id and c.owner_user_id = auth.uid()));

create policy lanu_quote_lines_owner_all on public.lanu_crm_quote_lines for all to authenticated
using (exists (select 1 from public.lanu_crm_quotes q join public.lanu_crm_customers c on c.id = q.customer_id where q.id = lanu_crm_quote_lines.quote_id and c.owner_user_id = auth.uid()))
with check (exists (select 1 from public.lanu_crm_quotes q join public.lanu_crm_customers c on c.id = q.customer_id where q.id = lanu_crm_quote_lines.quote_id and c.owner_user_id = auth.uid()));

create policy lanu_orders_owner_all on public.lanu_crm_orders for all to authenticated
using (exists (select 1 from public.lanu_crm_customers c where c.id = lanu_crm_orders.customer_id and c.owner_user_id = auth.uid()))
with check (exists (select 1 from public.lanu_crm_customers c where c.id = lanu_crm_orders.customer_id and c.owner_user_id = auth.uid()));

create policy lanu_order_lines_owner_all on public.lanu_crm_order_lines for all to authenticated
using (exists (select 1 from public.lanu_crm_orders o join public.lanu_crm_customers c on c.id = o.customer_id where o.id = lanu_crm_order_lines.order_id and c.owner_user_id = auth.uid()))
with check (exists (select 1 from public.lanu_crm_orders o join public.lanu_crm_customers c on c.id = o.customer_id where o.id = lanu_crm_order_lines.order_id and c.owner_user_id = auth.uid()));
