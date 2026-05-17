package com.example.gastracker.data

import kotlinx.coroutines.flow.Flow

class FillUpRepository(private val dao: FillUpDao) {
    fun observeAll(): Flow<List<FillUp>> = dao.observeAll()
    suspend fun getById(id: Long): FillUp? = dao.getById(id)
    suspend fun upsert(fillUp: FillUp): Long = dao.upsert(fillUp)
    suspend fun delete(fillUp: FillUp) = dao.delete(fillUp)
}
