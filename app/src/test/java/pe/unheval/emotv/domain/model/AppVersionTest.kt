package pe.unheval.emotv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `interpreta x y z e ignora sufijos`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("1.0.0rc1"))
        assertEquals(AppVersion(1, 0, 0), AppVersion.parse("1.0"))
        assertNull(AppVersion.parse("versión"))
    }

    @Test
    fun `compara por número y no por texto`() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertTrue(AppVersion.parse("0.9.0")!! < AppVersion.parse("1.0.0")!!)
        assertEquals(0, AppVersion.parse("1.0.0")!!.compareTo(AppVersion.parse("1.0")!!))
    }
}
