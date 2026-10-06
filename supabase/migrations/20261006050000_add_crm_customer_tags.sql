alter table if exists public.lanu_crm_customers
  add column if not exists tags_csv text not null default '';

comment on column public.lanu_crm_customers.tags_csv is
  'User-defined CRM segmentation tags, normalized and pipe-delimited by the client.';
