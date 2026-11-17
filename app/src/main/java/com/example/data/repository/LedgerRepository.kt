package com.example.data.repository

import com.example.data.local.LedgerDao
import com.example.data.local.LedgerTransactionEntity
import com.example.data.local.PartyEntity
import com.example.data.model.PartyRole
import com.example.data.model.PartySummary
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithParty
import com.example.util.UsernameHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LedgerRepository(private val dao: LedgerDao) {

    val partySummariesFlow: Flow<List<PartySummary>> = combine(
        dao.observeAllParties(),
        dao.observeAllTransactions()
    ) { parties, transactions ->
        val txByParty = transactions.groupBy { it.partyId }
        parties.map { party ->
            computePartySummary(party, txByParty[party.id].orEmpty())
        }
    }

    val allTransactionsWithPartyFlow: Flow<List<TransactionWithParty>> = combine(
        dao.observeAllParties(),
        dao.observeAllTransactions()
    ) { parties, transactions ->
        val partyMap = parties.associateBy { it.id }
        val txByParty = transactions.groupBy { it.partyId }
        val balanceByParty = parties.associate { p ->
            p.id to computePartySummary(p, txByParty[p.id].orEmpty()).netBalance
        }
        transactions.mapNotNull { tx ->
            val party = partyMap[tx.partyId] ?: return@mapNotNull null
            TransactionWithParty(
                transaction = tx,
                party = party,
                partyCurrentNetBalance = balanceByParty[party.id] ?: 0.0
            )
        }
    }

    fun observePartyDetail(partyId: Long): Flow<Pair<PartySummary?, List<LedgerTransactionEntity>>> {
        return combine(
            dao.observePartyById(partyId),
            dao.observeTransactionsForParty(partyId)
        ) { party, transactions ->
            val summary = party?.let { computePartySummary(it, transactions) }
            summary to transactions
        }
    }

    suspend fun ensureUniqueUsername(desiredRaw: String, companyName: String, excludePartyId: Long = -1L): String {
        val base = UsernameHelper.sanitizeUsername(desiredRaw).let {
            if (UsernameHelper.isValidUsername(it)) it else UsernameHelper.suggestBaseUsername(companyName)
        }
        if (dao.countUsernameUsage(base, excludePartyId) == 0) {
            return base
        }
        var suffix = 2
        while (true) {
            val candidate = "${base.take(24)}_$suffix"
            if (dao.countUsernameUsage(candidate, excludePartyId) == 0) {
                return candidate
            }
            suffix++
        }
    }

    suspend fun isUsernameAvailable(raw: String, excludePartyId: Long = -1L): Boolean {
        val sanitized = UsernameHelper.sanitizeUsername(raw)
        if (!UsernameHelper.isValidUsername(sanitized)) return false
        return dao.countUsernameUsage(sanitized, excludePartyId) == 0
    }

    suspend fun findPartyLedgerByUsername(usernameRaw: String): Pair<PartySummary, List<LedgerTransactionEntity>>? {
        val clean = UsernameHelper.sanitizeUsername(usernameRaw)
        if (clean.isEmpty()) return null
        val party = dao.getPartyByUsername(clean) ?: return null
        val txs = dao.getTransactionsForPartyOnce(party.id)
        return computePartySummary(party, txs) to txs
    }

    suspend fun addParty(
        companyName: String,
        personName: String,
        phoneNumber: String,
        address: String,
        role: PartyRole,
        desiredUsername: String = ""
    ): Long {
        val uniqueUsername = ensureUniqueUsername(desiredUsername, companyName)
        val entity = PartyEntity(
            companyName = companyName.trim(),
            personName = personName.trim(),
            phoneNumber = phoneNumber.trim(),
            address = address.trim(),
            partyRole = role.name,
            username = uniqueUsername
        )
        return dao.insertParty(entity)
    }

    suspend fun updateParty(
        existing: PartyEntity,
        companyName: String,
        personName: String,
        phoneNumber: String,
        address: String,
        role: PartyRole,
        desiredUsername: String
    ) {
        val uniqueUsername = ensureUniqueUsername(
            desiredRaw = desiredUsername.ifBlank { existing.username },
            companyName = companyName,
            excludePartyId = existing.id
        )
        dao.updateParty(
            existing.copy(
                companyName = companyName.trim(),
                personName = personName.trim(),
                phoneNumber = phoneNumber.trim(),
                address = address.trim(),
                partyRole = role.name,
                username = uniqueUsername
            )
        )
    }

    suspend fun deleteParty(partyId: Long) {
        dao.deletePartyById(partyId)
    }

    suspend fun recordTransaction(
        partyId: Long,
        type: TransactionType,
        transactionDateMillis: Long,
        itemName: String,
        quantityPcs: Int,
        unitPrice: Double,
        amount: Double,
        notes: String,
        proofFilePath: String?,
        proofMimeType: String?,
        proofDisplayName: String?
    ): TransactionWithParty? {
        val count = dao.getTransactionCount() + 1
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(transactionDateMillis))
        val receiptNo = String.format(Locale.US, "RCP-%s-%04d", dateStamp, count)

        val tx = LedgerTransactionEntity(
            partyId = partyId,
            receiptNo = receiptNo,
            transactionType = type.name,
            transactionDateMillis = transactionDateMillis,
            itemName = itemName.trim(),
            quantityPcs = quantityPcs,
            unitPrice = unitPrice,
            amount = amount,
            notes = notes.trim(),
            proofFilePath = proofFilePath,
            proofMimeType = proofMimeType,
            proofDisplayName = proofDisplayName
        )
        val insertedId = dao.insertTransaction(tx)
        val savedTx = dao.getTransactionById(insertedId) ?: tx.copy(id = insertedId)
        val party = dao.getPartyById(partyId) ?: return null

        return TransactionWithParty(
            transaction = savedTx,
            party = party,
            partyCurrentNetBalance = 0.0
        )
    }

    suspend fun deleteTransaction(transactionId: Long) {
        dao.deleteTransactionById(transactionId)
    }

    companion object {
        fun computePartySummary(
            party: PartyEntity,
            transactions: List<LedgerTransactionEntity>
        ): PartySummary {
            var productGivenVal = 0.0
            var productTakenVal = 0.0
            var moneyReceived = 0.0
            var moneyGiven = 0.0
            var pcsGiven = 0
            var pcsTaken = 0
            var lastActivity = party.createdAt

            for (tx in transactions) {
                if (tx.transactionDateMillis > lastActivity) {
                    lastActivity = tx.transactionDateMillis
                }
                when (TransactionType.fromRaw(tx.transactionType)) {
                    TransactionType.PRODUCT_GIVEN -> {
                        productGivenVal += tx.amount
                        pcsGiven += tx.quantityPcs
                    }
                    TransactionType.PRODUCT_TAKEN -> {
                        productTakenVal += tx.amount
                        pcsTaken += tx.quantityPcs
                    }
                    TransactionType.MONEY_RECEIVED -> {
                        moneyReceived += tx.amount
                    }
                    TransactionType.MONEY_GIVEN -> {
                        moneyGiven += tx.amount
                    }
                }
            }

            return PartySummary(
                party = party,
                totalProductGivenValue = productGivenVal,
                totalProductTakenValue = productTakenVal,
                totalMoneyReceived = moneyReceived,
                totalMoneyGiven = moneyGiven,
                totalPcsGiven = pcsGiven,
                totalPcsTaken = pcsTaken,
                transactionCount = transactions.size,
                lastActivityMillis = lastActivity
            )
        }
    }
}
