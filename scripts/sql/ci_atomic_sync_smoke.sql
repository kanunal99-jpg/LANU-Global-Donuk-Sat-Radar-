-- Ephemeral PostgreSQL ONLY. Runs after ci_rls_smoke.sql; its two synthetic users exist.
-- This validates the actual versioned RPC code, idempotency, concurrency and RLS isolation.
DO $$
BEGIN
  IF has_function_privilege('anon', 'public.lanu_apply_versioned_crm_mutation(uuid,text,jsonb,bigint)', 'EXECUTE')
     OR NOT has_function_privilege('authenticated', 'public.lanu_apply_versioned_crm_mutation(uuid,text,jsonb,bigint)', 'EXECUTE')
  THEN RAISE EXCEPTION 'Incorrect RPC permissions (anon/authenticated)'; END IF;
  IF EXISTS (
    SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.proname='lanu_apply_versioned_crm_mutation' AND p.prosecdef
  ) THEN RAISE EXCEPTION 'RPC must remain SECURITY INVOKER'; END IF;
END $$;

SET ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);

DO $$
DECLARE
  op1 uuid := '00000000-0000-0000-0000-000000000081';
  op2 uuid := '00000000-0000-0000-0000-000000000082';
  op3 uuid := '00000000-0000-0000-0000-000000000083';
  contact uuid := '00000000-0000-0000-0000-000000000061';
  parent uuid := '00000000-0000-0000-0000-000000000011';
  row1 jsonb;
  row2 jsonb;
  status text;
BEGIN
  row1 := jsonb_build_object('id',contact,'customer_id',parent,'full_name','Atomic owner A',
    'is_primary',false,'created_at_epoch_ms',1,'updated_at_epoch_ms',1,'version',1);
  status := public.lanu_apply_versioned_crm_mutation(op1,'contact',row1,0);
  IF status <> 'APPLIED' THEN RAISE EXCEPTION 'Atomic create failed: %',status; END IF;
  IF (SELECT version FROM public.lanu_crm_contacts WHERE id=contact) <> 1
    THEN RAISE EXCEPTION 'Atomic create version wrong'; END IF;
  IF public.lanu_apply_versioned_crm_mutation(op1,'contact',row1,0) <> 'APPLIED'
    THEN RAISE EXCEPTION 'Idempotent retry not APPLIED'; END IF;
  IF (SELECT count(*) FROM public.lanu_crm_applied_mutations WHERE operation_id=op1) <> 1
    THEN RAISE EXCEPTION 'Duplicated mutation receipt'; END IF;

  row2 := row1 || jsonb_build_object('full_name','Atomic v2','updated_at_epoch_ms',2,'version',2);
  status := public.lanu_apply_versioned_crm_mutation(op2,'contact',row2,1);
  IF status <> 'APPLIED' THEN RAISE EXCEPTION 'Atomic update failed: %',status; END IF;
  IF (SELECT version FROM public.lanu_crm_contacts WHERE id=contact) <> 2
    THEN RAISE EXCEPTION 'Atomic update version wrong'; END IF;
  IF public.lanu_apply_versioned_crm_mutation(op3,'contact',row2,1) <> 'CONFLICT'
    THEN RAISE EXCEPTION 'Stale concurrent update was not rejected'; END IF;
  IF public.lanu_apply_versioned_crm_mutation(op3,'contact',row2,0) <> 'INVALID_VERSION'
    THEN RAISE EXCEPTION 'Incorrect version jump not rejected'; END IF;
  IF (SELECT full_name FROM public.lanu_crm_contacts WHERE id=contact) <> 'Atomic v2'
    THEN RAISE EXCEPTION 'Conflict overwrote newer remote data'; END IF;
END $$;

SELECT set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
DO $$
DECLARE
  status text;
BEGIN
  IF EXISTS (SELECT 1 FROM public.lanu_crm_contacts
    WHERE id='00000000-0000-0000-0000-000000000061')
    THEN RAISE EXCEPTION 'Tenant B can read A contact'; END IF;
  BEGIN
    status := public.lanu_apply_versioned_crm_mutation(
      '00000000-0000-0000-0000-000000000084','contact',
      jsonb_build_object('id','00000000-0000-0000-0000-000000000061',
        'customer_id','00000000-0000-0000-0000-000000000011',
        'full_name','Hijacked','is_primary',false,'created_at_epoch_ms',1,
        'updated_at_epoch_ms',3,'version',3),2
    );
    IF status = 'APPLIED' THEN RAISE EXCEPTION 'Cross-tenant RPC mutation was applied'; END IF;
  EXCEPTION WHEN insufficient_privilege OR foreign_key_violation THEN
    NULL; -- RLS may reject the parent FK or UPDATE; either is an acceptable denial.
  END;
END $$;
SELECT set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
DO $$
BEGIN
  IF (SELECT full_name FROM public.lanu_crm_contacts
       WHERE id='00000000-0000-0000-0000-000000000061') <> 'Atomic v2'
     OR (SELECT version FROM public.lanu_crm_contacts
       WHERE id='00000000-0000-0000-0000-000000000061') <> 2
  THEN RAISE EXCEPTION 'Cross-tenant mutation changed owner data'; END IF;
END $$;
RESET ROLE;
SELECT 'Atomic RPC version, retry, race, tenant isolation PASS' AS result;


-- Core CRM: next-action and opportunity now share the atomic RPC.
-- This is an isolated CI-only multi-version/idempotent contract test.
SET ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
DO $$
DECLARE
  customer uuid := '00000000-0000-0000-0000-000000000011';
  action_id uuid := '00000000-0000-0000-0000-000000000071';
  opportunity_id uuid := '00000000-0000-0000-0000-000000000072';
  action_payload jsonb;
  opportunity_payload jsonb;
  status text;
BEGIN
  action_payload := jsonb_build_object(
    'id',action_id,'customer_id',customer,'type','CALL',
    'due_at','2026-10-09T12:00:00Z','note','Initial follow-up',
    'version',1
  );
  status := public.lanu_apply_versioned_crm_mutation(
    '00000000-0000-0000-0000-000000000091','next_action',action_payload,0);
  IF status <> 'APPLIED' THEN RAISE EXCEPTION 'Next-action create failed: %',status; END IF;

  action_payload := action_payload || jsonb_build_object('note','Updated follow-up','version',2);
  status := public.lanu_apply_versioned_crm_mutation(
    '00000000-0000-0000-0000-000000000092','next_action',action_payload,1);
  IF status <> 'APPLIED' THEN RAISE EXCEPTION 'Next-action update failed: %',status; END IF;
  IF public.lanu_apply_versioned_crm_mutation(
    '00000000-0000-0000-0000-000000000093','next_action',action_payload,1) <> 'CONFLICT'
  THEN RAISE EXCEPTION 'Next-action stale update was accepted'; END IF;
  IF (SELECT version FROM public.lanu_crm_next_actions WHERE id=action_id) <> 2
  THEN RAISE EXCEPTION 'Next-action version overwritten'; END IF;

  opportunity_payload := jsonb_build_object(
    'id',opportunity_id,'customer_id',customer,'title','CI Opportunity',
    'status','OPEN','amount','123.45','currency','TRY',
    'amount_origin','MANUAL','version',1
  );
  IF public.lanu_apply_versioned_crm_mutation(
    '00000000-0000-0000-0000-000000000094','opportunity',opportunity_payload,0) <> 'APPLIED'
  THEN RAISE EXCEPTION 'Opportunity create failed'; END IF;
  IF public.lanu_apply_versioned_crm_mutation(
    '00000000-0000-0000-0000-000000000094','opportunity',opportunity_payload,0) <> 'APPLIED'
  THEN RAISE EXCEPTION 'Opportunity retry not idempotent'; END IF;
  opportunity_payload := opportunity_payload || jsonb_build_object('title','CI Opportunity v2','version',2);
  IF public.lanu_apply_versioned_crm_mutation(
    '00000000-0000-0000-0000-000000000095','opportunity',opportunity_payload,1) <> 'APPLIED'
  THEN RAISE EXCEPTION 'Opportunity update failed'; END IF;
  IF public.lanu_apply_versioned_crm_mutation(
    '00000000-0000-0000-0000-000000000096','opportunity',opportunity_payload,1) <> 'CONFLICT'
  THEN RAISE EXCEPTION 'Opportunity stale update accepted'; END IF;
  IF (SELECT title FROM public.lanu_crm_opportunities WHERE id=opportunity_id) <> 'CI Opportunity v2'
  THEN RAISE EXCEPTION 'Opportunity data overwritten'; END IF;
END $$;
RESET ROLE;
SELECT 'Core opportunity/next-action atomic CAS + idempotency PASS' AS result;
