-- LANU CRM complete reproducible Supabase schema.
-- Safe to run after older partial migrations; all objects are idempotent where practical.

create table if not exists public.lanu_crm_customers (
    id text primary key,
    owner_user_id uuid not null references auth.users(id) on delete cascade,
    stage text not null default 'PROSPECT',
    source text not null default 'osm',
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

alter table public.lanu_crm_customers add column if not exists signboard_name text;
alter table public.lanu_crm_customers add column if not exists address text;
alter table public.lanu_crm_customers add column if not exists latitude double precision;
alter table public.lanu_crm_customers add column if not exists longitude double precision;
alter table public.lanu_crm_customers add column if not exists data_quality text not null default 'UNKNOWN';
alter table public.lanu_crm_customers add column if not exists contact_name text;
alter table public.lanu_crm_customers add column if not exists business_type text;
alter table public.lanu_crm_customers add column if not exists tax_or_national_id text;
alter table public.lanu_crm_customers add column if not exists phone text;
alter table public.lanu_crm_customers add column if not exists website text;
alter table public.lanu_crm_customers add column if not exists registry_status text not null default 'UNVERIFIED';
alter table public.lanu_crm_customers add column if not exists registry_source text;
alter table public.lanu_crm_customers add column if not exists registry_number text;
alter table public.lanu_crm_customers add column if not exists sync_version bigint not null default 1;

create index if not exists lanu_crm_customers_owner_region_idx
    on public.lanu_crm_customers(owner_user_id, city, district);
create index if not exists lanu_crm_customers_owner_stage_idx
    on public.lanu_crm_customers(owner_user_id, stage);
create index if not exists lanu_crm_customers_owner_updated_idx
    on public.lanu_crm_customers(owner_user_id, updated_at);
create index if not exists lanu_crm_customers_source_idx
    on public.lanu_crm_customers(owner_user_id, source_id);

create table if not exists public.lanu_crm_activities (
    id text primary key,
    owner_user_id uuid not null references auth.users(id) on delete cascade,
    customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
    type text not null,
    note text,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now()
);
create index if not exists lanu_crm_activities_owner_customer_idx
    on public.lanu_crm_activities(owner_user_id, customer_id, occurred_at);

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
create index if not exists lanu_crm_next_actions_owner_due_idx
    on public.lanu_crm_next_actions(owner_user_id, due_at, completed_at);
create index if not exists lanu_crm_next_actions_customer_idx
    on public.lanu_crm_next_actions(customer_id, due_at);

create table if not exists public.lanu_crm_opportunities (
    id text primary key,
    owner_user_id uuid not null references auth.users(id) on delete cascade,
    customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
    title text not null,
    status text not null default 'OPEN',
    note text,
    amount numeric(18,2),
    currency text,
    amount_origin text not null default 'UNKNOWN',
    version bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists lanu_crm_opportunities_owner_status_idx
    on public.lanu_crm_opportunities(owner_user_id, status);
create index if not exists lanu_crm_opportunities_customer_idx
    on public.lanu_crm_opportunities(customer_id, updated_at);

create table if not exists public.lanu_crm_stage_transitions (
    id text primary key,
    owner_user_id uuid not null references auth.users(id) on delete cascade,
    customer_id text not null references public.lanu_crm_customers(id) on delete cascade,
    from_stage text,
    to_stage text not null,
    changed_at timestamptz not null,
    changed_by_user_id uuid,
    client_version bigint not null default 1
);
create index if not exists lanu_crm_stage_transitions_owner_customer_idx
    on public.lanu_crm_stage_transitions(owner_user_id, customer_id, changed_at);

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
create index if not exists lanu_crm_contacts_customer_idx
    on public.lanu_crm_contacts(customer_id, updated_at_epoch_ms);

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
create index if not exists lanu_crm_quotes_customer_idx
    on public.lanu_crm_quotes(customer_id, updated_at_epoch_ms);
create index if not exists lanu_crm_quotes_status_idx
    on public.lanu_crm_quotes(status);

create table if not exists public.lanu_crm_quote_lines (
    id text primary key,
    quote_id text not null references public.lanu_crm_quotes(id) on delete cascade,
    product_id text,
    product_name text not null,
    unit text not null,
    quantity_milli bigint not null,
    unit_price_minor bigint not null,
    discount_basis_points integer not null default 0,
    line_total_minor bigint not null,
    created_at_epoch_ms bigint not null,
    updated_at_epoch_ms bigint not null,
    version bigint not null default 1,
    constraint lanu_crm_quote_lines_discount_check
        check (discount_basis_points between 0 and 10000)
);
create index if not exists lanu_crm_quote_lines_quote_idx
    on public.lanu_crm_quote_lines(quote_id, updated_at_epoch_ms);
create index if not exists lanu_crm_quote_lines_product_idx
    on public.lanu_crm_quote_lines(product_id);

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
create index if not exists lanu_crm_orders_customer_idx
    on public.lanu_crm_orders(customer_id, updated_at_epoch_ms);
create index if not exists lanu_crm_orders_status_idx
    on public.lanu_crm_orders(status);

create table if not exists public.lanu_crm_order_lines (
    id text primary key,
    order_id text not null references public.lanu_crm_orders(id) on delete cascade,
    product_id text,
    product_name text not null,
    unit text not null,
    quantity_milli bigint not null,
    unit_price_minor bigint not null,
    discount_basis_points integer not null default 0,
    line_total_minor bigint not null,
    created_at_epoch_ms bigint not null,
    updated_at_epoch_ms bigint not null,
    version bigint not null default 1,
    constraint lanu_crm_order_lines_discount_check
        check (discount_basis_points between 0 and 10000)
);
create index if not exists lanu_crm_order_lines_order_idx
    on public.lanu_crm_order_lines(order_id, updated_at_epoch_ms);
create index if not exists lanu_crm_order_lines_product_idx
    on public.lanu_crm_order_lines(product_id);

-- Core owner-scoped tables.
alter table public.lanu_crm_customers enable row level security;
alter table public.lanu_crm_activities enable row level security;
alter table public.lanu_crm_next_actions enable row level security;
alter table public.lanu_crm_opportunities enable row level security;
alter table public.lanu_crm_stage_transitions enable row level security;

drop policy if exists lanu_crm_customers_owner_all on public.lanu_crm_customers;
create policy lanu_crm_customers_owner_all
on public.lanu_crm_customers
for all
to authenticated
using (owner_user_id = auth.uid())
with check (owner_user_id = auth.uid());

drop policy if exists lanu_crm_activities_owner_all on public.lanu_crm_activities;
create policy lanu_crm_activities_owner_all
on public.lanu_crm_activities
for all
to authenticated
using (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
)
with check (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
);

drop policy if exists lanu_crm_next_actions_owner_all on public.lanu_crm_next_actions;
create policy lanu_crm_next_actions_owner_all
on public.lanu_crm_next_actions
for all
to authenticated
using (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
)
with check (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
);

drop policy if exists lanu_crm_opportunities_owner_all on public.lanu_crm_opportunities;
create policy lanu_crm_opportunities_owner_all
on public.lanu_crm_opportunities
for all
to authenticated
using (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
)
with check (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
);

drop policy if exists lanu_crm_stage_transitions_owner_all on public.lanu_crm_stage_transitions;
create policy lanu_crm_stage_transitions_owner_all
on public.lanu_crm_stage_transitions
for all
to authenticated
using (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
)
with check (
    owner_user_id = auth.uid()
    and exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
);

-- Commercial child tables intentionally derive ownership from their parent customer.
alter table public.lanu_crm_contacts enable row level security;
alter table public.lanu_crm_quotes enable row level security;
alter table public.lanu_crm_quote_lines enable row level security;
alter table public.lanu_crm_orders enable row level security;
alter table public.lanu_crm_order_lines enable row level security;

drop policy if exists lanu_crm_contacts_owner_all on public.lanu_crm_contacts;
create policy lanu_crm_contacts_owner_all
on public.lanu_crm_contacts
for all
to authenticated
using (
    exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
);

drop policy if exists lanu_crm_quotes_owner_all on public.lanu_crm_quotes;
create policy lanu_crm_quotes_owner_all
on public.lanu_crm_quotes
for all
to authenticated
using (
    exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
);

drop policy if exists lanu_crm_quote_lines_owner_all on public.lanu_crm_quote_lines;
create policy lanu_crm_quote_lines_owner_all
on public.lanu_crm_quote_lines
for all
to authenticated
using (
    exists (
        select 1
        from public.lanu_crm_quotes q
        join public.lanu_crm_customers c on c.id = q.customer_id
        where q.id = quote_id and c.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.lanu_crm_quotes q
        join public.lanu_crm_customers c on c.id = q.customer_id
        where q.id = quote_id and c.owner_user_id = auth.uid()
    )
);

drop policy if exists lanu_crm_orders_owner_all on public.lanu_crm_orders;
create policy lanu_crm_orders_owner_all
on public.lanu_crm_orders
for all
to authenticated
using (
    exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.lanu_crm_customers c
        where c.id = customer_id and c.owner_user_id = auth.uid()
    )
);

drop policy if exists lanu_crm_order_lines_owner_all on public.lanu_crm_order_lines;
create policy lanu_crm_order_lines_owner_all
on public.lanu_crm_order_lines
for all
to authenticated
using (
    exists (
        select 1
        from public.lanu_crm_orders o
        join public.lanu_crm_customers c on c.id = o.customer_id
        where o.id = order_id and c.owner_user_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.lanu_crm_orders o
        join public.lanu_crm_customers c on c.id = o.customer_id
        where o.id = order_id and c.owner_user_id = auth.uid()
    )
);

revoke all on table
    public.lanu_crm_customers,
    public.lanu_crm_activities,
    public.lanu_crm_next_actions,
    public.lanu_crm_opportunities,
    public.lanu_crm_stage_transitions,
    public.lanu_crm_contacts,
    public.lanu_crm_quotes,
    public.lanu_crm_quote_lines,
    public.lanu_crm_orders,
    public.lanu_crm_order_lines
from anon;

grant select, insert, update, delete on table
    public.lanu_crm_customers,
    public.lanu_crm_activities,
    public.lanu_crm_next_actions,
    public.lanu_crm_opportunities,
    public.lanu_crm_stage_transitions,
    public.lanu_crm_contacts,
    public.lanu_crm_quotes,
    public.lanu_crm_quote_lines,
    public.lanu_crm_orders,
    public.lanu_crm_order_lines
to authenticated;
