package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.model.BusinessSettings
import com.example.data.model.TransactionWithParty
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PayableRed
import com.example.ui.theme.ReceiptInk
import com.example.ui.theme.ReceiptInkMuted
import com.example.ui.theme.ReceiptPaper
import com.example.ui.theme.ReceiptPaperLayerBack
import com.example.ui.theme.ReceiptPaperLayerMid
import com.example.ui.theme.ReceiptRule
import com.example.ui.theme.ReceivableGreen
import com.example.util.Formatters
import com.example.util.ReceiptExportHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashPaymentReceiptDialog(
    item: TransactionWithParty,
    businessSettings: BusinessSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingPdfFile by remember { mutableStateOf<File?>(null) }
    var pendingPngFile by remember { mutableStateOf<File?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val src = pendingPdfFile
        if (destUri != null && src != null) {
            scope.launch {
                val ok = ReceiptExportHelper.copyFileToUri(context, src, destUri)
                Toast.makeText(
                    context,
                    if (ok) "Receipt PDF saved" else "Could not save PDF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destUri ->
        val src = pendingPngFile
        if (destUri != null && src != null) {
            scope.launch {
                val ok = ReceiptExportHelper.copyFileToUri(context, src, destUri)
                Toast.makeText(
                    context,
                    if (ok) "Receipt Image saved" else "Could not save image",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Receipt Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close receipt")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val pdf = ReceiptExportHelper.createReceiptPdfFile(
                                        context, item, businessSettings
                                    )
                                    ReceiptExportHelper.shareFile(
                                        context,
                                        pdf,
                                        "application/pdf",
                                        "Share Cash Payment Receipt"
                                    )
                                }
                            },
                            modifier = Modifier.testTag("receipt_share_button")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share receipt")
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ReceiptExportHelper.printReceipt(context, item, businessSettings)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_print_button")
                        ) {
                            Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Print")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val pdf = ReceiptExportHelper.createReceiptPdfFile(
                                        context, item, businessSettings
                                    )
                                    pendingPdfFile = pdf
                                    savePdfLauncher.launch("${item.transaction.receiptNo}.pdf")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_pdf_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val png = ReceiptExportHelper.createReceiptPngFile(
                                        context, item, businessSettings
                                    )
                                    pendingPngFile = png
                                    savePngLauncher.launch("${item.transaction.receiptNo}.png")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_image_button")
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Image")
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Classic Stacked Paper Slip Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .padding(top = 14.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Backmost layered paper edge
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .height(36.dp)
                            .offset(y = (-12).dp)
                            .zIndex(0f),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        color = ReceiptPaperLayerBack,
                        border = BorderStroke(1.dp, ReceiptRule.copy(alpha = 0.7f))
                    ) {}

                    // Middle layered paper edge
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(36.dp)
                            .offset(y = (-6).dp)
                            .zIndex(1f),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        color = ReceiptPaperLayerMid,
                        border = BorderStroke(1.dp, ReceiptRule.copy(alpha = 0.85f))
                    ) {}

                    // Main White Paper Receipt Slip
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(2f)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            color = ReceiptPaper,
                            border = BorderStroke(1.dp, ReceiptRule),
                            shadowElevation = 4.dp
                        ) {
                            ReceiptSlipBody(item = item, businessSettings = businessSettings)
                        }
                        // Classic serrated bottom edge
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                        ) {
                            val teethCount = 24
                            val toothWidth = size.width / teethCount
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                for (i in 0 until teethCount) {
                                    val xMid = i * toothWidth + toothWidth / 2f
                                    val xEnd = (i + 1) * toothWidth
                                    lineTo(xMid, size.height)
                                    lineTo(xEnd, 0f)
                                }
                                close()
                            }
                            drawPath(path = path, color = ReceiptPaper)
                        }
                    }
                }

                // Uploaded Bill / Voucher Preview below the receipt
                val proofPath = item.transaction.proofFilePath
                if (!proofPath.isNullOrBlank()) {
                    val proofFile = File(proofPath)
                    if (proofFile.exists()) {
                        Spacer(Modifier.height(20.dp))
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 540.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Attached Bill / Voucher Proof",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            ReceiptExportHelper.openProofFile(
                                                context,
                                                proofPath,
                                                item.transaction.proofMimeType
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.OpenInNew,
                                            contentDescription = "Open attached proof"
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                if (item.transaction.proofMimeType?.startsWith("image/") == true) {
                                    AsyncImage(
                                        model = proofFile,
                                        contentDescription = "Uploaded bill or voucher image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                ReceiptExportHelper.openProofFile(
                                                    context,
                                                    proofPath,
                                                    item.transaction.proofMimeType
                                                )
                                            },
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                ReceiptExportHelper.openProofFile(
                                                    context,
                                                    proofPath,
                                                    item.transaction.proofMimeType
                                                )
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Description, contentDescription = null)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = item.transaction.proofDisplayName ?: "Voucher Document",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptSlipBody(
    item: TransactionWithParty,
    businessSettings: BusinessSettings
) {
    val tx = item.transaction
    val party = item.party
    val type = item.type

    val partyDirectionLabel = if (type.isInflowToBusiness) "Received From" else "Paid / Given To"
    val handlerDirectionLabel = if (type.isInflowToBusiness) "Received By" else "Paid By"

    val quantityDisplay = when {
        tx.quantityPcs > 0 && tx.itemName.isNotBlank() -> {
            if (tx.unitPrice > 0.0) {
                "${tx.quantityPcs} Pcs — ${tx.itemName} (@ ${Formatters.formatMoney(tx.unitPrice, businessSettings.currencySymbol)})"
            } else {
                "${tx.quantityPcs} Pcs — ${tx.itemName}"
            }
        }
        tx.quantityPcs > 0 -> "${tx.quantityPcs} Pcs"
        tx.itemName.isNotBlank() -> tx.itemName
        else -> "Cash Settlement (${type.label})"
    }

    val amountDisplay = Formatters.formatExactMoney(tx.amount, businessSettings.currencySymbol)
    val balanceColor = when {
        item.partyCurrentNetBalance > 0.005 -> ReceivableGreen
        item.partyCurrentNetBalance < -0.005 -> PayableRed
        else -> ReceiptInkMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bold Centered Header
        Text(
            text = "CASH PAYMENT RECEIPT",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = EmeraldPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.1.sp
        )
        Text(
            text = businessSettings.businessName,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = ReceiptInkMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(thickness = 2.dp, color = EmeraldPrimary)

        // Top Metadata Row: Receipt No on left, Date on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1.25f)) {
                Text(
                    text = "Receipt No:",
                    color = ReceiptInkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = tx.receiptNo,
                    color = ReceiptInk,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                HorizontalDivider(thickness = 1.dp, color = ReceiptRule)
            }

            Column(modifier = Modifier.weight(0.95f)) {
                Text(
                    text = "Date:",
                    color = ReceiptInkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = Formatters.formatDateSlash(tx.transactionDateMillis),
                    color = ReceiptInk,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                HorizontalDivider(thickness = 1.dp, color = ReceiptRule)
            }
        }

        Spacer(Modifier.height(2.dp))

        // Vertical Form-Style Underlined Fields
        ReceiptUnderlinedField(
            label = partyDirectionLabel,
            value = "${party.companyName} (${party.personName})"
        )
        ReceiptUnderlinedField(
            label = "Quantity / Items",
            value = quantityDisplay
        )
        ReceiptUnderlinedField(
            label = "Amount",
            value = amountDisplay,
            valueColor = EmeraldPrimary,
            isMonospace = true,
            isBoldValue = true
        )
        ReceiptUnderlinedField(
            label = "Payment For",
            value = tx.notes.ifBlank { type.label }
        )
        ReceiptUnderlinedField(
            label = handlerDirectionLabel,
            value = businessSettings.businessName
        )
        ReceiptUnderlinedField(
            label = "Address",
            value = party.address.ifBlank { "—" }
        )
        ReceiptUnderlinedField(
            label = "Phone Number",
            value = party.phoneNumber.ifBlank { "—" },
            isMonospace = true
        )
        ReceiptUnderlinedField(
            label = "Remaining Due / Balance",
            value = Formatters.formatSignedBalance(
                item.partyCurrentNetBalance,
                businessSettings.currencySymbol
            ),
            valueColor = balanceColor,
            isBoldValue = true
        )
    }
}

@Composable
private fun ReceiptUnderlinedField(
    label: String,
    value: String,
    valueColor: Color = ReceiptInk,
    isMonospace: Boolean = false,
    isBoldValue: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "$label:",
                color = ReceiptInkMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = if (isBoldValue) 14.sp else 13.sp,
                fontWeight = if (isBoldValue) FontWeight.Bold else FontWeight.Medium,
                fontFamily = if (isMonospace) JetBrainsMonoFontFamily else MaterialTheme.typography.bodyMedium.fontFamily,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(5.dp))
        HorizontalDivider(thickness = 1.dp, color = ReceiptRule)
    }
}
