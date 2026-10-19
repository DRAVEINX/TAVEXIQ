package com.tavexiq.wallet

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.EnumMap

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF10243D)
private val Cyan = Color(0xFF36D9F5)
private val Silver = Color(0xFFE2EAF3)
private val Muted = Color(0xFF9CB0C8)

data class LoyaltyCard(val id: Long, val name: String, val code: String, val format: String, val favorite: Boolean = false)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TavexiqApp(this) }
    }
}

@Composable
private fun TavexiqApp(context: Context) {
    val storage = remember { EncryptedCardStorage(context) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var cards by remember {
        mutableStateOf(runCatching { storage.loadCards() }.getOrElse {
            storageError = "Je opgeslagen kaarten konden niet veilig worden gelezen. De gegevens zijn niet overschreven. Controleer de appgegevens of herstel een back-up."
            emptyList()
        })
    }
    var selected by remember { mutableStateOf<LoyaltyCard?>(null) }
    var pendingDelete by remember { mutableStateOf<LoyaltyCard?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<LoyaltyCard?>(null) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("QR") }
    var error by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sortAlphabetically by remember { mutableStateOf(false) }
    var sortNewestFirst by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirmation by remember { mutableStateOf("") }
    var backupDialogMode by remember { mutableStateOf<String?>(null) }
    var legacyBackupPending by remember { mutableStateOf<List<LoyaltyCard>?>(null) }
    var pendingEncryptedBackupJson by remember { mutableStateOf<String?>(null) }
    var pendingCardRestore by remember { mutableStateOf<List<LoyaltyCard>?>(null) }
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val passwordChars = backupPassword.toCharArray()
                val json = try {
                    EncryptedCardBackupCodec.encode(cards, passwordChars)
                } finally {
                    passwordChars.fill('\u0000')
                }
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Het back-upbestand kon niet worden geopend.")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            }.onSuccess {
                backupMessage = "Back-up gemaakt. Bewaar het bestand veilig: het bevat je kaartcodes."
                backupError = null
            }.onFailure {
                backupError = it.message ?: "Versleutelde back-up maken is mislukt."
            }
        }
        backupPassword = ""; backupPasswordConfirmation = ""
    }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Het back-upbestand kon niet worden gelezen.")
                val imported = input.bufferedReader(Charsets.UTF_8).use { reader ->
                    val result = StringBuilder()
                    val buffer = CharArray(8_192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(result.length + count <= 2_000_000) {
                            "Het back-upbestand is te groot."
                        }
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                }
                val root = org.json.JSONObject(imported)
                if (root.optString("app") == "TAVEXIQ") {
                    // Legacy backups are plaintext, so require an explicit confirmation before replacing current cards.
                    legacyBackupPending = CardBackupCodec.decode(imported)
                } else {
                    // Ask for the password only after the selected file is identified as encrypted.
                    pendingEncryptedBackupJson = imported
                    backupDialogMode = "decrypt"
                }
            }.onFailure {
                backupError = it.message ?: "Back-upbestand kon niet worden gecontroleerd."
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, onPrimary = Navy, secondary = Silver,
        background = Navy, surface = Panel, onSurface = Silver,
        onBackground = Silver, error = Color(0xFFFF6B7A)
    )) {
        Scaffold(
            containerColor = Navy,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(Navy).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).background(Cyan, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = Navy, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("TAVEXIQ", fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("YOUR EVERYDAY WALLET", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Silver) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0; selected = null },
                        icon = { Icon(Icons.Default.QrCode2, contentDescription = null) }, label = { Text("Kaarten") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1; selected = null },
                        icon = { Icon(Icons.Default.DirectionsTransit, contentDescription = null) }, label = { Text("OV") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2; selected = null },
                        icon = { Icon(Icons.Default.Security, contentDescription = null) }, label = { Text("Betalen") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3; selected = null },
                        icon = { Icon(Icons.Default.Badge, contentDescription = null) }, label = { Text("ID-kluis") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                if (storageError != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2028)),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
                        Text(storageError!!, color = Silver, modifier = Modifier.padding(14.dp))
                    }
                }
                when (tab) {
                    0 -> if (selected != null) {
                        CodeDetail(selected!!, onBack = { selected = null })
                    } else {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Mijn kaarten", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text(cards.size.toString() + " opgeslagen kaarten", color = Muted)
                            }
                            Button(enabled = storageError == null,
                                onClick = { editingCard = null; name = ""; code = ""; format = "QR"; error = ""; showAdd = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Toevoegen", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (cards.isEmpty()) {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = Cyan, modifier = Modifier.size(54.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Je wallet begint hier", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(5.dp))
                                    Text("Voeg een klantenkaart toe en toon de code bij de kassa.", color = Muted)
                                    Spacer(Modifier.height(14.dp))
                                    Button(enabled = storageError == null, onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Navy)) {
                                        Text("Eerste kaart toevoegen")
                                    }
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    singleLine = true,
                                    label = { Text("Zoek een klantenkaart") },
                                    placeholder = { Text("Naam van de winkel") },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Zoekopdracht wissen", tint = Muted)
                                            }
                                        }
                                    }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = favoritesOnly,
                                        onClick = { favoritesOnly = !favoritesOnly },
                                        label = { Text(if (favoritesOnly) "Alleen favorieten" else "Favorieten") },
                                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                    )
                                    FilterChip(
                                        selected = sortAlphabetically,
                                        onClick = {
                                            sortAlphabetically = !sortAlphabetically
                                            if (sortAlphabetically) sortNewestFirst = false
                                        },
                                        label = { Text(if (sortAlphabetically) "Naam A–Z" else "Sorteer A–Z") }
                                    )
                                    FilterChip(
                                        selected = sortNewestFirst,
                                        onClick = {
                                            sortNewestFirst = !sortNewestFirst
                                            if (sortNewestFirst) sortAlphabetically = false
                                        },
                                        label = { Text("Nieuwste eerst") }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        enabled = storageError == null,
                                        onClick = { backupError = null; backupMessage = null; backupPassword = ""; backupPasswordConfirmation = ""; backupDialogMode = "export" },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Back-up maken") }
                                    OutlinedButton(
                                        enabled = storageError == null,
                                        onClick = {
                                        backupError = null
                                        backupMessage = null
                                        backupPassword = ""; backupPasswordConfirmation = ""
                                        pendingEncryptedBackupJson = null
                                        legacyBackupPending = null
                                        importBackup.launch(arrayOf("application/json", "text/json", "text/plain"))
                                    },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Back-up terugzetten") }
                                }
                                Text("Back-ups zijn met je eigen wachtwoord versleuteld. Bewaar dat wachtwoord veilig; zonder wachtwoord kan herstel niet.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                                backupMessage?.let { Text(it, color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                                backupError?.let { Text(it, color = Color(0xFFFF6B7A), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                                val filteredCards = filterLoyaltyCards(cards, searchQuery, favoritesOnly)
                                val visibleCards = when {
                                    sortAlphabetically -> filteredCards.sortedBy { it.name.lowercase() }
                                    sortNewestFirst -> filteredCards.sortedByDescending { it.id }
                                    else -> filteredCards
                                }
                                 if (visibleCards.isEmpty()) {
                                     Column(
                                         modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                                         horizontalAlignment = Alignment.CenterHorizontally,
                                         verticalArrangement = Arrangement.spacedBy(8.dp)
                                     ) {
                                         Icon(
                                             if (favoritesOnly) Icons.Default.StarBorder else Icons.Default.CreditCard,
                                             contentDescription = null,
                                             tint = Muted,
                                             modifier = Modifier.size(36.dp)
                                         )
                                         Text(
                                             when {
                                                 favoritesOnly && searchQuery.isNotBlank() -> "Geen favorieten gevonden voor deze zoekopdracht."
                                                 favoritesOnly -> "Je hebt nog geen favorieten."
                                                 searchQuery.isNotBlank() -> "Geen kaarten gevonden voor deze zoekopdracht."
                                                 else -> "Je wallet is nog leeg."
                                             },
                                             color = Silver,
                                             fontWeight = FontWeight.SemiBold
                                         )
                                         Text(
                                             when {
                                                 favoritesOnly -> "Markeer een kaart met de ster om hem hier terug te vinden."
                                                 searchQuery.isNotBlank() -> "Controleer je zoekterm of wis de zoekbalk."
                                                 else -> "Voeg een klantenkaart toe om de barcode of QR-code snel bij de hand te hebben."
                                             },
                                             color = Muted,
                                             fontSize = 13.sp,
                                             lineHeight = 18.sp
                                         )
                                         if (searchQuery.isNotBlank() && !favoritesOnly) {
                                             TextButton(onClick = { searchQuery = "" }) { Text("Zoekopdracht wissen", color = Cyan) }
                                         }
                                         if (cards.isEmpty() && searchQuery.isBlank() && !favoritesOnly) {
                                             Button(onClick = {
                                                 editingCard = null
                                                 name = ""
                                                 code = ""
                                                 format = "QR"
                                                 error = ""
                                                 showAdd = true
                                             }) { Text("Eerste kaart toevoegen") }
                                         }
                                     }
                                 } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                                        items(visibleCards, key = { it.id }) { card ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Kaart ${card.name} openen") { selected = card },
                                                shape = RoundedCornerShape(18.dp),
                                                colors = CardDefaults.cardColors(containerColor = Panel)
                                            ) {
                                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Box(Modifier.size(48.dp).background(Color(0xFF173753), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = Cyan, modifier = Modifier.size(28.dp))
                                                    }
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(card.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                                        Text(card.format + " • " + LoyaltyCardRules.maskCode(card.code), color = Muted, fontSize = 13.sp)
                                                    }
                                                    IconButton(enabled = storageError == null, onClick = {
                                                        editingCard = card
                                                        name = card.name
                                                        code = card.code
                                                        format = card.format
                                                        error = ""
                                                        showAdd = true
                                                    }) {
                                                        Icon(Icons.Default.Edit, contentDescription = "Kaart ${card.name} bewerken", tint = Cyan)
                                                    }
                                                    IconButton(enabled = storageError == null, onClick = {
                                                        val updatedCards = cards.map { if (it.id == card.id) it.copy(favorite = !it.favorite) else it }
                                                        runCatching { storage.saveCards(updatedCards) }
                                                            .onSuccess { cards = updatedCards }
                                                            .onFailure { storageError = "Favoriet opslaan is mislukt; bestaande gegevens zijn behouden." }
                                                    }) {
                                                        Icon(if (card.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                            contentDescription = if (card.favorite) "${card.name} verwijderen uit favorieten" else "${card.name} markeren als favoriet",
                                                            tint = if (card.favorite) Cyan else Muted)
                                                    }
                                                    IconButton(enabled = storageError == null, onClick = {
                                                         pendingDelete = card
                                                     }) { Icon(Icons.Default.Delete, contentDescription = "Kaart ${card.name} verwijderen", tint = Muted) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> FeatureNotice(
                        icon = { Icon(Icons.Default.DirectionsTransit, contentDescription = null, tint = Cyan, modifier = Modifier.size(42.dp)) },
                        title = "OV & reizen",
                        body = "De officiële OV-chipkaart- en OVpay-integratie is nog niet gekoppeld. Hiervoor is ondersteuning en toestemming van de vervoerder nodig.",
                        note = "TAVEXIQ kopieert of emuleert geen fysieke OV-kaart. Bewaar je fysieke kaart."
                    )
                    2 -> FeatureNotice(
                        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = Cyan, modifier = Modifier.size(42.dp)) },
                        title = "Contactloos betalen",
                        body = "Contactloze bankbetalingen zijn nog niet actief. Hiervoor zijn een goedgekeurde walletpartner, ondersteuning van de bank en vereiste certificering nodig.",
                        note = "Voer nooit een pincode, CVC/CVV of volledige betaalkaartgegevens in deze MVP in."
                    )
                    else -> IdentityVaultLock(context) { IdentityVaultScreen(context) }
                }
            }
        }

        backupDialogMode?.let { mode ->
            AlertDialog(
                onDismissRequest = { backupDialogMode = null; backupPassword = ""; backupPasswordConfirmation = ""; pendingEncryptedBackupJson = null },
                containerColor = Panel,
                title = { Text(if (mode == "export") "Back-up versleutelen" else "Back-up ontsleutelen", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (mode == "export") "Kies een wachtwoord van minimaal 8 tekens. Je hebt dit nodig om de back-up later terug te zetten."
                            else "Deze back-up is versleuteld. Voer het wachtwoord in waarmee het bestand is gemaakt.",
                            color = Silver
                        )
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = { if (it.length <= 1_024) { backupPassword = it; backupError = null } },
                            label = { Text("Back-upwachtwoord") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        if (mode == "decrypt" && backupError != null) {
                            Text(backupError!!, color = Color(0xFFFF6B7A), fontSize = 12.sp)
                        }
                        if (mode == "export") {
                            OutlinedTextField(
                                value = backupPasswordConfirmation,
                                onValueChange = { if (it.length <= 1_024) backupPasswordConfirmation = it },
                                label = { Text("Herhaal back-upwachtwoord") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                isError = backupPasswordConfirmation.isNotEmpty() && backupPassword != backupPasswordConfirmation
                            )
                            if (backupPasswordConfirmation.isNotEmpty() && backupPassword != backupPasswordConfirmation) {
                                Text("De wachtwoorden zijn niet hetzelfde.", color = Color(0xFFFF6B7A), fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = backupPassword.length >= 8 && (mode != "export" || backupPassword == backupPasswordConfirmation),
                        onClick = {
                            if (mode == "export") {
                                backupDialogMode = null
                                exportBackup.launch("TAVEXIQ-encrypted-backup.json")
                            } else {
                                val passwordChars = backupPassword.toCharArray()
                                val decoded = try {
                                    runCatching {
                                        EncryptedCardBackupCodec.decode(
                                            pendingEncryptedBackupJson ?: error("Selecteer opnieuw het back-upbestand."),
                                            passwordChars
                                        )
                                    }
                                } finally {
                                    passwordChars.fill('\u0000')
                                }
                                decoded.onSuccess { restored ->
                                    pendingCardRestore = restored
                                    backupError = null
                                    backupMessage = "Back-up gecontroleerd. Bevestig hieronder om je huidige kaarten te vervangen."
                                    pendingEncryptedBackupJson = null
                                    backupDialogMode = null
                                    backupPassword = ""; backupPasswordConfirmation = ""
                                }.onFailure {
                                    backupError = it.message ?: "Back-up terugzetten is mislukt. Je huidige kaarten zijn niet gewijzigd."
                                }
                            }
                        }
                    ) { Text(if (mode == "export") "Versleutelen en opslaan" else "Bestand kiezen en herstellen", color = Cyan) }
                },
                dismissButton = {
                    TextButton(onClick = { backupDialogMode = null; backupPassword = ""; backupPasswordConfirmation = ""; pendingEncryptedBackupJson = null }) { Text("Annuleren", color = Silver) }
                }
            )
        }

        pendingCardRestore?.let { restored ->
            AlertDialog(
                onDismissRequest = { pendingCardRestore = null },
                containerColor = Panel,
                title = { Text("Huidige kaarten vervangen?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "De versleutelde back-up is gecontroleerd. Doorgaan vervangt alle ${cards.size} huidige kaart(en) door ${restored.size} kaart(en). Annuleren laat je huidige kaarten intact.",
                        color = Silver
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching { storage.saveCards(restored) }
                            .onSuccess {
                                cards = restored
                                selected = null
                                backupMessage = "Versleutelde back-up teruggezet: ${restored.size} kaarten."
                                backupError = null
                                pendingCardRestore = null
                            }
                            .onFailure {
                                backupError = "Herstellen is niet volledig afgerond. Controleer je kaarten voordat je opnieuw probeert."
                                pendingCardRestore = null
                            }
                    }) { Text("Vervangen en herstellen", color = Cyan) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCardRestore = null }) { Text("Annuleren", color = Silver) }
                }
            )
        }

        legacyBackupPending?.let { legacyCards ->
            AlertDialog(
                onDismissRequest = { legacyBackupPending = null },
                containerColor = Panel,
                title = { Text("Oudere back-up herstellen?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Dit is een oudere, niet-versleutelde TAVEXIQ-back-up met " + legacyCards.size + " kaarten. De kaartcodes staan leesbaar in het bestand. Als je doorgaat, worden je huidige kaarten vervangen. Herstel alleen als je dit bestand vertrouwt.",
                        color = Silver
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching { storage.saveCards(legacyCards) }
                            .onSuccess {
                                cards = legacyCards
                                selected = null
                                backupMessage = "Oudere back-up hersteld: " + legacyCards.size + " kaarten. Maak daarna bij voorkeur een nieuwe versleutelde back-up."
                                backupError = null
                                legacyBackupPending = null
                            }
                            .onFailure {
                                backupError = "Herstellen is niet volledig afgerond. Controleer je kaarten voordat je opnieuw probeert."
                                legacyBackupPending = null
                            }
                    }) { Text("Herstellen", color = Cyan) }
                },
                dismissButton = {
                    TextButton(onClick = { legacyBackupPending = null }) { Text("Annuleren", color = Silver) }
                }
            )
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false; editingCard = null },
                containerColor = Panel,
                title = { Text(if (editingCard == null) "Klantenkaart toevoegen" else "Klantenkaart bewerken", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it; error = "" }, label = { Text("Winkel / kaartnaam") }, singleLine = true)
                        OutlinedTextField(value = code, onValueChange = { code = it; error = "" }, label = { Text("Kaartcode") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                        Text("Codeweergave", color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = format == "QR", onClick = { format = "QR" }, label = { Text("QR-code") })
                            FilterChip(selected = format == "CODE_128", onClick = { format = "CODE_128" }, label = { Text("Code 128") })
                        }
                        if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B7A))
                        Text("Alleen winkel-/klantenkaartcodes. Geen betaalkaartgegevens.", color = Muted, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val validationError = LoyaltyCardRules.validate(name, code)
                        val existingCard = editingCard
                        val duplicateCode = hasDuplicateCardCode(cards, code, excludingId = existingCard?.id)
                        if (validationError != null) {
                            error = validationError
                        } else if (duplicateCode) {
                            error = "Deze kaartcode staat al in je wallet. Gebruik de bestaande kaart of controleer de code."
                        } else {
                            val updatedCards = if (existingCard == null) {
                                cards + LoyaltyCard(System.currentTimeMillis(), name.trim(), code.trim(), format)
                            } else {
                                cards.map { card ->
                                    if (card.id == existingCard.id) card.copy(name = name.trim(), code = code.trim(), format = format) else card
                                }
                            }
                            runCatching { storage.saveCards(updatedCards) }
                                .onSuccess {
                                    cards = updatedCards
                                    if (existingCard != null && selected?.id == existingCard.id) {
                                        selected = updatedCards.firstOrNull { it.id == existingCard.id }
                                    }
                                    showAdd = false
                                    editingCard = null
                                }
                                .onFailure { error = "Opslaan is niet volledig afgerond. Controleer je kaarten en back-up voordat je opnieuw probeert." }
                        }
                    }) { Text("Opslaan", color = Cyan) }
                },
                dismissButton = { TextButton(onClick = { showAdd = false; editingCard = null }) { Text("Annuleren", color = Silver) } }
            )
        }
        pendingDelete?.let { cardToDelete ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = Panel,
                title = { Text("Kaart verwijderen?", fontWeight = FontWeight.Bold) },
                text = { Text("Weet je zeker dat je deze kaart wilt verwijderen? Deze actie kan niet ongedaan worden gemaakt.") },
                confirmButton = {
                    TextButton(onClick = {
                        val updatedCards = cards.filterNot { it.id == cardToDelete.id }
                        runCatching { storage.saveCards(updatedCards) }
                            .onSuccess {
                                cards = updatedCards
                                if (selected?.id == cardToDelete.id) selected = null
                                pendingDelete = null
                            }
                            .onFailure {
                                storageError = "Verwijderen is niet volledig afgerond. Controleer je kaarten en back-up voordat je opnieuw probeert."
                                pendingDelete = null
                            }
                    }) { Text("Verwijderen", color = Color(0xFFFF6B7A)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Annuleren", color = Silver) }
                }
            )
        }
    }
}

@Composable
private fun FeatureNotice(icon: @Composable () -> Unit, title: String, body: String, note: String) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                icon()
                Spacer(Modifier.height(16.dp))
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(body, color = Silver, lineHeight = 22.sp)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2B4058))
                Spacer(Modifier.height(12.dp))
                Text(note, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun CodeDetail(card: LoyaltyCard, onBack: () -> Unit) {
    var showFullCode by remember(card.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Terug", color = Cyan) }
            Spacer(Modifier.width(8.dp))
            Text(card.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val bitmap = remember(card.code, card.format) { createCodeBitmap(card.code, card.format) }
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = card.format + " voor " + card.name,
                        modifier = Modifier.width(280.dp).height(if (card.format == "QR") 280.dp else 120.dp))
                } else {
                    Text("Code kan niet worden weergegeven", color = Color.Black)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(card.name, color = Silver, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                if (showFullCode) card.code else LoyaltyCardRules.maskCode(card.code),
                color = Muted,
                fontSize = 14.sp
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showFullCode = !showFullCode }) {
                Icon(
                    if (showFullCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showFullCode) "Kaartcode verbergen" else "Kaartcode tonen",
                    tint = Cyan
                )
            }
        }
        Text(
            if (showFullCode) "Je kaartcode is zichtbaar op het scherm." else "Je kaartcode is verborgen. Tik op het oog om hem te tonen.",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Text("Laat de code hierboven of de barcode bij de kassa scannen.", color = Muted)
    }
}

private fun createCodeBitmap(content: String, format: String): Bitmap? = try {
    val barcodeFormat = if (format == "CODE_128") BarcodeFormat.CODE_128 else BarcodeFormat.QR_CODE
    val width = if (barcodeFormat == BarcodeFormat.QR_CODE) 420 else 840
    val height = if (barcodeFormat == BarcodeFormat.QR_CODE) 420 else 300
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hints[EncodeHintType.MARGIN] = 1
    val matrix = MultiFormatWriter().encode(content, barcodeFormat, width, height, hints)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until width) for (y in 0 until height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
} catch (_: Exception) { null }

