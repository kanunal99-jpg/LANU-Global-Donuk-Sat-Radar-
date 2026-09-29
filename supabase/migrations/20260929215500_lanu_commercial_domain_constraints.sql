-- Keep cloud commercial documents within the same domain contract as the Android model.
alter table public.lanu_crm_quotes
  add constraint lanu_crm_quotes_status_check
  check (status in ('DRAFT','SENT','ACCEPTED','REJECTED','EXPIRED','CANCELLED')),
  add constraint lanu_crm_quotes_number_check
  check (length(trim(quote_number)) between 1 and 80);

alter table public.lanu_crm_orders
  add constraint lanu_crm_orders_status_check
  check (status in ('DRAFT','CONFIRMED','PREPARING','DISPATCHED','DELIVERED','CANCELLED')),
  add constraint lanu_crm_orders_number_check
  check (length(trim(order_number)) between 1 and 80);
