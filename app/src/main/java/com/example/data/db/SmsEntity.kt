package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // "Sent", "Failed", "Incoming"
    val phoneNumber: String,    // Number of sender or recipient
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String,         // "Sent", "Received", "Failed", "Pending"
    val simUsed: String,        // SIM Info (e.g. "SIM 1", "SIM 2", "Unknown")
    val errorMessage: String? = null
)
