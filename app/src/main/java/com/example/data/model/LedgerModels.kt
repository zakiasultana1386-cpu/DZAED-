package com.example.data.model

import com.example.data.local.LedgerTransactionEntity
import com.example.data.local.PartyEntity

enum class PartyRole(val label: String) {
    CUSTOMER("Customer"),
    SUPPLIER("Supplier"),
    BOTH("Customer & Supplier");

    companion object {
        fun fromRaw(raw: String): PartyRole =
            entries.firstOrNull { it.name == raw } ?: CUSTOMER
    }
}

enum class TransactionType(
    val label: String,
    val shortLabel: String,
    val isProductFlow: Boolean,
    val isInflowToBusiness: Boolean
) {
    PRODUCT_GIVEN(
        label = "Product Given",
        shortLabel = "Goods Given",
        isProductFlow = true,
        isInflowToBusiness = false
    ),
    PRODUCT_TAKEN(
        label = "Product Taken",
        shortLabel = "Goods Taken",
        isProductFlow = true,
        isInflowToBusiness = true
    ),
    MONEY_RECEIVED(
        label = "Money Received",
        shortLabel = "Cash In",
        isProductFlow = false,
        isInflowToBusiness = true
    ),
    MONEY_GIVEN(
        label = "Money Given",
        shortLabel = "Cash Out",
        isProductFlow = false,
        isInflowToBusiness = false
    );

    companion object {
        fun fromRaw(raw: String): TransactionType =
            entries.firstOrNull { it.name == raw } ?: MONEY_RECEIVED
    }
}

enum class PartyBalanceFilter(val label: String) {
    ALL("All"),
    RECEIVABLE("Receivable"),
    PAYABLE("Payable"),
    SETTLED("Settled")
}

data class PartySummary(
    val party: PartyEntity,
    val totalProductGivenValue: Double,
    val totalProductTakenValue: Double,
    val totalMoneyReceived: Double,
    val totalMoneyGiven: Double,
    val totalPcsGiven: Int,
    val totalPcsTaken: Int,
    val transactionCount: Int,
    val lastActivityMillis: Long
) {
    /**
     * Positive = Party owes me (Receivable).
     * Negative = I owe Party (Payable).
     */
    val netBalance: Double
        get() = (totalProductGivenValue + totalMoneyGiven) - (totalProductTakenValue + totalMoneyReceived)

    val totalReceivable: Double
        get() = if (netBalance > 0.005) netBalance else 0.0

    val totalPayable: Double
        get() = if (netBalance < -0.005) -netBalance else 0.0

    val netPcsDelivered: Int
        get() = totalPcsGiven - totalPcsTaken
}

data class TransactionWithParty(
    val transaction: LedgerTransactionEntity,
    val party: PartyEntity,
    val partyCurrentNetBalance: Double
) {
    val type: TransactionType
        get() = TransactionType.fromRaw(transaction.transactionType)
}

data class BusinessSettings(
    val businessName: String = "My Business",
    val currencySymbol: String = "$",
    val businessPhone: String = "",
    val businessAddress: String = ""
)

data class MonthlyCashFlowSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val totalPcsDelivered: Int,
    val totalPcsTaken: Int,
    val totalSalesValue: Double,
    val totalPurchaseValue: Double,
    val entries: List<TransactionWithParty>
) {
    val netCashFlow: Double
        get() = totalIncome - totalExpense
}
