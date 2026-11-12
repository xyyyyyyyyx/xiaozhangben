package com.xiaozhangben.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger_settings")

/** 主题模式：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromIndex(index: Int): ThemeMode = entries.getOrElse(index) { SYSTEM }
    }
}

data class AppSettings(
    val seeded: Boolean = false,
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinSalt: String = "",
    val pinHash: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastExportAt: Long = 0L,
    val lastImportAt: Long = 0L,
) {
    val hasPin: Boolean get() = pinHash.isNotEmpty() && pinSalt.isNotEmpty()
}

class AppPreferences(private val context: Context) {

    private object Keys {
        val SEEDED = booleanPreferencesKey("seeded")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val LAST_EXPORT_AT = longPreferencesKey("last_export_at")
        val LAST_IMPORT_AT = longPreferencesKey("last_import_at")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            AppSettings(
                seeded = preferences[Keys.SEEDED] ?: false,
                lockEnabled = preferences[Keys.LOCK_ENABLED] ?: false,
                biometricEnabled = preferences[Keys.BIOMETRIC_ENABLED] ?: false,
                pinSalt = preferences[Keys.PIN_SALT] ?: "",
                pinHash = preferences[Keys.PIN_HASH] ?: "",
                themeMode = ThemeMode.fromIndex(preferences[Keys.THEME_MODE] ?: 0),
                lastExportAt = preferences[Keys.LAST_EXPORT_AT] ?: 0L,
                lastImportAt = preferences[Keys.LAST_IMPORT_AT] ?: 0L,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setSeeded(value: Boolean) {
        context.dataStore.edit { it[Keys.SEEDED] = value }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.ordinal }
    }

    suspend fun setLockEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_ENABLED] = value }
    }

    suspend fun setBiometricEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = value }
    }

    /** 设置或修改 PIN，同时生成新的盐。 */
    suspend fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        context.dataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
        }
    }

    /** 关闭锁并清除 PIN。 */
    suspend fun clearPin() {
        context.dataStore.edit {
            it[Keys.PIN_SALT] = ""
            it[Keys.PIN_HASH] = ""
            it[Keys.LOCK_ENABLED] = false
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val current = current()
        return PinHasher.verify(pin, current.pinSalt, current.pinHash)
    }

    suspend fun markExported(at: Long) {
        context.dataStore.edit { it[Keys.LAST_EXPORT_AT] = at }
    }

    suspend fun markImported(at: Long) {
        context.dataStore.edit { it[Keys.LAST_IMPORT_AT] = at }
    }
}
