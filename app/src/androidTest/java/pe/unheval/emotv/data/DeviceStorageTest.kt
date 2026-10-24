package pe.unheval.emotv.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import pe.unheval.emotv.data.local.EncryptedRefreshTokenStore
import pe.unheval.emotv.data.local.KeystoreTokenCipher
import pe.unheval.emotv.data.local.PinHashStore
import pe.unheval.emotv.data.remote.AccessTokenHolder
import pe.unheval.emotv.data.remote.NetworkFactory
import pe.unheval.emotv.data.repository.AuthRepositoryImpl
import pe.unheval.emotv.data.repository.PinRepositoryImpl
import pe.unheval.emotv.domain.usecase.LogoutUseCase
import java.io.File
import java.security.KeyStore

/**
 * Comprueba en el dispositivo lo que queda en el almacenamiento del aplicativo: ninguna
 * imagen, y tras cerrar sesión ni el token ni el hash del PIN. Ejecutarla también después
 * de una sesión de demostración sirve para revisar que no quedó nada.
 */
@RunWith(AndroidJUnit4::class)
class DeviceStorageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val storeFile = File(context.filesDir, "datastore/emotv_storage_test.preferences_pb")

    @After
    fun cleanUp() {
        scope.cancel()
        storeFile.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(TEST_KEY_ALIAS)
    }

    @Test
    fun cerrarSesionBorraElTokenYElHashDelPin() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { storeFile }
        val refreshStore = EncryptedRefreshTokenStore(dataStore, KeystoreTokenCipher(alias = TEST_KEY_ALIAS))
        val pinStore = PinHashStore(dataStore)
        refreshStore.write("token-de-prueba")
        pinStore.write("hash-de-prueba")

        // Sin token de acceso en memoria, el cierre de sesión no llama al servidor.
        val auth = AuthRepositoryImpl(
            api = NetworkFactory.createApi("http://127.0.0.1:9/", AccessTokenHolder()),
            refreshTokenStore = refreshStore,
            tokenHolder = AccessTokenHolder(),
        )
        LogoutUseCase(auth, PinRepositoryImpl(pinStore)).invoke()

        assertNull(refreshStore.read())
        assertNull(pinStore.read())
        val raw = storeFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("refresh_token"))
        assertFalse(raw.contains("pin_hash"))
    }

    @Test
    fun noHayImagenesEnElAlmacenamientoDelAplicativo() {
        val roots = listOfNotNull(
            context.dataDir,
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(null),
        )
        val images = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.isImage() }.toList() }
        assertEquals("Imágenes en el almacenamiento: $images", emptyList<File>(), images)
    }

    private fun File.isImage(): Boolean {
        if (extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "bmp")) return true
        if (length() < 4) return false
        val header = ByteArray(4)
        val read = runCatching { inputStream().use { it.read(header) } }.getOrDefault(0)
        if (read < 4) return false
        val jpeg = header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
        val png = header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte()
        val webp = header.contentEquals("RIFF".toByteArray())
        return jpeg || png || webp
    }

    private companion object {
        const val TEST_KEY_ALIAS = "emotv_storage_test"
    }
}
