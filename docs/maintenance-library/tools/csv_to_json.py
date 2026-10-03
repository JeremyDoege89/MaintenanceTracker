#!/usr/bin/env python3
"""Regenerate the library JSON asset from its spreadsheet mirror.

Usage (from the repo root):
  python3 docs/maintenance-library/tools/csv_to_json.py \
      docs/maintenance-library/maintenance_library_v1.csv \
      app/src/main/assets/library/maintenance_library_v1.json [--data-version N]

* Keeps the header (schemaVersion, disclaimer, defaults) from the existing JSON when there is one.
* Bumps dataVersion by 1 unless --data-version is given, so installed apps re-sync the library.
* Never renumber or rename ids: tasks users already created point at them.
* Exits non-zero if validation fails; nothing is written in that case.
"""
import argparse
import csv
import json
import os
import sys
from collections import OrderedDict
from datetime import date

COLS = ["id", "vehicleType", "area", "title", "description", "kind", "phase", "timeIntervalDays",
        "meterInterval", "meterUnit", "altMeterInterval", "altMeterUnit", "defaultDueSoonPct", "appliesTo",
        "safetyCritical", "dateBasis", "season", "eventTrigger", "typicalRange", "severeService", "diyPro",
        "source", "sourceTier", "libraryGroup", "notes", "sortOrder"]
INTS = {"phase", "timeIntervalDays", "meterInterval", "altMeterInterval", "sortOrder"}
OPTIONAL = {"timeIntervalDays", "meterInterval", "meterUnit", "altMeterInterval", "altMeterUnit", "dateBasis",
            "season", "eventTrigger", "typicalRange", "severeService", "libraryGroup"}
METERS = {"MOTORHOME": {"MILES", "GENERATOR_HOURS"}, "TRAVEL_TRAILER": {"MILES", "GENERATOR_HOURS"},
          "BOAT_POWER": {"ENGINE_HOURS", "GENERATOR_HOURS"}, "BOAT_SAIL": {"ENGINE_HOURS", "GENERATOR_HOURS"},
          "MOTORCYCLE": {"MILES"}, "ATV_UTV": {"ENGINE_HOURS", "MILES"}, "PWC": {"ENGINE_HOURS"}}
ENUMS = {
    "vehicleType": set(METERS),
    "area": {"BODY_EXTERIOR", "BRAKES", "CHASSIS", "DRIVETRAIN", "ELECTRICAL", "ENGINE", "FUEL", "HULL", "HVAC",
             "OTHER", "PLUMBING_WATER", "PROPANE", "RIGGING", "ROOF_SEALS", "SAFETY_EQUIP", "TIRES_WHEELS",
             "TRAILER"},
    "kind": {"RECURRING", "EXPIRY", "SEASONAL", "ONE_TIME", "PER_MANUAL", "PRE_USE", "POST_USE", "EVENT"},
    "meterUnit": {"MILES", "ENGINE_HOURS", "GENERATOR_HOURS"},
    "altMeterUnit": {"MILES", "ENGINE_HOURS", "GENERATOR_HOURS"},
    "dateBasis": {"MANUFACTURE_DATE", "INSTALL_DATE", "IN_SERVICE_DATE", "REGISTRATION_DATE"},
    "season": {"FALL", "SPRING"},
    "sourceTier": {"NAMED_SOURCE", "GENERAL_PRACTICE"},
}


def parse_row(raw):
    t = OrderedDict()
    for c in COLS:
        v = (raw.get(c) or "").strip()
        if c in INTS:
            t[c] = int(v) if v else None
        elif c == "defaultDueSoonPct":
            t[c] = float(v) if v else 0.1
        elif c == "safetyCritical":
            t[c] = v.lower() == "true"
        elif c == "appliesTo":
            t[c] = [x.strip() for x in v.split(";") if x.strip()] or ["all"]
        else:
            t[c] = v if v or c not in OPTIONAL else None
    t["isSeeded"] = True
    return t


def validate(templates):
    errs, seen = [], set()
    for t in templates:
        tid = t["id"] or "<missing id>"
        if not t["id"] or not t["id"].startswith("std:"):
            errs.append(f"{tid}: id must start with 'std:'")
        if tid in seen:
            errs.append(f"{tid}: duplicate id")
        seen.add(tid)
        for field, allowed in ENUMS.items():
            if t[field] is not None and t[field] not in allowed:
                errs.append(f"{tid}: bad {field} '{t[field]}'")
        for field in ("title", "description", "notes", "source", "diyPro"):
            if not t[field]:
                errs.append(f"{tid}: {field} is empty")
        if t["phase"] not in (1, 2):
            errs.append(f"{tid}: phase must be 1 or 2")
        if t["meterUnit"] and t["vehicleType"] in METERS and t["meterUnit"] not in METERS[t["vehicleType"]]:
            errs.append(f"{tid}: {t['vehicleType']} can't log {t['meterUnit']}; move it to altMeterUnit")
        if (t["meterInterval"] is None) != (t["meterUnit"] is None):
            errs.append(f"{tid}: meterInterval and meterUnit must be set together")
        if t["phase"] == 1 and t["kind"] in ("RECURRING", "EXPIRY", "SEASONAL") \
                and t["timeIntervalDays"] is None and t["meterInterval"] is None:
            errs.append(f"{tid}: phase-1 {t['kind']} needs timeIntervalDays or meterInterval")
        if t["kind"] == "ONE_TIME" and t["meterInterval"] is None:
            errs.append(f"{tid}: ONE_TIME needs meterInterval")
        if t["kind"] == "SEASONAL" and not t["season"]:
            errs.append(f"{tid}: SEASONAL needs season")
        if t["kind"] == "EXPIRY" and not t["dateBasis"]:
            errs.append(f"{tid}: EXPIRY needs dateBasis")
        if t["kind"] == "EVENT" and not t["eventTrigger"]:
            errs.append(f"{tid}: EVENT needs eventTrigger")
    return errs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv_path")
    ap.add_argument("json_path")
    ap.add_argument("--data-version", type=int)
    args = ap.parse_args()

    with open(args.csv_path, newline="", encoding="utf-8") as f:
        templates = [parse_row(r) for r in csv.DictReader(f)]
    errs = validate(templates)
    if errs:
        print(f"{len(errs)} problem(s); nothing written:", *errs[:50], sep="\n  ")
        sys.exit(1)

    header = OrderedDict([("schemaVersion", 1), ("dataVersion", 0), ("generatedOn", ""), ("disclaimer", ""),
                          ("defaults", {"dueSoonPct": 0.1, "seasonAnchors": {"FALL": "10-01", "SPRING": "04-01"}})])
    if os.path.exists(args.json_path):
        with open(args.json_path, encoding="utf-8") as f:
            old = json.load(f, object_pairs_hook=OrderedDict)
        for k in header:
            if k in old:
                header[k] = old[k]
    header["dataVersion"] = args.data_version if args.data_version is not None else header["dataVersion"] + 1
    header["generatedOn"] = date.today().isoformat()
    out = OrderedDict(header)
    out["templates"] = templates
    with open(args.json_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, indent=2, ensure_ascii=True)
        f.write("\n")
    print(f"Wrote {len(templates)} templates to {args.json_path} (dataVersion {out['dataVersion']}).")


if __name__ == "__main__":
    main()
