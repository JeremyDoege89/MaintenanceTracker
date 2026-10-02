# Release checklist (Build Guide §12 M5, §13)

## Done in the code / verified

- [x] JSON (+zip with files) backup and CSV exports via SAF, Pro-gated; JSON/zip import; delete all data; storage usage
- [x] Documents tab + record attachments (Pro-gated, locked placeholder for free)
- [x] Costs tab: month / year / all-time totals, per-area chart, biggest spends
- [x] Settings: reminders, units (mi/km, currency, date format), data, about (licences, privacy summary)
- [x] Export → wipe → import round trip identical on a real device (oulu, 2026-10-01) + instrumented tests
- [x] Billing flows with the fake backend: success, pending→complete, cancel, error, refund, offline, reinstall restore

## Needs Play Console (owner)

- [ ] Create the app (final name chosen and trademark-checked — §14)
- [ ] Enrol in Play App Signing; create an **upload key** (`keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`), keep it out of git
- [ ] In-app product `mt_pro_lifetime`: one-time, non-consumable, $9.99, active. Do **not** create `mt_plus_*` yet
- [ ] License testers (Settings › License testing) incl. your own Google account
- [ ] Internal testing track → upload release AAB → install from Play on oulu
- [ ] With a license tester on the Play-installed build: buy Pro (test card "always approves"), "slow test card" for pending, cancel; uninstall + reinstall → Pro restored
- [ ] Store listing from `play-listing.md`; 7 screenshots are in `screenshots/play/` (2:1, Play-compliant; originals in `screenshots/`); still need feature graphic 1024×500 and hi-res icon 512×512
- [ ] Privacy policy (`privacy-policy.md`) posted at a public URL → set `PRIVACY_POLICY_URL` in `DataSettings.kt`
- [ ] Support email → set `SUPPORT_EMAIL` in `DataSettings.kt`
- [ ] Data safety form from `data-safety.md`; content rating; target audience; ads = none
- [ ] Closed testing track before production

## Device checks still open

- [ ] Min-SDK smoke: full loop on **API 26** (no API 26 device/emulator available here — this VM has no KVM). Also API 33 and current (oulu is current)
- [ ] Clean install → vehicle → complete task → reminder → export → paywall decline, on min and current API
- [x] Large history: 10k records — debug build scroll 1.8% janky frames, no ANR; search finds 1 of 10k (2026-10-02, oulu)
- [x] Dark mode pass; TalkBack sanity: no unlabeled tap targets on 11 screens (fixed extended-FAB labels, 2026-10-02)

## Build

- `./gradlew bundleRelease` (needs `signingConfigs.release` wired to the upload key — not yet configured)
- versionCode / versionName in `app/build.gradle.kts`
