package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms_logs WHERE type = :type ORDER BY timestamp DESC")
    fun getLogsByType(type: String): Flow<List<SmsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsEntity): Long

    @Query("DELETE FROM sms_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM sms_logs WHERE phoneNumber LIKE '%' || :query || '%' OR message LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchLogs(query: String): Flow<List<SmsEntity>>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE type = 'Sent' AND timestamp >= :startOfDay")
    suspend fun getSentTodayCount(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE type = 'Failed' AND timestamp >= :startOfDay")
    suspend fun getFailedTodayCount(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE type = 'Incoming' AND timestamp >= :startOfDay")
    suspend fun getReceivedTodayCount(startOfDay: Long): Int
}
