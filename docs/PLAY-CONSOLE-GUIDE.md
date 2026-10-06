# WeekendFleet — Play Console guide

Everything Play Console asks for, in the order it asks. Files named here are in this kit.

## 1. Payments profile (needed to sell Pro)

Settings › Payments profile: your legal name and address, a bank account for payouts, and US tax
info (W-9, uses your SSN).

## 2. Create app

| Field | Answer |
|---|---|
| App name | WeekendFleet |
| Default language | English (United States) |
| App or game | App |
| Free or paid | **Free** (Pro is sold in the app). A free app can never be switched to paid. |
| Declarations | Tick the Developer Program Policies and US export laws boxes |

## 3. Main store listing

| Field | What to use |
|---|---|
| App name (30 max) | `WeekendFleet: Maintenance Log` |
| Short description (80 max) | `Service log & reminders for RVs, boats, motorcycles & ATVs.` |
| Full description | Below, under "Full description" |
| App icon (512×512) | `store-listing/icon-512.png` |
| Feature graphic (1024×500) | `store-listing/feature-graphic.png` |
| Phone screenshots | `store-listing/screenshots/1-home.png` … `8-pro.png`, in that order |

### Full description

Paste this as-is (plain text; Play doesn't read Markdown):

```text
Car maintenance apps don't fit an RV, a boat or a dirt bike. They won't take a roof-seal inspection, a generator service or hull zincs, and they only count miles. This one was built for rigs.

Schedules that already know your machine
Pick motorhome, travel trailer / fifth wheel, powerboat, sailboat, motorcycle, ATV / UTV or personal watercraft and start with a ready-made schedule: roof and seals, wheel-bearing repacks, generator exercise and service, impellers, lower-unit oil, winterization and more. Untick what doesn't apply, edit any interval, add your own tasks.

Reminders by date OR miles/hours — whichever comes first
Every task tracks time, distance, engine hours or generator hours. See what's overdue and what's coming up at a glance, and get a notification on the day — with quiet hours, snooze, and a nudge if something slips past due.

Log every job in seconds
One tap from the home screen: what you did, the readings, cost, who did it (you or the shop), parts used and notes. The schedule resets itself from the work you log.

VIN and HIN, no typing
Scan the VIN barcode or plate, or a boat's hull number, with your camera. Tap Decode to fill in year, make and model from NHTSA's free VIN decoder. Boat HINs are decoded right on your phone.

Parts, torque specs and fluids
Keep part numbers, torque values and fluid specs with the task they belong to, so the next oil change doesn't start with a forum search.

Know what it costs
Totals by month and year, a per-area breakdown and your biggest spends.

Private by design
Works offline. No account, no sign-in, no ads, no analytics. Your data stays on your phone and only leaves when you export it, or when you tap Decode to look up a VIN with NHTSA's free decoder.

Pro — one payment, no subscription
The free version covers one vehicle completely. Pro ($9.99 once) adds unlimited vehicles, photos and receipts, and export to a backup file or spreadsheet.
```

## 4. Store settings

| Field | Answer |
|---|---|
| Category | Auto & Vehicles |
| Tags | Pick up to 5 that fit, e.g. vehicle maintenance, RV, boating |
| Contact email | jaydoege8969@gmail.com |
| Website | Leave blank |

## 5. App content (Policy › App content)

| Item | Answer |
|---|---|
| Privacy policy | https://jeremydoege89.github.io/maintenance-tracker-privacy/ |
| App access | All functionality is available without special access (no login) |
| Ads | No, the app doesn't contain ads |
| Content rating | Email: jaydoege8969@gmail.com. Category: "Utility, Productivity, Communication, or Other". Answer No to violence, sexuality, language, drugs, gambling, user interaction and sharing location. Digital purchases: Yes (Pro). Expect Everyone / PEGI 3. |
| Target audience | **18 and over** (avoids the Families policy) |
| Advertising ID | **No** (the release build doesn't request it) |
| News app | No |
| Health / financial features / government | No / None / No |
| Data safety | See section 6 |

## 6. Data safety

| Question | Answer |
|---|---|
| Does the app collect or share any required user data types? | See the two decisions below; with neither declared: **No** |
| Is data encrypted in transit? | Yes (Play Billing and NHTSA calls are HTTPS) |
| Can users request deletion? | Yes: Settings › Data › Delete all data, or uninstall. Nothing is kept on a server. |

**Your two decisions** (details in `policy/data-safety.md`):

1. **VIN decode.** A VIN leaves the phone when the user taps Decode (sent to NHTSA). Either answer
   "no data collected", or declare it: *App info and performance › Other app-generated data*
   (or *Personal info › Other info*), shared, optional, for app functionality, encrypted in transit.
2. **ML Kit (camera scan).** Images and scanned text never leave the phone, but Google's ML Kit
   may send its own diagnostics. Declare whatever Google's current page lists for the bundled
   barcode and text-recognition APIs:
   developers.google.com/ml-kit/android-data-disclosure

## 7. Pro product (Monetize › Products › In-app products)

| Field | Answer |
|---|---|
| Product ID | `mt_pro_lifetime` (must match exactly) |
| Name | WeekendFleet Pro |
| Description | Unlimited vehicles, photos and receipts, and backup and export. One payment. |
| Price | $9.99 (Play converts other countries) |
| Status | Active |

## 8. Testing and release

1. **App integrity:** accept Play App Signing (Google holds the app signing key).
2. **Settings › License testing:** add your Google account, so test purchases of Pro are free.
3. **Internal testing:** create a release, upload `release/app-release.aab`, install it from the
   Play link on your phone, and test buying, cancelling and restoring Pro.
4. **Closed testing:** promote the release, add 12+ testers by email or Google Group, countries
   US, Canada, UK, Australia. Send testers `docs/WeekendFleet-Tester-Invite.pdf`.
5. **After 14 days** with 12+ testers opted in: Dashboard › Apply for production access.

Package name: `dev.dreamz.weekendfleet` (permanent after the first upload). Version 1.0.0 (code 6).
