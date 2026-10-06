# Play Console › Data safety — answers

Based on the shipped code (no analytics/crash SDKs, `allowBackup="false"`). Network use: Google Play
Billing, and the optional VIN **Decode** button, which sends the typed VIN (plus a typed model year)
to NHTSA's vPIC API over HTTPS when the user taps it. Re-check if any SDK is added.

**SDK added: Google ML Kit** (bundled barcode scanning + text recognition, for the VIN/HIN scan
button). Camera frames and recognised text are processed on the device and never sent. ML Kit
itself may send Google diagnostics/usage metrics. **Owner decision:** check Google's current
"ML Kit data disclosure" page (developers.google.com/ml-kit/android-data-disclosure) and declare
whatever it lists for the bundled APIs; it changes over time, so don't copy old answers.

**Owner decision before submitting:** whether the VIN decode counts as collecting data. It is
user-initiated, over HTTPS, and sends no account, name or device identifier, but a VIN does leave the
device for a third party (NHTSA). If you declare it, the closest Play data type is
*App info and performance › Other app-generated data* or *Personal info › Other info*; mark it
*shared*, *not required* (optional), *for app functionality*, *encrypted in transit*. Play's
guidance on user-initiated transfers changes over time; check the current form text.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Yes — the only network calls (Play Billing, NHTSA vPIC) are HTTPS |
| Do you provide a way for users to request that their data is deleted? | In-app **Settings › Data › Delete all data** and uninstall remove everything on the device, including cached VIN decodes; the app keeps nothing on any server |

Notes for the form:

- Purchase data is handled by Google Play Billing; Google's own disclosures cover it. The app
  does not collect it (Play's guidance: data processed by Google Play services for billing
  doesn't need to be declared by the developer).
- "Collected" means sent off the device. Data stored only locally isn't "collected".
- Exports go to a user-chosen location by explicit user action — not collection.
- VIN decode: see the owner decision above.

## Other declarations

- **Ads:** No ads.
- **App access:** All functionality available without login (no account exists).
- **Content rating questionnaire:** Utility/productivity, no user-generated content shared,
  no violence etc. → expected rating Everyone / PEGI 3.
- **Target audience:** 18+ (vehicle owners); not designed for children.
- **Government app / financial features / health:** No.
- **Exact alarm permission (`SCHEDULE_EXACT_ALARM`):** Core feature is user-set reminders
  ("Remind me at" time, due dates). If Play asks, cite calendar-style user reminders; the app
  falls back to inexact alarms if the permission is denied.
