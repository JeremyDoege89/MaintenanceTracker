# Maintenance Library v1: Implementation Brief

**Audience:** the developer working in the MaintTracker repo (package `dev.maintracker`).

**Suggested prompt:**
> Read `docs/maintenance-library/IMPLEMENTATION.md` and implement Phase 1. Check every item in section 3 against the code first and tell me about anything that doesn't match. Then follow section 5 in order, adding the tests from section 6 as you go.

---

## 1. Goal

Replace the hardcoded `SeedTemplates` with a data-driven library of 528 sourced maintenance templates, 416 of which are offered in Phase 1.

Add a **Setup** step to the add-vehicle flow so each vehicle only gets the tasks that fit how it's built. Examples of what Setup asks:

- Motorhome chassis
- Boat drive type
- Motorcycle final drive
- Generator, slide-outs, water heater and similar equipment

The manufacturer is a *hint* used to pre-fill answers, never a filter. A coach brand doesn't tell you the chassis, and a boat brand doesn't tell you the drive.

Phase 1 product behavior:

1. **Type → Details → Setup → Schedule.** Setup asks 3–10 tap questions for the chosen vehicle type, including follow-ups that only appear when relevant. Most offer a "Not sure" option.
2. **Schedule has two sections:**
   - **Recommended:** templates that match the answers. Pre-checked and grouped by area.
   - **Might apply:** templates tied to "Not sure" or unanswered questions. Unchecked, with a "check your manual" hint.
   - Templates that contradict an answer are not shown.
3. **Safety-critical tasks** get a badge. Unchecking one asks for confirmation.
4. **Library updates** ship with app updates. A newer `dataVersion` upserts templates and retires removed ones. It never touches tasks users already have.

## 2. Files in this kit

| Path | Purpose |
|---|---|
| `app/src/main/assets/library/maintenance_library_v1.json` | The template library and runtime asset. **Source of truth.** |
| `app/src/main/assets/library/setup_questions_v1.json` | Setup questions per vehicle type, answer → tag mapping, make hints, common makes. |
| `app/src/test/resources/library/matching_fixtures_v1.json` | 9 scenarios with the exact expected Setup/matcher output. Use them as golden tests. |
| `docs/maintenance-library/maintenance_library_v1.csv` | Spreadsheet copy of the library, for reviewing and editing values. |
| `docs/maintenance-library/tools/csv_to_json.py` | Regenerates the JSON after spreadsheet edits. It validates the data and bumps `dataVersion`. |

If the Android module isn't named `app`, move the two `app/...` folders to match.

## 3. Verify before you start

This brief was written by inspecting the **v0.5.0 debug APK**, not the source. Confirm each item and adapt names and signatures where the code differs.

- **Room `AppDatabase`:** `version = 2`. The only migration is `MIGRATION_1_2` in `data/db/AppDatabaseKt.kt` (adds `service_record.vendorType`). It uses Room's newer `RoomOpenDelegate` codegen.
- **`maintenance_template` columns:** `id`, `vehicleType`, `area`, `title`, `description` (NOT NULL), `timeIntervalDays`, `meterInterval`, `meterUnit`, `defaultDueSoonPct` (default 0.1), `notes`, `isSeeded`, `sortOrder`. Indexed on `vehicleType`.
- **`maintenance_task.templateId`:** a FOREIGN KEY to `maintenance_template(id)` with **ON DELETE SET NULL**.
- **Seeding today:** `SeedTemplates` (`data/seed`) builds ids like `seed:MOTORHOME:01`. `VehicleRepository.ensureSeedTemplates()` inserts them through `TemplateDao.insertIgnore`.
- **`TaskSeeder.seed(vehicle, templates, now, zone, leadCap)`:**
  - Copies templates into `MaintenanceTask`.
  - Sets `baselineDate = vehicle.createdAt`.
  - Sets `baselineMeter` from `vehicle.meter(unit)`, falling back to `purchaseMeter` for the primary meter.
  - Calls `DueCalculator.recompute`.
- **`DueCalculator`:** one time interval plus one meter interval, whichever comes first. Status is `OK`, `DUE_SOON`, `OVERDUE` or `MANUAL` (`MANUAL` appears to mean the task has no interval). `MAX_LEAD_DAYS = 14`.
- **Add-vehicle flow:** `EditStep` has `TYPE`, `DETAILS` and `SCHEDULE`.
  - `VehicleEditViewModel` exposes `pickType`, `submitDetails`, `toggleTemplate`, `setAllTemplates`, `save` and `back`.
  - `EditState` holds `templates` and `selected`.
- **`VehicleType.meters` (first entry is the primary meter):**
  - `MOTORHOME`: `[MILES, GENERATOR_HOURS]`
  - `TRAVEL_TRAILER`: `[MILES]`
  - `BOAT_POWER` and `BOAT_SAIL`: `[ENGINE_HOURS, GENERATOR_HOURS]`
  - `MOTORCYCLE`: `[MILES]`
  - `ATV_UTV`: `[ENGINE_HOURS, MILES]`
  - `PWC`: `[ENGINE_HOURS]`
- **Enums:** `Area` has `BODY_EXTERIOR, BRAKES, CHASSIS, DRIVETRAIN, ELECTRICAL, ENGINE, FUEL, HULL, HVAC, OTHER, PLUMBING_WATER, PROPANE, RIGGING, ROOF_SEALS, SAFETY_EQUIP, TIRES_WHEELS, TRAILER`. `MeterUnit` has `MILES, ENGINE_HOURS, GENERATOR_HOURS`.
- **Serialization:** `Vehicle` and `MaintenanceTask` are `@Serializable` (kotlinx.serialization) and are used by `BackupManager`.
- **Settings:** a `SettingsStore` backed by DataStore exists.

## 4. Data contracts

### 4.1 `maintenance_library_v1.json`

The top level has these fields:

- `schemaVersion`
- `dataVersion`: bump to make apps re-sync.
- `generatedOn`
- `disclaimer`
- `defaults`: `{ dueSoonPct: 0.1, seasonAnchors: { FALL: "10-01", SPRING: "04-01" } }`
- `templates[]`

Template fields:

| Field | Type | Notes |
|---|---|---|
| `id` | String | Format `std:<VEHICLE_TYPE>:<slug>`. **Permanent.** Never renumber or rename. |
| `vehicleType` | `VehicleType` name | Matches the app enum exactly. |
| `area` | `Area` name | Matches the app enum exactly. |
| `title` | String | |
| `description` | String | Never empty. What to do, plus how-to details. |
| `kind` | `TemplateKind` | See 4.2. |
| `phase` | Int 1 or 2 | Phase 1 only offers `phase == 1`. |
| `timeIntervalDays` | Int? | Months converted with `floor(months * 365.25 / 12)`, so 12 mo = 365 and 6 mo = 182. |
| `meterInterval`, `meterUnit` | Int?, `MeterUnit`? | Always a meter that `vehicleType.meters` contains. Generator tasks use `GENERATOR_HOURS`. |
| `altMeterInterval`, `altMeterUnit` | Int?, `MeterUnit`? | Second limit kept for Phase 2. See the note below. |
| `defaultDueSoonPct` | Double | Always 0.1. |
| `appliesTo` | List<String> | Tags. `"all"` means every unit of the type. |
| `safetyCritical` | Boolean | |
| `dateBasis` | `DateBasis`? | Expiry anchor: `MANUFACTURE_DATE`, `INSTALL_DATE`, `IN_SERVICE_DATE` or `REGISTRATION_DATE`. `null` means since last completion. |
| `season` | `Season`? | `FALL` or `SPRING` (SEASONAL only). |
| `eventTrigger` | String? | EVENT only (Phase 2), for example `wheel_mounted` or `storage_start`. |
| `typicalRange`, `severeService` | String? | For a "Why this interval?" section. |
| `diyPro` | String | `DIY`, `Pro`, or `DIY or Pro`. |
| `source`, `sourceTier` | String | `sourceTier` is `NAMED_SOURCE` or `GENERAL_PRACTICE`. |
| `libraryGroup` | String? | `coach_house_systems`, `marine_hull_systems`, `marine_onboard_systems`, `marine_safety_gear` or `trailer_running_gear`. |
| `notes` | String | One readable paragraph (typical range, severe service, source). Copied into the task like today. |
| `sortOrder` | Int | Within a vehicle type, ordered by area. |
| `isSeeded` | Boolean | Always `true`. |

How the meter fields were filled:

- **Templates with two limits** (an Allison fluid task and six ATV/UTV tasks) keep the limit the type can log as the primary meter. The other limit goes in `alt*`.
- **Boat and PWC trailer templates** that only had mileage now have `meterUnit = null`. They keep the miles in `alt*` and use a time limit; boats don't log miles.

### 4.2 Kinds and phases

| Kind | Phase-1 count | How it schedules |
|---|---|---|
| `RECURRING` | 331 | Today's behavior: time and/or meter, whichever comes first, from the last completion. |
| `EXPIRY` | 48 | Age limit counted from `dateBasis`, for example tire age from the DOT date or flares from the manufacture date. Completion resets the baseline to the service date. |
| `SEASONAL` | 11 | Once a year. The first due date is the next season anchor; after that, 365 days from completion. |
| `ONE_TIME` | 15 | Break-in and first services, measured from 0 on the meter. Only offered while the current meter is below the interval. Deactivated once completed. |
| `PER_MANUAL` | 11 | No generic value exists. Seeded with no intervals, so it lands in `MANUAL` status and prompts the owner. |
| `PRE_USE`, `POST_USE`, `EVENT` | 0 (109 in phase 2) | Checklists and event triggers. **Not in Phase 1.** |

Three more templates are phase 2 because they depend on runtime state tags (`while_in_storage`, `tires_over_5_years`).

Phase-1 templates per type:

| Type | Templates |
|---|---|
| `MOTORHOME` | 94 |
| `TRAVEL_TRAILER` | 67 |
| `BOAT_POWER` | 71 |
| `BOAT_SAIL` | 90 |
| `MOTORCYCLE` | 29 |
| `ATV_UTV` | 26 |
| `PWC` | 39 |

### 4.3 `setup_questions_v1.json`

```text
questions[]: { id, vehicleTypes[], phase, prompt, help?, type: SINGLE|MULTI, required, showIf?, options[] }
options[]:   { id, label, tags[], maybeTags[], showIf?, vehicleTypes? }
showIf:      { questionId, anyOptionOf[] }       // option-level showIf works the same way
derivedTags: { tag, allOf[] }                    // e.g. saltwater_launch = trailered + saltwater_use
runtimeTags: tags set by app state, never by Setup (phase 2)
makeHints:   { vehicleType, makePattern (regex, (?i)), questionId, optionId }
commonMakes: { VEHICLE_TYPE: [make, ...] }       // for a make autocomplete in Details
```

The evaluation algorithm below is exact. The Kotlin version must match the fixtures.

```text
confirmed = {}, maybe = {}, effective = {}            // effective: questionId -> chosen optionIds
for q in questions, in file order:
    skip if vehicleType not in q.vehicleTypes, or q.phase > 1
    skip if q.showIf is set and not met by `effective`
    opts   = options visible for this vehicleType whose showIf (if any) is met by `effective`
    chosen = opts whose id is in answers[q.id]
    if chosen not empty:
        effective[q.id] = ids of chosen
        confirmed += all chosen.tags; maybe += all chosen.maybeTags
    else if q.type == SINGLE:                          // unanswered SINGLE = "Not sure"
        maybe += tags and maybeTags of every opt
    // an unanswered MULTI means "none of these" and adds nothing
for rule in derivedTags:
    if rule.allOf ⊆ confirmed: confirmed += rule.tag
    else if rule.allOf ⊆ confirmed ∪ maybe: maybe += rule.tag
maybe -= confirmed

pool        = templates where vehicleType == type and phase == 1 and !retired
recommended = pool where "all" in appliesTo or appliesTo ∩ confirmed ≠ ∅
mightApply  = pool − recommended where appliesTo ∩ maybe ≠ ∅
```

After matching, drop `ONE_TIME` templates unless `vehicle.meter(meterUnit)` is known and below `meterInterval`.

## 5. Implementation steps

### Step 1: Room migration 2 → 3

Write it in the same style as `MIGRATION_1_2` (whether that uses `SupportSQLiteDatabase` or `SQLiteConnection`), register it, and bump `version` to 3.

```sql
ALTER TABLE maintenance_template ADD COLUMN appliesTo TEXT NOT NULL DEFAULT 'all';
ALTER TABLE maintenance_template ADD COLUMN kind TEXT NOT NULL DEFAULT 'RECURRING';
ALTER TABLE maintenance_template ADD COLUMN phase INTEGER NOT NULL DEFAULT 1;
ALTER TABLE maintenance_template ADD COLUMN safetyCritical INTEGER NOT NULL DEFAULT 0;
ALTER TABLE maintenance_template ADD COLUMN dateBasis TEXT;
ALTER TABLE maintenance_template ADD COLUMN season TEXT;
ALTER TABLE maintenance_template ADD COLUMN eventTrigger TEXT;
ALTER TABLE maintenance_template ADD COLUMN altMeterInterval INTEGER;
ALTER TABLE maintenance_template ADD COLUMN altMeterUnit TEXT;
ALTER TABLE maintenance_template ADD COLUMN typicalRange TEXT;
ALTER TABLE maintenance_template ADD COLUMN severeService TEXT;
ALTER TABLE maintenance_template ADD COLUMN diyPro TEXT;
ALTER TABLE maintenance_template ADD COLUMN source TEXT;
ALTER TABLE maintenance_template ADD COLUMN sourceTier TEXT;
ALTER TABLE maintenance_template ADD COLUMN libraryGroup TEXT;
ALTER TABLE maintenance_template ADD COLUMN retired INTEGER NOT NULL DEFAULT 0;
ALTER TABLE maintenance_task ADD COLUMN kind TEXT NOT NULL DEFAULT 'RECURRING';
ALTER TABLE maintenance_task ADD COLUMN safetyCritical INTEGER NOT NULL DEFAULT 0;
ALTER TABLE vehicle ADD COLUMN configTags TEXT NOT NULL DEFAULT '';
ALTER TABLE vehicle ADD COLUMN setupAnswers TEXT;
```

Room validates the migrated schema against the entities, so the defaults must match exactly:

- Every NOT NULL column above needs a matching `@ColumnInfo(defaultValue = ...)` on its entity field: `"all"`, `"RECURRING"`, `"1"`, `"0"` or `""`. Room quotes text defaults itself.
- Nullable columns get no default.

If schema export is off, turn it on (`room.schemaLocation`) so future migrations can be tested.

### Step 2: Models and enums

Add these enums: `TemplateKind`, `DateBasis`, `Season`, `SourceTier`, `QuestionType`. Their values are in section 4.

Add the new fields to the existing data classes, with defaults:

```kotlin
// MaintenanceTemplate (Room entity)
@ColumnInfo(defaultValue = "all") val appliesTo: String = "all",   // ';'-separated tags, no TypeConverter needed
@ColumnInfo(defaultValue = "RECURRING") val kind: TemplateKind = TemplateKind.RECURRING,
@ColumnInfo(defaultValue = "1") val phase: Int = 1,
@ColumnInfo(defaultValue = "0") val safetyCritical: Boolean = false,
val dateBasis: DateBasis? = null, val season: Season? = null, val eventTrigger: String? = null,
val altMeterInterval: Int? = null, val altMeterUnit: MeterUnit? = null,
val typicalRange: String? = null, val severeService: String? = null, val diyPro: String? = null,
val source: String? = null, val sourceTier: String? = null, val libraryGroup: String? = null,
@ColumnInfo(defaultValue = "0") val retired: Boolean = false,
// helper
val appliesToTags: Set<String> get() = appliesTo.split(';').filter { it.isNotBlank() }.toSet()

// MaintenanceTask
@ColumnInfo(defaultValue = "RECURRING") val kind: TemplateKind = TemplateKind.RECURRING,
@ColumnInfo(defaultValue = "0") val safetyCritical: Boolean = false,

// Vehicle
@ColumnInfo(defaultValue = "") val configTags: String = "",        // ';'-separated confirmed tags
val setupAnswers: String? = null,                                   // JSON: {"questionId":["optionId",...]}
```

The defaults also keep old backups decodable (see step 9).

### Step 3: Library loader and sync (replaces `ensureSeedTemplates`)

Write `@Serializable` DTOs that mirror section 4.1 (`LibraryFile`, `LibraryDefaults`, `LibraryTemplate`). Decode them with `Json { ignoreUnknownKeys = true }` and map them to the entity with `appliesTo.joinToString(";")`.

```kotlin
class LibrarySync(private val assets: AssetManager, private val db: AppDatabase,
                  private val settings: SettingsStore) {
    suspend fun syncIfNeeded() {
        val file = assets.open("library/maintenance_library_v1.json")
            .use { json.decodeFromStream<LibraryFile>(it) }
        if (settings.libraryDataVersion() >= file.dataVersion) return
        db.withTransaction {
            db.templates().upsertAll(file.templates.map { it.toEntity() })
            val keep = file.templates.mapTo(HashSet()) { it.id }
            db.templates().seededIds().filterNot { it in keep }
                .chunked(500).forEach { db.templates().retire(it) }   // stays under SQLite's variable limit
        }
        settings.setLibraryDataVersion(file.dataVersion)
    }
}
```

DAO changes:

```kotlin
@Upsert suspend fun upsertAll(templates: List<MaintenanceTemplate>)
@Query("SELECT id FROM maintenance_template WHERE isSeeded = 1") suspend fun seededIds(): List<String>
@Query("UPDATE maintenance_template SET retired = 1 WHERE id IN (:ids)") suspend fun retire(ids: List<String>)
// forType: add "AND retired = 0 AND phase = 1" and keep ORDER BY sortOrder
```

- **Do not use `@Insert(onConflict = REPLACE)`.** REPLACE deletes and re-inserts the row, which fires `ON DELETE SET NULL` and silently unlinks users' tasks from their templates.
- Call `syncIfNeeded()` wherever `ensureSeedTemplates()` is called today, and make sure it finishes before the Schedule step loads templates.
- Then delete `SeedTemplates` and `ensureSeedTemplates()`. On the first sync, the old `seed:*` rows become `retired = 1`. Existing tasks keep their `templateId` and all of their values.
- Add `libraryDataVersion` (Int, default 0) to `SettingsStore`.

### Step 4: Setup evaluator and matcher (pure Kotlin, no Android dependencies)

- Add the DTOs `SetupFile`, `SetupQuestion`, `SetupOption`, `ShowIf`, `DerivedTag` and `MakeHint` from section 4.3, loaded from `library/setup_questions_v1.json`.
- `SetupEvaluator.visibleQuestions(file, type, answers)` returns the visible questions in order, for the UI.
- `SetupEvaluator.evaluate(file, type, answers): SetupResult(confirmed, maybe, effectiveAnswers)` implements the algorithm in 4.3 exactly.
- `LibraryMatcher.match(templates, type, result): Match(recommended, mightApply)`.
- `MaintenanceTemplate.isOfferable(vehicle)` applies the ONE_TIME rule.
- `MakeHints.prefill(file, type, make, answers)` pre-selects an option only when its question is still unanswered.

### Step 5: Add-vehicle flow, Setup step

- **Step order:** add `SETUP` to `EditStep`, ordered `TYPE → DETAILS → SETUP → SCHEDULE`. `submitDetails` goes to `SETUP`, and `back` from `SCHEDULE` returns to `SETUP`.
- **Prefill:** on entering Setup, run `MakeHints.prefill` against `form.make`.
- **Layout:** one scrollable Compose screen showing `visibleQuestions` (recompute on every change, so `showIf` questions appear and disappear).
  - SINGLE questions use radio buttons, and MULTI questions use checkboxes.
  - `help` is supporting text.
  - Required questions are marked, and Next stays disabled until they're answered.
- **On Next:**
  1. Evaluate the answers.
  2. Match them against `templateDao.forType(type)`.
  3. Filter with `isOfferable`.
  4. Set `EditState.templates` to recommended + mightApply (keep both id sets).
  5. Set `selected` to the recommended ids.
- **State:** add `answers: Map<String, Set<String>>`, `recommendedIds`, `mightApplyIds` and `expiryDates: Map<String, Long>` to `EditState`.
- **Make autocomplete (optional):** turn the Details make field into an autocomplete using `commonMakes[type]`, still allowing free text.

### Step 6: Schedule step

- **Recommended section:** pre-checked and grouped by `area`, with collapsible groups that show counts.
- **Might apply section:** unchecked, with the hint "Check your manual". Optionally show which answer would make it apply by mapping its `appliesTo` tags back to option labels.
- **Select all:** `setAllTemplates` affects the Recommended section only.
- **Safety-critical badge:** shown when `safetyCritical` is true. Unchecking one shows a confirm dialog: "This is a safety item. Turn it off anyway?"
- **Date field for checked EXPIRY rows:** an optional month/year picker. Label it "Made or installed" (`MANUFACTURE_DATE`/`INSTALL_DATE`) or "Last renewed" (`REGISTRATION_DATE`). Store it in `expiryDates[templateId]`, and reuse the existing date picker (`ui/common/PickerDates`).
- **PER_MANUAL rows:** show "Set the interval from your manual".
- **Save:** persist `vehicle.configTags` (confirmed tags joined with `;`) and `vehicle.setupAnswers` (JSON). Pass `expiryDates` through `createVehicle` to the seeder.

### Step 7: `TaskSeeder` by kind

```kotlin
val baselineDate = when (t.kind) {
    TemplateKind.EXPIRY   -> expiryDates[t.id] ?: vehicle.purchaseDate ?: vehicle.createdAt
    TemplateKind.SEASONAL -> nextSeasonAnchor(t.season!!, now, zone, anchors)
                                 .minusDays(t.timeIntervalDays!!.toLong())           // due = next anchor
                                 .atStartOfDay(zone).toInstant().toEpochMilli()
    TemplateKind.ONE_TIME -> vehicle.purchaseDate ?: vehicle.createdAt
    else                  -> vehicle.createdAt                                    // unchanged
}
val baselineMeter = if (t.kind == TemplateKind.ONE_TIME) 0.0 else /* existing logic */
// copy kind and safetyCritical into the task; everything else as today
```

- `nextSeasonAnchor` returns the next occurrence of the `MM-dd` in `defaults.seasonAnchors` that is after today.
- An EXPIRY task whose date has already passed should come out `OVERDUE`. That's correct: those tires really are too old.

### Step 8: Completion handling

Wherever logging a service updates the task (`VehicleRepository.logService` / recompute), add this rule: if `task.kind == ONE_TIME`, set `isActive = false` and `notifyEnabled = false` instead of rescheduling.

Every other kind keeps today's behavior. Completion resets the baselines.

### Step 9: Meters, backups and copy

- **Travel trailer meters:** `VehicleType.meters` for `TRAVEL_TRAILER` becomes `[MILES, GENERATOR_HOURS]`. The library's trailer generator tasks need this. Label the Details field "Generator hours (if equipped)".
- **Backups:** the new fields have defaults, so old backups still decode. If `BackupManager`'s `Json` doesn't already set `ignoreUnknownKeys = true`, add it. If backups include templates, a library sync after restore re-applies them.
- **Disclaimer:** replace uses of `SeedTemplates.DISCLAIMER` with the library's `disclaimer`.
- **"Why this interval?" (optional):** in task detail, look up the template by `templateId` and show `typicalRange`, `severeService` and `source`.

### Step 10 (should-have): Edit setup on an existing vehicle

From vehicle edit, reopen Setup with the saved `setupAnswers`. On save:

- Recompute the tags.
- Offer only the newly matching templates the vehicle doesn't already have a task for (compare `templateId`).
- Never delete or change existing tasks automatically.

## 6. Tests and acceptance criteria

**JVM unit tests:**

1. **Library parse.**
   - The asset decodes to 528 templates with unique ids.
   - Every enum value is valid, and every `meterUnit` is in `vehicleType.meters` (after step 9).
   - Phase-1 RECURRING, EXPIRY and SEASONAL templates have an interval, and ONE_TIME templates have a `meterInterval`.
2. **Golden fixtures.** For each of the 9 scenarios in `matching_fixtures_v1.json`:
   - `evaluate` gives exactly `expectedConfirmedTags` and `expectedMaybeTags`.
   - `match` gives exactly `expectedRecommendedIds` and `expectedMightApplyIds` (compare as sets).
   - Read the assets from `src/main/assets/library/` or copy them into test resources.
3. **Tag coverage.** Every tag used by a phase-1 template of a type can be produced by one of these:
   - a phase-1 option for that type (in `tags` or `maybeTags`)
   - a derived tag
   - `all`
4. **Seeder.**
   - EXPIRY uses the provided date as its baseline.
   - SEASONAL comes due on the next anchor.
   - ONE_TIME uses baseline meter 0 and is offered only below its interval.
   - Generator templates use `GENERATOR_HOURS`.
   - ATV/UTV oil uses `ENGINE_HOURS` at 50.
5. **Completion.** Completing a ONE_TIME task deactivates it.

**Instrumented or Robolectric tests:**

6. **Migration 2 → 3.** Existing rows survive, and the new columns hold their defaults.
7. **Library sync.**
   - A task linked to `seed:MOTORHOME:01` keeps its `templateId` after sync, and that template becomes `retired`.
   - `forType` excludes retired and phase-2 templates.
   - Re-running the sync with the same `dataVersion` changes nothing.
   - Bumping `dataVersion` updates template values without changing any `maintenance_task` row.

**Manual QA:** add one vehicle of each type using the fixture answers. The Schedule counts should match the fixtures, minus any ONE_TIME templates filtered out by current meters.

## 7. Phase 2 (do not build now)

- **Checklists:** PRE_USE (64) and POST_USE (11) templates.
- **Event triggers** (34 templates):
  - `wheel_mounted` → lug re-torque at 10/25/50 mi
  - `storage_start` → storage prep
  - `hub_submerged`, `grounding`, `rollover`, `submerged`, `tank_dump`, `drive_removed`, `inspection_finds_damage`
- **Runtime tags:**
  - `while_in_storage`: needs a storage-mode toggle.
  - `tires_over_5_years`: derived from the tire-age task's baseline.
- **Second meter:** "whichever comes first" across both meters, using `altMeterInterval`/`altMeterUnit`.
- **Phase-2 questions:** `pb_size` (engine cut-off lanyard rule) and `trailer_hubs` (repack after immersion). Both are already in the questions file with `phase: 2`.
- **Severe-service toggle:** for example, Polaris says to service at 50% of the interval for severe use.

## 8. Updating the library later

1. Edit `docs/maintenance-library/maintenance_library_v1.csv` in a spreadsheet.
2. Run `python3 docs/maintenance-library/tools/csv_to_json.py docs/maintenance-library/maintenance_library_v1.csv app/src/main/assets/library/maintenance_library_v1.json`. It validates the data and bumps `dataVersion`.
3. Ship the update. On next launch, `LibrarySync` upserts changed templates and retires deleted rows.

Rules for edits:

- Never change an existing `id`.
- New rows need a new `std:<TYPE>:<slug>` id.
- Adding a new tag means adding a question option (or a derived tag) that produces it in `setup_questions_v1.json`. Then regenerate the fixtures, or update them by hand.
