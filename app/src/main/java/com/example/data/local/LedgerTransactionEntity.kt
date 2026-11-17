package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = PartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["partyId"]), Index(value = ["transactionDateMillis"])]
)
data class LedgerTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val partyId: Long,
    val receiptNo: String,
    val transactionType: String,
    val transactionDateMillis: Long,
    val itemName: String = "",
    val quantityPcs: Int = 0,
    val unitPrice: Double = 0.0,
    val amount: Double,
    val notes: String = "",
    val proofFilePath: String? = null,
    val proofMimeType: String? = null,
    val proofDisplayName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
