-- LANU atomic optimistic-concurrency boundary for versioned CRM mutations.
-- Keeps RLS active (SECURITY INVOKER), makes retries idempotent by operation_id,
-- and ensures two devices editing the same base version cannot silently overwrite each other.

create table if not exists public.lanu_crm_applied_mutations (
    operation_id uuid primary key,
    owner_user_id uuid not null references auth.users(id) on delete cascade,
    entity_type text not null,
    entity_id uuid not null,
    payload_version bigint not null check (payload_version >= 1),
    applied_at timestamptz not null default now()
);

alter table public.lanu_crm_applied_mutations enable row level security;

revoke all on table public.lanu_crm_applied_mutations from anon;
revoke all on table public.lanu_crm_applied_mutations from authenticated;
grant select, insert on table public.lanu_crm_applied_mutations to authenticated;

create index if not exists lanu_crm_applied_mutations_owner_applied_idx
    on public.lanu_crm_applied_mutations(owner_user_id, applied_at desc);

drop policy if exists lanu_crm_applied_mutations_select_own on public.lanu_crm_applied_mutations;
create policy lanu_crm_applied_mutations_select_own
    on public.lanu_crm_applied_mutations
    for select
    to authenticated
    using (owner_user_id = auth.uid());

drop policy if exists lanu_crm_applied_mutations_insert_own on public.lanu_crm_applied_mutations;
create policy lanu_crm_applied_mutations_insert_own
    on public.lanu_crm_applied_mutations
    for insert
    to authenticated
    with check (owner_user_id = auth.uid());

create or replace function public.lanu_apply_versioned_crm_mutation(
    p_operation_id uuid,
    p_entity_type text,
    p_payload jsonb,
    p_expected_version bigint
) returns text
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_id uuid;
    v_payload_version bigint;
    v_rows integer := 0;
begin
    if v_uid is null then
        raise exception 'LANU_AUTH_REQUIRED' using errcode = '42501';
    end if;
    if p_operation_id is null or p_payload is null then
        raise exception 'LANU_INVALID_MUTATION' using errcode = '22023';
    end if;
    if p_expected_version < 0 then
        raise exception 'LANU_INVALID_EXPECTED_VERSION' using errcode = '22023';
    end if;

    if exists (
        select 1
        from public.lanu_crm_applied_mutations m
        where m.operation_id = p_operation_id
          and m.owner_user_id = v_uid
    ) then
        return 'APPLIED';
    end if;

    v_id := (p_payload ->> 'id')::uuid;
    v_payload_version := case
        when p_entity_type = 'customer' then (p_payload ->> 'sync_version')::bigint
        else (p_payload ->> 'version')::bigint
    end;

    if v_payload_version is null or v_payload_version <> p_expected_version + 1 then
        return 'INVALID_VERSION';
    end if;

    case p_entity_type
        when 'customer' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_customers where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_customers (
                id, owner_user_id, stage, source, source_id, name,
                city, district, neighborhood, address, latitude, longitude,
                data_quality, notes, sync_version, created_at, updated_at
            ) values (
                v_id, v_uid,
                p_payload ->> 'stage',
                nullif(p_payload ->> 'source', ''),
                nullif(p_payload ->> 'source_id', ''),
                p_payload ->> 'name',
                nullif(p_payload ->> 'city', ''),
                nullif(p_payload ->> 'district', ''),
                nullif(p_payload ->> 'neighborhood', ''),
                nullif(p_payload ->> 'address', ''),
                nullif(p_payload ->> 'latitude', '')::double precision,
                nullif(p_payload ->> 'longitude', '')::double precision,
                coalesce(nullif(p_payload ->> 'data_quality', ''), 'UNKNOWN'),
                nullif(p_payload ->> 'notes', ''),
                v_payload_version,
                coalesce(nullif(p_payload ->> 'created_at', '')::timestamptz, now()),
                coalesce(nullif(p_payload ->> 'updated_at', '')::timestamptz, now())
            )
            on conflict (id) do update set
                stage = excluded.stage,
                source = excluded.source,
                source_id = excluded.source_id,
                name = excluded.name,
                city = excluded.city,
                district = excluded.district,
                neighborhood = excluded.neighborhood,
                address = excluded.address,
                latitude = excluded.latitude,
                longitude = excluded.longitude,
                data_quality = excluded.data_quality,
                notes = excluded.notes,
                sync_version = excluded.sync_version,
                updated_at = excluded.updated_at
            where lanu_crm_customers.owner_user_id = v_uid
              and lanu_crm_customers.sync_version = p_expected_version;
            get diagnostics v_rows = row_count;

        when 'next_action' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_next_actions where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_next_actions (
                id, owner_user_id, customer_id, type, due_at, note,
                created_by_user_id, completed_at, completed_by_user_id,
                version, created_at, updated_at
            ) values (
                v_id, v_uid,
                (p_payload ->> 'customer_id')::uuid,
                p_payload ->> 'type',
                (p_payload ->> 'due_at')::timestamptz,
                nullif(p_payload ->> 'note', ''),
                coalesce(nullif(p_payload ->> 'created_by_user_id', '')::uuid, v_uid),
                nullif(p_payload ->> 'completed_at', '')::timestamptz,
                nullif(p_payload ->> 'completed_by_user_id', '')::uuid,
                v_payload_version,
                coalesce(nullif(p_payload ->> 'created_at', '')::timestamptz, now()),
                coalesce(nullif(p_payload ->> 'updated_at', '')::timestamptz, now())
            )
            on conflict (id) do update set
                customer_id = excluded.customer_id,
                type = excluded.type,
                due_at = excluded.due_at,
                note = excluded.note,
                completed_at = excluded.completed_at,
                completed_by_user_id = excluded.completed_by_user_id,
                version = excluded.version,
                updated_at = excluded.updated_at
            where lanu_crm_next_actions.owner_user_id = v_uid
              and lanu_crm_next_actions.version = p_expected_version;
            get diagnostics v_rows = row_count;

        when 'opportunity' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_opportunities where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_opportunities (
                id, owner_user_id, customer_id, title, status, amount, currency,
                amount_origin, note, version, created_at, updated_at
            ) values (
                v_id, v_uid,
                (p_payload ->> 'customer_id')::uuid,
                p_payload ->> 'title',
                p_payload ->> 'status',
                nullif(p_payload ->> 'amount', '')::numeric,
                nullif(p_payload ->> 'currency', ''),
                coalesce(nullif(p_payload ->> 'amount_origin', ''), 'UNKNOWN'),
                nullif(p_payload ->> 'note', ''),
                v_payload_version,
                coalesce(nullif(p_payload ->> 'created_at', '')::timestamptz, now()),
                coalesce(nullif(p_payload ->> 'updated_at', '')::timestamptz, now())
            )
            on conflict (id) do update set
                customer_id = excluded.customer_id,
                title = excluded.title,
                status = excluded.status,
                amount = excluded.amount,
                currency = excluded.currency,
                amount_origin = excluded.amount_origin,
                note = excluded.note,
                version = excluded.version,
                updated_at = excluded.updated_at
            where lanu_crm_opportunities.owner_user_id = v_uid
              and lanu_crm_opportunities.version = p_expected_version;
            get diagnostics v_rows = row_count;

        when 'contact' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_contacts where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_contacts (
                id, customer_id, full_name, role, phone, email, is_primary,
                created_at_epoch_ms, updated_at_epoch_ms, version
            ) values (
                v_id,
                (p_payload ->> 'customer_id')::uuid,
                p_payload ->> 'full_name',
                nullif(p_payload ->> 'role', ''),
                nullif(p_payload ->> 'phone', ''),
                nullif(p_payload ->> 'email', ''),
                coalesce((p_payload ->> 'is_primary')::boolean, false),
                (p_payload ->> 'created_at_epoch_ms')::bigint,
                (p_payload ->> 'updated_at_epoch_ms')::bigint,
                v_payload_version
            )
            on conflict (id) do update set
                customer_id = excluded.customer_id,
                full_name = excluded.full_name,
                role = excluded.role,
                phone = excluded.phone,
                email = excluded.email,
                is_primary = excluded.is_primary,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                version = excluded.version
            where lanu_crm_contacts.version = p_expected_version;
            get diagnostics v_rows = row_count;

        when 'quote' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_quotes where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_quotes (
                id, customer_id, opportunity_id, quote_number, status, currency,
                total_minor, valid_until_epoch_ms, notes,
                created_at_epoch_ms, updated_at_epoch_ms, version
            ) values (
                v_id,
                (p_payload ->> 'customer_id')::uuid,
                nullif(p_payload ->> 'opportunity_id', '')::uuid,
                p_payload ->> 'quote_number',
                p_payload ->> 'status',
                p_payload ->> 'currency',
                (p_payload ->> 'total_minor')::bigint,
                nullif(p_payload ->> 'valid_until_epoch_ms', '')::bigint,
                nullif(p_payload ->> 'notes', ''),
                (p_payload ->> 'created_at_epoch_ms')::bigint,
                (p_payload ->> 'updated_at_epoch_ms')::bigint,
                v_payload_version
            )
            on conflict (id) do update set
                customer_id = excluded.customer_id,
                opportunity_id = excluded.opportunity_id,
                quote_number = excluded.quote_number,
                status = excluded.status,
                currency = excluded.currency,
                total_minor = excluded.total_minor,
                valid_until_epoch_ms = excluded.valid_until_epoch_ms,
                notes = excluded.notes,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                version = excluded.version
            where lanu_crm_quotes.version = p_expected_version;
            get diagnostics v_rows = row_count;

        when 'quote_line' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_quote_lines where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_quote_lines (
                id, quote_id, product_id, product_name, unit, quantity_milli,
                unit_price_minor, discount_basis_points, line_total_minor,
                created_at_epoch_ms, updated_at_epoch_ms, version
            ) values (
                v_id,
                (p_payload ->> 'quote_id')::uuid,
                nullif(p_payload ->> 'product_id', ''),
                p_payload ->> 'product_name',
                p_payload ->> 'unit',
                (p_payload ->> 'quantity_milli')::bigint,
                (p_payload ->> 'unit_price_minor')::bigint,
                (p_payload ->> 'discount_basis_points')::integer,
                (p_payload ->> 'line_total_minor')::bigint,
                (p_payload ->> 'created_at_epoch_ms')::bigint,
                (p_payload ->> 'updated_at_epoch_ms')::bigint,
                v_payload_version
            )
            on conflict (id) do update set
                quote_id = excluded.quote_id,
                product_id = excluded.product_id,
                product_name = excluded.product_name,
                unit = excluded.unit,
                quantity_milli = excluded.quantity_milli,
                unit_price_minor = excluded.unit_price_minor,
                discount_basis_points = excluded.discount_basis_points,
                line_total_minor = excluded.line_total_minor,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                version = excluded.version
            where lanu_crm_quote_lines.version = p_expected_version;
            get diagnostics v_rows = row_count;

        when 'order' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_orders where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_orders (
                id, customer_id, quote_id, order_number, status, currency,
                total_minor, notes, created_at_epoch_ms, updated_at_epoch_ms, version
            ) values (
                v_id,
                (p_payload ->> 'customer_id')::uuid,
                nullif(p_payload ->> 'quote_id', '')::uuid,
                p_payload ->> 'order_number',
                p_payload ->> 'status',
                p_payload ->> 'currency',
                (p_payload ->> 'total_minor')::bigint,
                nullif(p_payload ->> 'notes', ''),
                (p_payload ->> 'created_at_epoch_ms')::bigint,
                (p_payload ->> 'updated_at_epoch_ms')::bigint,
                v_payload_version
            )
            on conflict (id) do update set
                customer_id = excluded.customer_id,
                quote_id = excluded.quote_id,
                order_number = excluded.order_number,
                status = excluded.status,
                currency = excluded.currency,
                total_minor = excluded.total_minor,
                notes = excluded.notes,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                version = excluded.version
            where lanu_crm_orders.version = p_expected_version;
            get diagnostics v_rows = row_count;

        when 'order_line' then
            if p_expected_version > 0 and not exists (
                select 1 from public.lanu_crm_order_lines where id = v_id
            ) then return 'CONFLICT'; end if;

            insert into public.lanu_crm_order_lines (
                id, order_id, product_id, product_name, unit, quantity_milli,
                unit_price_minor, discount_basis_points, line_total_minor,
                created_at_epoch_ms, updated_at_epoch_ms, version
            ) values (
                v_id,
                (p_payload ->> 'order_id')::uuid,
                nullif(p_payload ->> 'product_id', ''),
                p_payload ->> 'product_name',
                p_payload ->> 'unit',
                (p_payload ->> 'quantity_milli')::bigint,
                (p_payload ->> 'unit_price_minor')::bigint,
                (p_payload ->> 'discount_basis_points')::integer,
                (p_payload ->> 'line_total_minor')::bigint,
                (p_payload ->> 'created_at_epoch_ms')::bigint,
                (p_payload ->> 'updated_at_epoch_ms')::bigint,
                v_payload_version
            )
            on conflict (id) do update set
                order_id = excluded.order_id,
                product_id = excluded.product_id,
                product_name = excluded.product_name,
                unit = excluded.unit,
                quantity_milli = excluded.quantity_milli,
                unit_price_minor = excluded.unit_price_minor,
                discount_basis_points = excluded.discount_basis_points,
                line_total_minor = excluded.line_total_minor,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                version = excluded.version
            where lanu_crm_order_lines.version = p_expected_version;
            get diagnostics v_rows = row_count;

        else
            raise exception 'LANU_UNKNOWN_ENTITY_TYPE: %', p_entity_type using errcode = '22023';
    end case;

    if v_rows = 0 then
        if exists (
            select 1
            from public.lanu_crm_applied_mutations m
            where m.operation_id = p_operation_id
              and m.owner_user_id = v_uid
        ) then
            return 'APPLIED';
        end if;
        return 'CONFLICT';
    end if;

    insert into public.lanu_crm_applied_mutations (
        operation_id, owner_user_id, entity_type, entity_id, payload_version
    ) values (
        p_operation_id, v_uid, p_entity_type, v_id, v_payload_version
    ) on conflict (operation_id) do nothing;

    return 'APPLIED';
end;
$$;

revoke all on function public.lanu_apply_versioned_crm_mutation(uuid, text, jsonb, bigint) from public;
revoke all on function public.lanu_apply_versioned_crm_mutation(uuid, text, jsonb, bigint) from anon;
grant execute on function public.lanu_apply_versioned_crm_mutation(uuid, text, jsonb, bigint) to authenticated;
