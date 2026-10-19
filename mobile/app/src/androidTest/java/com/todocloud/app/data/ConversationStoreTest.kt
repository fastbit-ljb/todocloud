package com.todocloud.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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

    @Test fun deletedRecordCannotReturnOrBeWrittenAgain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = "conversation-delete@example.invalid"
        val id = "deleted-test"
        val keptId = "kept-test"
        val store = ConversationStore(context)
        try {
            store.clear(account)
            store.put(account, JSONObject().put("id", id).put("title", "删除我").put("messages", JSONArray()))
            store.put(account, JSONObject().put("id", keptId).put("title", "保留我").put("messages", JSONArray()))
            store.remove(account, setOf(id))
            store.remove(account, setOf(id))

            val reopened = ConversationStore(context)
            assertNull(reopened.get(account, id))
            assertEquals(listOf(keptId), reopened.list(account).map { it.getString("id") })
            assertEquals(setOf(id), reopened.deletedIds(account))
            try {
                reopened.put(account, JSONObject().put("id", id).put("title", "复活").put("messages", JSONArray()))
                fail("deleted conversation was written again")
            } catch (expected: DeletedConversationException) {
                assertEquals(id, expected.conversationId)
            }
        } finally { store.clear(account) }
    }

    @Test fun migrationMarkerSurvivesStoreRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = "conversation-migration@example.invalid"
        val store = ConversationStore(context)
        try {
            store.clear(account)
            assertTrue(!store.historyMigrated(account))
            store.markHistoryMigrated(account)
            store.put(account, JSONObject().put("id", "last").put("title", "最后一条").put("messages", JSONArray()))
            store.remove(account, setOf("last"))
            assertTrue(ConversationStore(context).historyMigrated(account))
            assertTrue(ConversationStore(context).list(account).isEmpty())
        } finally { store.clear(account) }
    }
}
