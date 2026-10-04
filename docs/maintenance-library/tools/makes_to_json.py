#!/usr/bin/env python3
"""Load the make suggestions (common_makes.csv) into the setup questions JSON.

Usage (from the repo root):
  python3 docs/maintenance-library/tools/makes_to_json.py \
      docs/maintenance-library/common_makes.csv \
      app/src/main/assets/library/setup_questions_v1.json

* CSV header: vehicle_type,make. One row per make per vehicle type; order doesn't matter.
* Replaces commonMakes wholesale (sorted, duplicates dropped) and bumps dataVersion by 1.
* Prints how each make answers a make-hinted question (e.g. the Harley question), so a
  wrong match is caught before it ships.
* Exits non-zero if validation fails; nothing is written in that case.
"""
import csv
import json
import re
import sys

TYPES = ["MOTORHOME", "TRAVEL_TRAILER", "BOAT_POWER", "BOAT_SAIL", "MOTORCYCLE", "ATV_UTV", "PWC"]


def main(csv_path, json_path):
    with open(json_path, encoding="utf-8") as f:
        data = json.load(f)
    makes = {t: {} for t in TYPES}
    errors = []
    with open(csv_path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames != ["vehicle_type", "make"]:
            sys.exit(f"header must be vehicle_type,make (got {reader.fieldnames})")
        for n, row in enumerate(reader, start=2):
            t, m = (row["vehicle_type"] or "").strip(), " ".join((row["make"] or "").split())
            if t not in makes:
                errors.append(f"line {n}: unknown vehicle_type {t!r}")
            elif not m:
                errors.append(f"line {n}: empty make")
            else:
                makes[t].setdefault(m.casefold(), m)  # first spelling wins
    if errors:
        sys.exit("\n".join(errors))

    data["commonMakes"] = {t: sorted(v.values(), key=str.casefold) for t, v in makes.items() if v}

    # How each listed make answers the hinted questions (first matching hint wins, like the app).
    for t, listed in data["commonMakes"].items():
        hints = [h for h in data.get("makeHints", []) if h["vehicleType"] == t]
        if not hints:
            continue
        by_answer = {}
        for m in listed:
            hit = next((h for h in hints if re.search(h["makePattern"].replace("(?i)", ""), m, re.I)), None)
            key = f'{hit["questionId"]}={hit["optionId"]}' if hit else "asks the question"
            by_answer.setdefault(key, []).append(m)
        for key, ms in sorted(by_answer.items()):
            print(f"{t} {key}: {', '.join(ms)}")

    data["dataVersion"] = data.get("dataVersion", 0) + 1
    with open(json_path, "w", encoding="utf-8") as f:
        f.write(json.dumps(data, indent=2) + "\n")
    counts = ", ".join(f"{t} {len(v)}" for t, v in data["commonMakes"].items())
    print(f"wrote {json_path}: dataVersion {data['dataVersion']}; {counts}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
