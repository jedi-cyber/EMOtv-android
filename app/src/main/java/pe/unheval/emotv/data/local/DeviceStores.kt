package pe.unheval.emotv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Un valor de texto guardado en el dispositivo. */
interface ValueStore {
    suspend fun read(): String?
    suspend fun write(value: String)
    suspend fun clear()
}

/**
 * Token de renovación cifrado con [TokenCipher]. Si no se puede descifrar (por ejemplo,
 * la clave del Keystore se perdió), se borra y se pide iniciar sesión de nuevo.
 */
class EncryptedRefreshTokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : ValueStore {

    override suspend fun read(): String? {
        val stored = dataStore.data.first()[KEY] ?: return null
        return runCatching { cipher.decrypt(stored) }.getOrElse {
            clear()
            null
        }
    }

    override suspend fun write(value: String) {
        val encrypted = cipher.encrypt(value)
        dataStore.edit { it[KEY] = encrypted }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("refresh_token")
    }
}

/**
 * Las versiones que conducían sesiones guardaban el hash de un PIN para salir del modo
 * sesión. Ya no se usa: se borra al iniciar el aplicativo.
 */
suspend fun DataStore<Preferences>.removeLegacyPinHash() {
    edit { it.remove(stringPreferencesKey("pin_hash")) }
}
