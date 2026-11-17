package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.PartyEntity
import com.example.data.model.PartyRole
import com.example.util.UsernameHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditPartyDialog(
    existingParty: PartyEntity? = null,
    takenUsernames: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSave: (
        companyName: String,
        personName: String,
        phone: String,
        address: String,
        role: PartyRole,
        username: String
    ) -> Unit
) {
    var companyName by remember(existingParty) { mutableStateOf(existingParty?.companyName ?: "") }
    var personName by remember(existingParty) { mutableStateOf(existingParty?.personName ?: "") }
    var phoneNumber by remember(existingParty) { mutableStateOf(existingParty?.phoneNumber ?: "") }
    var address by remember(existingParty) { mutableStateOf(existingParty?.address ?: "") }
    var usernameInput by remember(existingParty) { mutableStateOf(existingParty?.username ?: "") }
    var usernameManuallyEdited by remember(existingParty) { mutableStateOf(existingParty != null) }
    var selectedRole by remember(existingParty) {
        mutableStateOf(
            existingParty?.let { PartyRole.fromRaw(it.partyRole) } ?: PartyRole.CUSTOMER
        )
    }

    val normalizedExisting = existingParty?.username?.lowercase()
    val sanitizedUsername = UsernameHelper.sanitizeUsername(usernameInput)
    val isDuplicateUsername = sanitizedUsername.isNotEmpty() &&
        sanitizedUsername != normalizedExisting &&
        takenUsernames.any { it.equals(sanitizedUsername, ignoreCase = true) }

    var showValidationErrors by remember { mutableStateOf(false) }
    val companyError = showValidationErrors && companyName.isBlank()
    val personError = showValidationErrors && personName.isBlank()
    val phoneError = showValidationErrors && phoneNumber.isBlank()
    val usernameFormatError = showValidationErrors && !UsernameHelper.isValidUsername(sanitizedUsername)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (existingParty == null) "Add New Party" else "Edit Party Details",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Register a customer or supplier with a unique username so they can look up their full ledger history in Chrome.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Party Relationship",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PartyRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { newName ->
                        companyName = newName
                        if (!usernameManuallyEdited) {
                            usernameInput = UsernameHelper.suggestBaseUsername(newName)
                        }
                    },
                    label = { Text("Company / Shop Name *") },
                    placeholder = { Text("e.g., Rahman Textiles") },
                    leadingIcon = { Icon(Icons.Outlined.Business, contentDescription = null) },
                    isError = companyError,
                    supportingText = if (companyError) {
                        { Text("Company or shop name is required") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_company_input")
                )

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { raw ->
                        usernameManuallyEdited = true
                        usernameInput = raw.lowercase().replace(" ", "_")
                    },
                    label = { Text("Unique Username (for Chrome Lookup) *") },
                    placeholder = { Text("e.g., rahman_textiles") },
                    leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
                    isError = isDuplicateUsername || usernameFormatError,
                    supportingText = {
                        when {
                            isDuplicateUsername -> Text("This username is already taken by another party.")
                            usernameFormatError -> Text("Use 3–30 lowercase letters, numbers, or underscores.")
                            else -> Text("Party can paste @$sanitizedUsername in Chrome portal to view history.")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_username_input")
                )

                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person's Full Name *") },
                    placeholder = { Text("e.g., Tariqul Islam") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    isError = personError,
                    supportingText = if (personError) {
                        { Text("Person's full name is required") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_person_input")
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number *") },
                    placeholder = { Text("e.g., +880 1711-000000") },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                    isError = phoneError,
                    supportingText = if (phoneError) {
                        { Text("Phone number is required") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_phone_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    placeholder = { Text("Shop no, street, city") },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_address_input")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_party_button")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val effectiveUsername = sanitizedUsername.ifBlank {
                                UsernameHelper.suggestBaseUsername(companyName)
                            }
                            if (
                                companyName.isBlank() ||
                                personName.isBlank() ||
                                phoneNumber.isBlank() ||
                                isDuplicateUsername ||
                                !UsernameHelper.isValidUsername(effectiveUsername)
                            ) {
                                showValidationErrors = true
                            } else {
                                onSave(
                                    companyName,
                                    personName,
                                    phoneNumber,
                                    address,
                                    selectedRole,
                                    effectiveUsername
                                )
                            }
                        },
                        modifier = Modifier.testTag("save_party_button")
                    ) {
                        Text(if (existingParty == null) "Save Party" else "Update Party")
                    }
                }
            }
        }
    }
}
