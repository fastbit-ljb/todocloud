package com.todocloud.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal class DeletedConversationException(val conversationId: String) : IllegalStateException("会话已删除，请新建会话")

/** Encrypted, account-separated cache. Local mode never contacts the server. */
internal class ConversationStore(context: Context) {
    private val storage = SecureSessionStore(context, "todocloud_conversations")
    private val mutex = Mutex()

    private fun accountKey(account: String): String = MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun storedIds(key: String): Set<String> {
        val stored = JSONArray(storage.get(key) ?: "[]")
        return (0 until stored.length()).mapTo(linkedSetOf()) { stored.getString(it) }
    }

    suspend fun deletedIds(account: String): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock { storedIds("${accountKey(account)}:deleted") }
    }

    suspend fun historyMigrated(account: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { storage.get("${accountKey(account)}:migrated") == "true" }
    }

    suspend fun markHistoryMigrated(account: String) = withContext(Dispatchers.IO) {
        mutex.withLock { storage.put("${accountKey(account)}:migrated", "true") }
    }

    suspend fun list(account: String): List<JSONObject> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            val ids = JSONArray(storage.get("$prefix:index") ?: "[]")
            val deleted = storedIds("$prefix:deleted")
            (0 until ids.length()).mapNotNull { index ->
                ids.getString(index).takeUnless { it in deleted }?.let { storage.get("$prefix:$it") }?.let(::JSONObject)
            }
        }
    }

    suspend fun get(account: String, id: String): JSONObject? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            if (id in storedIds("$prefix:deleted")) null else storage.get("$prefix:$id")?.let(::JSONObject)
        }
    }

    suspend fun put(account: String, value: JSONObject) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            val id = value.getString("id")
            if (id in storedIds("$prefix:deleted")) throw DeletedConversationException(id)
            val stored = JSONArray(storage.get("$prefix:index") ?: "[]")
            val ids = (0 until stored.length()).map { stored.getString(it) }.toMutableSet().apply { add(id) }
            storage.put("$prefix:$id", value.toString())
            storage.put("$prefix:index", JSONArray(ids.toList()).toString())
        }
    }

    suspend fun remove(account: String, ids: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            // Write the marker first: interrupted cleanup must never restore the content.
            storage.putSynchronously("$prefix:deleted", JSONArray((storedIds("$prefix:deleted") + ids).toList()).toString())
            ids.forEach { storage.remove("$prefix:$it") }
            storage.put("$prefix:index", JSONArray((storedIds("$prefix:index") - ids).toList()).toString())
        }
    }

    suspend fun clear(account: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            val stored = JSONArray(storage.get("$prefix:index") ?: "[]")
            (0 until stored.length()).forEach { storage.remove("$prefix:${stored.getString(it)}") }
            storage.remove("$prefix:index")
            storage.remove("$prefix:deleted")
            storage.remove("$prefix:migrated")
        }
    }
}
