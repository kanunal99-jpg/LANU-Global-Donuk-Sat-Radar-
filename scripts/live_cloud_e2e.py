#!/usr/bin/env python3
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

RESULT = Path("cloud-e2e-result.txt")
RUN_ID = os.environ.get("GITHUB_RUN_ID", "local")
SOURCE = Path("app/src/main/java/com/lanu/globaldonuksatisradari/crm/SupabaseBackend.kt").read_text()
URL = re.search(r'const val URL = "([^"]+)"', SOURCE).group(1)
KEY = re.search(r'const val PUBLISHABLE_KEY = "([^"]+)"', SOURCE).group(1)


def request(method, path, payload=None, token=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(URL + path, data=data, method=method)
    req.add_header("apikey", KEY)
    req.add_header("Accept", "application/json")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            body = json.loads(raw) if raw else None
        except Exception:
            body = {"message": raw}
        return error.code, body


def write_result(**values):
    RESULT.write_text("\n".join(f"{key}={value}" for key, value in values.items()) + "\n")


def anonymous_session(actor):
    status, body = request(
        "POST",
        "/auth/v1/signup",
        {"data": {"lanu_e2e_run": RUN_ID, "lanu_e2e_actor": actor}},
    )
    if status not in (200, 201) or not body or not body.get("access_token"):
        message = (body or {}).get("msg") or (body or {}).get("message") or "anonymous auth failed"
        write_result(
            run_id=RUN_ID,
            result="anonymous_auth_unavailable",
            actor=actor,
            http_status=status,
            message=message,
        )
        raise SystemExit(18)
    return body


def rpc(token, operation_id, entity_type, payload, expected_version):
    status, body = request(
        "POST",
        "/rest/v1/rpc/lanu_apply_versioned_crm_mutation",
        {
            "p_operation_id": operation_id,
            "p_entity_type": entity_type,
            "p_payload": payload,
            "p_expected_version": expected_version,
        },
        token,
    )
    if status != 200:
        raise RuntimeError(f"RPC {entity_type} failed HTTP {status}: {body}")
    return body


def select(token, table, row_id, fields):
    query = urllib.parse.urlencode({"id": f"eq.{row_id}", "select": fields})
    status, body = request("GET", f"/rest/v1/{table}?{query}", token=token)
    if status != 200:
        raise RuntimeError(f"SELECT {table} failed HTTP {status}: {body}")
    return body


def delete(token, table, row_id):
    query = urllib.parse.urlencode({"id": f"eq.{row_id}"})
    status, body = request("DELETE", f"/rest/v1/{table}?{query}", token=token)
    if status not in (200, 204):
        raise RuntimeError(f"DELETE {table} failed HTTP {status}: {body}")


if "[cloud-e2e]" not in os.environ.get("LANU_COMMIT_MESSAGE", ""):
    write_result(run_id=RUN_ID, result="skipped_no_opt_in_marker")
    raise SystemExit(0)

customer_id = str(uuid.uuid4())
contact_id = str(uuid.uuid4())
write_result(run_id=RUN_ID, customer_id=customer_id, contact_id=contact_id, result="started")

user_a = anonymous_session("A")
user_b = anonymous_session("B")
a_token = user_a["access_token"]
b_token = user_b["access_token"]
now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

base = {
    "id": customer_id,
    "stage": "PROSPECT",
    "source": "manual",
    "source_id": "ci:" + RUN_ID,
    "name": "LANU Cloud E2E " + RUN_ID,
    "city": "İstanbul",
    "district": "Kadıköy",
    "data_quality": "USER_ENTERED",
    "notes": "cloud-e2e",
    "sync_version": 1,
    "created_at": now,
    "updated_at": now,
}
op_create = str(uuid.uuid4())
assert rpc(a_token, op_create, "customer", base, 0) == "APPLIED"
assert rpc(a_token, op_create, "customer", base, 0) == "APPLIED"

v2 = dict(
    base,
    stage="MEETING",
    name="LANU Cloud E2E v2 " + RUN_ID,
    notes="cloud-e2e-v2",
    sync_version=2,
)
assert rpc(a_token, str(uuid.uuid4()), "customer", v2, 1) == "APPLIED"
stale = dict(v2, stage="LOST", name="STALE WRITER " + RUN_ID)
assert rpc(a_token, str(uuid.uuid4()), "customer", stale, 1) == "CONFLICT"
foreign = dict(stale, sync_version=3)
assert rpc(b_token, str(uuid.uuid4()), "customer", foreign, 2) == "CONFLICT"

rows_a = select(a_token, "lanu_crm_customers", customer_id, "id,name,stage,sync_version")
assert len(rows_a) == 1 and rows_a[0]["sync_version"] == 2 and rows_a[0]["stage"] == "MEETING"
assert select(b_token, "lanu_crm_customers", customer_id, "id") == []

ms = int(datetime.now(timezone.utc).timestamp() * 1000)
contact = {
    "id": contact_id,
    "customer_id": customer_id,
    "full_name": "LANU E2E Contact",
    "role": "Test",
    "phone": None,
    "email": None,
    "is_primary": True,
    "created_at_epoch_ms": ms,
    "updated_at_epoch_ms": ms,
    "version": 1,
}
assert rpc(a_token, str(uuid.uuid4()), "contact", contact, 0) == "APPLIED"
assert select(b_token, "lanu_crm_contacts", contact_id, "id") == []

status, refreshed = request(
    "POST",
    "/auth/v1/token?grant_type=refresh_token",
    {"refresh_token": user_a["refresh_token"]},
)
assert status == 200 and refreshed and refreshed.get("access_token")
rows_refreshed = select(
    refreshed["access_token"],
    "lanu_crm_customers",
    customer_id,
    "id,sync_version",
)
assert len(rows_refreshed) == 1 and rows_refreshed[0]["sync_version"] == 2

delete(refreshed["access_token"], "lanu_crm_contacts", contact_id)
delete(refreshed["access_token"], "lanu_crm_customers", customer_id)

write_result(
    run_id=RUN_ID,
    auth_method="anonymous_authenticated_role",
    user_a=user_a["user"]["id"],
    user_b=user_b["user"]["id"],
    customer_id=customer_id,
    contact_id=contact_id,
    result="success",
    create="APPLIED",
    retry="APPLIED",
    update="APPLIED",
    stale_writer="CONFLICT",
    foreign_writer="CONFLICT",
    user_b_visibility="DENIED",
    contact_isolation="DENIED",
    refresh_token_reconnect="SUCCESS",
    api_cleanup="SUCCESS",
)
print("LANU live cloud E2E passed")
