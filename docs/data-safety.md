# Play Console › Data safety — answers

Based on the shipped code (no analytics/crash SDKs, `allowBackup="false"`, no network use
except Google Play Billing). Re-check if any SDK is added.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable — no data collected |
| Do you provide a way for users to request that their data is deleted? | Data never leaves the device; in-app **Settings › Data › Delete all data** and uninstall remove it |

Notes for the form:

- Purchase data is handled by Google Play Billing; Google's own disclosures cover it. The app
  does not collect it (Play's guidance: data processed by Google Play services for billing
  doesn't need to be declared by the developer).
- "Collected" means sent off the device. Data stored only locally isn't "collected".
- Exports go to a user-chosen location by explicit user action — not collection.

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
