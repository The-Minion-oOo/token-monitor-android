package io.github.theminionooo.tokenmonitor

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import org.junit.Assert.*
import org.junit.Test

class InternetPairingStorageTest {
    @Test fun consentRoundTripsEncryptedAndLegacyRecordsStayOff() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        check(target.packageName.endsWith(".preview"))
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): java.io.File = java.io.File(target.cacheDir, "internet-pairing-test").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences("internet-pairing-test-$name", mode)
        }
        val store = SecureConnectionStore(context)
        val saved = HubConnection("https://hub.example.com", "synthetic-storage-secret", false, allowPublicHttps = true)
        try {
            store.save(saved)
            assertEquals(saved, store.read())
            assertFalse(context.getSharedPreferences("secure_connection", 0).all.values.any { it.toString().contains(saved.secret) })
            val old = HubConnection("http://100.88.1.2:17321", "synthetic-storage-secret", false, "http://192.168.1.2:17321")
            val parts = store.encode(old).split('.')
            assertFalse(store.decode(parts.take(3).joinToString("."))!!.allowPublicHttps)
            assertEquals(old, store.decode(parts.take(4).joinToString(".")))
            assertFalse(store.decode(parts.take(4).joinToString("."))!!.allowPublicHttps)
            assertNull(store.decode(parts.take(4).joinToString(".") + ".invalid"))
            store.save(saved.copy(allowPublicHttps = false))
            assertFalse(store.read()!!.allowPublicHttps)
            store.save(saved)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val repository = io.github.theminionooo.tokenmonitor.data.HubRepository(context) { null }
                try {
                    repository.disablePublicHttps()
                    assertFalse(repository.state.value.allowPublicHttps)
                    assertFalse(store.read()!!.allowPublicHttps)
                    assertThrows(io.github.theminionooo.tokenmonitor.data.network.HubApiException::class.java) {
                        io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator.requireAllowed(store.read()!!)
                    }
                } finally { repository.close() }
            }
        } finally { store.clear() }
    }
}
