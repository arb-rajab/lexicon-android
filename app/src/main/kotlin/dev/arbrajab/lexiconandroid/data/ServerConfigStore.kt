package dev.arbrajab.lexiconandroid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "server_config")

/**
 * Backs [ServerConfigStore.authHeaderValue] — the one secret this app holds (see class doc on
 * [ServerConfigStore]). Kept out of the plain-text `server_config` DataStore and out of
 * `allowBackup` extraction by using Android's Keystore-backed AES256-GCM encrypted prefs instead.
 */
private const val ENCRYPTED_PREFS_NAME = "server_config_secure"
private const val KEY_AUTH_HEADER_VALUE = "auth_header_value"

private fun encryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

data class ServerConfig(
    val baseUrl: String = "",
    val authHeaderName: String = "",
    val authHeaderValue: String = ""
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
}

/**
 * lexicon v1 has no login endpoint of its own — `05-api-contracts.md` states
 * instance-level auth is a deployment concern, not a designed mechanism, and
 * the backend ships with no auth middleware at all (see ADR-0001). So rather
 * than fabricate a login screen against endpoints that don't exist, the app
 * asks the user for a server URL plus an optional static header (name +
 * value) they configure themselves — matching whatever their deployment's
 * reverse proxy or hosting environment actually enforces (basic auth
 * translated to a header, an API gateway token, or nothing at all).
 */
class ServerConfigStore(private val context: Context) {
    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyHeaderName = stringPreferencesKey("auth_header_name")
    private val securePrefs by lazy { encryptedPrefs(context) }

    val config: Flow<ServerConfig> =
        context.dataStore.data.map { prefs ->
            ServerConfig(
                baseUrl = prefs[keyBaseUrl] ?: "",
                authHeaderName = prefs[keyHeaderName] ?: "",
                authHeaderValue = securePrefs.getString(KEY_AUTH_HEADER_VALUE, "") ?: ""
            )
        }

    suspend fun current(): ServerConfig = config.first()

    suspend fun save(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[keyBaseUrl] =
                if (config.baseUrl.isBlank()) "" else config.baseUrl.trimEnd('/') + "/"
            prefs[keyHeaderName] = config.authHeaderName
        }
        securePrefs.edit().putString(KEY_AUTH_HEADER_VALUE, config.authHeaderValue).apply()
    }
}
