package com.tavexiq.wallet

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val IdCyan = Color(0xFF36D9F5)
private val IdPanel = Color(0xFF10243D)
private val IdMuted = Color(0xFF9CB0C8)
private val IdSilver = Color(0xFFE2EAF3)

@Composable
internal fun IdentityVaultScreen(context: Context) {
    val storage = remember { EncryptedIdentityStorage(context) }
    var documents by remember {
        mutableStateOf(runCatching { storage.load() }.getOrElse { emptyList() })
    }
    var storageFailed by remember {
        mutableStateOf(runCatching { storage.load() }.isFailure)
    }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<IdentityDocument?>(null) }
    var editingDocument by remember { mutableStateOf<IdentityDocument?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Identiteitsbewijs") }
    var issuer by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var backupMode by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupError by remember { mutableStateOf<String?>(null) }

    val exportIdentityBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val encrypted = IdentityBackupCodec.encode(documents, backupPassword.toCharArray())
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(encrypted) }
                    ?: throw IllegalStateException("Bestand kon niet worden geopend.")
            }.onSuccess {
                backupMessage = "Versleutelde ID-back-up gemaakt. Bewaar ook je back-upwachtwoord."
                backupError = null
            }.onFailure { backupError = it.message ?: "ID-back-up maken is mislukt." }
        }
        backupPassword = ""
        backupMode = null
    }
    val importIdentityBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val backup = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("Bestand kon niet worden gelezen.")
                require(backup.length <= 1_500_000) { "Het back-upbestand is te groot." }
                val restored = IdentityBackupCodec.decode(backup, backupPassword.toCharArray())
                storage.save(restored)
                restored
            }.onSuccess {
                documents = it
                storageFailed = false
                backupMessage = "ID-kluis hersteld: " + it.size + " documentvermelding(en)."
                backupError = null
            }.onFailure { backupError = it.message ?: "Terugzetten mislukt; de huidige kluis is behouden." }
        }
        backupPassword = ""
        backupMode = null
    }

    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Mijn ID-kluis", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = IdSilver)
                Text("${documents.size} opgeslagen document(en)", color = IdMuted)
            }
            Button(
                enabled = !storageFailed,
                onClick = {
                    editingDocument = null
                    name = ""; type = "Identiteitsbewijs"; issuer = ""; expiry = ""; notes = ""; error = null; showAdd = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = IdCyan, contentColor = Color(0xFF071426))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Toevoegen")
            }
        }
        Spacer(Modifier.size(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !storageFailed, modifier = Modifier.weight(1f),
                onClick = { backupPassword = ""; backupError = null; backupMode = "export" }) {
                Text("Versleutelde back-up")
            }
            OutlinedButton(enabled = !storageFailed, modifier = Modifier.weight(1f),
                onClick = { backupPassword = ""; backupError = null; backupMode = "import" }) {
                Text("Back-up herstellen")
            }
        }
        Text("ID-back-ups zijn met een eigen wachtwoord versleuteld. Bewaar dat wachtwoord veilig; het kan niet worden hersteld.", color = IdMuted, fontSize = 11.sp)
        backupMessage?.let { Text(it, color = IdCyan, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        backupError?.let { Text(it, color = Color(0xFFFF6B7A), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        Spacer(Modifier.size(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = IdPanel)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Privacy eerst", fontWeight = FontWeight.Bold, color = IdCyan)
                Spacer(Modifier.size(5.dp))
                Text(
                    "Deze eerste versie bewaart alleen documentmetadata, versleuteld op dit apparaat. Vul geen BSN, documentnummer, pincode of foto/scan in. Dit is geen officieel digitaal identiteitsbewijs; NFC-lezen en wettelijke acceptatie zijn nog niet beschikbaar.",
                    color = IdSilver,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        if (storageFailed) {
            Text(
                "De kluis kon niet veilig worden gelezen. Bewerken is uitgeschakeld zodat bestaande gegevens niet worden overschreven.",
                color = Color(0xFFFF6B7A),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else if (documents.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 26.dp)) {
                Icon(Icons.Default.Badge, contentDescription = null, tint = IdCyan, modifier = Modifier.size(42.dp))
                Spacer(Modifier.size(10.dp))
                Text("Je kluis is nog leeg", color = IdSilver, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Voeg een documentvermelding toe, bijvoorbeeld je paspoort of rijbewijs, met optionele vervaldatum.", color = IdMuted)
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                singleLine = true,
                label = { Text("Zoek in je ID-kluis") },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Zoekopdracht wissen")
                    }
                }
            )
            val visibleDocuments = documents.filter { document ->
                listOf(document.name, document.type, document.issuer, document.expiryDate, document.notes)
                    .any { it.contains(searchQuery.trim(), ignoreCase = true) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                if (visibleDocuments.isEmpty()) {
                    item { Text("Geen documenten gevonden. Probeer een andere zoekterm.", color = IdMuted, modifier = Modifier.padding(vertical = 20.dp)) }
                }
                items(visibleDocuments, key = { it.id }) { document ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = IdPanel)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(document.name, color = IdSilver, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(document.type, color = IdCyan, fontSize = 13.sp)
                                if (document.issuer.isNotBlank()) Text("Uitgever: ${document.issuer}", color = IdMuted, fontSize = 13.sp)
                                if (document.expiryDate.isNotBlank()) {
                                    val expiryStatus = IdentityDocumentRules.expiryStatus(document.expiryDate)
                                    val statusText = when (expiryStatus) {
                                        IdentityExpiryStatus.EXPIRED -> "VERLOPEN"
                                        IdentityExpiryStatus.EXPIRING_SOON -> "VERLOOPT BINNEN 30 DAGEN"
                                        IdentityExpiryStatus.VALID -> "GELDIG"
                                        IdentityExpiryStatus.INVALID -> "ONGELDIGE DATUM"
                                        IdentityExpiryStatus.NOT_SET -> ""
                                    }
                                    val statusColor = when (expiryStatus) {
                                        IdentityExpiryStatus.EXPIRED, IdentityExpiryStatus.INVALID -> Color(0xFFFF6B7A)
                                        IdentityExpiryStatus.EXPIRING_SOON -> Color(0xFFFFC857)
                                        else -> IdMuted
                                    }
                                    Text("Vervaldatum: ${document.expiryDate}", color = IdMuted, fontSize = 13.sp)
                                    Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (document.notes.isNotBlank()) Text(document.notes, color = IdMuted, fontSize = 13.sp)
                            }
                            Column {
                                IconButton(onClick = {
                                    editingDocument = document
                                    name = document.name
                                    type = document.type
                                    issuer = document.issuer
                                    expiry = document.expiryDate
                                    notes = document.notes
                                    error = null
                                    showAdd = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Document bewerken", tint = IdCyan)
                                }
                                IconButton(onClick = { pendingDelete = document }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Document verwijderen", tint = IdMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    backupMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { backupMode = null; backupPassword = "" },
            containerColor = IdPanel,
            title = { Text(if (mode == "export") "ID-kluis veilig exporteren" else "ID-kluis herstellen", color = IdSilver) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (mode == "export")
                        "Kies een wachtwoord van minimaal 8 tekens. Alleen documentmetadata wordt opgenomen; geen scans of officiële identiteitsgegevens."
                        else "Voer het wachtwoord in waarmee deze back-up is gemaakt. Herstellen vervangt de huidige ID-kluis.",
                        color = IdMuted)
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("Back-upwachtwoord") }, singleLine = true)
                    backupError?.let { Text(it, color = Color(0xFFFF6B7A)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (backupPassword.length < 8) backupError = "Gebruik minimaal 8 tekens voor het back-upwachtwoord."
                    else {
                        backupError = null
                        if (mode == "export") exportIdentityBackup.launch("TAVEXIQ-ID-kluis-backup.txt")
                        else importIdentityBackup.launch(arrayOf("text/plain", "application/octet-stream", "application/json"))
                    }
                }) { Text(if (mode == "export") "Bestand opslaan" else "Bestand kiezen", color = IdCyan) }
            },
            dismissButton = {
                TextButton(onClick = { backupMode = null; backupPassword = "" }) { Text("Annuleren", color = IdSilver) }
            }
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false; editingDocument = null },
            containerColor = IdPanel,
            title = { Text(if (editingDocument == null) "Document toevoegen" else "Document bewerken", color = IdSilver, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Naam, bijv. Paspoort") }, singleLine = true)
                    Text("Type", color = IdMuted)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Identiteitsbewijs", "Paspoort", "Rijbewijs", "Verblijfsdocument", "Anders").forEach { option ->
                            FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                        }
                    }
                    OutlinedTextField(value = issuer, onValueChange = { issuer = it }, label = { Text("Uitgevende instantie (optioneel)") }, singleLine = true)
                    OutlinedTextField(value = expiry, onValueChange = { expiry = it; error = null }, label = { Text("Vervaldatum (optioneel, DD-MM-JJJJ)") }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notitie (optioneel, geen gevoelige nummers)") }, minLines = 2)
                    error?.let { Text(it, color = Color(0xFFFF6B7A)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        name.isBlank() -> error = "Vul een naam in."
                        !IdentityDocumentRules.validateExpiryDate(expiry) ->
                            error = "Vul een bestaande datum in als DD-MM-JJJJ (bijv. 31-12-2028)."
                        notes.length > 500 -> error = "Houd notities kort (maximaal 500 tekens)."
                        else -> {
                            val existing = editingDocument
                            val replacement = IdentityDocument(
                                id = existing?.id ?: System.currentTimeMillis(),
                                name = name.trim(),
                                type = type,
                                issuer = issuer.trim(),
                                expiryDate = expiry.trim(),
                                notes = notes.trim()
                            )
                            val updated = if (existing == null) documents + replacement
                                else documents.map { if (it.id == existing.id) replacement else it }
                            runCatching { storage.save(updated) }
                                .onSuccess { documents = updated; showAdd = false; editingDocument = null; error = null }
                                .onFailure { error = "Veilig opslaan is mislukt. De bestaande kluis is behouden." }
                        }
                    }
                }) { Text("Veilig opslaan", color = IdCyan) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false; editingDocument = null }) { Text("Annuleren", color = IdSilver) } }
        )
    }

    pendingDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = IdPanel,
            title = { Text("Documentvermelding verwijderen?", color = IdSilver) },
            text = { Text("Alleen deze versleutelde vermelding wordt verwijderd. Dit maakt geen officieel document ongeldig.", color = IdSilver) },
            confirmButton = {
                TextButton(onClick = {
                    val updated = documents.filterNot { it.id == document.id }
                    runCatching { storage.save(updated) }
                        .onSuccess { documents = updated; pendingDelete = null }
                        .onFailure { storageFailed = true; pendingDelete = null }
                }) { Text("Verwijderen", color = Color(0xFFFF6B7A)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annuleren", color = IdSilver) } }
        )
    }
}
