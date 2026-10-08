-- Run only after applying *all* committed CRM migrations into a disposable PostgreSQL DB.
-- Fail closed on missing RLS or any cross-user access to CRM rows.
do $$
declare total int; insecure int;
begin
  select count(*), count(*) filter (where not c.relrowsecurity)
    into total, insecure
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relkind = 'r'
    and c.relname like 'lanu_crm_%';
  if total <> 12 or insecure <> 0 then
    raise exception 'Expected 12 RLS-enabled LANU CRM tables; got %, insecure %', total, insecure;
  end if;
  if has_table_privilege('anon', 'public.lanu_crm_customers', 'SELECT') then
    raise exception 'anon unexpectedly has CRM table read permission';
  end if;
end $$;

insert into auth.users(id) values
  ('00000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000002');

set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);
insert into public.lanu_crm_customers(id,owner_user_id,name) values
  ('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001','CI owner A');
insert into public.lanu_crm_quotes(id,customer_id,quote_number,status,currency,created_at_epoch_ms,updated_at_epoch_ms) values
  ('00000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000011','CI-Q-1','DRAFT','TRY',1,1);
insert into public.lanu_crm_quote_lines(
  id,quote_id,product_name,unit,quantity_milli,unit_price_minor,line_total_minor,created_at_epoch_ms,updated_at_epoch_ms
) values ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000021','CI Product','pcs',1000,100,100,1,1);
insert into public.lanu_crm_orders(id,customer_id,quote_id,order_number,status,currency,created_at_epoch_ms,updated_at_epoch_ms)
values ('00000000-0000-0000-0000-000000000041','00000000-0000-0000-0000-000000000011',
        '00000000-0000-0000-0000-000000000021','CI-ORDER-1','DRAFT','TRY',1,1);
insert into public.lanu_crm_order_lines(
  id,order_id,product_name,unit,quantity_milli,unit_price_minor,line_total_minor,created_at_epoch_ms,updated_at_epoch_ms
) values ('00000000-0000-0000-0000-000000000051','00000000-0000-0000-0000-000000000041','CI Product','pcs',1000,100,100,1,1);

do $$
begin
  if (select count(*) from public.lanu_crm_customers) <> 1
  or (select count(*) from public.lanu_crm_quotes) <> 1
  or (select count(*) from public.lanu_crm_quote_lines) <> 1
  or (select count(*) from public.lanu_crm_orders) <> 1
  or (select count(*) from public.lanu_crm_order_lines) <> 1 then
    raise exception 'Owner A cannot see own CRM tree';
  end if;
end $$;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', false);
do $$
begin
  if (select count(*) from public.lanu_crm_customers) <> 0
  or (select count(*) from public.lanu_crm_quotes) <> 0
  or (select count(*) from public.lanu_crm_quote_lines) <> 0
  or (select count(*) from public.lanu_crm_orders) <> 0
  or (select count(*) from public.lanu_crm_order_lines) <> 0 then
    raise exception 'Cross-tenant SELECT leak detected';
  end if;

  begin
    insert into public.lanu_crm_customers(id,owner_user_id,name) values
      ('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000001','Spoofed owner');
    raise exception 'Cross-tenant customer INSERT unexpectedly succeeded';
  exception when insufficient_privilege then null;
  end;

  begin
    insert into public.lanu_crm_quote_lines(
      id,quote_id,product_name,unit,quantity_milli,unit_price_minor,line_total_minor,created_at_epoch_ms,updated_at_epoch_ms
    ) values ('00000000-0000-0000-0000-000000000032','00000000-0000-0000-0000-000000000021','Spoofed','pcs',1000,100,100,1,1);
    raise exception 'Cross-tenant quote-line INSERT unexpectedly succeeded';
  exception when insufficient_privilege then null;
  end;
end $$;

insert into public.lanu_crm_customers(id,owner_user_id,name) values
  ('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000002','CI owner B');
do $$
begin
  begin
    update public.lanu_crm_customers
      set owner_user_id = '00000000-0000-0000-0000-000000000001'
      where id = '00000000-0000-0000-0000-000000000012';
    raise exception 'Cross-tenant reassignment UPDATE unexpectedly succeeded';
  exception when insufficient_privilege then null;
  end;
  if (select count(*) from public.lanu_crm_customers) <> 1 then
    raise exception 'Owner B cannot access own customer or owner A leaked';
  end if;
end $$;
reset role;
select 'Fresh migration + 12-table RLS + tenant isolation smoke PASS' as result;
