# WeekendFleet — features

Offline maintenance log and reminders for RVs, trailers, boats, motorcycles, ATVs and personal
watercraft. Android 8.0+ (API 26). Version 1.0.0.

## Vehicles

- **Seven vehicle types:** motorhome, travel trailer / fifth wheel, powerboat, sailboat,
  motorcycle, ATV / UTV, personal watercraft.
- **Vehicle profile:** name, photo, VIN or HIN, year, make, model, current readings, purchase date
  and reading at purchase, notes.
- **Make suggestions:** a dropdown of 288 common makes for the chosen type. A make is required for
  motorcycles, and it answers brand-specific setup questions automatically.
- **Readings:** miles, engine hours and generator hours, whichever the type uses. Distances are
  stored in miles and can be shown in kilometres.
- **Garage:** all vehicles in one list. Archive a vehicle to hide it without losing its history,
  or delete it for good.

## VIN and HIN

- **VIN decode:** tap Decode to look up year, make and model with NHTSA's free vPIC decoder. It
  only fills fields that are blank. The answer is saved on the phone, so the same VIN is never
  sent twice. For motorhomes it shows the chassis details instead.
- **VIN checks:** format and check digit are checked before anything is sent. A bad check digit
  shows a warning but still decodes (common outside North America).
- **HIN decode (boats and PWC):** done entirely on the phone. It reads the model year and looks
  up the builder in the bundled US Coast Guard maker-code table (16,237 codes). It accepts a
  country prefix like "US-". Brand corrections can be added in a separate overrides file.
- **Camera scan:** read a VIN from the door-frame barcode or the windshield plate, or a HIN from
  the transom. It runs on the phone, corrects common misreads, needs several consistent reads,
  and asks you to confirm the number. It has a flashlight and tap to focus.

## Maintenance schedules

- **Built-in library:** 529 curated tasks across the seven types, for example roof and seal
  inspections, wheel-bearing repacks, generator exercise and service, impellers, lower-unit oil,
  winterization, chain care and valve checks.
- **Setup questions:** 39 short questions, such as engine type, generator, slide-outs, inboard or
  outboard, and salt or fresh water. Follow-up questions only appear when they apply. The
  answers decide which tasks are offered. You can re-run setup later from Edit vehicle.
- **Review before saving:** suggested tasks come in sections, and you untick anything that
  doesn't apply. Safety-critical tasks ask for confirmation before you remove them.
- **"Why this interval?":** each library task shows where its interval comes from.
- **Task kinds:** recurring, seasonal, one-time (for example a break-in service, which finishes
  once it's done) and expiry (for example registration or extinguisher dates).
- **Intervals:** by date, miles, engine hours or generator hours. Combine them and whichever
  comes first wins.
- **Custom tasks:** add your own, or edit any interval, area, notes or reminder setting.
- **Parts and specs per task:** part numbers, torque specs and fluid specs live with the task.

## Due tracking

- **Home screen:** overdue and due-soon tasks across every vehicle, with an overdue count.
- **Status for every task:** OK, due soon, overdue, or manual. The due-soon window can be changed
  (default 14 days ahead, or 10% of the interval if that's shorter).
- **Vehicle schedule:** Due soon and All tasks tabs, grouped by status and then by area. Swipe
  right to complete, swipe left to edit.
- **Plain-language due text,** for example "30 mi over" or "122 days / 1,330 mi over".

## Logging service

- **Quick add:** one button opens a sheet to log a service, or to log new readings only.
  Readings start filled in from the vehicle profile.
- **Service record:** tasks done, date, readings, cost, who did the work (Self, Shop, Dealer or
  Mobile, with a name), parts used and notes.
- **Schedules reset themselves** from the work you log. You can undo for 10 seconds.
- **History:** every record for a vehicle, searchable and filterable by area and task. Open a
  record to edit or delete it. Deleting it rolls the schedule back.

## Reminders

- **Notifications on the due day,** at a time you choose.
- **Quiet hours:** no alerts between the times you set.
- **Repeat overdue reminders:** another nudge 7 and 30 days after the due date (optional).
- **Snooze 3 days** straight from the notification.
- **Reliable:** uses exact alarms and re-schedules after a reboot, an app update, or a time or
  time-zone change. Permissions are only asked for when you need them.

## Costs

- **Totals:** this month, this year and all time. Tap a card to switch the period.
- **Breakdown by area** and your biggest spends.
- **Currency and date format** are your choice.

## Documents and photos (Pro)

- **Documents tab per vehicle:** photos, receipts, manuals, insurance and other files.
- **Attachments on service records:** take a photo or pick a file. Thumbnails show for images.
- Deleting a record moves its attachments to the vehicle's Documents, and undo puts them back.

## Backup and export (Pro)

- **Full backup (.zip):** all data plus photos and documents.
- **Data-only backup (JSON)** and **CSV spreadsheet export.**
- **Import:** the backup is fully checked before anything is replaced, then swapped in one step.
- **Storage usage** and **Delete all data** in Settings.

## Free and Pro

- **Free:** one active vehicle with every feature except attachments and export. Archived
  vehicles don't count toward the limit.
- **Pro:** $9.99 once through Google Play (`mt_pro_lifetime`), no subscription. Adds unlimited
  vehicles, documents and photos, and backup/export.
- **Restore purchases;** works offline from a cached unlock; refunds are respected.

## Privacy

- No account, no sign-in, no ads, no analytics SDKs. Works offline.
- Data only leaves the phone when you export it, or tap Decode for a VIN (sent to NHTSA). HINs
  and camera images never leave the phone. Google's ML Kit, which reads the scans, may send Google
  its own diagnostics.
- Android cloud backup is turned off for this app.
- Privacy policy: https://jeremydoege89.github.io/maintenance-tracker-privacy/

## Settings

- Reminder time, quiet hours, due-soon window, repeat overdue reminders.
- Units (mi/km), currency, date format.
- Data: backup, export, import, storage usage, delete all data.
- About: version, licences, privacy summary and policy link, contact (jaydoege8969@gmail.com).

## Design and accessibility

- Material 3 with light and dark themes.
- Screen-reader labels on all tap targets, and 48 dp minimum touch targets.
- Tested with 10,000 service records: smooth scrolling and instant search.
