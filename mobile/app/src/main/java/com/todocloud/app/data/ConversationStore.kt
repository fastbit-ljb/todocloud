package com.todocloud.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Encrypted, account-separated cache. Local mode never contacts the server. */
internal class ConversationStore(context: Context) {
    private val storage = SecureSessionStore(context, "todocloud_conversations")
    private val mutex = Mutex()

    private fun accountKey(account: String): String = MessageDigest.getInstance("SHA-256")
        .digest(account.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun list(account: String): List<JSONObject> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            val ids = JSONArray(storage.get("$prefix:index") ?: "[]")
            (0 until ids.length()).mapNotNull { index ->
                storage.get("$prefix:${ids.getString(index)}")?.let(::JSONObject)
            }
        }
    }

    suspend fun get(account: String, id: String): JSONObject? = withContext(Dispatchers.IO) {
        mutex.withLock { storage.get("${accountKey(account)}:$id")?.let(::JSONObject) }
    }

    suspend fun put(account: String, value: JSONObject) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            val id = value.getString("id")
            val stored = JSONArray(storage.get("$prefix:index") ?: "[]")
            val ids = (0 until stored.length()).map { stored.getString(it) }.toMutableSet().apply { add(id) }
            storage.put("$prefix:$id", value.toString())
            storage.put("$prefix:index", JSONArray(ids.toList()).toString())
        }
    }

    suspend fun clear(account: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = accountKey(account)
            val stored = JSONArray(storage.get("$prefix:index") ?: "[]")
            (0 until stored.length()).forEach { storage.remove("$prefix:${stored.getString(it)}") }
            storage.remove("$prefix:index")
        }
    }
}
