package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM parties ORDER BY companyName COLLATE NOCASE ASC")
    fun observeAllParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties")
    suspend fun getAllPartiesOnce(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE id = :partyId LIMIT 1")
    fun observePartyById(partyId: Long): Flow<PartyEntity?>

    @Query("SELECT * FROM parties WHERE id = :partyId LIMIT 1")
    suspend fun getPartyById(partyId: Long): PartyEntity?

    @Query("SELECT * FROM parties WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getPartyByUsername(username: String): PartyEntity?

    @Query("SELECT COUNT(*) FROM parties WHERE LOWER(username) = LOWER(:username) AND id != :excludePartyId")
    suspend fun countUsernameUsage(username: String, excludePartyId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParty(party: PartyEntity): Long

    @Update
    suspend fun updateParty(party: PartyEntity)

    @Query("DELETE FROM parties WHERE id = :partyId")
    suspend fun deletePartyById(partyId: Long)

    @Query("SELECT * FROM transactions ORDER BY transactionDateMillis DESC, createdAt DESC")
    fun observeAllTransactions(): Flow<List<LedgerTransactionEntity>>

    @Query("SELECT * FROM transactions WHERE partyId = :partyId ORDER BY transactionDateMillis DESC, createdAt DESC")
    fun observeTransactionsForParty(partyId: Long): Flow<List<LedgerTransactionEntity>>

    @Query("SELECT * FROM transactions WHERE partyId = :partyId ORDER BY transactionDateMillis DESC, createdAt DESC")
    suspend fun getTransactionsForPartyOnce(partyId: Long): List<LedgerTransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: Long): LedgerTransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: LedgerTransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)
}
