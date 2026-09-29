-- Cover nullable commercial relationship foreign keys flagged by Supabase performance advisor.
create index if not exists lanu_crm_quotes_opportunity_idx
  on public.lanu_crm_quotes(opportunity_id)
  where opportunity_id is not null;

create index if not exists lanu_crm_orders_quote_idx
  on public.lanu_crm_orders(quote_id)
  where quote_id is not null;
