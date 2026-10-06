-- Reproducible production migration applied on 2026-10-04.
-- The value is accepted only from user-authorized official registry inputs.
-- Public discovery sources (Overture/OSM) must never infer VKN/TCKN.
--
-- Fresh-install safety: the historical production migration may run before the
-- full CRM bootstrap migration. In that case this migration intentionally
-- becomes a no-op; the bootstrap migration creates the same column/constraint.

do $$
begin
  if to_regclass('public.lanu_crm_customers') is not null then
    alter table public.lanu_crm_customers
      add column if not exists tax_or_national_id text;

    alter table public.lanu_crm_customers
      drop constraint if exists lanu_crm_customers_tax_or_national_id_format;

    alter table public.lanu_crm_customers
      add constraint lanu_crm_customers_tax_or_national_id_format
      check (
        tax_or_national_id is null
        or tax_or_national_id ~ '^[0-9]{10,11}$'
      );

    comment on column public.lanu_crm_customers.tax_or_national_id is
      'User-authorized official source VKN/TCKN; never inferred from public discovery sources.';
  end if;
end $$;
