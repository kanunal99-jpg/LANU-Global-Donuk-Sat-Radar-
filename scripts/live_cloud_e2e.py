#!/usr/bin/env python3
import hashlib
import json
import os
import re
import subprocess
import sys
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
BROKER_PATH = "/functions/v1/lanu-ci-auth-broker"
OIDC_AUDIENCE = "lanu-cloud-e2e"


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


def expect_equal(label, actual, expected):
    if actual != expected:
        raise AssertionError(f"{label}: expected={expected!r} actual={actual!r}")
    return actual


def expect_true(label, condition, actual=None):
    if not condition:
        raise AssertionError(f"{label}: expected=True actual={actual!r}")
    return True


def current_provenance():
    actual_head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    event = {}
    event_path = os.environ.get("GITHUB_EVENT_PATH", "")
    if event_path and Path(event_path).is_file():
        event = json.loads(Path(event_path).read_text())
    expected_head = (
        event.get("pull_request", {}).get("head", {}).get("sha")
        or os.environ.get("GITHUB_SHA", "")
    )
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    repository_id = os.environ.get("GITHUB_REPOSITORY_ID", "")
    project_ref = urllib.parse.urlparse(URL).hostname.split(".", 1)[0]
    script_sha256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    if expected_head:
        expect_equal("checkout_source_head", actual_head, expected_head)
    return {
        "repository": repository,
        "repository_id": repository_id,
        "expected_head_sha": expected_head or actual_head,
        "actual_head_sha": actual_head,
        "workflow": os.environ.get("GITHUB_WORKFLOW", ""),
        "run_id": RUN_ID,
        "supabase_project_ref": project_ref,
        "script_sha256": script_sha256,
    }


def github_oidc_token():
    request_url = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL", "")
    request_token = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_TOKEN", "")
    if not request_url or not request_token:
        raise RuntimeError("GitHub OIDC environment is unavailable")
    separator = "&" if "?" in request_url else "?"
    oidc_url = request_url + separator + "audience=" + urllib.parse.quote(OIDC_AUDIENCE)
    req = urllib.request.Request(oidc_url, method="GET")
    req.add_header("Authorization", "bearer " + request_token)
    with urllib.request.urlopen(req, timeout=20) as response:
        body = json.loads(response.read().decode())
    token = body.get("value")
    if not token:
        raise RuntimeError("GitHub OIDC token was not returned")
    return token


def broker_session(actor, oidc_token):
    status, body = request(
        "POST",
        BROKER_PATH,
        {"action": "session", "run_id": RUN_ID, "actor": actor},
        oidc_token,
    )
    if status != 200 or not body or not body.get("access_token") or not body.get("refresh_token"):
        raise RuntimeError(f"CI auth broker session failed HTTP {status}: {body}")
    return body


def broker_cleanup(user_id, oidc_token):
    status, body = request(
        "POST",
        BROKER_PATH,
        {"action": "cleanup", "run_id": RUN_ID, "user_id": user_id},
        oidc_token,
    )
    if status != 200 or not body or body.get("deleted") is not True:
        raise RuntimeError(f"CI auth broker cleanup failed HTTP {status}: {body}")


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


def run_acceptance(user_a, user_b, ids):
    a_token = user_a["access_token"]
    b_token = user_b["access_token"]
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    ms = int(datetime.now(timezone.utc).timestamp() * 1000)

    customer = {
        "id": ids["customer"],
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
    customer_op = str(uuid.uuid4())
    expect_equal("customer_create", rpc(a_token, customer_op, "customer", customer, 0), "APPLIED")
    expect_equal("customer_retry_idempotent", rpc(a_token, customer_op, "customer", customer, 0), "APPLIED")

    customer_v2 = dict(
        customer,
        stage="MEETING",
        name="LANU Cloud E2E v2 " + RUN_ID,
        notes="cloud-e2e-v2",
        sync_version=2,
    )
    expect_equal(
        "customer_update",
        rpc(a_token, str(uuid.uuid4()), "customer", customer_v2, 1),
        "APPLIED",
    )
    customer_stale = dict(customer_v2, stage="LOST", name="STALE WRITER " + RUN_ID)
    expect_equal(
        "customer_stale_writer",
        rpc(a_token, str(uuid.uuid4()), "customer", customer_stale, 1),
        "CONFLICT",
    )
    customer_foreign = dict(customer_stale, sync_version=3)
    expect_equal(
        "customer_foreign_writer",
        rpc(b_token, str(uuid.uuid4()), "customer", customer_foreign, 2),
        "CONFLICT",
    )

    rows_a = select(a_token, "lanu_crm_customers", ids["customer"], "id,name,stage,sync_version")
    expect_true(
        "customer_owner_visibility",
        len(rows_a) == 1 and rows_a[0]["sync_version"] == 2 and rows_a[0]["stage"] == "MEETING",
        rows_a,
    )
    expect_equal(
        "customer_cross_user_visibility",
        select(b_token, "lanu_crm_customers", ids["customer"], "id"),
        [],
    )

    contact = {
        "id": ids["contact"],
        "customer_id": ids["customer"],
        "full_name": "LANU E2E Contact",
        "role": "Test",
        "phone": None,
        "email": None,
        "is_primary": True,
        "created_at_epoch_ms": ms,
        "updated_at_epoch_ms": ms,
        "version": 1,
    }
    expect_equal(
        "contact_create",
        rpc(a_token, str(uuid.uuid4()), "contact", contact, 0),
        "APPLIED",
    )
    expect_equal(
        "contact_cross_user_visibility",
        select(b_token, "lanu_crm_contacts", ids["contact"], "id"),
        [],
    )

    quote = {
        "id": ids["quote"],
        "customer_id": ids["customer"],
        "opportunity_id": None,
        "quote_number": f"CI-Q-{RUN_ID}",
        "status": "DRAFT",
        "currency": "TRY",
        "total_minor": 12550,
        "valid_until_epoch_ms": ms + 86_400_000,
        "notes": "cloud-e2e-quote",
        "created_at_epoch_ms": ms,
        "updated_at_epoch_ms": ms,
        "version": 1,
    }
    quote_op = str(uuid.uuid4())
    expect_equal("quote_create", rpc(a_token, quote_op, "quote", quote, 0), "APPLIED")
    expect_equal("quote_retry_idempotent", rpc(a_token, quote_op, "quote", quote, 0), "APPLIED")
    expect_equal(
        "quote_cross_user_visibility",
        select(b_token, "lanu_crm_quotes", ids["quote"], "id"),
        [],
    )
    quote_foreign = dict(quote, status="SENT", version=2, updated_at_epoch_ms=ms + 1)
    expect_equal(
        "quote_foreign_writer",
        rpc(b_token, str(uuid.uuid4()), "quote", quote_foreign, 1),
        "CONFLICT",
    )

    quote_v2 = dict(quote, status="SENT", version=2, updated_at_epoch_ms=ms + 2)
    expect_equal(
        "quote_send",
        rpc(a_token, str(uuid.uuid4()), "quote", quote_v2, 1),
        "APPLIED",
    )
    quote_stale = dict(
        quote_v2,
        status="REJECTED",
        notes="stale-commercial-writer",
        version=2,
        updated_at_epoch_ms=ms + 3,
    )
    expect_equal(
        "quote_stale_writer",
        rpc(a_token, str(uuid.uuid4()), "quote", quote_stale, 1),
        "CONFLICT",
    )
    quote_v3 = dict(quote_v2, status="ACCEPTED", version=3, updated_at_epoch_ms=ms + 4)
    expect_equal(
        "quote_accept",
        rpc(a_token, str(uuid.uuid4()), "quote", quote_v3, 2),
        "APPLIED",
    )

    quote_line = {
        "id": ids["quote_line"],
        "quote_id": ids["quote"],
        "product_id": None,
        "product_name": "LANU E2E Ürün",
        "unit": "Porsiyon",
        "quantity_milli": 1000,
        "unit_price_minor": 12550,
        "discount_basis_points": 0,
        "line_total_minor": 12550,
        "created_at_epoch_ms": ms,
        "updated_at_epoch_ms": ms,
        "version": 1,
    }
    expect_equal(
        "quote_line_create",
        rpc(a_token, str(uuid.uuid4()), "quote_line", quote_line, 0),
        "APPLIED",
    )
    expect_equal(
        "quote_line_cross_user_visibility",
        select(b_token, "lanu_crm_quote_lines", ids["quote_line"], "id"),
        [],
    )

    order = {
        "id": ids["order"],
        "customer_id": ids["customer"],
        "quote_id": ids["quote"],
        "order_number": f"CI-O-{RUN_ID}",
        "status": "DRAFT",
        "currency": "TRY",
        "total_minor": 12550,
        "notes": "cloud-e2e-order",
        "created_at_epoch_ms": ms,
        "updated_at_epoch_ms": ms,
        "version": 1,
    }
    order_op = str(uuid.uuid4())
    expect_equal("order_create", rpc(a_token, order_op, "order", order, 0), "APPLIED")
    expect_equal("order_retry_idempotent", rpc(a_token, order_op, "order", order, 0), "APPLIED")
    expect_equal(
        "order_cross_user_visibility",
        select(b_token, "lanu_crm_orders", ids["order"], "id"),
        [],
    )

    order_line = {
        "id": ids["order_line"],
        "order_id": ids["order"],
        "product_id": None,
        "product_name": "LANU E2E Ürün",
        "unit": "Porsiyon",
        "quantity_milli": 1000,
        "unit_price_minor": 12550,
        "discount_basis_points": 0,
        "line_total_minor": 12550,
        "created_at_epoch_ms": ms,
        "updated_at_epoch_ms": ms,
        "version": 1,
    }
    expect_equal(
        "order_line_create",
        rpc(a_token, str(uuid.uuid4()), "order_line", order_line, 0),
        "APPLIED",
    )
    expect_equal(
        "order_line_cross_user_visibility",
        select(b_token, "lanu_crm_order_lines", ids["order_line"], "id"),
        [],
    )

    order_v2 = dict(order, status="CONFIRMED", version=2, updated_at_epoch_ms=ms + 5)
    expect_equal(
        "order_confirm",
        rpc(a_token, str(uuid.uuid4()), "order", order_v2, 1),
        "APPLIED",
    )
    order_stale = dict(
        order_v2,
        status="CANCELLED",
        notes="stale-order-writer",
        version=2,
        updated_at_epoch_ms=ms + 6,
    )
    expect_equal(
        "order_stale_writer",
        rpc(a_token, str(uuid.uuid4()), "order", order_stale, 1),
        "CONFLICT",
    )

    refreshed_status, refreshed = request(
        "POST",
        "/auth/v1/token?grant_type=refresh_token",
        {"refresh_token": user_a["refresh_token"]},
    )
    expect_equal("refresh_http_status", refreshed_status, 200)
    expect_true(
        "refresh_access_token",
        bool(refreshed and refreshed.get("access_token")),
        refreshed,
    )
    refreshed_token = refreshed["access_token"]

    rows_refreshed = select(
        refreshed_token,
        "lanu_crm_customers",
        ids["customer"],
        "id,sync_version",
    )
    expect_true(
        "refresh_reconnect_customer",
        len(rows_refreshed) == 1 and rows_refreshed[0]["sync_version"] == 2,
        rows_refreshed,
    )
    quote_rows = select(
        refreshed_token,
        "lanu_crm_quotes",
        ids["quote"],
        "id,status,version",
    )
    expect_true(
        "refresh_reconnect_quote",
        len(quote_rows) == 1 and quote_rows[0]["status"] == "ACCEPTED" and quote_rows[0]["version"] == 3,
        quote_rows,
    )
    order_rows = select(
        refreshed_token,
        "lanu_crm_orders",
        ids["order"],
        "id,status,version",
    )
    expect_true(
        "refresh_reconnect_order",
        len(order_rows) == 1 and order_rows[0]["status"] == "CONFIRMED" and order_rows[0]["version"] == 2,
        order_rows,
    )

    # Contact is deleted explicitly; deleting the owned customer must cascade commercial children.
    delete(refreshed_token, "lanu_crm_contacts", ids["contact"])
    delete(refreshed_token, "lanu_crm_customers", ids["customer"])

    cleanup_tables = {
        "customer": ("lanu_crm_customers", ids["customer"]),
        "contact": ("lanu_crm_contacts", ids["contact"]),
        "quote": ("lanu_crm_quotes", ids["quote"]),
        "quote_line": ("lanu_crm_quote_lines", ids["quote_line"]),
        "order": ("lanu_crm_orders", ids["order"]),
        "order_line": ("lanu_crm_order_lines", ids["order_line"]),
    }
    for label, (table, row_id) in cleanup_tables.items():
        expect_equal(
            f"api_cleanup_{label}",
            select(refreshed_token, table, row_id, "id"),
            [],
        )

    return {
        "auth_method": "github_oidc_confirmed_ephemeral_users",
        "customer_create": "APPLIED",
        "customer_retry": "APPLIED",
        "customer_update": "APPLIED",
        "customer_stale_writer": "CONFLICT",
        "customer_foreign_writer": "CONFLICT",
        "customer_cross_user_visibility": "DENIED",
        "contact_create": "APPLIED",
        "contact_isolation": "DENIED",
        "quote_create": "APPLIED",
        "quote_retry": "APPLIED",
        "quote_send": "APPLIED",
        "quote_accept": "APPLIED",
        "quote_stale_writer": "CONFLICT",
        "quote_foreign_writer": "CONFLICT",
        "quote_isolation": "DENIED",
        "quote_line_create": "APPLIED",
        "quote_line_isolation": "DENIED",
        "order_create": "APPLIED",
        "order_retry": "APPLIED",
        "order_confirm": "APPLIED",
        "order_stale_writer": "CONFLICT",
        "order_isolation": "DENIED",
        "order_line_create": "APPLIED",
        "order_line_isolation": "DENIED",
        "refresh_token_reconnect": "SUCCESS",
        "api_cleanup": "SUCCESS",
    }


def main():
    provenance = current_provenance()
    ids = {
        "customer": str(uuid.uuid4()),
        "contact": str(uuid.uuid4()),
        "quote": str(uuid.uuid4()),
        "quote_line": str(uuid.uuid4()),
        "order": str(uuid.uuid4()),
        "order_line": str(uuid.uuid4()),
    }
    oidc_token = None
    user_a = None
    user_b = None
    evidence = {}
    failure = None
    cleanup_errors = []

    write_result(
        result="started",
        **provenance,
        **{f"{name}_id": value for name, value in ids.items()},
    )
    try:
        oidc_token = github_oidc_token()
        user_a = broker_session("A", oidc_token)
        user_b = broker_session("B", oidc_token)
        evidence = run_acceptance(user_a, user_b, ids)
    except Exception as exc:
        failure = f"{type(exc).__name__}: {exc}"
    finally:
        if oidc_token:
            for user in (user_a, user_b):
                if user and user.get("user", {}).get("id"):
                    try:
                        broker_cleanup(user["user"]["id"], oidc_token)
                    except Exception as cleanup_exc:
                        cleanup_errors.append(f"{type(cleanup_exc).__name__}: {cleanup_exc}")

    if failure or cleanup_errors:
        write_result(
            result="failure",
            error=failure or "none",
            broker_cleanup="FAILED" if cleanup_errors else "SUCCESS",
            cleanup_error=" | ".join(cleanup_errors) if cleanup_errors else "none",
            **provenance,
            **{f"{name}_id": value for name, value in ids.items()},
        )
        raise SystemExit(1)

    write_result(
        result="success",
        broker_cleanup="SUCCESS",
        user_a=user_a["user"]["id"],
        user_b=user_b["user"]["id"],
        **provenance,
        **{f"{name}_id": value for name, value in ids.items()},
        **evidence,
    )
    print("LANU live cloud E2E passed")


if __name__ == "__main__":
    main()
