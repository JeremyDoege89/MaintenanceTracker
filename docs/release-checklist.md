# Release checklist (Build Guide §12 M5, §13)

## Done in the code / verified

- [x] JSON (+zip with files) backup and CSV exports via SAF, Pro-gated; JSON/zip import; delete all data; storage usage
- [x] Documents tab + record attachments (Pro-gated, locked placeholder for free)
- [x] Costs tab: month / year / all-time totals, per-area chart, biggest spends
- [x] Settings: reminders, units (mi/km, currency, date format), data, about (licences, privacy summary)
- [x] Export → wipe → import round trip identical on a real device (oulu, 2026-10-01) + instrumented tests
- [x] Billing flows with the fake backend: success, pending→complete, cancel, error, refund, offline, reinstall restore

## Needs Play Console (owner)

- [ ] Create the app as **WeekendFleet** (name chosen 2026-10-06; quick web search found no app by that name — not a legal trademark search)
- [ ] Enrol in Play App Signing (let Google generate the app signing key)
- [x] Upload key created 2026-10-06 (`~/keys/maintracker-upload.jks` on the build VM, alias `upload`, RSA 4096, password in `~/keys/README-maintracker-upload.txt`; upload cert SHA-256 `B9:FA:2D:43:…:A7:6D:AE`). **Back up the .jks + password off the VM.** `keystore.properties` points at it (git-ignored)
- [ ] In-app product `mt_pro_lifetime`: one-time, non-consumable, $9.99, active. Do **not** create `mt_plus_*` yet
- [ ] License testers (Settings › License testing) incl. your own Google account
- [ ] Internal testing track → upload release AAB → install from Play on oulu
- [ ] With a license tester on the Play-installed build: buy Pro (test card "always approves"), "slow test card" for pending, cancel; uninstall + reinstall → Pro restored
- [ ] Store listing from `play-listing.md`; 7 screenshots are in `screenshots/play/` (2:1, Play-compliant; originals in `screenshots/`); feature graphic 1024×500 and hi-res icon 512×512 are in `graphics/` (HTML sources there; re-render the feature graphic once the app name is chosen)
- [x] Privacy policy live at https://jeremydoege89.github.io/maintenance-tracker-privacy/ (public repo JeremyDoege89/maintenance-tracker-privacy, GitHub Pages; regenerate `index.html` from `privacy-policy.md` when it changes); `PRIVACY_POLICY_URL` set
- [x] Support email jaydoege8969@gmail.com set in `SUPPORT_EMAIL`
- [ ] Data safety form from `data-safety.md`; content rating; target audience; ads = none
- [ ] Closed testing track before production

## Device checks still open

- [ ] Min-SDK smoke: full loop on **API 26** (no API 26 device/emulator available here — this VM has no KVM). Also API 33 and current (oulu is current)
- [ ] Clean install → vehicle → complete task → reminder → export → paywall decline, on min and current API
- [x] Large history: 10k records — debug build scroll 1.8% janky frames, no ANR; search finds 1 of 10k (2026-10-02, oulu)
- [x] Dark mode pass; TalkBack sanity: no unlabeled tap targets on 11 screens (fixed extended-FAB labels, 2026-10-02)

## Build

- `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`, signed with the upload key (verified 2026-10-06)
- versionCode / versionName in `app/build.gradle.kts`
