package com.example.gastracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "fill_ups")
data class FillUp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val pricePerLiterCents: Long,
    val totalCostCents: Long,
    val odometerKm: Long? = null,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
    val pricePerLiter: Money get() = Money(pricePerLiterCents)
    val totalCost: Money get() = Money(totalCostCents)
    val litres: Double
        get() = if (pricePerLiterCents > 0L) totalCostCents.toDouble() / pricePerLiterCents else 0.0
}
