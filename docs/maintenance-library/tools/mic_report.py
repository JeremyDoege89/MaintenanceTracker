#!/usr/bin/env python3
"""Which boat / PWC makes the USCG MIC table can fill, and candidate rows for the ones it can't.

Usage (from the repo root):
  python3 docs/maintenance-library/tools/mic_report.py \
      app/src/main/assets/hin/uscg_mic.csv \
      app/src/main/assets/hin/mic_brand_overrides.csv \
      app/src/main/assets/library/setup_questions_v1.json > mic_report.md

Matching mirrors the app (domain/Hin.kt, MicMake): a brand (override or table column) is compared
as-is; a company name is compared with trailing legal and trade words removed (Inc, LLC, Corp,
Corporation, Co, Company, Ltd, Limited, Mfg, Manufacturing, Boats, Boat, Marine, Yachts), from the
end only, repeatedly, never down to nothing. Comparison ignores case and anything but a-z / 0-9.

Candidates are rows whose company name contains the make as whole words. Nothing is applied:
confirmed ones go into mic_brand_overrides.csv as `mic,brand` by hand.
"""
import csv
import json
import re
import sys

TYPES = ["BOAT_POWER", "BOAT_SAIL", "PWC"]
MATCH_IGNORED = {"inc", "llc", "corp", "corporation", "co", "company", "ltd", "limited", "mfg",
                 "manufacturing", "boats", "boat", "marine", "yachts"}
CANDIDATE_CAP = 40


def key(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def strip_trailing(name, endings):
    words = name.split()
    while len(words) > 1 and (key(words[-1]) == "" or key(words[-1]) in endings):
        words.pop()
    return " ".join(words).rstrip(",;&- ")


def match_key(name):
    return key(strip_trailing(name, MATCH_IGNORED))


def words(s):
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).split()


def contains_words(company, make):
    hay, needle = words(company), words(make)
    return bool(needle) and any(hay[i:i + len(needle)] == needle for i in range(len(hay) - len(needle) + 1))


def main(mic_path, overrides_path, setup_path):
    table = {}
    with open(mic_path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            mic, company = (row.get("mic") or "").strip().upper(), (row.get("company") or "").strip()
            if len(mic) == 3 and company:
                table[mic] = {"company": company, "brand": (row.get("brand") or "").strip() or None}
    overrides = 0
    with open(overrides_path, newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            mic, brand = (row.get("mic") or "").strip().upper(), (row.get("brand") or "").strip()
            if len(mic) == 3 and brand:
                table.setdefault(mic, {"company": brand, "brand": None})["brand"] = brand
                overrides += 1
    with open(setup_path, encoding="utf-8") as f:
        makes = json.load(f)["commonMakes"]

    by_brand, by_company = {}, {}
    for mic, e in table.items():
        if e["brand"]:
            by_brand.setdefault(key(e["brand"]), []).append(mic)
        else:
            by_company.setdefault(match_key(e["company"]), []).append(mic)

    print("# USCG MIC coverage of the boat and PWC make lists\n")
    print(f"MIC rows: {len(table)} (brand overrides applied: {overrides}).\n")
    for t in TYPES:
        listed = makes.get(t, [])
        matched, unmatched = [], []
        for m in listed:
            mics = sorted(set(by_brand.get(key(m), []) + by_company.get(match_key(m), [])))
            (matched if mics else unmatched).append((m, mics))
        print(f"## {t}: {len(matched)} of {len(listed)} makes matched\n")
        if matched:
            print("Matched: " + "; ".join(f"{m} ({', '.join(mics)})" for m, mics in matched) + "\n")
        if not unmatched:
            continue
        print("### Not matched by any MIC row\n")
        for m, _ in unmatched:
            cands = sorted((mic, e["company"]) for mic, e in table.items() if contains_words(e["company"], m))
            print(f"- **{m}**: " + (f"{len(cands)} candidate row(s)" if cands else "no candidates"))
            for mic, company in cands[:CANDIDATE_CAP]:
                print(f"  - `{mic}` {company}")
            if len(cands) > CANDIDATE_CAP:
                print(f"  - ...and {len(cands) - CANDIDATE_CAP} more")
        print()


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
