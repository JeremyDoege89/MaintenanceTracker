package dev.dreamz.weekendfleet.data.model

enum class VehicleType(val label: String) {
    MOTORHOME("Motorhome"),
    TRAVEL_TRAILER("Travel trailer / fifth wheel"),
    BOAT_POWER("Powerboat"),
    BOAT_SAIL("Sailboat"),
    MOTORCYCLE("Motorcycle"),
    ATV_UTV("ATV / UTV"),
    PWC("Personal watercraft"),
    ;

    /** Meters worth prompting for on this type, primary first. */
    val meters: List<MeterUnit>
        get() = when (this) {
            MOTORHOME -> listOf(MeterUnit.MILES, MeterUnit.GENERATOR_HOURS)
            TRAVEL_TRAILER -> listOf(MeterUnit.MILES, MeterUnit.GENERATOR_HOURS)
            BOAT_POWER -> listOf(MeterUnit.ENGINE_HOURS, MeterUnit.GENERATOR_HOURS)
            BOAT_SAIL -> listOf(MeterUnit.ENGINE_HOURS, MeterUnit.GENERATOR_HOURS)
            MOTORCYCLE -> listOf(MeterUnit.MILES)
            ATV_UTV -> listOf(MeterUnit.ENGINE_HOURS, MeterUnit.MILES)
            PWC -> listOf(MeterUnit.ENGINE_HOURS)
        }

    /** The unit Vehicle.purchaseMeter is recorded in. */
    val primaryMeter: MeterUnit get() = meters.first()

    /** Boats carry a HIN rather than a VIN. */
    val idLabel: String
        get() = when (this) {
            BOAT_POWER, BOAT_SAIL, PWC -> "HIN"
            else -> "VIN"
        }
}

/** Closed list (Build Guide §3.2.1). Custom tasks pick from this or OTHER + free-text detail. */
enum class Area(val label: String) {
    ENGINE("Engine"),
    FUEL("Fuel"),
    DRIVETRAIN("Drivetrain"),
    BRAKES("Brakes"),
    TIRES_WHEELS("Tires & wheels"),
    CHASSIS("Chassis"),
    ROOF_SEALS("Roof & seals"),
    HVAC("HVAC"),
    PLUMBING_WATER("Plumbing & water"),
    ELECTRICAL("Electrical"),
    PROPANE("Propane"),
    HULL("Hull"),
    RIGGING("Rigging"),
    TRAILER("Trailer"),
    SAFETY_EQUIP("Safety equipment"),
    BODY_EXTERIOR("Body & exterior"),
    OTHER("Other"),
}

enum class MeterUnit(val short: String, val long: String) {
    MILES("mi", "Miles"),
    ENGINE_HOURS("eng hrs", "Engine hours"),
    GENERATOR_HOURS("gen hrs", "Generator hours"),
}

/** Who did the work (the "Done by" tabs). Shop / Dealer / Mobile carry a name in ServiceRecord.vendor. */
enum class VendorType(val label: String, val nameLabel: String?) {
    SELF("Self", null),
    SHOP("Shop", "Shop name"),
    DEALER("Dealer", "Dealer name"),
    MOBILE("Mobile", "Mechanic or business name"),
}

enum class TaskStatus { OK, DUE_SOON, OVERDUE, MANUAL }

enum class AttachmentKind { PHOTO, RECEIPT, MANUAL, INSURANCE, OTHER }

enum class ReminderKind { DUE_SOON_TIME, OVERDUE_TIME, DUE_SOON_METER, OVERDUE_METER }

/** How a library template schedules (IMPLEMENTATION.md §4.2). */
enum class TemplateKind {
    /** Time and/or meter from the last completion, whichever first. */
    RECURRING,
    /** Age limit from [DateBasis]; completion resets the baseline to the service date. */
    EXPIRY,
    /** Once a year, first due at the next season anchor. */
    SEASONAL,
    /** Break-in / first service, measured from 0 on the meter; deactivated once completed. */
    ONE_TIME,
    /** No generic interval: seeded MANUAL so the owner fills it in from the manual. */
    PER_MANUAL,
    // Phase 2 — never offered yet.
    PRE_USE,
    POST_USE,
    EVENT,
}

/** What an EXPIRY template's age is counted from. */
enum class DateBasis { MANUFACTURE_DATE, INSTALL_DATE, IN_SERVICE_DATE, REGISTRATION_DATE }

enum class Season { FALL, SPRING }

enum class SourceTier { NAMED_SOURCE, GENERAL_PRACTICE }

enum class QuestionType { SINGLE, MULTI }
