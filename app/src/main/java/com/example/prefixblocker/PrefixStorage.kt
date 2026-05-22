package com.example.prefixblocker

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "prefixes")

class PrefixStorage(private val context: Context) {

    companion object {
        val PREFIX_KEY = stringSetPreferencesKey("blocked_prefixes")
    }

    val prefixesFlow: Flow<Set<String>> = context.dataStore.data.map {
        it[PREFIX_KEY] ?: emptySet()
    }

    suspend fun savePrefix(prefix: String) {
        context.dataStore.edit {
            val current = it[PREFIX_KEY] ?: emptySet()
            it[PREFIX_KEY] = current + prefix
        }
    }

    suspend fun removePrefix(prefix: String) {
        context.dataStore.edit {
            val current = it[PREFIX_KEY] ?: emptySet()
            it[PREFIX_KEY] = current - prefix
        }
    }
}