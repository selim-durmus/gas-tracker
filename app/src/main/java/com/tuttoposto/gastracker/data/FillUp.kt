package com.tuttoposto.gastracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One trip to the pump.
 *
 * Money is stored as integers so sums never drift: the total in cents, and the price per
 * litre in mills (thousandths of a dollar), because pump prices carry three decimals
 * ($1.799/L). v1 stored the price in whole cents and silently rounded that third decimal
 * away, which also skewed the derived litres and every efficiency figure.
 */
@Entity(
    tableName = "fill_ups",
    indices = [Index(value = ["dateEpochDay"])],
)
data class FillUp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val pricePerLitreMills: Long,
    val totalCostCents: Long,
    val odometerKm: Long? = null,
    /** False when the tank wasn't filled to the top; its fuel rolls into the next full fill. */
    val fullTank: Boolean = true,
    val note: String = "",
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)

    /** Litres pumped, derived from what the receipt says: total / price. */
    val litres: Double
        get() = if (pricePerLitreMills > 0L) totalCostCents * 10.0 / pricePerLitreMills else 0.0
}
