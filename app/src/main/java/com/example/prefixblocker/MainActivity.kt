package com.example.prefixblocker

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val telecomManager =
            getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        Log.d(
            "PREFIX_BLOCKER",
            "Default dialer: ${telecomManager.defaultDialerPackage}"
        )

        val enabled = packageManager.getComponentEnabledSetting(
            android.content.ComponentName(
                this,
                PrefixCallScreeningService::class.java
            )
        )

        Log.d(
            "PREFIX_BLOCKER",
            "Service enabled state: $enabled"
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

                val intent = roleManager.createRequestRoleIntent(
                    RoleManager.ROLE_CALL_SCREENING
                )

                startActivity(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefixBlockerApp(context: Context) {

    val storage = remember {
        PrefixStorage(context)
    }

    val prefixes by storage.prefixesFlow.collectAsState(initial = emptySet())

    var text by remember {
        mutableStateOf("")
    }

    var darkMode by remember {
        mutableStateOf(true)
    }

    val backgroundGradient =
        if (darkMode) {
            listOf(
                Color(0xFF0B1120),
                Color(0xFF111827),
                Color(0xFF1E1B4B)
            )
        } else {
            listOf(
                Color(0xFFE0EAFF),
                Color(0xFFF8FAFC),
                Color(0xFFDDE7FF)
            )
        }

    val cardColor =
        if (darkMode) {
            Color(0xFF1E293B)
        } else {
            Color.White
        }

    val textColor =
        if (darkMode) {
            Color.White
        } else {
            Color(0xFF111827)
        }

    val secondaryText =
        if (darkMode) {
            Color(0xFFD1D5DB)
        } else {
            Color(0xFF475569)
        }

    MaterialTheme {

        val view = LocalView.current

        val window = (view.context as Activity).window

        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowCompat
            .getInsetsController(window, view)
            .isAppearanceLightStatusBars = false

        Scaffold { padding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            backgroundGradient
                        )
                    )
                    .padding(padding)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 22.dp,
                            end = 22.dp,
                            top = 18.dp,
                            bottom = 12.dp
                        )
                ) {

                    // TOP BAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Column {

                            Text(
                                text = "PrefixBlocker",
                                color = textColor,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Blocca automaticamente le chiamate spam",
                                color = secondaryText,
                                fontSize = 14.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            // SWITCH TEMA
                            Switch(
                                checked = darkMode,

                                onCheckedChange = {
                                    darkMode = it
                                },

                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF8B5CF6),

                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFF94A3B8)
                                )
                            )

                            Spacer(modifier = Modifier.size(10.dp))

                            // SETTINGS ICON
                            Box(
                                modifier = Modifier
                                    .background(
                                        cardColor,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .padding(12.dp)
                            ) {

                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // HEADER CARD
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF7C3AED),
                                        Color(0xFF2563EB)
                                    )
                                ),
                                shape = RoundedCornerShape(28.dp)
                            )
                            .padding(22.dp)
                    ) {

                        Column {

                            Text(
                                text = "Protezione chiamate",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Blocca automaticamente numeri indesiderati usando prefissi personalizzati.",
                                color = Color(0xFFE2E8F0),
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // INPUT CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(28.dp),

                        colors = CardDefaults.cardColors(
                            containerColor = cardColor
                        )
                    ) {

                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {

                            Text(
                                text = "Nuovo prefisso",
                                color = textColor,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Esempio: 0422111",
                                color = secondaryText,
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = text,

                                onValueChange = {

                                    val filtered = it.filter { char ->
                                        char.isDigit()
                                    }

                                    if (filtered.length <= 12) {
                                        text = filtered
                                    }
                                },

                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),

                                singleLine = true,

                                modifier = Modifier.fillMaxWidth(),

                                shape = RoundedCornerShape(22.dp),

                                label = {
                                    Text("Prefisso")
                                },

                                textStyle = TextStyle(
                                    color = textColor,
                                    fontSize = 20.sp
                                ),

                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = Color(0xFF475569),

                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,

                                    cursorColor = textColor,

                                    focusedContainerColor =
                                        if (darkMode)
                                            Color(0xFF0F172A)
                                        else
                                            Color(0xFFF8FAFC),

                                    unfocusedContainerColor =
                                        if (darkMode)
                                            Color(0xFF0F172A)
                                        else
                                            Color(0xFFF8FAFC)
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {

                                    if (text.isNotBlank()) {

                                        val prefixToSave = text

                                        CoroutineScope(Dispatchers.IO).launch {
                                            storage.savePrefix(prefixToSave)
                                        }

                                        text = ""
                                    }
                                },

                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),

                                shape = RoundedCornerShape(22.dp)
                            ) {

                                Text(
                                    "Aggiungi prefisso",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Prefissi bloccati",
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (prefixes.isEmpty()) {

                        Card(
                            modifier = Modifier.fillMaxWidth(),

                            shape = RoundedCornerShape(28.dp),

                            colors = CardDefaults.cardColors(
                                containerColor = cardColor
                            )
                        ) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(36.dp),

                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                Icon(
                                    imageVector = Icons.Default.PhoneDisabled,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(52.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Nessun prefisso salvato",
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Aggiungi un prefisso per iniziare.",
                                    color = secondaryText,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    else {

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {

                            items(prefixes.toList()) { prefix ->

                                Card(
                                    modifier = Modifier.fillMaxWidth(),

                                    shape = RoundedCornerShape(26.dp),

                                    colors = CardDefaults.cardColors(
                                        containerColor = cardColor
                                    )
                                ) {

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),

                                        verticalAlignment = Alignment.CenterVertically,

                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {

                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        Color(0xFF7C3AED),
                                                        shape = RoundedCornerShape(18.dp)
                                                    )
                                                    .padding(12.dp)
                                            ) {

                                                Icon(
                                                    imageVector = Icons.Default.PhoneDisabled,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.size(16.dp))

                                            Column {

                                                Text(
                                                    text = "$prefix*",
                                                    color = textColor,
                                                    fontSize = 21.sp,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = "Blocco attivo",
                                                    color = secondaryText,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {

                                                CoroutineScope(Dispatchers.IO).launch {
                                                    storage.removePrefix(prefix)
                                                }
                                            }
                                        ) {

                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = Color(0xFFFF6B6B)
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
}