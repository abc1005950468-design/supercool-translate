package com.mine.offlinetranslate

import android.content.SharedPreferences
import com.mine.offlinetranslate.core.AsrMode
import com.mine.offlinetranslate.core.CloudConfig
import com.mine.offlinetranslate.core.MtMode
import com.mine.offlinetranslate.core.ProviderStore
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStoreTest {
    private val sample = "test fixture value"

    @Test
    fun keyIsCommittedBeforeSavingOtherSettingsAndBlankInputPreservesIt() {
        val disk = mutableMapOf<String, Any>()
        val prefs = MemoryPreferences(disk)
        val store = ProviderStore(prefs.handle, prefs.handle)

        assertTrue(store.saveApiKey("  $sample  "))
        assertFalse(store.saveApiKey("  "))
        assertEquals(sample, reload(disk).cloud.apiKey)
    }

    @Test
    fun switchingEnginesPreservesKeyAcrossStoreInstances() {
        val disk = mutableMapOf<String, Any>()
        val prefs = MemoryPreferences(disk)
        val store = ProviderStore(prefs.handle, prefs.handle)
        val stale = store.load()
        assertTrue(store.saveApiKey(sample))

        store.save(stale.copy(asr = AsrMode.CLOUD, mt = MtMode.CLOUD))
        assertEquals(sample, store.load().cloud.apiKey)
        store.save(stale.copy(asr = AsrMode.LOCAL, mt = MtMode.LOCAL_LLM))
        prefs.flush()
        val offline = reload(disk)
        assertEquals(AsrMode.LOCAL, offline.asr)
        assertEquals(MtMode.LOCAL_LLM, offline.mt)
        assertEquals(sample, offline.cloud.apiKey)
        store.save(offline.copy(mt = MtMode.CLOUD, cloud = CloudConfig()))
        prefs.flush()
        assertEquals(MtMode.CLOUD, reload(disk).mt)
        assertEquals(sample, reload(disk).cloud.apiKey)
    }

    @Test
    fun explicitClearRemovesKeyAndStaleSettingsCannotRestoreIt() {
        val disk = mutableMapOf<String, Any>()
        val prefs = MemoryPreferences(disk)
        val store = ProviderStore(prefs.handle, prefs.handle)
        assertTrue(store.saveApiKey(sample))
        val stale = store.load()

        assertTrue(store.clearApiKey())
        assertEquals("", reload(disk).cloud.apiKey)
        store.save(stale)
        prefs.flush()
        assertEquals("", reload(disk).cloud.apiKey)
        assertFalse(disk.containsKey("cloud_key"))
    }

    @Test
    fun unavailableEncryptionRejectsKeyChangesAndKeepsEngineSettingsUsable() {
        val disk = mutableMapOf<String, Any>()
        val prefs = MemoryPreferences(disk)
        val store = ProviderStore(prefs.handle, null)

        assertFalse(store.saveApiKey(sample))
        assertFalse(store.clearApiKey())
        store.save(store.load().copy(mt = MtMode.MLKIT, cloud = CloudConfig(apiKey = sample)))
        prefs.flush()
        assertEquals(MtMode.MLKIT, store.load().mt)
        assertFalse(disk.containsKey("cloud_key"))
    }

    private fun reload(disk: MutableMap<String, Any>) = MemoryPreferences(disk).handle.let {
        ProviderStore(it, it).load()
    }

    private class MemoryPreferences(private val disk: MutableMap<String, Any>) {
        private val memory = disk.toMutableMap()

        val handle = proxy(SharedPreferences::class.java) { method, args ->
            when (method) {
                "getString", "getBoolean" -> memory[args[0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preferences method: $method")
            }
        }

        fun flush() {
            disk.clear()
            disk.putAll(memory)
        }

        private fun editor(): SharedPreferences.Editor {
            val changes = mutableMapOf<String, Any?>()
            return proxy(SharedPreferences.Editor::class.java) { method, args ->
                when (method) {
                    "putString", "putBoolean" -> {
                        changes[args[0] as String] = args[1]
                        null
                    }
                    "remove" -> {
                        changes[args[0] as String] = null
                        null
                    }
                    "apply", "commit" -> {
                        changes.forEach { (key, value) ->
                            if (value == null) memory.remove(key) else memory[key] = value
                        }
                        if (method == "commit") {
                            flush()
                            true
                        } else {
                            null
                        }
                    }
                    else -> error("Unexpected editor method: $method")
                }
            }
        }

        private fun <T> proxy(type: Class<T>, call: (String, Array<out Any?>) -> Any?): T =
            type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
                val result = call(method.name, args ?: emptyArray())
                if (method.returnType == type && result == null) instance else result
            })!!
    }
}
