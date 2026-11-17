package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PartyEntity
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithParty
import com.example.ui.components.AddEditPartyDialog
import com.example.ui.components.CashPaymentReceiptDialog
import com.example.ui.components.RecordTransactionSheet
import com.example.ui.components.ShareableViewDialog
import com.example.ui.components.WebPortalDialog
import com.example.ui.screens.CashFlowScreen
import com.example.ui.screens.PartiesScreen
import com.example.ui.screens.PartyProfileScreen
import com.example.ui.screens.ReceiptsAndSettingsScreen
import com.example.ui.screens.ShareableViewScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TradeLedgerTheme
import com.example.ui.viewmodel.LedgerViewModel
import com.example.ui.viewmodel.ShareableViewUiState

private enum class TopTab(val label: String) {
    PARTIES("Parties"),
    CASH_FLOW("Cash Flow"),
    RECEIPTS("Receipts")
}

class MainActivity : ComponentActivity() {
    private val ledgerViewModel: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ledgerViewModel.handleIncomingDeepLink(intent?.data)
        setContent {
            TradeLedgerTheme {
                TradeLedgerApp(viewModel = ledgerViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ledgerViewModel.handleIncomingDeepLink(intent.data)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeLedgerApp(viewModel: LedgerViewModel) {
    val allParties by viewModel.allPartySummaries.collectAsStateWithLifecycle()
    val filteredParties by viewModel.filteredParties.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val balanceFilter by viewModel.balanceFilter.collectAsStateWithLifecycle()
    val selectedPartyId by viewModel.selectedPartyId.collectAsStateWithLifecycle()
    val selectedPartyDetail by viewModel.selectedPartyDetail.collectAsStateWithLifecycle()
    val cashFlowFilter by viewModel.cashFlowFilter.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlyCashFlowSummary.collectAsStateWithLifecycle()
    val receipts by viewModel.allReceipts.collectAsStateWithLifecycle()
    val receiptSearch by viewModel.receiptSearchQuery.collectAsStateWithLifecycle()
    val businessSettings by viewModel.businessSettings.collectAsStateWithLifecycle()
    val activeReceipt by viewModel.activeReceipt.collectAsStateWithLifecycle()
    val webPortalState by viewModel.webPortalState.collectAsStateWithLifecycle()
    val shareableViewState by viewModel.shareableViewState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(TopTab.PARTIES) }
    var showAddPartyDialog by remember { mutableStateOf(false) }
    var editingParty by remember { mutableStateOf<PartyEntity?>(null) }
    var showWebPortalDialog by remember { mutableStateOf(false) }
    var showShareableDialog by remember { mutableStateOf(false) }
    var shareableInitialUsername by remember { mutableStateOf("") }

    var showTransactionSheet by remember { mutableStateOf(false) }
    var transactionPreselectedPartyId by remember { mutableStateOf<Long?>(null) }
    var transactionInitialType by remember { mutableStateOf(TransactionType.MONEY_RECEIVED) }

    val takenUsernames = remember(allParties) {
        allParties.map { it.party.username.lowercase() }.filter { it.isNotBlank() }.toSet()
    }

    if (showWebPortalDialog) {
        WebPortalDialog(
            state = webPortalState,
            onToggleServer = { enabled ->
                if (enabled) viewModel.startWebPortal() else viewModel.stopWebPortal()
            },
            onDismiss = { showWebPortalDialog = false }
        )
    }

    if (showShareableDialog) {
        ShareableViewDialog(
            parties = allParties,
            initialUsername = shareableInitialUsername,
            onGenerateSession = { username, ttl ->
                viewModel.generateShareableLink(username, ttl)
            },
            onOpenReadOnlyView = { session ->
                showShareableDialog = false
                viewModel.openShareableView(session)
            },
            onDismiss = { showShareableDialog = false }
        )
    }

    // Modal: Add or Edit Party
    if (showAddPartyDialog || editingParty != null) {
        AddEditPartyDialog(
            existingParty = editingParty,
            takenUsernames = takenUsernames,
            onDismiss = {
                showAddPartyDialog = false
                editingParty = null
            },
            onSave = { company, person, phone, address, role, username ->
                val target = editingParty
                if (target == null) {
                    viewModel.addParty(company, person, phone, address, role, username)
                } else {
                    viewModel.updateParty(target, company, person, phone, address, role, username)
                }
                showAddPartyDialog = false
                editingParty = null
            }
        )
    }

    // Modal: Record Transaction & Upload Bill/Voucher Proof
    if (showTransactionSheet) {
        RecordTransactionSheet(
            parties = allParties,
            preselectedPartyId = transactionPreselectedPartyId,
            initialType = transactionInitialType,
            currencySymbol = businessSettings.currencySymbol,
            onDismiss = { showTransactionSheet = false },
            onSubmit = { partyId, type, dateMillis, itemName, pcs, unitPrice, amount, notes, proofUri ->
                showTransactionSheet = false
                viewModel.recordTransaction(
                    partyId = partyId,
                    type = type,
                    transactionDateMillis = dateMillis,
                    itemName = itemName,
                    quantityPcs = pcs,
                    unitPrice = unitPrice,
                    amount = amount,
                    notes = notes,
                    pickedProofUri = proofUri
                )
            }
        )
    }

    // Modal: Auto-Generated Stacked Paper Slip "CASH PAYMENT RECEIPT"
    activeReceipt?.let { receiptItem ->
        CashPaymentReceiptDialog(
            item = receiptItem,
            businessSettings = businessSettings,
            onDismiss = { viewModel.dismissReceipt() }
        )
    }

    // Local Navigation Route: Simplified Read-Only Shareable View
    val activeShareState = shareableViewState
    if (activeShareState is ShareableViewUiState.Active) {
        ShareableViewScreen(
            state = activeShareState,
            businessSettings = businessSettings,
            onClose = { viewModel.closeShareableView() },
            onViewReceipt = { tx ->
                val summary = activeShareState.partySummary
                if (summary != null) {
                    viewModel.openReceipt(
                        TransactionWithParty(
                            transaction = tx,
                            party = summary.party,
                            partyCurrentNetBalance = summary.netBalance
                        )
                    )
                }
            }
        )
        return
    }

    // If a Party Profile is open, render the dedicated Party Profile & Ledger Screen
    val partyDetailSummary = selectedPartyDetail.first
    if (selectedPartyId != null && partyDetailSummary != null) {
        PartyProfileScreen(
            partySummary = partyDetailSummary,
            transactions = selectedPartyDetail.second,
            businessSettings = businessSettings,
            webPortalState = webPortalState,
            onBack = { viewModel.selectParty(null) },
            onEditParty = { editingParty = it },
            onDeleteParty = { id -> viewModel.deleteParty(id) },
            onRecordTransaction = { type ->
                transactionPreselectedPartyId = partyDetailSummary.party.id
                transactionInitialType = type
                showTransactionSheet = true
            },
            onViewReceipt = { tx ->
                viewModel.openReceiptForPartyTransaction(tx, partyDetailSummary)
            },
            onDeleteTransaction = { txId ->
                viewModel.deleteTransaction(txId)
            },
            onOpenShareableView = { username ->
                shareableInitialUsername = username
                showShareableDialog = true
            }
        )
        return
    }

    // Main Top-Level Scaffold with 3-Tab NavigationBar
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentTab) {
                            TopTab.PARTIES -> "DZAED"
                            TopTab.CASH_FLOW -> "Monthly Cash Flow"
                            TopTab.RECEIPTS -> "Cash Payment Receipts"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            shareableInitialUsername = ""
                            showShareableDialog = true
                        },
                        modifier = Modifier.testTag("open_shareable_view_button")
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = "Shareable View & Deep-Link",
                            tint = EmeraldPrimary
                        )
                    }
                    IconButton(
                        onClick = { showWebPortalDialog = true },
                        modifier = Modifier.testTag("open_web_portal_button")
                    ) {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = "Chrome Lookup Portal",
                            tint = if (webPortalState.isRunning) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            val icon = when (tab) {
                                TopTab.PARTIES -> if (selected) Icons.Filled.Groups else Icons.Outlined.Groups
                                TopTab.CASH_FLOW -> if (selected) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
                                TopTab.RECEIPTS -> if (selected) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong
                            }
                            Icon(icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == TopTab.PARTIES) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingParty = null
                        showAddPartyDialog = true
                    },
                    icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                    text = { Text("Add New Party") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_party")
                )
            } else if (allParties.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        transactionPreselectedPartyId = null
                        transactionInitialType = TransactionType.MONEY_RECEIVED
                        showTransactionSheet = true
                    },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("New Entry") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_new_entry")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                TopTab.PARTIES -> {
                    PartiesScreen(
                        allParties = allParties,
                        filteredParties = filteredParties,
                        searchQuery = searchQuery,
                        balanceFilter = balanceFilter,
                        businessSettings = businessSettings,
                        onSearchChange = viewModel::setSearchQuery,
                        onFilterChange = viewModel::setBalanceFilter,
                        onAddNewPartyClick = {
                            editingParty = null
                            showAddPartyDialog = true
                        },
                        onQuickTransactionClick = {
                            transactionPreselectedPartyId = null
                            transactionInitialType = TransactionType.MONEY_RECEIVED
                            showTransactionSheet = true
                        },
                        onPartyClick = { id -> viewModel.selectParty(id) }
                    )
                }
                TopTab.CASH_FLOW -> {
                    CashFlowScreen(
                        filter = cashFlowFilter,
                        summary = monthlySummary,
                        businessSettings = businessSettings,
                        onPreviousMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        onToggleAllMonths = viewModel::toggleAllMonths,
                        onSelectDateFilter = viewModel::setSpecificDateFilter,
                        onSelectTypeFilter = viewModel::setTransactionTypeFilter,
                        onViewReceipt = viewModel::openReceipt
                    )
                }
                TopTab.RECEIPTS -> {
                    ReceiptsAndSettingsScreen(
                        receipts = receipts,
                        searchQuery = receiptSearch,
                        businessSettings = businessSettings,
                        onSearchChange = viewModel::setReceiptSearchQuery,
                        onUpdateSettings = viewModel::updateBusinessSettings,
                        onOpenReceipt = viewModel::openReceipt
                    )
                }
            }
        }
    }
}
