package com.example.gastracker.data

import java.time.Month
import java.time.YearMonth

data class MonthSummary(
    val yearMonth: YearMonth,
    val totalSpentCents: Long,
    val totalLitres: Double,
    val fillUpCount: Int,
) {
    val avgPricePerLiterCents: Long
        get() = if (totalLitres > 0.0) Math.round(totalSpentCents / totalLitres) else 0L
    val totalSpent: Money get() = Money(totalSpentCents)
    val avgPricePerLiter: Money get() = Money(avgPricePerLiterCents)
}

data class YearSummary(
    val year: Int,
    val totalSpentCents: Long,
    val totalLitres: Double,
    val fillUpCount: Int,
) {
    val totalSpent: Money get() = Money(totalSpentCents)
}

data class LifetimeSummary(
    val totalSpentCents: Long,
    val totalLitres: Double,
    val fillUpCount: Int,
    val kmDriven: Long?,
    val avgLper100km: Double?,
) {
    val totalSpent: Money get() = Money(totalSpentCents)
    val avgPricePerLiterCents: Long
        get() = if (totalLitres > 0.0) Math.round(totalSpentCents / totalLitres) else 0L
}

data class FillUpWithEfficiency(
    val fillUp: FillUp,
    val lPer100km: Double?,
    /**
     * How many fill-ups' fuel is included in this efficiency reading. 1 for a clean
     * tank-to-tank reading; >1 when one or more partial fills (no odometer) were folded into
     * the interval, in which case the value is an average over a combined span.
     */
    val fillsCovered: Int = 1,
) {
    val isCombined: Boolean get() = lPer100km != null && fillsCovered > 1
}

data class HistorySection(
    val year: YearSummary,
    val months: List<MonthSummary>,
)

fun List<FillUp>.toHistorySections(): List<HistorySection> {
    if (isEmpty()) return emptyList()

    val byMonth = groupBy { YearMonth.of(it.date.year, it.date.month) }
        .mapValues { (ym, entries) ->
            MonthSummary(
                yearMonth = ym,
                totalSpentCents = entries.sumOf { it.totalCostCents },
                totalLitres = entries.sumOf { it.litres },
                fillUpCount = entries.size,
            )
        }

    val sortedMonths = byMonth.values.sortedByDescending { it.yearMonth }

    return sortedMonths
        .groupBy { it.yearMonth.year }
        .toSortedMap(compareByDescending { it })
        .map { (year, months) ->
            HistorySection(
                year = YearSummary(
                    year = year,
                    totalSpentCents = months.sumOf { it.totalSpentCents },
                    totalLitres = months.sumOf { it.totalLitres },
                    fillUpCount = months.sumOf { it.fillUpCount },
                ),
                months = months,
            )
        }
}

@Suppress("unused")
fun Month.shortName(): String = name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

/**
 * One fuel-efficiency measurement: the distance covered since the previous fill-up that
 * recorded an odometer reading, and the litres burned over that distance.
 *
 * Efficiency is measured tank-to-tank. A fill-up with no odometer reading is treated as a
 * partial fill: it does not produce its own measurement, and its litres are carried forward
 * into the next fill-up that does record an odometer (so no fuel is lost from the average).
 * This lets the user simply omit the odometer to skip a noisy entry without breaking the
 * next entry's efficiency.
 */
private data class EfficiencySegment(
    val fillUpId: Long,
    val distanceKm: Long,
    val litres: Double,
    val fillsCovered: Int,
) {
    val lPer100km: Double get() = litres / distanceKm * 100.0
}

private fun List<FillUp>.efficiencySegments(): List<EfficiencySegment> {
    // Deterministic order: by date, then by id to break same-day ties consistently.
    val sortedAsc = sortedWith(compareBy({ it.dateEpochDay }, { it.id }))
    val segments = ArrayList<EfficiencySegment>()
    var lastOdo: Long? = null
    var pendingLitres = 0.0
    var pendingCount = 0
    for (entry in sortedAsc) {
        val currOdo = entry.odometerKm
        if (currOdo == null) {
            // No odometer: fold this fill's fuel into the next measured interval.
            pendingLitres += entry.litres
            pendingCount += 1
            continue
        }
        val prevOdo = lastOdo
        if (prevOdo != null && currOdo > prevOdo) {
            segments += EfficiencySegment(
                fillUpId = entry.id,
                distanceKm = currOdo - prevOdo,
                litres = entry.litres + pendingLitres,
                fillsCovered = pendingCount + 1,
            )
        }
        // Advance the baseline whenever we have a reading (even on a non-increasing
        // odometer, e.g. a reset), and reset carried-over fuel.
        lastOdo = currOdo
        pendingLitres = 0.0
        pendingCount = 0
    }
    return segments
}

fun List<FillUp>.toLifetimeSummary(): LifetimeSummary {
    val totalSpentCents = sumOf { it.totalCostCents }
    val totalLitres = sumOf { it.litres }

    val segments = efficiencySegments()
    val kmDriven = segments.sumOf { it.distanceKm }
    val consumedLitres = segments.sumOf { it.litres }
    val hasPair = segments.isNotEmpty()

    return LifetimeSummary(
        totalSpentCents = totalSpentCents,
        totalLitres = totalLitres,
        fillUpCount = size,
        kmDriven = if (hasPair) kmDriven else null,
        avgLper100km = if (hasPair && kmDriven > 0) consumedLitres / kmDriven * 100.0 else null,
    )
}

fun List<FillUp>.withEfficiency(): List<FillUpWithEfficiency> {
    val segmentById = efficiencySegments().associateBy { it.fillUpId }
    return map { entry ->
        val segment = segmentById[entry.id]
        FillUpWithEfficiency(
            fillUp = entry,
            lPer100km = segment?.lPer100km,
            fillsCovered = segment?.fillsCovered ?: 1,
        )
    }
}
