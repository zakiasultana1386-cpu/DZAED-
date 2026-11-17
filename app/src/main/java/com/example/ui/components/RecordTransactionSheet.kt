package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.PartySummary
import com.example.data.model.TransactionType
import com.example.ui.theme.InventoryAmber
import com.example.ui.theme.PayableRed
import com.example.ui.theme.ReceivableGreen
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordTransactionSheet(
    parties: List<PartySummary>,
    preselectedPartyId: Long?,
    initialType: TransactionType = TransactionType.MONEY_RECEIVED,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSubmit: (
        partyId: Long,
        type: TransactionType,
        dateMillis: Long,
        itemName: String,
        quantityPcs: Int,
        unitPrice: Double,
        amount: Double,
        notes: String,
        proofUri: Uri?
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedPartyId by remember(preselectedPartyId, parties) {
        mutableStateOf(preselectedPartyId ?: parties.firstOrNull()?.party?.id)
    }
    var partyDropdownExpanded by remember { mutableStateOf(false) }

    var selectedType by remember { mutableStateOf(initialType) }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var itemName by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var unitPriceText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var amountManuallyEdited by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var pickedProofUri by remember { mutableStateOf<Uri?>(null) }
    var showError by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedProofUri = uri
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pickedProofUri = uri
    }

    fun recalculateTotal(newQty: String, newUnit: String) {
        if (!amountManuallyEdited) {
            val q = newQty.toIntOrNull() ?: 0
            val u = newUnit.toDoubleOrNull() ?: 0.0
            if (q > 0 && u > 0.0) {
                val total = q * u
                amountText = if (total % 1.0 == 0.0) total.toLong().toString() else String.format("%.2f", total)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Record Transaction",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Party Selector (dropdown if multiple or not locked)
            val selectedParty = parties.firstOrNull { it.party.id == selectedPartyId }
            if (preselectedPartyId == null) {
                ExposedDropdownMenuBox(
                    expanded = partyDropdownExpanded,
                    onExpandedChange = { partyDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedParty?.let { "${it.party.companyName} (${it.party.personName})" }
                            ?: "Select a Party",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Customer / Supplier *") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded)
                        },
                        isError = showError && selectedPartyId == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = partyDropdownExpanded,
                        onDismissRequest = { partyDropdownExpanded = false }
                    ) {
                        parties.forEach { summary ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(summary.party.companyName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            summary.party.personName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedPartyId = summary.party.id
                                    partyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else if (selectedParty != null) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedParty.party.companyName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedParty.party.personName} • ${selectedParty.party.phoneNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Transaction Type Selector
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransactionType.entries.forEach { type ->
                    val accent = when (type) {
                        TransactionType.MONEY_RECEIVED -> ReceivableGreen
                        TransactionType.MONEY_GIVEN -> PayableRed
                        TransactionType.PRODUCT_GIVEN -> MaterialTheme.colorScheme.primary
                        TransactionType.PRODUCT_TAKEN -> InventoryAmber
                    }
                    val selected = selectedType == type
                    FilterChip(
                        selected = selected,
                        onClick = { selectedType = type },
                        label = { Text(type.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.14f),
                            selectedLabelColor = accent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = accent
                        )
                    )
                }
            }

            // Date Picker Row
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .testTag("transaction_date_picker"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = "Select date",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Transaction Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatDateSlash(dateMillis),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Product & Quantity (Pcs) Section
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = {
                    Text(
                        if (selectedType.isProductFlow) "Product / Item Details"
                        else "Item / Reference (Optional)"
                    )
                },
                placeholder = { Text("e.g., Cotton Shirts, Denim Batch #4") },
                leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_item_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() }
                        qtyText = cleaned
                        recalculateTotal(cleaned, unitPriceText)
                    },
                    label = { Text("Pieces (Pcs)") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("transaction_pcs_input")
                )

                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() || it == '.' }
                        unitPriceText = cleaned
                        recalculateTotal(qtyText, cleaned)
                    },
                    label = { Text("Unit Price ($currencySymbol)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("transaction_unit_price_input")
                )
            }

            val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
            val amountError = showError && parsedAmount <= 0.0

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isDigit() || it == '.' }
                    amountText = cleaned
                    amountManuallyEdited = true
                },
                label = { Text("Total Amount ($currencySymbol) *") },
                placeholder = { Text("0.00") },
                isError = amountError,
                supportingText = if (amountError) {
                    { Text("Please enter a valid amount greater than 0") }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_amount_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Payment Notes / Purpose") },
                placeholder = { Text("e.g., Advance cash payment, Invoice #204 settlement") },
                maxLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_notes_input")
            )

            // Bill / Voucher Proof Upload (Zero-Permission Photo Picker & OpenDocument)
            Text(
                text = "Bill / Voucher Proof (Optional)",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Image")
                }

                OutlinedButton(
                    onClick = {
                        documentPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach File")
                }
            }

            if (pickedProofUri != null) {
                val mime = context.contentResolver.getType(pickedProofUri!!) ?: ""
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (mime.startsWith("image/")) {
                                AsyncImage(
                                    model = pickedProofUri,
                                    contentDescription = "Selected bill preview",
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Proof attached",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (mime.startsWith("image/")) "Voucher Image" else "Document Attachment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { pickedProofUri = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove attachment")
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val pId = selectedPartyId
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (pId == null || amount <= 0.0) {
                        showError = true
                    } else {
                        onSubmit(
                            pId,
                            selectedType,
                            dateMillis,
                            itemName,
                            qtyText.toIntOrNull() ?: 0,
                            unitPriceText.toDoubleOrNull() ?: 0.0,
                            amount,
                            notes,
                            pickedProofUri
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_transaction_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save & Generate Cash Receipt", fontWeight = FontWeight.Bold)
            }
        }
    }
}
