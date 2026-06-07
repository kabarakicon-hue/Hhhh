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

    // --- SCHEDULED SMS ---
    val allScheduledSms: Flow<List<ScheduledSmsEntity>> = smsDao.getAllScheduledSms()
    suspend fun insertScheduledSms(sms: ScheduledSmsEntity): Long = smsDao.insertScheduledSms(sms)
    suspend fun deleteScheduledSmsById(id: Long) = smsDao.deleteScheduledSmsById(id)
    suspend fun updateScheduledSmsStatus(id: Long, isActive: Boolean) = smsDao.updateScheduledSmsStatus(id, isActive)

    // --- SMS DRAFTS ---
    val allDrafts: Flow<List<SmsDraftEntity>> = smsDao.getAllDrafts()
    suspend fun insertDraft(draft: SmsDraftEntity): Long = smsDao.insertDraft(draft)
    suspend fun deleteDraftById(id: Long) = smsDao.deleteDraftById(id)

    // --- MESSAGE TEMPLATES ---
    val allTemplates: Flow<List<SmsTemplateEntity>> = smsDao.getAllTemplates()
    suspend fun insertTemplate(template: SmsTemplateEntity): Long = smsDao.insertTemplate(template)
    suspend fun deleteTemplateById(id: Long) = smsDao.deleteTemplateById(id)

    // --- SYNCED CONTACTS ---
    val allContacts: Flow<List<ContactEntity>> = smsDao.getAllContacts()
    suspend fun insertContacts(contacts: List<ContactEntity>) = smsDao.insertContacts(contacts)
    suspend fun insertContact(contact: ContactEntity): Long = smsDao.insertContact(contact)
    suspend fun clearContacts() = smsDao.clearContacts()
}
