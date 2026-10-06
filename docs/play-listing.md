# Play Store listing (draft)

App name **WeekendFleet** (chosen 2026-10-06). Support / developer email: jaydoege8969@gmail.com.
Privacy policy: https://jeremydoege89.github.io/maintenance-tracker-privacy/

- **Category:** Auto & Vehicles (secondary: Tools)
- **Price:** Free, with in-app product `mt_pro_lifetime` (Pro, one-time, $9.99)
- **Contains ads:** No
- **Launch markets (§14 recommendation):** US, CA, UK, AU first

## Title (30 chars max)

`WeekendFleet: Maintenance Log` (29). Alternative: `WeekendFleet: RV & Boat Log` (27).

## Short description (80 chars max)

> Service log & reminders for RVs, boats, motorcycles & ATVs. (59)

## Full description (4,000 chars max)

Paste-ready plain-text version (no Markdown, no hard wraps): `PLAY-CONSOLE-GUIDE.md` §3.

> Car maintenance apps don't fit an RV, a boat or a dirt bike. They won't take a roof-seal
> inspection, a generator service or hull zincs, and they only count miles. This one was built
> for rigs.
>
> **Schedules that already know your machine**
> Pick motorhome, travel trailer / fifth wheel, powerboat, sailboat, motorcycle, ATV / UTV or
> personal watercraft and start with a ready-made schedule: roof and seals, wheel-bearing
> repacks, generator exercise and service, impellers, lower-unit oil, winterization and more.
> Untick what doesn't apply, edit any interval, add your own tasks.
>
> **Reminders by date OR miles/hours — whichever comes first**
> Every task tracks time, distance, engine hours or generator hours. See what's overdue and
> what's coming up at a glance, and get a notification on the day — with quiet hours, snooze,
> and a nudge if something slips past due.
>
> **Log every job in seconds**
> One tap from the home screen: what you did, the readings, cost, who did it (you or the shop),
> parts used and notes. The schedule resets itself from the work you log.
>
> **VIN and HIN, no typing**
> Scan the VIN barcode or plate, or a boat's hull number, with your camera. Tap Decode to fill in
> year, make and model from NHTSA's free VIN decoder. Boat HINs are decoded right on your phone.
>
> **Parts, torque specs and fluids**
> Keep part numbers, torque values and fluid specs with the task they belong to, so the next
> oil change doesn't start with a forum search.
>
> **Know what it costs**
> Totals by month and year, a per-area breakdown and your biggest spends.
>
> **Private by design**
> Works offline. No account, no sign-in, no ads, no analytics. Your data stays on your phone and
> only leaves when you export it, or when you tap Decode to look up a VIN with NHTSA's free decoder.
>
> **Pro — one payment, no subscription**
> The free version covers one vehicle completely. Pro ($9.99 once) adds unlimited vehicles,
> photos and receipts, and export to a backup file or spreadsheet.

Keyword targets worked in above (§11.1): rv maintenance log, boat maintenance tracker, trailer
service log, motorcycle maintenance tracker, atv maintenance schedule, rv service records,
generator service log, winterization checklist, maintenance reminders offline, fifth wheel.

## Feature bullets (§11.2)

- Pre-loaded maintenance schedules: motorhome, trailer, powerboat, sailboat, motorcycle, ATV, jet ski.
- Reminders by date OR miles/hours — whichever comes first.
- Log every oil change, repair and penny — with photos and receipts.
- Track part numbers, torque specs and fluid specs.
- Works offline. No account. Your data stays on your phone.
- Optional VIN decode fills in year, make and model (NHTSA's free decoder).
- Scan a VIN or boat HIN with the camera instead of typing it (read on your phone).
- One-time purchase for Pro. No subscription required.

## Screenshots (§11.3) — from the real build with demo data

Debug builds load the demo garage with
`adb shell am broadcast -n dev.dreamz.weekendfleet/.DebugDataReceiver -a seed_demo`
(back up first: `-a export_to --es name me.zip`, restore with `-a import_from --es name me.zip`).

Retaken 2026-10-06 on the moto g stylus (1080×2400, light theme, clean status bar). Play copies in
`screenshots/play/` are 1200×2400 (Play needs at most 2:1; edges extended). Upload them in this order:

1. Home: overdue tasks across the whole garage
2. Schedule (All tasks): "whichever first" intervals, grouped by status and area
3. Task detail: due line, Mark complete, "Why this interval?" with source
4. Log service sheet mid-entry (task, readings, cost, Done by)
5. Add motorcycle: VIN decoded into year, make and model
6. Costs: this year, by area, biggest spends
7. Vehicle types picker (all seven, new ATV icon)
8. Pro comparison (paywall)

`screenshots/extra-vehicle-overview.png` is a spare (Play takes at most 8 phone screenshots).
