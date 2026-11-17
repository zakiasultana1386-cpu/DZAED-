package com.example

import com.example.data.local.LedgerTransactionEntity
import com.example.data.local.PartyEntity
import com.example.data.model.BusinessSettings
import com.example.data.model.TransactionType
import com.example.data.repository.LedgerRepository
import com.example.util.Formatters
import com.example.util.UsernameHelper
import com.example.web.LocalLedgerWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAccountingTest {

    @Test
    fun `party summary accurately computes receivable payable and pcs totals`() {
        val party = PartyEntity(
            id = 1L,
            companyName = "Rahman Textiles",
            personName = "Tariqul Islam",
            phoneNumber = "+8801711000000",
            username = "rahman_textiles"
        )

        val transactions = listOf(
            LedgerTransactionEntity(
                id = 1L,
                partyId = 1L,
                receiptNo = "RCP-20261117-0001",
                transactionType = TransactionType.PRODUCT_GIVEN.name,
                transactionDateMillis = 1700000000000L,
                itemName = "Cotton Shirts",
                quantityPcs = 50,
                unitPrice = 12.0,
                amount = 600.0
            ),
            LedgerTransactionEntity(
                id = 2L,
                partyId = 1L,
                receiptNo = "RCP-20261117-0002",
                transactionType = TransactionType.MONEY_RECEIVED.name,
                transactionDateMillis = 1700001000000L,
                amount = 350.0
            ),
            LedgerTransactionEntity(
                id = 3L,
                partyId = 1L,
                receiptNo = "RCP-20261117-0003",
                transactionType = TransactionType.PRODUCT_TAKEN.name,
                transactionDateMillis = 1700002000000L,
                itemName = "Denim Fabric",
                quantityPcs = 10,
                unitPrice = 10.0,
                amount = 100.0
            ),
            LedgerTransactionEntity(
                id = 4L,
                partyId = 1L,
                receiptNo = "RCP-20261117-0004",
                transactionType = TransactionType.MONEY_GIVEN.name,
                transactionDateMillis = 1700003000000L,
                amount = 50.0
            )
        )

        val summary = LedgerRepository.computePartySummary(party, transactions)

        assertEquals(50, summary.totalPcsGiven)
        assertEquals(10, summary.totalPcsTaken)
        assertEquals(600.0, summary.totalProductGivenValue, 0.001)
        assertEquals(100.0, summary.totalProductTakenValue, 0.001)
        assertEquals(350.0, summary.totalMoneyReceived, 0.001)
        assertEquals(50.0, summary.totalMoneyGiven, 0.001)
        assertEquals(200.0, summary.netBalance, 0.001)
        assertEquals(200.0, summary.totalReceivable, 0.001)
        assertEquals(0.0, summary.totalPayable, 0.001)
    }

    @Test
    fun `negative net balance surfaces as payable`() {
        val party = PartyEntity(
            id = 2L,
            companyName = "Apex Supplies",
            personName = "Karim Mia",
            phoneNumber = "01800000000",
            username = "apex_supplies"
        )
        val transactions = listOf(
            LedgerTransactionEntity(
                id = 10L,
                partyId = 2L,
                receiptNo = "RCP-20261117-0010",
                transactionType = TransactionType.PRODUCT_TAKEN.name,
                transactionDateMillis = 1700000000000L,
                quantityPcs = 20,
                unitPrice = 25.0,
                amount = 500.0
            ),
            LedgerTransactionEntity(
                id = 11L,
                partyId = 2L,
                receiptNo = "RCP-20261117-0011",
                transactionType = TransactionType.MONEY_GIVEN.name,
                transactionDateMillis = 1700001000000L,
                amount = 150.0
            )
        )

        val summary = LedgerRepository.computePartySummary(party, transactions)
        assertEquals(-350.0, summary.netBalance, 0.001)
        assertEquals(0.0, summary.totalReceivable, 0.001)
        assertEquals(350.0, summary.totalPayable, 0.001)
        assertEquals("$350 Payable", Formatters.formatSignedBalance(summary.netBalance, "$"))
    }

    @Test
    fun `username sanitization and html portal rendering work properly`() {
        assertEquals("rahman_textiles", UsernameHelper.sanitizeUsername(" @Rahman Textiles! "))
        assertTrue(UsernameHelper.isValidUsername("rahman_textiles"))
        assertFalse(UsernameHelper.isValidUsername("ab"))

        val party = PartyEntity(
            id = 5L,
            companyName = "Rahman Textiles",
            personName = "Tariqul Islam",
            phoneNumber = "01711000000",
            username = "rahman_textiles"
        )
        val tx = LedgerTransactionEntity(
            id = 20L,
            partyId = 5L,
            receiptNo = "RCP-20261117-0020",
            transactionType = TransactionType.PRODUCT_GIVEN.name,
            transactionDateMillis = 1700000000000L,
            itemName = "Polo Shirts",
            quantityPcs = 30,
            unitPrice = 10.0,
            amount = 300.0,
            notes = "Batch #1"
        )
        val summary = LedgerRepository.computePartySummary(party, listOf(tx))
        val html = LocalLedgerWebServer.renderPartyStatementHtml(
            summary = summary,
            transactions = listOf(tx),
            settings = BusinessSettings(businessName = "My Shop", currencySymbol = "$"),
            isLivePortal = true
        )

        assertTrue(html.contains("@rahman_textiles"))
        assertTrue(html.contains("RCP-20261117-0020"))
        assertTrue(html.contains("30 Pcs"))
    }
}
