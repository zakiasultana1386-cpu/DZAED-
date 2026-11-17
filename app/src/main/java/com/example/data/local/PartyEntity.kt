package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parties",
    indices = [Index(value = ["username"], unique = true)]
)
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val companyName: String,
    val personName: String,
    val phoneNumber: String,
    val address: String = "",
    val partyRole: String = "CUSTOMER",
    val username: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
