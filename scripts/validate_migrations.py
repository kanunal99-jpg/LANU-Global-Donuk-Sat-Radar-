#!/usr/bin/env python3
"""Dependency-free structural preflight for versioned PostgreSQL migrations.

This is NOT a replacement for executing migrations against a fresh local
Supabase/PostgreSQL instance. It blocks obvious broken SQL before Android CI.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path


class MigrationSyntaxError(ValueError):
    pass


def validate_sql(source: str, filename: str = "<memory>") -> None:
    state = "normal"
    dollar_delimiter = ""
    block_depth = 0
    paren_depth = 0
    line = 1
    statement_start = 1
    has_statement = False
    i = 0

    def fail(reason: str) -> None:
        raise MigrationSyntaxError(f"{filename}:{line}: {reason}")

    while i < len(source):
        char = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if char == "\n":
            line += 1

        if state == "line_comment":
            if char == "\n":
                state = "normal"
            i += 1
            continue

        if state == "block_comment":
            if char == "/" and nxt == "*":
                block_depth += 1
                i += 2
                continue
            if char == "*" and nxt == "/":
                block_depth -= 1
                i += 2
                if block_depth == 0:
                    state = "normal"
                continue
            i += 1
            continue

        if state == "single":
            if char == "'" and nxt == "'":
                i += 2
                continue
            if char == "'":
                state = "normal"
            i += 1
            continue

        if state == "double":
            if char == '"' and nxt == '"':
                i += 2
                continue
            if char == '"':
                state = "normal"
            i += 1
            continue

        if state == "dollar":
            if source.startswith(dollar_delimiter, i):
                i += len(dollar_delimiter)
                state = "normal"
                continue
            i += 1
            continue

        if char == "-" and nxt == "-":
            state = "line_comment"
            i += 2
            continue
        if char == "/" and nxt == "*":
            state = "block_comment"
            block_depth = 1
            i += 2
            continue
        if char.isspace():
            i += 1
            continue
        if not has_statement:
            statement_start = line
        has_statement = True
        if char == "'":
            state = "single"
        elif char == '"':
            state = "double"
        elif char == "$":
            match = re.match(r"\$(?:[A-Za-z_][A-Za-z_0-9]*)?\$", source[i:])
            if match:
                dollar_delimiter = match.group()
                state = "dollar"
                i += len(dollar_delimiter)
                continue
        elif char == "(":
            paren_depth += 1
        elif char == ")":
            paren_depth -= 1
            if paren_depth < 0:
                fail("unexpected closing parenthesis")
        elif char == ";":
            if paren_depth != 0:
                fail(f"unbalanced parentheses (depth={paren_depth})")
            has_statement = False
        i += 1

    if state not in ("normal", "line_comment"):
        fail(f"unterminated SQL {state} region")
    if paren_depth:
        fail(f"unbalanced parentheses at EOF (depth={paren_depth})")
    if has_statement:
        raise MigrationSyntaxError(f"{filename}:{statement_start}: statement missing terminating semicolon")


def validate_crm_invariants(sql: str, filename: str) -> None:
    if "reproducible_crm_schema_and_rls" not in filename:
        return
    assert re.search(r"tax_or_national_id\s*~\s*'\^\[0-9\]\{10,11\}\$'", sql), (
        f"{filename}: missing anchored 10-11 digit VKN/TCKN format check"
    )
    tables = set(re.findall(r"create table if not exists public\.(\w+)", sql, re.I))
    secured = set(re.findall(r"alter table public\.(\w+) enable row level security", sql, re.I))
    missing = tables - secured
    assert not missing, f"{filename}: RLS missing for {sorted(missing)}"
    assert "owner_user_id = (select auth.uid())" in sql, (
        f"{filename}: missing ownership predicate"
    )


def self_test() -> None:
    valid = "create table x (v text check (v ~ '^[0-9]{10,11}$')); -- valid\n"
    validate_sql(valid)
    validate_sql("do $$ begin perform 1; end $$;")
    validate_sql("select 'it''s ok'; /* nested /* block */ comment */")
    for broken in (
        "create table x (v text check (v ~ '^[0-9]{10,11}",
        "create table x (v text); select (1;",
        "select 1",
        "select 1; )",
    ):
        try:
            validate_sql(broken)
        except MigrationSyntaxError:
            pass
        else:
            raise AssertionError(f"Broken SQL unexpectedly accepted: {broken[:40]}")
    print("SQL structural preflight self-tests passed")


def main(argv: list[str]) -> int:
    if argv == ["--self-test"]:
        self_test()
        return 0
    if not argv:
        print("Usage: validate_migrations.py [--self-test | migration.sql ...]", file=sys.stderr)
        return 2
    for filename in argv:
        source = Path(filename).read_text(encoding="utf-8")
        validate_sql(source, filename)
        validate_crm_invariants(source, filename)
        print(f"Validated migration structure: {filename}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except (MigrationSyntaxError, AssertionError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
