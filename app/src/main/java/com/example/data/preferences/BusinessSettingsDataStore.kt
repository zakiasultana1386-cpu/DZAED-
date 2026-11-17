package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BusinessSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "business_settings")

class BusinessSettingsDataStore(private val context: Context) {
    private object Keys {
        val BUSINESS_NAME = stringPreferencesKey("business_name")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val BUSINESS_PHONE = stringPreferencesKey("business_phone")
        val BUSINESS_ADDRESS = stringPreferencesKey("business_address")
    }

    val settingsFlow: Flow<BusinessSettings> = context.dataStore.data.map { prefs ->
        BusinessSettings(
            businessName = prefs[Keys.BUSINESS_NAME]?.takeIf { it.isNotBlank() } ?: "My Business",
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL]?.takeIf { it.isNotBlank() } ?: "$",
            businessPhone = prefs[Keys.BUSINESS_PHONE] ?: "",
            businessAddress = prefs[Keys.BUSINESS_ADDRESS] ?: ""
        )
    }

    suspend fun updateSettings(
        businessName: String,
        currencySymbol: String,
        businessPhone: String,
        businessAddress: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BUSINESS_NAME] = businessName.trim().ifBlank { "My Business" }
            prefs[Keys.CURRENCY_SYMBOL] = currencySymbol.trim().ifBlank { "$" }
            prefs[Keys.BUSINESS_PHONE] = businessPhone.trim()
            prefs[Keys.BUSINESS_ADDRESS] = businessAddress.trim()
        }
    }
}
