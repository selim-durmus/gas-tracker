package com.tuttoposto.gastracker.data

import java.time.LocalDate
import java.time.YearMonth

data class PeriodSummary(
    val spentCents: Long = 0,
    val litres: Double = 0.0,
    val count: Int = 0,
) {
    /** Volume-weighted average price, in mills per litre. */
    val avgPriceMills: Long?
        get() = if (litres > 0.0) Math.round(spentCents * 10.0 / litres) else null

    operator fun plus(other: PeriodSummary) =
        PeriodSummary(spentCents + other.spentCents, litres + other.litres, count + other.count)
}

fun List<FillUp>.summary(): PeriodSummary =
    PeriodSummary(sumOf { it.totalCostCents }, sumOf { it.litres }, size)

/**
 * One fuel-economy measurement, ending at a full-tank fill with an odometer reading.
 *
 * Economy is measured tank to tank: the distance since the previous full fill with an
 * odometer reading, and all fuel bought since then (any partial fills or fills without a
 * reading in between are folded in, so no fuel is lost from the average).
 */
data class EfficiencySegment(
    /** The full fill the distance is measured from. */
    val startFillUpId: Long,
    val endFillUpId: Long,
    val endDateEpochDay: Long,
    val distanceKm: Long,
    val litres: Double,
    val costCents: Long,
    val fillsCovered: Int,
) {
    val lPer100km: Double get() = litres / distanceKm * 100.0
    val isCombined: Boolean get() = fillsCovered > 1
}

/** Oldest first, with same-day ties broken by id so results never depend on input order. */
fun List<FillUp>.chronological(): List<FillUp> =
    sortedWith(compareBy({ it.dateEpochDay }, { it.id }))

fun List<FillUp>.efficiencySegments(): List<EfficiencySegment> {
    val segments = ArrayList<EfficiencySegment>()
    var baselineOdo: Long? = null
    var baselineId = 0L
    var litres = 0.0
    var cost = 0L
    var fills = 0
    for (entry in chronological()) {
        val odo = entry.odometerKm
        val closesSegment = entry.fullTank && odo != null
        if (entry.missedPrevious) {
            // A fill-up before this one was never logged, so the fuel since the baseline is
            // incomplete and any reading ending here would be far too low. Skip it and measure
            // fresh from this fill (or from the next full reading if this one can't start one).
            baselineOdo = if (closesSegment) odo else null
            baselineId = entry.id
            litres = 0.0
            cost = 0L
            fills = 0
            continue
        }
        if (baselineOdo == null) {
            // Fuel bought before the first full reading can't be attributed to any distance.
            if (closesSegment) {
                baselineOdo = odo
                baselineId = entry.id
            }
            continue
        }
        litres += entry.litres
        cost += entry.totalCostCents
        fills += 1
        if (!closesSegment) continue
        if (odo!! > baselineOdo) {
            segments += EfficiencySegment(
                startFillUpId = baselineId,
                endFillUpId = entry.id,
                endDateEpochDay = entry.dateEpochDay,
                distanceKm = odo - baselineOdo,
                litres = litres,
                costCents = cost,
                fillsCovered = fills,
            )
        }
        // A non-increasing reading (typo, new car, odometer swap) restarts the baseline.
        baselineOdo = odo
        baselineId = entry.id
        litres = 0.0
        cost = 0L
        fills = 0
    }
    return segments
}

/** Why a fill marked "full" looks like it wasn't. */
enum class PartialHint {
    /** Its own reading is far below normal: it took less fuel than the distance needed. */
    LowReading,
    /** The next reading is far above normal: the tank wasn't really full after this fill. */
    NextReadingHigh,
}

/**
 * Full-tank fills whose economy readings only make sense if the tank wasn't actually full.
 *
 * Compares each tank-to-tank reading with the median of all readings (needs at least three).
 * Below [lowRatio] × median, the fill that closed the reading is the likely partial; above
 * [highRatio] × median, the fill the reading started from is. Fills already marked partial
 * never close or start a reading, so flagging one makes its hint go away.
 */
fun List<FillUp>.partialFillSuspects(
    segments: List<EfficiencySegment> = efficiencySegments(),
    lowRatio: Double = 0.6,
    highRatio: Double = 1.6,
): Map<Long, PartialHint> {
    if (segments.size < 3) return emptyMap()
    val median = segments.map { it.lPer100km }.sorted().let { v ->
        if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
    }
    val hints = LinkedHashMap<Long, PartialHint>()
    for (s in segments) {
        when {
            s.lPer100km < median * lowRatio -> hints[s.endFillUpId] = PartialHint.LowReading
            s.lPer100km > median * highRatio && s.startFillUpId !in hints ->
                hints[s.startFillUpId] = PartialHint.NextReadingHigh
        }
    }
    return hints
}

data class LifetimeSummary(
    val total: PeriodSummary,
    val kmTracked: Long?,
    val lPer100km: Double?,
    /** Fuel cost per 100 km over the tracked distance. */
    val costPer100kmCents: Long?,
    val firstDate: LocalDate?,
)

fun List<FillUp>.lifetime(segments: List<EfficiencySegment> = efficiencySegments()): LifetimeSummary {
    val km = segments.sumOf { it.distanceKm }
    val hasDistance = segments.isNotEmpty() && km > 0
    return LifetimeSummary(
        total = summary(),
        kmTracked = if (hasDistance) km else null,
        lPer100km = if (hasDistance) segments.sumOf { it.litres } / km * 100.0 else null,
        costPer100kmCents = if (hasDistance) Math.round(segments.sumOf { it.costCents } * 100.0 / km) else null,
        firstDate = minOfOrNull { it.dateEpochDay }?.let(LocalDate::ofEpochDay),
    )
}

data class MonthSummary(val month: YearMonth, val summary: PeriodSummary)

data class YearSection(val year: Int, val summary: PeriodSummary, val months: List<MonthSummary>)

/** Years newest first, each with its months newest first. Only months with fill-ups appear. */
fun List<FillUp>.history(): List<YearSection> =
    groupBy { YearMonth.from(it.date) }
        .map { (month, entries) -> MonthSummary(month, entries.summary()) }
        .sortedByDescending { it.month }
        .groupBy { it.month.year }
        .map { (year, months) ->
            YearSection(year, months.fold(PeriodSummary()) { acc, m -> acc + m.summary }, months)
        }

/** Spend per month for the [count] months ending at [endMonth], oldest first, zeros included. */
fun List<FillUp>.monthlySpend(endMonth: YearMonth, count: Int): List<Pair<YearMonth, Long>> {
    val byMonth = groupBy { YearMonth.from(it.date) }.mapValues { (_, e) -> e.sumOf { it.totalCostCents } }
    return (count - 1 downTo 0).map { back ->
        val month = endMonth.minusMonths(back.toLong())
        month to (byMonth[month] ?: 0L)
    }
}

data class Dashboard(
    val today: LocalDate,
    val thisMonth: PeriodSummary,
    val lastMonth: PeriodSummary,
    val thisYear: PeriodSummary,
    val lifetime: LifetimeSummary,
    val segments: List<EfficiencySegment>,
    val history: List<YearSection>,
    val monthlySpend: List<Pair<YearMonth, Long>>,
) {
    val latestEfficiency: EfficiencySegment? get() = segments.lastOrNull()
}

fun List<FillUp>.dashboard(today: LocalDate): Dashboard {
    val month = YearMonth.from(today)
    val segments = efficiencySegments()
    return Dashboard(
        today = today,
        thisMonth = filter { YearMonth.from(it.date) == month }.summary(),
        lastMonth = filter { YearMonth.from(it.date) == month.minusMonths(1) }.summary(),
        thisYear = filter { it.date.year == today.year }.summary(),
        lifetime = lifetime(segments),
        segments = segments,
        history = history(),
        monthlySpend = monthlySpend(month, 12),
    )
}
