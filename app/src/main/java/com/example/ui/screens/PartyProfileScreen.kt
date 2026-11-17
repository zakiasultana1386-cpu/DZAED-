package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LedgerTransactionEntity
import com.example.data.local.PartyEntity
import com.example.data.model.BusinessSettings
import com.example.data.model.PartyRole
import com.example.data.model.PartySummary
import com.example.data.model.TransactionType
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InventoryAmber
import com.example.ui.theme.InventoryAmberBg
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PayableRed
import com.example.ui.theme.PayableRedBg
import com.example.ui.theme.ReceivableGreen
import com.example.ui.theme.ReceivableGreenBg
import com.example.ui.viewmodel.WebPortalState
import com.example.util.Formatters
import com.example.util.ReceiptExportHelper
import kotlinx.coroutines.launch

private enum class ProfileHistoryFilter(val label: String) {
    ALL("All History"),
    PRODUCTS("Product (Pcs) Ledger"),
    CASH("Cash Only")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyProfileScreen(
    partySummary: PartySummary,
    transactions: List<LedgerTransactionEntity>,
    businessSettings: BusinessSettings,
    webPortalState: WebPortalState,
    onBack: () -> Unit,
    onEditParty: (PartyEntity) -> Unit,
    onDeleteParty: (Long) -> Unit,
    onRecordTransaction: (TransactionType) -> Unit,
    onViewReceipt: (LedgerTransactionEntity) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onOpenShareableView: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val party = partySummary.party
    var historyFilter by remember { mutableStateOf(ProfileHistoryFilter.ALL) }
    var showDeletePartyConfirm by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    val filteredTransactions = remember(transactions, historyFilter) {
        when (historyFilter) {
            ProfileHistoryFilter.ALL -> transactions
            ProfileHistoryFilter.PRODUCTS -> transactions.filter {
                val t = TransactionType.fromRaw(it.transactionType)
                t.isProductFlow || it.quantityPcs > 0
            }
            ProfileHistoryFilter.CASH -> transactions.filter {
                val t = TransactionType.fromRaw(it.transactionType)
                !t.isProductFlow
            }
        }
    }

    if (showDeletePartyConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePartyConfirm = false },
            title = { Text("Delete ${party.companyName}?") },
            text = {
                Text("This will permanently remove this party and all ${transactions.size} ledger transactions.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePartyConfirm = false
                        onDeleteParty(party.id)
                    }
                ) {
                    Text("Delete", color = PayableRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePartyConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = party.companyName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${PartyRole.fromRaw(party.partyRole).label} • @${party.username}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to parties")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEditParty(party) },
                        modifier = Modifier.testTag("edit_party_button")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit party")
                    }
                    IconButton(
                        onClick = { showDeletePartyConfirm = true },
                        modifier = Modifier.testTag("delete_party_button")
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete party")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Contact & Profile Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = party.personName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${party.phoneNumber}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Call")
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = party.phoneNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }

                            if (party.address.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = party.address,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Unique Username & Chrome Portal Lookup Card
                item {
                    val partyUrl = webPortalState.partyUrl(party.username)
                    val partyLocalUrl = webPortalState.partyLocalUrl(party.username)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Language,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Chrome Lookup & Unique Username",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.AlternateEmail,
                                            contentDescription = null,
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = party.username,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (webPortalState.isRunning && partyUrl.isNotBlank()) {
                                    "Share username \"${party.username}\" or link below so they can paste it into Chrome to view all their history:"
                                } else {
                                    "Share username \"${party.username}\" or export an HTML web ledger file for Chrome."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (webPortalState.isRunning && partyUrl.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = partyUrl,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = EmeraldPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { copyToClipboard("Username", party.username) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("copy_party_username_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy ID", maxLines = 1)
                                }
                                if (webPortalState.isRunning && partyUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { copyToClipboard("Chrome Portal Link", partyUrl) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("copy_party_link_button"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copy Link", maxLines = 1)
                                    }
                                    Button(
                                        onClick = {
                                            ReceiptExportHelper.openUrlInBrowser(
                                                context,
                                                partyLocalUrl.ifBlank { partyUrl }
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("open_party_chrome_button"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Chrome", maxLines = 1)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val htmlFile = ReceiptExportHelper.createPartyWebPortalHtmlFile(
                                            context = context,
                                            summary = partySummary,
                                            transactions = transactions,
                                            settings = businessSettings
                                        )
                                        ReceiptExportHelper.shareFile(
                                            context = context,
                                            file = htmlFile,
                                            mimeType = "text/html",
                                            title = "Share Chrome Ledger Statement (@${party.username})"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("share_party_html_button")
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share Offline HTML Statement (Opens in Any Browser)")
                            }

                            Button(
                                onClick = { onOpenShareableView(party.username) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("party_shareable_view_button")
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Read-Only Shareable View")
                            }
                        }
                    }
                }

                // Total Receivable, Total Payable, Net Balance Strip
                item {
                    val net = partySummary.netBalance
                    val netColor = when {
                        net > 0.005 -> ReceivableGreen
                        net < -0.005 -> PayableRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BalanceStatCard(
                            title = "Total Receivable",
                            amount = Formatters.formatMoney(
                                partySummary.totalReceivable,
                                businessSettings.currencySymbol
                            ),
                            caption = "They owe me",
                            accent = ReceivableGreen,
                            modifier = Modifier.weight(1f)
                        )
                        BalanceStatCard(
                            title = "Total Payable",
                            amount = Formatters.formatMoney(
                                partySummary.totalPayable,
                                businessSettings.currencySymbol
                            ),
                            caption = "I owe them",
                            accent = PayableRed,
                            modifier = Modifier.weight(1f)
                        )
                        BalanceStatCard(
                            title = "Net Balance",
                            amount = Formatters.formatMoney(
                                kotlin.math.abs(net),
                                businessSettings.currencySymbol
                            ),
                            caption = when {
                                net > 0.005 -> "Receivable"
                                net < -0.005 -> "Payable"
                                else -> "Settled"
                            },
                            accent = netColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Product / Quantity (Pcs) Ledger Summary Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Product & Quantity (Pcs) Ledger",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PcsStatColumn(
                                    label = "Total Pcs Given",
                                    value = "${partySummary.totalPcsGiven} Pcs",
                                    sub = Formatters.formatMoney(
                                        partySummary.totalProductGivenValue,
                                        businessSettings.currencySymbol
                                    )
                                )
                                PcsStatColumn(
                                    label = "Total Pcs Taken",
                                    value = "${partySummary.totalPcsTaken} Pcs",
                                    sub = Formatters.formatMoney(
                                        partySummary.totalProductTakenValue,
                                        businessSettings.currencySymbol
                                    )
                                )
                                PcsStatColumn(
                                    label = "Cash In / Out",
                                    value = "+${Formatters.formatMoney(partySummary.totalMoneyReceived, businessSettings.currencySymbol)}",
                                    sub = "-${Formatters.formatMoney(partySummary.totalMoneyGiven, businessSettings.currencySymbol)}"
                                )
                            }
                        }
                    }
                }

                // Prominent Record Transaction Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onRecordTransaction(TransactionType.MONEY_RECEIVED) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_record_transaction_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Record New Transaction / Payment", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onRecordTransaction(TransactionType.PRODUCT_GIVEN) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Give Pcs", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onRecordTransaction(TransactionType.MONEY_RECEIVED) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cash In", color = ReceivableGreen, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onRecordTransaction(TransactionType.MONEY_GIVEN) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cash Out", color = PayableRed, maxLines = 1)
                            }
                        }
                    }
                }

                // History Filter Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileHistoryFilter.entries.forEach { tab ->
                            FilterChip(
                                selected = historyFilter == tab,
                                onClick = { historyFilter = tab },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                // Chronological History List
                if (filteredTransactions.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "No entries recorded yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Record product pieces (Pcs) given/taken or cash received/given above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        TransactionHistoryCard(
                            tx = tx,
                            currencySymbol = businessSettings.currencySymbol,
                            onViewReceipt = { onViewReceipt(tx) },
                            onDelete = { onDeleteTransaction(tx.id) }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun BalanceStatCard(
    title: String,
    amount: String,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun PcsStatColumn(label: String, value: String, sub: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TransactionHistoryCard(
    tx: LedgerTransactionEntity,
    currencySymbol: String,
    partyLabel: String? = null,
    onViewReceipt: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val type = TransactionType.fromRaw(tx.transactionType)
    val (badgeBg, badgeColor) = when (type) {
        TransactionType.MONEY_RECEIVED -> ReceivableGreenBg to ReceivableGreen
        TransactionType.MONEY_GIVEN -> PayableRedBg to PayableRed
        TransactionType.PRODUCT_GIVEN -> MaterialTheme.colorScheme.primaryContainer to EmeraldPrimary
        TransactionType.PRODUCT_TAKEN -> InventoryAmberBg to InventoryAmber
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = type.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                    Text(
                        text = Formatters.formatDateSlash(tx.transactionDateMillis),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = Formatters.formatExactMoney(tx.amount, currencySymbol),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }

            if (!partyLabel.isNullOrBlank()) {
                Text(
                    text = partyLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (tx.quantityPcs > 0 || tx.itemName.isNotBlank()) {
                val itemDesc = buildString {
                    if (tx.quantityPcs > 0) append("${tx.quantityPcs} Pcs")
                    if (tx.itemName.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(tx.itemName)
                    }
                    if (tx.unitPrice > 0.0) {
                        append(" (@ ${Formatters.formatMoney(tx.unitPrice, currencySymbol)}/pc)")
                    }
                }
                Text(
                    text = itemDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (tx.notes.isNotBlank()) {
                Text(
                    text = tx.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tx.receiptNo,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!tx.proofFilePath.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Outlined.AttachFile,
                                contentDescription = "Proof attached",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "Proof",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete entry",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onViewReceipt,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("view_receipt_${tx.id}")
                    ) {
                        Icon(
                            Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("View Receipt", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
