package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PartySummary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.ShareableLinkSession
import com.example.util.UsernameHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareableViewDialog(
    parties: List<PartySummary>,
    initialUsername: String = "",
    onGenerateSession: (username: String, ttlMinutes: Long) -> ShareableLinkSession,
    onOpenReadOnlyView: (ShareableLinkSession) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var usernameInput by remember(initialUsername) {
        mutableStateOf(
            initialUsername.ifBlank { parties.firstOrNull()?.party?.username.orEmpty() }
        )
    }
    var selectedTtlMinutes by remember { mutableLongStateOf(60L) }
    var generatedSession by remember { mutableStateOf<ShareableLinkSession?>(null) }

    val cleanInput = UsernameHelper.sanitizeUsername(usernameInput)
    val matchedParty = parties.firstOrNull {
        it.party.username.equals(cleanInput, ignoreCase = true)
    }

    val ttlOptions = listOf(
        15L to "15 Min",
        60L to "1 Hour",
        1440L to "24 Hours"
    )

    fun copyText(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = EmeraldPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Shareable View",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Enter a party username to generate a temporary deep-link or launch their simplified read-only transaction history view.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = {
                        usernameInput = it
                        generatedSession = null
                    },
                    label = { Text("Party Username") },
                    placeholder = { Text("e.g., rahman_textiles") },
                    leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
                    singleLine = true,
                    supportingText = {
                        if (matchedParty != null) {
                            Text("Matched: ${matchedParty.party.companyName} (${matchedParty.party.personName})")
                        } else if (cleanInput.isNotEmpty()) {
                            Text("No local party found with @$cleanInput")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shareable_username_input")
                )

                if (parties.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        parties.take(5).forEach { summary ->
                            FilterChip(
                                selected = cleanInput.equals(summary.party.username, ignoreCase = true),
                                onClick = {
                                    usernameInput = summary.party.username
                                    generatedSession = null
                                },
                                label = {
                                    Text(
                                        "@${summary.party.username}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Temporary Link Expiry",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ttlOptions.forEach { (minutes, label) ->
                        FilterChip(
                            selected = selectedTtlMinutes == minutes,
                            onClick = {
                                selectedTtlMinutes = minutes
                                generatedSession = null
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (cleanInput.isNotEmpty()) {
                                generatedSession = onGenerateSession(cleanInput, selectedTtlMinutes)
                            }
                        },
                        enabled = cleanInput.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("generate_deeplink_button")
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Deep-Link")
                    }

                    Button(
                        onClick = {
                            if (cleanInput.isNotEmpty()) {
                                val session = generatedSession ?: onGenerateSession(
                                    cleanInput,
                                    selectedTtlMinutes
                                )
                                onOpenReadOnlyView(session)
                            }
                        },
                        enabled = cleanInput.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_readonly_view_button")
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open View")
                    }
                }

                generatedSession?.let { session ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Temporary Deep-Link Ready",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = session.deepLinkUrl,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (session.webShareUrl.isNotBlank()) {
                                Text(
                                    text = "Browser Link: ${session.webShareUrl}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { copyText("Deep-Link", session.deepLinkUrl) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val shareText = buildString {
                                            append("Temporary read-only ledger for @${session.username}:\n")
                                            append(session.deepLinkUrl)
                                            if (session.webShareUrl.isNotBlank()) {
                                                append("\nBrowser: ${session.webShareUrl}")
                                            }
                                        }
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Share Temporary View")
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Share, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
