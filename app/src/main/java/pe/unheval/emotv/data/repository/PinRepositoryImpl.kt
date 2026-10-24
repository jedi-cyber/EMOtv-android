package pe.unheval.emotv.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.unheval.emotv.data.local.PinHasher
import pe.unheval.emotv.data.local.ValueStore
import pe.unheval.emotv.domain.repository.PinRepository

class PinRepositoryImpl(
    private val store: ValueStore,
    private val hasher: PinHasher = PinHasher(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PinRepository {

    override suspend fun hasPin(): Boolean = store.read() != null

    override suspend fun savePin(pin: String) {
        val hash = withContext(dispatcher) { hasher.hash(pin) }
        store.write(hash)
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val stored = store.read() ?: return false
        return withContext(dispatcher) { hasher.verify(pin, stored) }
    }

    override suspend fun clear() = store.clear()
}
