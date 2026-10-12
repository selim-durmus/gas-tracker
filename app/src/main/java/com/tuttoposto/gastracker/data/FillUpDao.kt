package com.tuttoposto.gastracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FillUpDao {
    @Query("SELECT * FROM fill_ups ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<FillUp>>

    @Query("SELECT * FROM fill_ups ORDER BY dateEpochDay DESC, id DESC")
    suspend fun getAll(): List<FillUp>

    @Query("SELECT * FROM fill_ups WHERE id = :id")
    suspend fun getById(id: Long): FillUp?

    @Query("SELECT COUNT(*) FROM fill_ups")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(fillUp: FillUp): Long

    @Insert
    suspend fun insertAll(fillUps: List<FillUp>)

    @Delete
    suspend fun delete(fillUp: FillUp)

    @Query("DELETE FROM fill_ups")
    suspend fun deleteAll()

    /** Swaps the whole log in one transaction, so a failed import can't leave it half-empty. */
    @Transaction
    suspend fun replaceAll(fillUps: List<FillUp>) {
        deleteAll()
        insertAll(fillUps)
    }
}
