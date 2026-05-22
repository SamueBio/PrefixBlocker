package com.example.prefixblocker

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.telecom.TelecomManager
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType


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

    MaterialTheme {

        Scaffold { padding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B)
                            )
                        )
                    )
                    .padding(padding)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {

                    Text(
                        text = "Blocca prefissi telefonici",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Esempio: 0422111",
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = text,
                        onValueChange = {

                            // solo numeri
                            val filtered = it.filter { char ->
                                char.isDigit()
                            }

                            // massimo 12 cifre
                            if (filtered.length <= 12) {
                                text = filtered
                            }
                        },

                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),

                        label = {
                            Text(
                                "Prefisso",
                                color = Color.LightGray
                            )
                        },

                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 20.sp
                        ),

                        modifier = Modifier.fillMaxWidth(),

                        singleLine = true,

                        shape = RoundedCornerShape(20.dp),

                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Aggiungi prefisso")
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        items(prefixes.toList()) { prefix ->

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF334155)
                                ),
                                shape = RoundedCornerShape(24.dp)
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

                                        Icon(
                                            imageVector = Icons.Default.PhoneDisabled,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Spacer(modifier = Modifier.size(14.dp))

                                        Text(
                                            text = prefix + "*",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
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
                                            tint = Color.Red
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