package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.data.model.BusinessSettings
import com.example.data.model.MonthlyCashFlowSummary
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithParty
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PayableRed
import com.example.ui.theme.ReceivableGreen
import com.example.ui.viewmodel.CashFlowFilter
import com.example.util.Formatters
import com.example.util.ReceiptExportHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    filter: CashFlowFilter,
    summary: MonthlyCashFlowSummary,
    businessSettings: BusinessSettings,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleAllMonths: (Boolean) -> Unit,
    onSelectDateFilter: (Long?) -> Unit,
    onSelectTypeFilter: (TransactionType?) -> Unit,
    onViewReceipt: (TransactionWithParty) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = pendingExportFile
        if (uri != null && src != null) {
            scope.launch {
                val ok = ReceiptExportHelper.copyFileToUri(context, src, uri)
                Toast.makeText(
                    context,
                    if (ok) "Monthly PDF report saved" else "Failed to save report",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val src = pendingExportFile
        if (uri != null && src != null) {
            scope.launch {
                val ok = ReceiptExportHelper.copyFileToUri(context, src, uri)
                Toast.makeText(
                    context,
                    if (ok) "Monthly CSV report saved" else "Failed to save CSV",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filter.selectedDayMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onSelectDateFilter(state.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month Navigator & Export Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onPreviousMonth) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                        contentDescription = "Previous month"
                                    )
                                }
                                Text(
                                    text = filter.periodLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = onNextMonth) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = "Next month"
                                    )
                                }
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { showExportMenu = true },
                                    modifier = Modifier.testTag("export_monthly_report_button")
                                ) {
                                    Icon(
                                        Icons.Outlined.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export")
                                }
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Save Monthly PDF Report") },
                                        leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                                        onClick = {
                                            showExportMenu = false
                                            scope.launch {
                                                val file = ReceiptExportHelper.createMonthlyReportPdfFile(
                                                    context,
                                                    filter.periodLabel,
                                                    summary,
                                                    businessSettings
                                                )
                                                pendingExportFile = file
                                                savePdfLauncher.launch(file.name)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Save Monthly CSV Report") },
                                        leadingIcon = { Icon(Icons.Outlined.TableChart, null) },
                                        onClick = {
                                            showExportMenu = false
                                            scope.launch {
                                                val file = ReceiptExportHelper.createMonthlyReportCsvFile(
                                                    context,
                                                    filter.periodLabel,
                                                    summary,
                                                    businessSettings
                                                )
                                                pendingExportFile = file
                                                saveCsvLauncher.launch(file.name)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Monthly PDF") },
                                        leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                        onClick = {
                                            showExportMenu = false
                                            scope.launch {
                                                val file = ReceiptExportHelper.createMonthlyReportPdfFile(
                                                    context,
                                                    filter.periodLabel,
                                                    summary,
                                                    businessSettings
                                                )
                                                ReceiptExportHelper.shareFile(
                                                    context,
                                                    file,
                                                    "application/pdf",
                                                    "Monthly Report (${filter.periodLabel})"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Date & All Months quick toggles
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = filter.allMonths,
                                onClick = { onToggleAllMonths(!filter.allMonths) },
                                label = { Text("All Months") }
                            )
                            FilterChip(
                                selected = filter.selectedDayMillis != null,
                                onClick = { showDatePicker = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        filter.selectedDayMillis?.let { Formatters.formatDateSlash(it) }
                                            ?: "Filter by Date"
                                    )
                                }
                            )
                            if (filter.selectedDayMillis != null) {
                                IconButton(
                                    onClick = { onSelectDateFilter(null) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear date filter")
                                }
                            }
                        }
                    }
                }
            }

            // Monthly Income, Expense & Units Sold KPIs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CashFlowKpiCard(
                        title = "Monthly Income",
                        amount = Formatters.formatMoney(summary.totalIncome, businessSettings.currencySymbol),
                        subtitle = "Cash Received",
                        accent = ReceivableGreen,
                        icon = {
                            Icon(
                                Icons.Outlined.TrendingUp,
                                contentDescription = null,
                                tint = ReceivableGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CashFlowKpiCard(
                        title = "Monthly Expense",
                        amount = Formatters.formatMoney(summary.totalExpense, businessSettings.currencySymbol),
                        subtitle = "Cash Given Out",
                        accent = PayableRed,
                        icon = {
                            Icon(
                                Icons.Outlined.TrendingDown,
                                contentDescription = null,
                                tint = PayableRed,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val netColor = if (summary.netCashFlow >= 0) ReceivableGreen else PayableRed
                    CashFlowKpiCard(
                        title = "Net Cash Flow",
                        amount = (if (summary.netCashFlow >= 0) "+" else "-") +
                            Formatters.formatMoney(
                                kotlin.math.abs(summary.netCashFlow),
                                businessSettings.currencySymbol
                            ),
                        subtitle = "Income - Expense",
                        accent = netColor,
                        icon = {
                            Icon(
                                Icons.Outlined.TrendingUp,
                                contentDescription = null,
                                tint = netColor,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CashFlowKpiCard(
                        title = "Units Delivered",
                        amount = "${summary.totalPcsDelivered} Pcs",
                        subtitle = "Value: ${Formatters.formatMoney(summary.totalSalesValue, businessSettings.currencySymbol)}",
                        accent = EmeraldPrimary,
                        icon = {
                            Icon(
                                Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Transaction Type Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter.transactionType == null,
                            onClick = { onSelectTypeFilter(null) },
                            label = { Text("All Types") }
                        )
                    }
                    items(TransactionType.entries) { type ->
                        FilterChip(
                            selected = filter.transactionType == type,
                            onClick = {
                                onSelectTypeFilter(if (filter.transactionType == type) null else type)
                            },
                            label = { Text(type.label) }
                        )
                    }
                }
            }

            // Cash Flow & Activity Entries
            if (summary.entries.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No entries for ${filter.periodLabel}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Switch months, clear filters, or record a new transaction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(summary.entries, key = { it.transaction.id }) { item ->
                    TransactionHistoryCard(
                        tx = item.transaction,
                        currencySymbol = businessSettings.currencySymbol,
                        partyLabel = "${item.party.companyName} • ${item.party.personName}",
                        onViewReceipt = { onViewReceipt(item) }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun CashFlowKpiCard(
    title: String,
    amount: String,
    subtitle: String,
    accent: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                icon()
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
