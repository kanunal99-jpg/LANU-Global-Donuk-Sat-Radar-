alter table if exists public.lanu_crm_customers
  add column if not exists tags_csv text not null default '';

alter table if exists public.lanu_crm_customers
  add column if not exists merged_into_customer_id uuid
  references public.lanu_crm_customers(id) on delete set null;

create index if not exists lanu_crm_customers_merged_idx
  on public.lanu_crm_customers(owner_user_id, merged_into_customer_id);

comment on column public.lanu_crm_customers.tags_csv is
  'User-defined CRM segmentation tags, normalized and pipe-delimited by the client.';

comment on column public.lanu_crm_customers.merged_into_customer_id is
  'Audit tombstone. Non-null rows were merged into the referenced active CRM customer.';
