#!/usr/bin/env python3
"""Fetch the USCG Manufacturer Identification Code (MIC) list into a CSV.

Source:  https://uscgboating.org/content/manufacturers-identification.php
Output:  mic,company,brand   (brand is left empty for you to fill in by hand)

Usage:   python3 fetch_uscg_mic.py [output.csv]

Standard library only. One polite pass over the public list: about 650
requests at one per second, so roughly 12 minutes.
"""
import csv
import html
import http.client
import re
import sys
import time
import urllib.error
import urllib.request

BASE = "https://uscgboating.org/content/manufacturers-identification.php"
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) uscg-mic-fetch/1.0 (personal use)"
DELAY_SECONDS = 1.0
PAGE_SIZE = 25

DETAIL_LINK = "manufacturers-identification-detail.php?id="
ROW_SPLIT = re.compile(r"<tr\b", re.I)
CELL_SPLIT = re.compile(r"<td\b[^>]*>", re.I)
CELL_END = re.compile(r"</td\s*>", re.I)
TAG = re.compile(r"<[^>]+>")
PAGING = re.compile(r"pageNum_manufacturers=(\d+)&(?:amp;)?totalRows_manufacturers=(\d+)")
MIC = re.compile(r"^[A-Z0-9]{3}$")


def fetch(url, tries=4):
    """GET a page as text, retrying a few times on network trouble."""
    for attempt in range(1, tries + 1):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8", errors="replace")
        except (urllib.error.URLError, http.client.HTTPException, OSError) as exc:
            if attempt == tries:
                raise
            wait = 5 * attempt
            print(f"  fetch failed ({exc}); retrying in {wait}s", file=sys.stderr)
            time.sleep(wait)


def cell_text(fragment):
    """Text of one table cell: cut at </td>, drop tags, decode entities."""
    fragment = CELL_END.split(fragment, maxsplit=1)[0]
    return " ".join(html.unescape(TAG.sub(" ", fragment)).split())


def parse_rows(page):
    """Return [(mic, company), ...] for every manufacturer row on a page."""
    rows = []
    for chunk in ROW_SPLIT.split(page)[1:]:
        if DETAIL_LINK not in chunk:
            continue
        cells = CELL_SPLIT.split(chunk)[1:]
        # The MIC is the cell holding the detail link; the company is the next cell.
        linked = next((i for i, c in enumerate(cells) if DETAIL_LINK in c), None)
        if linked is None or linked + 1 >= len(cells):
            continue
        mic = cell_text(cells[linked]).upper()
        company = cell_text(cells[linked + 1])
        if MIC.match(mic) and company:
            rows.append((mic, company))
    return rows


def page_url(page_number, total_rows):
    url = f"{BASE}?pageNum_manufacturers={page_number}"
    if total_rows:
        url += f"&totalRows_manufacturers={total_rows}"
    return url


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "uscg_mic.csv"

    page = fetch(BASE)
    paging = PAGING.findall(page)
    last_page = max((int(p) for p, _ in paging), default=None)
    expected = max((int(t) for _, t in paging), default=None)
    if last_page is not None:
        print(f"Site reports {expected} rows across {last_page + 1} pages.")
    else:
        print("No paging links found; reading until a short page.")

    table = {}
    conflicts = []
    page_number = 0
    while True:
        rows = parse_rows(page)
        if not rows:
            sys.exit(
                f"Parsed 0 rows on page {page_number}. The site's HTML has probably "
                "changed (or it served an error page). Fix parse_rows() and rerun."
            )
        added = 0
        for mic, company in rows:
            if mic not in table:
                table[mic] = company
                added += 1
            elif table[mic] != company:
                conflicts.append((mic, table[mic], company))
        print(f"  page {page_number}: {len(rows)} rows, {len(table)} total")

        if last_page is not None:
            if page_number >= last_page:
                break
        elif len(rows) < PAGE_SIZE or added == 0:
            break

        page_number += 1
        time.sleep(DELAY_SECONDS)
        page = fetch(page_url(page_number, expected))

    with open(out_path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["mic", "company", "brand"])
        for mic in sorted(table):
            writer.writerow([mic, table[mic], ""])

    print(f"Wrote {len(table)} rows to {out_path}")
    if expected and len(table) != expected:
        print(
            f"WARNING: the site's paging links say {expected} rows but {len(table)} "
            "were written. Check the conflicts below and the parser.",
            file=sys.stderr,
        )
    for mic, kept, dropped in conflicts:
        print(f"CONFLICT {mic}: kept '{kept}', dropped '{dropped}'", file=sys.stderr)


if __name__ == "__main__":
    main()
