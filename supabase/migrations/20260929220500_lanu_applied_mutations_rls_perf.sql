-- Cache row-independent auth.uid() once per statement instead of evaluating it per candidate row.
-- This preserves the exact owner-isolation semantics while clearing Supabase auth_rls_initplan.
drop policy if exists lanu_crm_applied_mutations_select_own
    on public.lanu_crm_applied_mutations;
create policy lanu_crm_applied_mutations_select_own
    on public.lanu_crm_applied_mutations
    for select
    to authenticated
    using (owner_user_id = (select auth.uid()));

drop policy if exists lanu_crm_applied_mutations_insert_own
    on public.lanu_crm_applied_mutations;
create policy lanu_crm_applied_mutations_insert_own
    on public.lanu_crm_applied_mutations
    for insert
    to authenticated
    with check (owner_user_id = (select auth.uid()));
