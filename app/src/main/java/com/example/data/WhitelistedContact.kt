package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelisted_contacts")
data class WhitelistedContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
