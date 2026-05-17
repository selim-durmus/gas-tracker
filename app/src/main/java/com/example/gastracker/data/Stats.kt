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
)

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

fun List<FillUp>.toLifetimeSummary(): LifetimeSummary {
    val totalSpentCents = sumOf { it.totalCostCents }
    val totalLitres = sumOf { it.litres }

    val sortedAsc = sortedBy { it.dateEpochDay }
    var kmDriven = 0L
    var consumedLitres = 0.0
    var hasPair = false
    for (i in 1 until sortedAsc.size) {
        val prev = sortedAsc[i - 1]
        val curr = sortedAsc[i]
        val prevOdo = prev.odometerKm
        val currOdo = curr.odometerKm
        if (prevOdo != null && currOdo != null && currOdo > prevOdo) {
            kmDriven += currOdo - prevOdo
            consumedLitres += curr.litres
            hasPair = true
        }
    }

    return LifetimeSummary(
        totalSpentCents = totalSpentCents,
        totalLitres = totalLitres,
        fillUpCount = size,
        kmDriven = if (hasPair) kmDriven else null,
        avgLper100km = if (hasPair && kmDriven > 0) consumedLitres / kmDriven * 100.0 else null,
    )
}

fun List<FillUp>.withEfficiency(): List<FillUpWithEfficiency> {
    val sortedAsc = sortedBy { it.dateEpochDay }
    val efficiencyById = HashMap<Long, Double>()
    for (i in 1 until sortedAsc.size) {
        val prev = sortedAsc[i - 1]
        val curr = sortedAsc[i]
        val prevOdo = prev.odometerKm
        val currOdo = curr.odometerKm
        if (prevOdo != null && currOdo != null && currOdo > prevOdo) {
            efficiencyById[curr.id] = curr.litres / (currOdo - prevOdo) * 100.0
        }
    }
    return map { FillUpWithEfficiency(it, efficiencyById[it.id]) }
}
