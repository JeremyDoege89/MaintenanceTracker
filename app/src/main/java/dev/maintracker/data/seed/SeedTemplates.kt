package dev.maintracker.data.seed

import dev.maintracker.data.model.Area
import dev.maintracker.data.model.Area.BODY_EXTERIOR
import dev.maintracker.data.model.Area.BRAKES
import dev.maintracker.data.model.Area.CHASSIS
import dev.maintracker.data.model.Area.DRIVETRAIN
import dev.maintracker.data.model.Area.ELECTRICAL
import dev.maintracker.data.model.Area.ENGINE
import dev.maintracker.data.model.Area.FUEL
import dev.maintracker.data.model.Area.HULL
import dev.maintracker.data.model.Area.HVAC
import dev.maintracker.data.model.Area.PLUMBING_WATER
import dev.maintracker.data.model.Area.PROPANE
import dev.maintracker.data.model.Area.RIGGING
import dev.maintracker.data.model.Area.ROOF_SEALS
import dev.maintracker.data.model.Area.SAFETY_EQUIP
import dev.maintracker.data.model.Area.TIRES_WHEELS
import dev.maintracker.data.model.Area.TRAILER
import dev.maintracker.data.model.MaintenanceTemplate
import dev.maintracker.data.model.MeterUnit
import dev.maintracker.data.model.MeterUnit.ENGINE_HOURS
import dev.maintracker.data.model.MeterUnit.MILES
import dev.maintracker.data.model.VehicleType

/**
 * Shipped schedules, Build Guide §4. Every interval is an editable default — a rule of thumb from
 * owner communities, not manufacturer guidance. Where the guide gives a range, the seed uses the
 * conservative (shorter) end and says so in notes.
 *
 * Ids are stable ("seed:TYPE:NN"): they are inserted with IGNORE on every launch, so new rows can
 * ship in an update without overwriting a user's edits. Never renumber an existing id.
 */
object SeedTemplates {
    const val DISCLAIMER =
        "Intervals are common rules of thumb from owner communities, not manufacturer guidance. " +
            "Edit them to match your owner's manual."

    private const val MONTHLY = 30
    private const val QUARTERLY = 91
    private const val TWICE_YEARLY = 182
    private const val YEARLY = 365
    private const val TWO_YEARS = 730

    private class Row(
        val area: Area,
        val title: String,
        val description: String,
        val days: Int? = null,
        val meter: Int? = null,
        val unit: MeterUnit? = null,
        val notes: String? = null,
    )

    private fun r(
        area: Area, title: String, description: String,
        days: Int? = null, meter: Int? = null, unit: MeterUnit? = null, notes: String? = null,
    ) = Row(area, title, description, days, meter, unit, notes)

    private val rows: Map<VehicleType, List<Row>> = mapOf(
        VehicleType.MOTORHOME to listOf(
            r(ENGINE, "Engine oil & filter", "Drain, replace filter, refill to spec.", TWICE_YEARLY, 5000, MILES,
                "Gas engines: 6 mo / 5,000 mi, whichever first. Diesel: follow your OEM schedule."),
            r(ENGINE, "Generator exercise under load", "Run 1–2 hrs at 50%+ load.", MONTHLY,
                notes = "Keeps fuel moving and windings dry."),
            r(ENGINE, "Air filter inspection", "Inspect engine air filter; replace if dirty.", YEARLY),
            r(ROOF_SEALS, "Roof sealant / Dicor inspection", "Inspect seams, vents and fixtures; reseal cracks.",
                TWICE_YEARLY, notes = "Spring + fall."),
            r(TIRES_WHEELS, "Tire age check", "Check DOT date codes, sidewall cracking and pressures.", YEARLY,
                notes = "Replace at ~6 years regardless of tread (rule of thumb)."),
            r(DRIVETRAIN, "Transmission fluid & filter", "Inspect fluid level and condition.", YEARLY,
                notes = "Service interval per OEM; this is the annual inspection."),
            r(BRAKES, "Brake fluid flush", "Flush and replace brake fluid.", TWO_YEARS,
                notes = "2 years (rule of thumb)."),
            r(CHASSIS, "Wheel bearing service", "Inspect and repack or service wheel bearings.", YEARLY, 12000, MILES),
            r(HVAC, "Roof A/C coils & shroud clean", "Clean coils, filters and shroud.", YEARLY),
            r(PROPANE, "Propane system leak check", "Leak-test lines and fittings; check regulator.", YEARLY),
            r(PLUMBING_WATER, "Fresh water sanitize", "Sanitize fresh tank and lines.", YEARLY),
            r(PLUMBING_WATER, "Water heater anode", "Pull and inspect the anode rod.", YEARLY,
                notes = "Replace when ~75% consumed (rule of thumb)."),
            r(ELECTRICAL, "Battery water / charge check", "Check electrolyte, terminals and state of charge.", MONTHLY),
            r(BODY_EXTERIOR, "Slide-out seal lube & inspection", "Clean, inspect and treat slide seals.", TWICE_YEARLY),
            r(PLUMBING_WATER, "Winterization", "Drain, bypass water heater, add RV antifreeze.", YEARLY,
                notes = "Before freeze season. Disable if you don't store in a freezing climate."),
        ),
        VehicleType.TRAVEL_TRAILER to listOf(
            r(TIRES_WHEELS, "Wheel bearing repack", "Remove, inspect, repack with NLGI #2.", YEARLY, 10000, MILES,
                "Applies to oil-bath or greased bearings both."),
            r(TIRES_WHEELS, "Tire age check", "Check DOT date codes, sidewall cracking and pressures.", YEARLY,
                notes = "Replace at ~5 years regardless of tread (rule of thumb)."),
            r(ROOF_SEALS, "Roof / seam sealant inspection", "Inspect seams, vents and fixtures; reseal cracks.",
                TWICE_YEARLY),
            r(BRAKES, "Brake inspection & adjustment", "Inspect magnets and shoes; adjust electric brakes.", YEARLY),
            r(TRAILER, "Breakaway battery test", "Pull the breakaway pin; confirm brakes lock; check charge.",
                TWICE_YEARLY),
            r(TRAILER, "Hitch / coupler / pin box inspect & lube", "Inspect for wear and cracks; lubricate.", YEARLY),
            r(PROPANE, "Propane system leak check", "Leak-test lines and fittings; check regulator.", YEARLY),
            r(PLUMBING_WATER, "Fresh water sanitize", "Sanitize fresh tank and lines.", YEARLY),
            r(PLUMBING_WATER, "Water heater anode", "Pull and inspect the anode rod.", YEARLY),
            r(HVAC, "A/C filter & coils clean", "Clean filters and coils.", YEARLY),
            r(ELECTRICAL, "Battery check", "Check terminals and state of charge.", QUARTERLY),
            r(BODY_EXTERIOR, "Slide-out seal lube", "Clean and treat slide seals.", TWICE_YEARLY),
            r(ELECTRICAL, "Running / brake lights & 7-pin check", "Test every light and the 7-pin connector.",
                YEARLY, notes = "Pre-season."),
            r(PLUMBING_WATER, "Winterization", "Drain, bypass water heater, add RV antifreeze.", YEARLY,
                notes = "Before freeze season. Disable if you don't store in a freezing climate."),
        ),
        VehicleType.BOAT_POWER to listOf(
            r(ENGINE, "Engine oil & filter", "Drain, replace filter, refill to spec.", YEARLY, 100, ENGINE_HOURS,
                "Annual or 100 hrs, whichever first."),
            r(ENGINE, "Raw water impeller", "Replace the raw-water pump impeller.", YEARLY,
                notes = "1–2 years (rule of thumb); seeded at 1 year."),
            r(FUEL, "Fuel filter / water separator", "Replace filter; check bowl for water.", YEARLY),
            r(DRIVETRAIN, "Lower unit gear oil", "Drain, check for water or metal, refill.", YEARLY),
            r(HULL, "Sacrificial anodes (zincs)", "Inspect all anodes.", TWICE_YEARLY,
                notes = "Replace at ~50% consumed (rule of thumb)."),
            r(DRIVETRAIN, "Bellows inspection (stern drive)", "Inspect bellows for cracks and wear.", YEARLY,
                notes = "Stern drives only. Replace ~every 5 years (rule of thumb)."),
            r(HULL, "Bottom paint", "Inspect bottom paint.", YEARLY,
                notes = "Repaint every ~2–3 years (rule of thumb)."),
            r(HULL, "Prop & shaft inspection", "Check prop for dings and the shaft for play.", YEARLY),
            r(TRAILER, "Trailer wheel bearing repack", "Remove, inspect, repack bearings.", YEARLY),
            r(TRAILER, "Trailer tire age check", "Check DOT date codes and sidewalls.", YEARLY,
                notes = "Replace at ~5 years (rule of thumb)."),
            r(ELECTRICAL, "Bilge pump test", "Test pump and float switch.", MONTHLY, notes = "During season."),
            r(SAFETY_EQUIP, "Fire extinguishers", "Inspect gauge, pin and mounting.", YEARLY,
                notes = "Service per gauge."),
            r(SAFETY_EQUIP, "Flare kit expiry", "Check expiration dates.", YEARLY),
            r(ENGINE, "Winterization / off-season layup", "Stabilize fuel, fog, drain or antifreeze.", YEARLY),
            r(DRIVETRAIN, "Steering cable / hydraulic check", "Check for binding, leaks and play.", YEARLY),
        ),
        VehicleType.BOAT_SAIL to listOf(
            r(RIGGING, "Standing rigging inspection", "Inspect shrouds, stays, terminals and chainplates.", YEARLY,
                notes = "Pre-season. Professional inspection ~every 5 years (rule of thumb)."),
            r(RIGGING, "Running rigging chafe check", "Inspect lines and sheaves for chafe.", YEARLY),
            r(RIGGING, "Winch service", "Strip, clean and re-grease winches.", YEARLY),
            r(RIGGING, "Sail inspection (UV / chafe)", "Inspect stitching, UV cover and chafe points.", YEARLY,
                notes = "End of season."),
            r(ENGINE, "Aux engine oil & filter", "Drain, replace filter, refill to spec.", YEARLY, 100, ENGINE_HOURS),
            r(ENGINE, "Raw water impeller", "Replace the raw-water pump impeller.", TWO_YEARS,
                notes = "2 years (rule of thumb)."),
            r(FUEL, "Fuel filter / water separator", "Replace filter; check bowl for water.", YEARLY),
            r(HULL, "Seacocks & through-hulls", "Operate and lubricate every seacock.", YEARLY),
            r(HULL, "Sacrificial anodes", "Inspect all anodes.", TWICE_YEARLY),
            r(HULL, "Bottom paint", "Inspect bottom paint.", YEARLY,
                notes = "Repaint every ~2–3 years (rule of thumb)."),
            r(ELECTRICAL, "Bilge pump test", "Test pump and float switch.", MONTHLY, notes = "During season."),
            r(SAFETY_EQUIP, "Lifeline & jackline check", "Inspect lifelines, stanchions and jacklines.", YEARLY),
            r(SAFETY_EQUIP, "Fire extinguisher / flares", "Check gauges and flare expiry dates.", YEARLY),
            r(ENGINE, "Winterization / layup", "Stabilize fuel, winterize engine and water systems.", YEARLY),
        ),
        VehicleType.MOTORCYCLE to listOf(
            r(DRIVETRAIN, "Chain clean, lube & tension", "Clean, lube and set chain slack.", meter = 500, unit = MILES,
                notes = "Every 300–600 mi (rule of thumb) or after riding in rain. Disable for belt/shaft bikes."),
            r(DRIVETRAIN, "Belt / shaft drive inspection", "Inspect belt or shaft drive.", YEARLY,
                notes = "Service per OEM. Disable for chain bikes."),
            r(ENGINE, "Engine oil & filter", "Drain, replace filter, refill to spec.", YEARLY, 3000, MILES,
                "Per OEM. Common: 3,000–5,000 mi or annual (rule of thumb); seeded at 3,000."),
            r(ENGINE, "Valve clearance check", "Measure and adjust valve clearances.", meter = 15000, unit = MILES,
                notes = "Per OEM. Common: 15,000–26,000 mi (rule of thumb)."),
            r(ENGINE, "Air filter", "Inspect; clean or replace.", YEARLY, notes = "Service interval per OEM."),
            r(ENGINE, "Coolant (liquid-cooled)", "Drain and replace coolant.", TWO_YEARS,
                notes = "2 years (rule of thumb). Disable for air-cooled bikes."),
            r(ENGINE, "Spark plugs", "Inspect or replace plugs.",
                notes = "Per OEM — set the interval from your owner's manual."),
            r(BRAKES, "Brake pad inspection", "Check pad thickness front and rear.", meter = 3000, unit = MILES,
                notes = "Every 3,000 mi (rule of thumb)."),
            r(BRAKES, "Brake fluid flush", "Flush and replace brake fluid.", TWO_YEARS,
                notes = "2 years (rule of thumb)."),
            r(TIRES_WHEELS, "Tire inspection", "Check pressures, tread, cuts and date codes.", MONTHLY,
                notes = "Replace at ~5 years age regardless of tread (rule of thumb)."),
            r(CHASSIS, "Fork oil", "Replace fork oil.", meter = 10000, unit = MILES,
                notes = "Per OEM. Common: 10,000–15,000 mi (rule of thumb)."),
            r(ELECTRICAL, "Battery check / tender", "Check charge; keep on a tender.", MONTHLY,
                notes = "Mainly during the off-season."),
            r(CHASSIS, "Off-season storage prep", "Stabilize fuel, tender battery, protect tires.", YEARLY),
        ),
        VehicleType.ATV_UTV to listOf(
            r(ENGINE, "Engine oil & filter", "Drain, replace filter, refill to spec.", YEARLY, 50, ENGINE_HOURS,
                "50–100 hrs or annual, whichever first (rule of thumb); seeded at 50."),
            r(ENGINE, "Air filter service", "Inspect, clean or replace the air filter.", meter = 25, unit = ENGINE_HOURS,
                notes = "Every 25 hrs (rule of thumb); more often in dust."),
            r(DRIVETRAIN, "CVT belt & clutch inspection", "Inspect belt and clutches for wear.",
                meter = 100, unit = ENGINE_HOURS, notes = "Every 100 hrs (rule of thumb)."),
            r(DRIVETRAIN, "CV boots & axles", "Inspect boots for tears and axles for play.",
                meter = 100, unit = ENGINE_HOURS, notes = "Every 100 hrs (rule of thumb)."),
            r(CHASSIS, "Grease fittings", "Grease every zerk.", meter = 25, unit = ENGINE_HOURS,
                notes = "Every 25–50 hrs (rule of thumb); seeded at 25."),
            r(ENGINE, "Valve lash inspection", "Measure and adjust valve lash.", YEARLY, 100, ENGINE_HOURS,
                "Per OEM. Common: 100 hrs or annual (rule of thumb)."),
            r(BRAKES, "Brake pads & rotors", "Check pads and rotors.", meter = 100, unit = ENGINE_HOURS),
            r(ENGINE, "Coolant", "Drain and replace coolant.", TWO_YEARS, notes = "2 years (rule of thumb)."),
            r(TIRES_WHEELS, "Tire & wheel inspection", "Check pressures, damage and lug torque.", MONTHLY),
            r(ELECTRICAL, "Battery check", "Check terminals and state of charge.", MONTHLY),
            r(CHASSIS, "Bolt / skid-plate check", "Check fastener torque and skid plates.",
                meter = 50, unit = ENGINE_HOURS, notes = "Every 50 hrs (rule of thumb)."),
            r(CHASSIS, "Winch service", "Inspect cable/rope, hook and operation.", YEARLY),
            r(CHASSIS, "Pre-season full service", "Full pre-season inspection.", YEARLY),
        ),
        VehicleType.PWC to listOf(
            r(ENGINE, "Engine oil & filter (4-stroke)", "Drain, replace filter, refill to spec.",
                YEARLY, 50, ENGINE_HOURS, "Annual or 50–100 hrs, whichever first (rule of thumb); seeded at 50."),
            r(ENGINE, "Spark plugs", "Inspect or replace plugs.", YEARLY),
            r(ENGINE, "Supercharger rebuild / inspection", "Inspect or rebuild the supercharger.",
                meter = 200, unit = ENGINE_HOURS, notes = "If equipped. Per OEM; common ~200 hrs (rule of thumb)."),
            r(ENGINE, "Cooling system flush", "Flush with fresh water.",
                notes = "After every saltwater ride — a checklist item, never auto-due."),
            r(DRIVETRAIN, "Jet pump & wear ring inspection", "Inspect impeller and wear ring clearance.", YEARLY),
            r(HULL, "Intake grate & hull inspection", "Inspect intake grate and hull.", YEARLY),
            r(ENGINE, "Throttle & steering cables", "Check for binding and adjust.", YEARLY),
            r(ELECTRICAL, "Battery check", "Check terminals and state of charge.", MONTHLY),
            r(TRAILER, "Trailer bearings & tires", "Repack bearings; check tires.", YEARLY,
                notes = "Replace trailer tires at ~5 years age (rule of thumb)."),
            r(ENGINE, "Winterization / fogging", "Stabilize fuel, fog engine, drain water.", YEARLY),
            r(SAFETY_EQUIP, "Fire extinguisher", "Inspect gauge, pin and mounting.", YEARLY),
        ),
    )

    val all: List<MaintenanceTemplate> = rows.flatMap { (type, list) ->
        list.mapIndexed { i, row ->
            MaintenanceTemplate(
                id = "seed:${type.name}:%02d".format(i + 1),
                vehicleType = type,
                area = row.area,
                title = row.title,
                description = row.description,
                timeIntervalDays = row.days,
                meterInterval = row.meter,
                meterUnit = row.unit,
                notes = row.notes,
                isSeeded = true,
                sortOrder = i,
            )
        }
    }

    fun forType(type: VehicleType): List<MaintenanceTemplate> = all.filter { it.vehicleType == type }
}
