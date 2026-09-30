import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@6.1.0";

const GITHUB_ISSUER = "https://token.actions.githubusercontent.com";
const GITHUB_JWKS = createRemoteJWKSet(
  new URL("https://token.actions.githubusercontent.com/.well-known/jwks"),
);
const AUDIENCE = "lanu-cloud-e2e";
const REPOSITORY = "kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-";
const REPOSITORY_ID = "1370170806";
const ACTOR_ID = "309099491";
const WORKFLOW_FRAGMENT = `${REPOSITORY}/.github/workflows/live-cloud-e2e.yml@`;

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

async function authorize(req: Request) {
  const header = req.headers.get("authorization") ?? "";
  if (!header.startsWith("Bearer ")) throw new Error("missing bearer token");
  const token = header.slice("Bearer ".length);
  const { payload } = await jwtVerify(token, GITHUB_JWKS, {
    issuer: GITHUB_ISSUER,
    audience: AUDIENCE,
  });

  if (String(payload.repository ?? "") !== REPOSITORY) throw new Error("repository mismatch");
  if (String(payload.repository_id ?? "") !== REPOSITORY_ID) throw new Error("repository id mismatch");
  if (String(payload.actor_id ?? "") !== ACTOR_ID) throw new Error("actor mismatch");
  if (!String(payload.workflow_ref ?? "").includes(WORKFLOW_FRAGMENT)) throw new Error("workflow mismatch");
  if (!String(payload.run_id ?? "").match(/^\d+$/)) throw new Error("run id missing");
  return payload;
}

function adminClient() {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const modern = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}");
  const key = modern.default ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!url || !key) throw new Error("admin environment unavailable");
  return createClient(url, key, { auth: { autoRefreshToken: false, persistSession: false } });
}

function publicClient() {
  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const modern = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
  const key = modern.default ?? Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (!url || !key) throw new Error("public environment unavailable");
  return createClient(url, key, { auth: { autoRefreshToken: false, persistSession: false } });
}

async function cleanupCrm(admin: ReturnType<typeof createClient>, userId: string) {
  const customers = await admin.from("lanu_crm_customers").select("id").eq("owner_user_id", userId);
  if (customers.error) throw customers.error;
  const ids = (customers.data ?? []).map((row: { id: string }) => row.id);
  if (ids.length > 0) {
    const contacts = await admin.from("lanu_crm_contacts").delete().in("customer_id", ids);
    if (contacts.error) throw contacts.error;
  }

  for (const table of [
    "lanu_crm_applied_mutations",
    "lanu_crm_sync_operations",
    "lanu_crm_stage_transitions",
    "lanu_crm_activities",
    "lanu_crm_next_actions",
    "lanu_crm_opportunities",
  ]) {
    const removed = await admin.from(table).delete().eq("owner_user_id", userId);
    if (removed.error) throw removed.error;
  }

  const removedCustomers = await admin.from("lanu_crm_customers").delete().eq("owner_user_id", userId);
  if (removedCustomers.error) throw removedCustomers.error;
}

async function deleteMatchingUser(admin: ReturnType<typeof createClient>, userId: string, runId: string) {
  const lookup = await admin.auth.admin.getUserById(userId);
  if (lookup.error || !lookup.data.user) return false;
  const user = lookup.data.user;
  const metadata = user.user_metadata ?? {};
  if (metadata.lanu_e2e !== true || String(metadata.lanu_e2e_run ?? "") !== runId) {
    throw new Error("refusing to delete non-E2E user");
  }
  await cleanupCrm(admin, userId);
  const deleted = await admin.auth.admin.deleteUser(userId);
  if (deleted.error) throw deleted.error;
  return true;
}

async function cleanupStale(admin: ReturnType<typeof createClient>) {
  const listed = await admin.auth.admin.listUsers({ page: 1, perPage: 1000 });
  if (listed.error) return;
  const cutoff = Date.now() - 6 * 60 * 60 * 1000;
  for (const user of listed.data.users) {
    const metadata = user.user_metadata ?? {};
    const created = Date.parse(user.created_at ?? "");
    if (metadata.lanu_e2e === true && Number.isFinite(created) && created < cutoff) {
      try {
        await cleanupCrm(admin, user.id);
        await admin.auth.admin.deleteUser(user.id);
      } catch (error) {
        console.error("stale cleanup failed", user.id, error);
      }
    }
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  try {
    const claims = await authorize(req);
    const body = await req.json();
    const runId = String(body.run_id ?? "");
    if (runId !== String(claims.run_id ?? "")) return json(403, { error: "run_id_mismatch" });

    const admin = adminClient();
    if (body.action === "session") {
      const actor = String(body.actor ?? "").toUpperCase();
      if (actor !== "A" && actor !== "B") return json(400, { error: "invalid_actor" });
      await cleanupStale(admin);

      const email = `lanu-ci-${runId}-${actor.toLowerCase()}@example.invalid`;
      const existing = await admin.auth.admin.listUsers({ page: 1, perPage: 1000 });
      if (existing.error) throw existing.error;
      for (const user of existing.data.users) {
        if (user.email === email && user.user_metadata?.lanu_e2e === true) {
          await cleanupCrm(admin, user.id);
          await admin.auth.admin.deleteUser(user.id);
        }
      }

      const password = `LanuCI!${crypto.randomUUID()}Aa9`;
      const created = await admin.auth.admin.createUser({
        email,
        password,
        email_confirm: true,
        user_metadata: { lanu_e2e: true, lanu_e2e_run: runId, lanu_e2e_actor: actor },
      });
      if (created.error || !created.data.user) throw created.error ?? new Error("user creation failed");

      const client = publicClient();
      const signed = await client.auth.signInWithPassword({ email, password });
      if (signed.error || !signed.data.session || !signed.data.user) {
        await admin.auth.admin.deleteUser(created.data.user.id);
        throw signed.error ?? new Error("session creation failed");
      }

      return json(200, {
        access_token: signed.data.session.access_token,
        refresh_token: signed.data.session.refresh_token,
        user: { id: signed.data.user.id },
      });
    }

    if (body.action === "cleanup") {
      const userId = String(body.user_id ?? "");
      if (!userId) return json(400, { error: "user_id_required" });
      const deleted = await deleteMatchingUser(admin, userId, runId);
      return json(200, { deleted });
    }

    return json(400, { error: "invalid_action" });
  } catch (error) {
    console.error("lanu-ci-auth-broker", error);
    return json(403, { error: "unauthorized_or_failed" });
  }
});
