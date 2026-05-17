package com.example.gastracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FillUpDao {
    @Query("SELECT * FROM fill_ups ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<FillUp>>

    @Query("SELECT * FROM fill_ups WHERE id = :id")
    suspend fun getById(id: Long): FillUp?

    @Upsert
    suspend fun upsert(fillUp: FillUp): Long

    @Delete
    suspend fun delete(fillUp: FillUp)
}
