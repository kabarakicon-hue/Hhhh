package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_sms")
data class ScheduledSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val messageTemplate: String,
    val recipients: String, // Comma-separated phone numbers
    val scheduleTime: Long, // Epoch millis
    val intervalSec: Long = 0, // Repeat interval: 0 = one-time, >0 = periodic recurring seconds
    val isActive: Boolean = true,
    val lastRunTime: Long = 0
)

@Entity(tableName = "sms_drafts")
data class SmsDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val recipients: String, // Comma-separated
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_templates")
data class SmsTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val messageText: String,
    val category: String // "OTP", "General", "Marketing", "Reminder"
)

@Entity(tableName = "synced_contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val source: String // "PhoneContacts" or "HistoryImport"
)
