package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LedgerTransactionEntity
import com.example.data.model.BusinessSettings
import com.example.data.model.TransactionType
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InventoryAmber
import com.example.ui.theme.InventoryAmberBg
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PayableRed
import com.example.ui.theme.PayableRedBg
import com.example.ui.theme.ReceivableGreen
import com.example.ui.theme.ReceivableGreenBg
import com.example.ui.viewmodel.ShareableViewUiState
import com.example.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareableViewScreen(
    state: ShareableViewUiState.Active,
    businessSettings: BusinessSettings,
    onClose: () -> Unit,
    onViewReceipt: (LedgerTransactionEntity) -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current

    fun copyDeepLink() {
        if (state.deepLinkUrl.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Shareable Deep-Link", state.deepLinkUrl))
        Toast.makeText(context, "Temporary deep-link copied", Toast.LENGTH_SHORT).show()
    }

    fun shareDeepLink() {
        if (state.deepLinkUrl.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "View read-only ledger history for @${state.username} with ${businessSettings.businessName}:\n${state.deepLinkUrl}"
            )
        }
        context.startActivity(Intent.createChooser(intent, "Share Temporary Ledger Link"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Shareable View",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Read-Only Statement • @${state.username}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("shareable_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Exit shareable view"
                        )
                    }
                },
                actions = {
                    if (!state.notFound && !state.isExpired && state.deepLinkUrl.isNotBlank()) {
                        IconButton(
                            onClick = ::copyDeepLink,
                            modifier = Modifier.testTag("shareable_copy_link_button")
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy deep-link")
                        }
                        IconButton(
                            onClick = ::shareDeepLink,
                            modifier = Modifier.testTag("shareable_send_link_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share deep-link")
                        }
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
            if (state.isExpired) {
                ShareableNoticeCard(
                    title = "Temporary Link Expired",
                    message = "This shareable view link for @${state.username} has expired. Request a new temporary deep-link to view updated history.",
                    isError = true,
                    onClose = onClose
                )
            } else if (state.notFound || state.partySummary == null) {
                ShareableNoticeCard(
                    title = "Party Not Found",
                    message = "No ledger record matches username \"@${state.username}\". Verify the username and try again.",
                    isError = true,
                    onClose = onClose
                )
            } else {
                val summary = state.partySummary
                val party = summary.party
                val net = summary.netBalance
                val netColor = when {
                    net > 0.005 -> ReceivableGreen
                    net < -0.005 -> PayableRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Read-Only Header Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0B3B2D), Color(0xFF0F4C3A))
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.Lock,
                                                contentDescription = null,
                                                tint = Color(0xFFA7F3D0),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "READ-ONLY VIEW",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFA7F3D0),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    state.expiresAtMillis?.let { exp ->
                                        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                            .format(Date(exp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Valid until $timeStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = party.companyName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${party.personName} • ${party.phoneNumber}" +
                                        if (party.address.isNotBlank()) "\n${party.address}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.14f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Outlined.AlternateEmail,
                                                contentDescription = null,
                                                tint = Color(0xFF6EE7B7),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = party.username,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = Color(0xFF6EE7B7)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Business: ${businessSettings.businessName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Simplified Balance & Pcs Summary Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Current Net Balance",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = Formatters.formatSignedBalance(
                                                net,
                                                businessSettings.currencySymbol
                                            ),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = netColor
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Pieces (Pcs)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${summary.totalPcsGiven} Given / ${summary.totalPcsTaken} Taken",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Receivable: ${Formatters.formatMoney(summary.totalReceivable, businessSettings.currencySymbol)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ReceivableGreen
                                    )
                                    Text(
                                        text = "Payable: ${Formatters.formatMoney(summary.totalPayable, businessSettings.currencySymbol)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PayableRed
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Historical Transactions (${state.transactions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (state.transactions.isEmpty()) {
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
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No historical transactions yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.transactions, key = { it.id }) { tx ->
                            ReadOnlyTransactionCard(
                                tx = tx,
                                currencySymbol = businessSettings.currencySymbol,
                                onViewReceipt = { onViewReceipt(tx) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyTransactionCard(
    tx: LedgerTransactionEntity,
    currencySymbol: String,
    onViewReceipt: () -> Unit
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
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

            if (tx.quantityPcs > 0 || tx.itemName.isNotBlank()) {
                val itemText = buildString {
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
                    text = itemText,
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
                Text(
                    text = tx.receiptNo,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onViewReceipt,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Receipt", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ShareableNoticeCard(
    title: String,
    message: String,
    isError: Boolean,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.widthIn(max = 460.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                1.dp,
                if (isError) PayableRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) PayableRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Return to Ledger")
                }
            }
        }
    }
}
