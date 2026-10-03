#!/usr/bin/env python3
"""Build LANU's Turkey-wide Overture Places snapshot.

The script reads the latest Overture GeoParquet release directly from public S3,
assigns each selected place to a Turkish province and district using Overture
division polygons, and writes one gzip JSONL asset per province plus a manifest.

It intentionally does not scrape Google Maps or authenticated official registries.
Official registry data remains a separate enrichment layer in the Android app.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import pathlib
import re
import shutil
import sys
import time
import unicodedata
import urllib.request

import duckdb

STAC_URL = "https://stac.overturemaps.org/catalog.json"
S3_ROOT = "s3://overturemaps-us-west-2/release"
COUNTRY_CODE = "TR"
SCHEMA_VERSION = 1

# Overture Places covers businesses, services, institutions, attractions and
# other POIs. LANU keeps every business-relevant top-level family and excludes
# only government/community and purely geographic entities.
BUSINESS_TOP_LEVELS = (
    "services_and_business",
    "shopping",
    "food_and_drink",
    "lifestyle_services",
    "travel_and_transportation",
    "health_care",
    "education",
    "cultural_and_historic",
    "sports_and_recreation",
    "lodging",
    "arts_and_entertainment",
)

# Coarse bbox only exists to let GeoParquet row-group statistics prune the
# global scan. Final inclusion is ST_WITHIN against Overture's Turkey region
# polygons, so neighboring-country records are not accepted from this bbox.
TURKEY_SCAN_BBOX = (25.0, 35.0, 45.5, 42.7)


def latest_release() -> str:
    request = urllib.request.Request(
        STAC_URL,
        headers={"User-Agent": "LANU-Business-Directory-Builder/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = json.load(response)
    release = str(payload.get("latest", "")).strip()
    if not re.fullmatch(r"20\d{2}-\d{2}-\d{2}\.\d+", release):
        raise RuntimeError(f"Unexpected Overture release value: {release!r}")
    return release


def normalize_text(value: str | None) -> str:
    raw = unicodedata.normalize("NFKD", value or "")
    asciiish = "".join(ch for ch in raw if not unicodedata.combining(ch))
    return " ".join(
        asciiish.casefold()
        .replace("ı", "i")
        .replace("ğ", "g")
        .replace("ü", "u")
        .replace("ş", "s")
        .replace("ö", "o")
        .replace("ç", "c")
        .split()
    )


def first_nonempty(*values: object) -> str | None:
    for value in values:
        if value is None:
            continue
        text = str(value).strip()
        if text and text.lower() != "null":
            return text
    return None


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_region_asset(region_code: str) -> str:
    if not re.fullmatch(r"TR-\d{2}", region_code):
        raise RuntimeError(f"Unexpected Turkey region code: {region_code!r}")
    return f"{region_code}.jsonl.gz"


def open_overture_connection() -> duckdb.DuckDBPyConnection:
    con = duckdb.connect()
    con.execute("INSTALL spatial")
    con.execute("LOAD spatial")
    con.execute("INSTALL httpfs")
    con.execute("LOAD httpfs")
    con.execute("SET s3_region='us-west-2'")
    con.execute("SET threads=2")
    con.execute("SET preserve_insertion_order=false")
    con.execute("SET memory_limit='6GB'")
    return con


def smoke_validate_sources(release: str) -> dict:
    """Touch the live release with the exact nested fields the full builder uses."""
    division_path = f"{S3_ROOT}/{release}/theme=divisions/type=division/*"
    places_path = f"{S3_ROOT}/{release}/theme=places/type=place/*"
    xmin, ymin, xmax, ymax = TURKEY_SCAN_BBOX
    con = open_overture_connection()
    try:
        region_count = con.execute(
            f"""
            SELECT count(DISTINCT CAST(region AS VARCHAR))
            FROM read_parquet('{division_path}', hive_partitioning=1)
            WHERE subtype = 'region'
              AND country = '{COUNTRY_CODE}'
              AND CAST(region AS VARCHAR) LIKE 'TR-%'
            """
        ).fetchone()[0]
        if int(region_count or 0) < 81:
            raise RuntimeError(
                f"Overture Turkey region smoke check returned only {region_count} regions"
            )

        sample = con.execute(
            f"""
            SELECT
                CAST(id AS VARCHAR),
                CAST(names.primary AS VARCHAR),
                CAST(basic_category AS VARCHAR),
                CAST(taxonomy.primary AS VARCHAR),
                CAST(taxonomy.hierarchy[1] AS VARCHAR),
                CAST(phones[1] AS VARCHAR),
                CAST(websites[1] AS VARCHAR),
                CAST(addresses[1].freeform AS VARCHAR),
                CAST(addresses[1].locality AS VARCHAR),
                CAST(addresses[1].postcode AS VARCHAR),
                CAST(operating_status AS VARCHAR),
                CAST(confidence AS DOUBLE),
                ST_Y(geometry),
                ST_X(geometry)
            FROM read_parquet('{places_path}', hive_partitioning=1)
            WHERE names.primary IS NOT NULL
              AND bbox.xmin BETWEEN {xmin} AND {xmax}
              AND bbox.ymin BETWEEN {ymin} AND {ymax}
            LIMIT 1
            """
        ).fetchone()
        if sample is None or not str(sample[0] or "").strip() or not str(sample[1] or "").strip():
            raise RuntimeError("Overture Places smoke query returned no usable Turkey-bbox row")

        return {
            "release": release,
            "turkeyRegions": int(region_count),
            "sampleId": str(sample[0]),
            "sampleName": str(sample[1]),
        }
    finally:
        con.close()


def build_snapshot(output_dir: pathlib.Path, release: str, min_confidence: float) -> dict:
    if not 0.0 <= min_confidence <= 1.0:
        raise ValueError("--min-confidence must be between 0 and 1")

    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    con = open_overture_connection()

    division_path = f"{S3_ROOT}/{release}/theme=divisions/type=division/*"
    division_area_path = f"{S3_ROOT}/{release}/theme=divisions/type=division_area/*"
    places_path = f"{S3_ROOT}/{release}/theme=places/type=place/*"

    con.execute(
        f"""
        CREATE OR REPLACE TEMP TABLE tr_regions AS
        SELECT
            d.id AS division_id,
            CAST(d.names.primary AS VARCHAR) AS city,
            CAST(d.region AS VARCHAR) AS region_code,
            a.geometry AS geometry,
            CAST(a.bbox.xmin AS DOUBLE) AS xmin,
            CAST(a.bbox.ymin AS DOUBLE) AS ymin,
            CAST(a.bbox.xmax AS DOUBLE) AS xmax,
            CAST(a.bbox.ymax AS DOUBLE) AS ymax
        FROM read_parquet('{division_path}', hive_partitioning=1) AS d
        INNER JOIN read_parquet('{division_area_path}', hive_partitioning=1) AS a
            ON a.division_id = d.id
        WHERE d.subtype = 'region'
          AND d.country = '{COUNTRY_CODE}'
          AND CAST(d.region AS VARCHAR) LIKE 'TR-%'
          AND d.names.primary IS NOT NULL
          AND a.is_land = TRUE
        """
    )

    con.execute(
        f"""
        CREATE OR REPLACE TEMP TABLE tr_counties AS
        SELECT
            d.id AS division_id,
            CAST(d.names.primary AS VARCHAR) AS district,
            CAST(d.region AS VARCHAR) AS region_code,
            a.geometry AS geometry
        FROM read_parquet('{division_path}', hive_partitioning=1) AS d
        INNER JOIN read_parquet('{division_area_path}', hive_partitioning=1) AS a
            ON a.division_id = d.id
        WHERE d.subtype IN ('county', 'localadmin')
          AND d.country = '{COUNTRY_CODE}'
          AND CAST(d.region AS VARCHAR) LIKE 'TR-%'
          AND d.names.primary IS NOT NULL
          AND a.is_land = TRUE
        """
    )

    region_rows = con.execute(
        """
        SELECT region_code, city, xmin, ymin, xmax, ymax
        FROM tr_regions
        ORDER BY region_code
        """
    ).fetchall()
    dedup_regions: dict[str, tuple[str, float, float, float, float]] = {}
    for code, city, xmin, ymin, xmax, ymax in region_rows:
        region_code = str(code)
        if region_code not in dedup_regions:
            dedup_regions[region_code] = (
                str(city),
                float(xmin),
                float(ymin),
                float(xmax),
                float(ymax),
            )

    if len(dedup_regions) < 81:
        raise RuntimeError(
            f"Overture Turkey region coverage is incomplete: {len(dedup_regions)} regions"
        )

    counts = {region_code: 0 for region_code in dedup_regions}
    top_levels_sql = ",".join(
        "'" + value.replace("'", "''") + "'" for value in BUSINESS_TOP_LEVELS
    )
    confidence_sql = f"{min_confidence:.6f}"

    try:
        # Process one province at a time. The original all-Türkiye spatial join
        # exceeded the GitHub runner's 12.4 GiB RAM. Province bbox predicates let
        # GeoParquet prune remote row groups before spatial joins, while each query
        # keeps only one province and its districts in memory.
        for region_code, region_info in sorted(dedup_regions.items()):
            city, xmin, ymin, xmax, ymax = region_info
            asset = output_dir / safe_region_asset(region_code)
            raw_file = asset.open("wb")
            binary = gzip.GzipFile(
                filename="",
                mode="wb",
                fileobj=raw_file,
                compresslevel=6,
                mtime=0,
            )
            text_writer = __import__("io").TextIOWrapper(
                binary,
                encoding="utf-8",
                newline="\n",
            )

            try:
                cursor = con.execute(
                    f"""
                    WITH candidates AS (
                        SELECT
                            CAST(p.id AS VARCHAR) AS id,
                            CAST(p.names.primary AS VARCHAR) AS name,
                            CAST(p.basic_category AS VARCHAR) AS basic_category,
                            CAST(p.taxonomy.primary AS VARCHAR) AS category,
                            CAST(p.taxonomy.hierarchy[1] AS VARCHAR) AS top_level_category,
                            CAST(p.operating_status AS VARCHAR) AS operating_status,
                            CAST(p.confidence AS DOUBLE) AS confidence,
                            CAST(p.phones[1] AS VARCHAR) AS phone,
                            CAST(p.websites[1] AS VARCHAR) AS website,
                            CAST(p.addresses[1].freeform AS VARCHAR) AS address_freeform,
                            CAST(p.addresses[1].locality AS VARCHAR) AS address_locality,
                            CAST(p.addresses[1].postcode AS VARCHAR) AS postcode,
                            p.geometry AS geometry
                        FROM read_parquet('{places_path}', hive_partitioning=1) AS p
                        WHERE p.names.primary IS NOT NULL
                          AND p.bbox.xmin BETWEEN {xmin} AND {xmax}
                          AND p.bbox.ymin BETWEEN {ymin} AND {ymax}
                          AND CAST(p.taxonomy.hierarchy[1] AS VARCHAR) IN ({top_levels_sql})
                          AND (p.confidence IS NULL OR p.confidence >= {confidence_sql})
                    )
                    SELECT
                        p.id,
                        p.name,
                        c.district,
                        p.address_locality,
                        p.address_freeform,
                        p.postcode,
                        ST_Y(p.geometry) AS latitude,
                        ST_X(p.geometry) AS longitude,
                        p.basic_category,
                        p.category,
                        p.top_level_category,
                        p.phone,
                        p.website,
                        p.operating_status,
                        p.confidence
                    FROM candidates AS p
                    LEFT JOIN tr_counties AS c
                        ON c.region_code = ?
                       AND ST_WITHIN(p.geometry, c.geometry)
                    WHERE ST_WITHIN(
                        p.geometry,
                        (SELECT geometry
                         FROM tr_regions
                         WHERE region_code = ?
                         LIMIT 1)
                    )
                    QUALIFY ROW_NUMBER() OVER (
                        PARTITION BY p.id
                        ORDER BY CASE WHEN c.district IS NULL THEN 1 ELSE 0 END, c.district
                    ) = 1
                    """,
                    [region_code, region_code],
                )

                columns = [item[0] for item in cursor.description]
                while True:
                    batch = cursor.fetchmany(5_000)
                    if not batch:
                        break
                    for row in batch:
                        item = dict(zip(columns, row))
                        district = first_nonempty(item.get("district"))
                        locality = first_nonempty(item.get("address_locality"))
                        neighborhood = locality
                        if neighborhood and normalize_text(neighborhood) in {
                            normalize_text(city),
                            normalize_text(district),
                        }:
                            neighborhood = None

                        freeform = first_nonempty(item.get("address_freeform"))
                        postcode = first_nonempty(item.get("postcode"))
                        address_parts = []
                        for part in (freeform, locality, postcode):
                            if part and normalize_text(part) not in {
                                normalize_text(existing) for existing in address_parts
                            }:
                                address_parts.append(part)
                        address = ", ".join(address_parts) or None

                        record = {
                            "id": first_nonempty(item.get("id")),
                            "name": first_nonempty(item.get("name")),
                            "city": city,
                            "regionCode": region_code,
                            "district": district,
                            "neighborhood": neighborhood,
                            "address": address,
                            "latitude": item.get("latitude"),
                            "longitude": item.get("longitude"),
                            "basicCategory": first_nonempty(item.get("basic_category")),
                            "category": first_nonempty(item.get("category")),
                            "topLevelCategory": first_nonempty(item.get("top_level_category")),
                            "phone": first_nonempty(item.get("phone")),
                            "website": first_nonempty(item.get("website")),
                            "operatingStatus": first_nonempty(item.get("operating_status")),
                            "confidence": item.get("confidence"),
                        }
                        if not record["id"] or not record["name"] or not record["city"]:
                            continue

                        text_writer.write(
                            json.dumps(
                                record,
                                ensure_ascii=False,
                                separators=(",", ":"),
                            )
                            + "\n"
                        )
                        counts[region_code] += 1
            finally:
                try:
                    text_writer.flush()
                finally:
                    text_writer.close()
                    raw_file.close()

            print(
                json.dumps(
                    {
                        "regionCode": region_code,
                        "city": city,
                        "records": counts[region_code],
                    },
                    ensure_ascii=False,
                ),
                flush=True,
            )
    finally:
        con.close()

    generated_at_epoch_ms = int(time.time() * 1000)
    cities = []
    for region_code, region_info in sorted(dedup_regions.items()):
        city = region_info[0]
        asset_name = safe_region_asset(region_code)
        asset_path = output_dir / asset_name
        if not asset_path.exists():
            raise RuntimeError(f"Missing generated province asset: {asset_name}")
        cities.append(
            {
                "city": city,
                "regionCode": region_code,
                "asset": asset_name,
                "sha256": sha256_file(asset_path),
                "recordCount": counts.get(region_code, 0),
            }
        )

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "overtureRelease": release,
        "generatedAtEpochMs": generated_at_epoch_ms,
        "country": "Türkiye",
        "countryCode": COUNTRY_CODE,
        "minConfidence": min_confidence,
        "businessTopLevels": list(BUSINESS_TOP_LEVELS),
        "attribution": "Overture Maps Foundation; see per-theme source attribution at docs.overturemaps.org/attribution/",
        "cities": cities,
        "totalRecordCount": sum(counts.values()),
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="build/business-directory")
    parser.add_argument("--release", default="")
    parser.add_argument("--min-confidence", type=float, default=0.0)
    parser.add_argument(
        "--smoke-only",
        action="store_true",
        help="Validate current Overture S3 paths and nested fields without building the snapshot.",
    )
    args = parser.parse_args()

    release = args.release.strip() or latest_release()
    if not re.fullmatch(r"20\d{2}-\d{2}-\d{2}\.\d+", release):
        raise RuntimeError(f"Invalid Overture release: {release!r}")

    if args.smoke_only:
        print(json.dumps(smoke_validate_sources(release), ensure_ascii=False))
        return 0

    output_dir = pathlib.Path(args.output_dir).resolve()
    manifest = build_snapshot(output_dir, release, args.min_confidence)
    print(
        json.dumps(
            {
                "release": manifest["overtureRelease"],
                "cities": len(manifest["cities"]),
                "records": manifest["totalRecordCount"],
                "outputDir": os.fspath(output_dir),
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise
