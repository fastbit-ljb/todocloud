package com.todocloud.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationStoreTest {
    @Test fun recordsSurviveStoreRecreationAndAreIsolatedByAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ConversationStore(context)
        val accounts = listOf("conversation-test-a@example.invalid", "conversation-test-b@example.invalid")
        try {
            accounts.forEach { store.clear(it) }
            store.put(accounts[0], JSONObject().put("id", "test").put("title", "仅属于账号 A").put("messages", JSONArray()))
            assertTrue(store.list(accounts[1]).isEmpty())
            store.put(accounts[1], JSONObject().put("id", "test").put("title", "仅属于账号 B").put("messages", JSONArray()))
            val reopened = ConversationStore(context)
            assertEquals("仅属于账号 A", reopened.get(accounts[0], "test")!!.getString("title"))
            assertEquals("仅属于账号 B", reopened.get(accounts[1], "test")!!.getString("title"))
        } finally { accounts.forEach { store.clear(it) } }
    }
}
