package me.jbusdriver.modern.data.repository

import androidx.room.withTransaction
import me.jbusdriver.modern.data.db.CollectDatabase
import javax.inject.Inject
import javax.inject.Singleton

interface CollectTransactionRunner {
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

@Singleton
class RoomCollectTransactionRunner @Inject constructor(
    private val database: CollectDatabase
) : CollectTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        return database.withTransaction {
            block()
        }
    }
}
