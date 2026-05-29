package com.example.prefixblocker

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextOverflow

// ---------------- DATA ----------------

data class CallItem(
    val number: String,
    val name: String? = null
)

fun getContactName(context: Context, phoneNumber: String): String? {

    val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        .buildUpon()
        .appendPath(Uri.encode(phoneNumber))
        .build()

    val cursor = context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
        null,
        null,
        null
    )

    cursor?.use {
        if (it.moveToFirst()) {
            return it.getString(
                it.getColumnIndexOrThrow(
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                )
            )
        }
    }

    return null
}

// ---------------- ACTIVITY ----------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE
            ),
            1
        )

        askForCallScreeningRole()

        enableEdgeToEdge()

        setContent {
            PrefixBlockerApp(this)
        }
    }

    private fun askForCallScreeningRole() {

        val roleManager = getSystemService(RoleManager::class.java)

        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {

            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {

                startActivity(
                    roleManager.createRequestRoleIntent(
                        RoleManager.ROLE_CALL_SCREENING
                    )
                )
            }
        }
    }
}

// ---------------- UI ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefixBlockerApp(context: Context) {

    val storage = remember { PrefixStorage(context) }

    val prefixes by storage.prefixesFlow.collectAsState(initial = null)

    var text by remember { mutableStateOf("") }

    var searchText by remember { mutableStateOf("") }

    var searchVisible by remember { mutableStateOf(false) }

    var sortDescending by remember { mutableStateOf(true) }

    var sortMode by remember {
        mutableStateOf("recent")
    }

    var sortMenuExpanded by remember {
        mutableStateOf(false)
    }


    var addExpanded by remember { mutableStateOf(true) }

    var showCallDialog by remember { mutableStateOf(false) }

    var recentCalls by remember {
        mutableStateOf<List<CallItem>>(emptyList())
    }

    val systemDark = isSystemInDarkTheme()

    var isDark by remember {
        mutableStateOf(systemDark)
    }

    val prefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var blockInternational by remember {
        mutableStateOf(
            prefs.getBoolean("block_international", false)
        )
    }

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    var attemptsMap by remember {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }

    val scope = rememberCoroutineScope()

    val keyboardController = LocalSoftwareKeyboardController.current

    val colors = if (isDark) {

        darkColorScheme(
            background = Color(0xFF0B1120),
            surface = Color(0xFF111827),
            primary = Color(0xFF7C3AED)
        )

    } else {

        lightColorScheme(
            background = Color(0xFFF5F7FF),
            surface = Color(0xFFFFFFFF),
            primary = Color(0xFF4F46E5)
        )
    }

    fun loadRecentCalls() {

        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val callsMap = linkedMapOf<String, CallItem>()

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {

            val idx = it.getColumnIndex(CallLog.Calls.NUMBER)

            while (it.moveToNext() && callsMap.size < 20) {

                val number = it.getString(idx) ?: continue

                val clean = number.filter { c ->
                    c.isDigit()
                }

                if (!callsMap.containsKey(clean)) {

                    callsMap[clean] = CallItem(
                        clean,
                        getContactName(context, clean)
                    )
                }
            }
        }

        recentCalls = callsMap.values.toList()
    }

    fun loadAttempts() {

        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val map = mutableMapOf<String, Int>()

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {

            val idx = it.getColumnIndex(CallLog.Calls.NUMBER)

            while (it.moveToNext()) {

                var number =
                    it.getString(idx)
                        ?.filter { c -> c.isDigit() }
                        ?: continue

                // rimuove prefissi internazionali
                // numero internazionale NON italiano
                val isForeignInternational =
                    (
                            number.startsWith("00") &&
                                    !number.startsWith("0039")
                            )

// modalità Italia:
// ignora solo esteri veri
                if (!blockInternational && isForeignInternational) {
                    continue
                }

// normalizzazione Italia
                if (number.startsWith("39") && number.length > 10) {
                    number = number.removePrefix("39")
                }

// normalizzazione internazionale
                if (number.startsWith("00")) {
                    number = number.drop(2)
                }

                (prefixes ?: emptySet()).forEach { prefix ->

                    if (number.startsWith(prefix)) {

                        map[prefix] =
                            (map[prefix] ?: 0) + 1
                    }
                }
            }
        }

        attemptsMap = map
    }

    LaunchedEffect(prefixes) {
        loadAttempts()
    }


    MaterialTheme(colorScheme = colors) {

        val view = LocalView.current

        val window = (view.context as Activity).window

        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowCompat
            .getInsetsController(window, view)
            .isAppearanceLightStatusBars = !isDark

        Scaffold(

            snackbarHost = {

                SnackbarHost(hostState = snackbarHostState) { data ->

                    Box(
                        modifier = Modifier
                            .padding(18.dp, 12.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF9333EA),
                                        Color(0xFF2563EB),
                                        Color(0xFF06B6D4)
                                    )
                                ),
                                RoundedCornerShape(24.dp)
                            )
                            .border(
                                2.dp,
                                Color.White.copy(0.35f),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(26.dp, 18.dp)
                    ) {

                        Text(
                            text = data.visuals.message,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

        ) { padding ->

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark)
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0B1120),
                                    Color(0xFF111827),
                                    Color(0xFF1E1B4B)
                                )
                            )
                        else
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFF8FAFF),
                                    Color(0xFFEFF2FF),
                                    Color(0xFFFFFFFF)
                                )
                            )
                    )
                    .padding(padding)
            ) {

                Column(
                    Modifier.padding(22.dp)
                ) {

                    // HEADER

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF7C3AED),
                                        Color(0xFF2563EB)
                                    )
                                ),
                                RoundedCornerShape(28.dp)
                            )
                            .padding(22.dp)
                    ) {

                        Column {

                            Row(
                                Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically
                            ) {

                                // TITOLO

                                Text(
                                    "PrefixBlocker",
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                // ICONE DESTRA

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    // ITALIA / GLOBALE

                                    TextButton(
                                        onClick = {

                                            blockInternational = !blockInternational

                                            prefs.edit()
                                                .putBoolean(
                                                    "block_international",
                                                    blockInternational
                                                )
                                                .apply()
                                        }
                                    ) {

                                        Text(
                                            text =
                                                if (blockInternational)
                                                    "🌍"
                                                else
                                                    "🇮🇹",

                                            fontSize = 22.sp
                                        )
                                    }

                                    // DARK MODE

                                    IconButton(
                                        onClick = {
                                            isDark = !isDark
                                        }
                                    ) {

                                        Icon(
                                            imageVector =
                                                if (isDark)
                                                    Icons.Default.LightMode
                                                else
                                                    Icons.Default.DarkMode,

                                            contentDescription = null,

                                            tint = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                "Blocca automaticamente le chiamate spam usando prefissi personalizzati.",
                                color = Color(0xFFE2E8F0),
                                fontSize = 15.sp
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text =
                                    if (blockInternational)
                                        "Protezione globale attiva"
                                    else
                                        "Protezione Italia attiva",

                                color = Color.White.copy(0.85f),

                                fontSize = 13.sp,

                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))


                    // INPUT CARD

                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (isDark)
                                    Color(0xFF1E293B)
                                else
                                    Color.White
                        )
                    ) {

                        Column(
                            Modifier.padding(18.dp)
                        ) {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        }
                                    ) {
                                        addExpanded = !addExpanded
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Column {

                                    Text(
                                        "Nuovo prefisso",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color =
                                            if (isDark)
                                                Color.White
                                            else
                                                Color.Black
                                    )

                                    Text(
                                        "Esempio: 0422111",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }

                                Icon(
                                    imageVector =
                                        if (addExpanded)
                                            Icons.Default.KeyboardArrowUp
                                        else
                                            Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }

                            if (addExpanded) {

                                Spacer(Modifier.height(18.dp))

                                OutlinedTextField(

                                    value = text,

                                    onValueChange = {

                                        val filtered =
                                            it.filter { c ->
                                                c.isDigit()
                                            }

                                        if (filtered.length <= 12) {
                                            text = filtered
                                        }
                                    },

                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),

                                    singleLine = true,

                                    modifier = Modifier.fillMaxWidth(),

                                    shape = RoundedCornerShape(22.dp),

                                    label = {
                                        Text("Prefisso")
                                    },

                                    trailingIcon = {

                                        IconButton(
                                            onClick = {
                                                loadRecentCalls()
                                                showCallDialog = true
                                            }
                                        ) {

                                            Icon(
                                                Icons.Default.Call,
                                                null
                                            )
                                        }
                                    }
                                )

                                Spacer(Modifier.height(14.dp))

                                Button(

                                    onClick = {

                                        if (text.length >= 3) {

                                            val p = text

                                            scope.launch(Dispatchers.IO) {
                                                storage.savePrefix(p)
                                            }

                                            scope.launch {

                                                snackbarHostState.showSnackbar(
                                                    message = "Prefisso $p bloccato",
                                                    duration = SnackbarDuration.Indefinite
                                                )
                                            }

                                            scope.launch {

                                                delay(2000)

                                                snackbarHostState
                                                    .currentSnackbarData
                                                    ?.dismiss()
                                            }

                                            text = ""

                                            keyboardController?.hide()
                                        }
                                    },

                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp),

                                    shape = RoundedCornerShape(22.dp)
                                ) {

                                    Text(
                                        "Aggiungi prefisso",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }


                    Spacer(Modifier.height(20.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {

                        Text(
                            "Prefissi bloccati",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color =
                                if (isDark)
                                    Color.White
                                else
                                    Color.Black
                        )

                        Row {

                            // SEARCH

                            IconButton(
                                onClick = {
                                    searchVisible = !searchVisible

                                    if (!searchVisible) {
                                        searchText = ""
                                    }
                                }
                            ) {

                                Icon(
                                    Icons.Default.Search,
                                    null
                                )
                            }

                            // SORT

                            Box {

                                IconButton(
                                    onClick = {
                                        sortMenuExpanded = true
                                    }
                                ) {

                                    Icon(
                                        Icons.Default.Sort,
                                        contentDescription = null
                                    )
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = {
                                        sortMenuExpanded = false
                                    }
                                ) {

                                    /*DropdownMenuItem(
                                        text = {
                                            Text("Più recenti")
                                        },
                                        onClick = {
                                            sortMode = "recent"
                                            sortMenuExpanded = false
                                        }
                                    )
*/
                                    DropdownMenuItem(
                                        text = {
                                            Text("Più spam rilevati")
                                        },
                                        onClick = {
                                            sortMode = "spam"
                                            sortMenuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text("0-9")
                                               },
                                        onClick = {
                                            sortMode = "numeric"
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (searchVisible) {

                        OutlinedTextField(

                            value = searchText,

                            onValueChange = {

                                val f = it.filter { c ->
                                    c.isDigit()
                                }

                                if (f.length <= 12) {
                                    searchText = f
                                }
                            },

                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),

                            modifier = Modifier.fillMaxWidth(),

                            shape = RoundedCornerShape(20.dp),

                            label = {
                                Text("Cerca prefisso")
                            }
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    val filteredPrefixes = (
                            prefixes?.filter {
                                it.contains(searchText)
                            } ?: emptyList()
                            ).let { list ->

                            when (sortMode) {

                                /*"recent" -> {
                                    if (sortDescending)
                                        list.reversed()
                                    else
                                        list
                                }*/

                                "spam" -> {

                                    val sorted = list.sortedBy {
                                        attemptsMap[it] ?: 0
                                    }

                                    if (sortDescending)
                                        sorted.reversed()
                                    else
                                        sorted
                                }

                                "numeric" -> {

                                    val sorted = list.sorted()

                                    if (sortDescending)
                                        sorted
                                    else
                                        sorted.reversed()
                                }

                                else -> list
                            }
                        }

                    if (prefixes == null) {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {

                            CircularProgressIndicator(
                                color = Color(0xFF8B5CF6)
                            )
                        }

                    } else if (filteredPrefixes.isEmpty()) {

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    if (isDark)
                                        Color(0xFF1E293B)
                                    else
                                        Color.White
                            )
                        ) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(36.dp),

                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default.PhoneDisabled,

                                    contentDescription = null,

                                    tint = Color(0xFF8B5CF6),

                                    modifier = Modifier.size(52.dp)
                                )

                                Spacer(
                                    modifier = Modifier.height(16.dp)
                                )

                                Text(
                                    text =
                                        if (searchText.isNotEmpty())
                                            "Nessun risultato trovato"
                                        else
                                            "Nessun prefisso salvato",

                                    color =
                                        if (isDark)
                                            Color.White
                                        else
                                            Color.Black,

                                    fontSize = 18.sp,

                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    text =
                                        if (searchText.isNotEmpty())
                                            "Prova con un altro prefisso."
                                        else
                                            "Aggiungi un prefisso per iniziare.",

                                    color = Color.Gray,

                                    fontSize = 14.sp
                                )
                            }
                        }

                    } else {

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement =
                                Arrangement.spacedBy(14.dp)
                        ) {

                            items(filteredPrefixes.toList()) { prefix ->

                                Card(
                                    shape = RoundedCornerShape(26.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor =
                                            if (isDark)
                                                Color(0xFF1E293B)
                                            else
                                                Color.White
                                    )
                                ) {

                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),

                                        Arrangement.SpaceBetween,

                                        Alignment.CenterVertically
                                    ) {

                                        Row(
                                            verticalAlignment =
                                                Alignment.CenterVertically
                                        ) {

                                            Icon(
                                                Icons.Default.PhoneDisabled,
                                                null
                                            )

                                            Spacer(
                                                Modifier.width(16.dp)
                                            )

                                            Column {

                                                Text(
                                                    "$prefix*",
                                                    fontWeight =
                                                        FontWeight.Bold,

                                                    color =
                                                        if (isDark)
                                                            Color.White
                                                        else
                                                            Color.Black
                                                )

                                                val attempts = attemptsMap[prefix] ?: 0

                                                val attemptsColor =
                                                    when {
                                                        attempts == 0 -> Color(0xFF22C55E)
                                                        attempts <= 15 -> Color(0xFFF59E0B)
                                                        else -> Color(0xFFEF4444)
                                                    }

                                                Text(
                                                    text = "N. tentativi: $attempts",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = attemptsColor
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {

                                                scope.launch(
                                                    Dispatchers.IO
                                                ) {
                                                    storage.removePrefix(prefix)
                                                }
                                            }
                                        ) {

                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = Color(0xFFFF4D4D)
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

        // CALL DIALOG

        if (showCallDialog) {

            AlertDialog(

                onDismissRequest = {
                    showCallDialog = false
                },

                confirmButton = {

                    TextButton(
                        onClick = {
                            showCallDialog = false
                        }
                    ) {
                        Text("Chiudi")
                    }
                },

                title = {
                    Text("Chiamate recenti")
                },

                text = {

                    LazyColumn {

                        items(recentCalls) { call ->

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),

                                horizontalArrangement =
                                    Arrangement.SpaceBetween,

                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Text(
                                    text = call.name ?: call.number,

                                    fontWeight = FontWeight.Bold,

                                    fontSize = 17.sp,

                                    maxLines = 1,

                                    overflow = TextOverflow.Ellipsis,

                                    modifier = Modifier.weight(1f),

                                    color =
                                        if (isDark)
                                            Color.White
                                        else
                                            Color.Black
                                )

                                Spacer(
                                    modifier = Modifier.width(12.dp)
                                )

                                Button(
                                    onClick = {

                                        var clean =
                                            call.number.filter {
                                                it.isDigit()
                                            }

                                        if (
                                            clean.startsWith("39")
                                            && clean.length > 10
                                        ) {
                                            clean =
                                                clean.removePrefix("39")
                                        }

                                        if (clean.startsWith("00")) {
                                            clean = clean.drop(2)
                                        }

                                        if (clean.length in 3..12) {

                                            text = clean

                                            showCallDialog = false
                                        }
                                    }
                                ) {

                                    Text("Usa")
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}