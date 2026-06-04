package com.example.data.db

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class SmsRepository(private val smsDao: SmsDao) {

    val allLogs: Flow<List<SmsEntity>> = smsDao.getAllLogs()

    fun getLogsByType(type: String): Flow<List<SmsEntity>> = smsDao.getLogsByType(type)

    fun searchLogs(query: String): Flow<List<SmsEntity>> = smsDao.searchLogs(query)

    suspend fun insertLog(log: SmsEntity): Long {
        return smsDao.insertLog(log)
    }

    suspend fun clearLogs() {
        smsDao.clearLogs()
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    suspend fun getSentTodayCount(): Int = smsDao.getSentTodayCount(getStartOfDay())

    suspend fun getFailedTodayCount(): Int = smsDao.getFailedTodayCount(getStartOfDay())

    suspend fun getReceivedTodayCount(): Int = smsDao.getReceivedTodayCount(getStartOfDay())
}
