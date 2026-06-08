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

    // --- SCHEDULED SMS ---
    @Query("SELECT * FROM scheduled_sms ORDER BY scheduleTime ASC")
    fun getAllScheduledSms(): Flow<List<ScheduledSmsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledSms(sms: ScheduledSmsEntity): Long

    @Query("DELETE FROM scheduled_sms WHERE id = :id")
    suspend fun deleteScheduledSmsById(id: Long)

    @Query("UPDATE scheduled_sms SET isActive = :isActive WHERE id = :id")
    suspend fun updateScheduledSmsStatus(id: Long, isActive: Boolean)

    // --- SMS DRAFTS ---
    @Query("SELECT * FROM sms_drafts ORDER BY timestamp DESC")
    fun getAllDrafts(): Flow<List<SmsDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: SmsDraftEntity): Long

    @Query("DELETE FROM sms_drafts WHERE id = :id")
    suspend fun deleteDraftById(id: Long)

    // --- MESSAGE TEMPLATES ---
    @Query("SELECT * FROM sms_templates ORDER BY title ASC")
    fun getAllTemplates(): Flow<List<SmsTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: SmsTemplateEntity): Long

    @Query("DELETE FROM sms_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)

    // --- SYNCED CONTACTS ---
    @Query("SELECT * FROM synced_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Query("DELETE FROM synced_contacts")
    suspend fun clearContacts()

    // --- DYNAMIC WEBSITE TABLES ---
    @Query("SELECT * FROM dynamic_rows ORDER BY lastUpdated DESC")
    fun getAllDynamicRows(): Flow<List<DynamicRowEntity>>

    @Query("SELECT * FROM dynamic_rows WHERE tableName = :tableName ORDER BY lastUpdated DESC")
    fun getDynamicRowsByTable(tableName: String): Flow<List<DynamicRowEntity>>

    @Query("SELECT * FROM dynamic_rows WHERE tableName = :tableName ORDER BY lastUpdated DESC")
    suspend fun getDynamicRowsByTableSync(tableName: String): List<DynamicRowEntity>

    @Query("SELECT * FROM dynamic_rows WHERE tableName = :tableName AND itemId = :itemId LIMIT 1")
    suspend fun getDynamicRow(tableName: String, itemId: String): DynamicRowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDynamicRow(row: DynamicRowEntity): Long

    @Query("DELETE FROM dynamic_rows WHERE id = :id")
    suspend fun deleteDynamicRow(id: Long)

    @Query("DELETE FROM dynamic_rows WHERE tableName = :tableName")
    suspend fun clearDynamicTable(tableName: String)

    @Query("DELETE FROM dynamic_rows")
    suspend fun clearAllDynamicRows()

    // --- INTEGRATION HUB RUNNING EVENTS LOGS ---
    @Query("SELECT * FROM hub_event_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllHubLogs(): Flow<List<HubEventLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHubLog(log: HubEventLogEntity): Long

    @Query("DELETE FROM hub_event_logs")
    suspend fun clearHubLogs()

    // --- LOCAL NOTIFICATION AUDITS ---
    @Query("SELECT * FROM notification_audits ORDER BY timestamp DESC LIMIT 200")
    fun getAllNotificationAudits(): Flow<List<NotificationAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationAudit(audit: NotificationAuditEntity): Long

    @Query("DELETE FROM notification_audits")
    suspend fun clearNotificationAudits()
}
