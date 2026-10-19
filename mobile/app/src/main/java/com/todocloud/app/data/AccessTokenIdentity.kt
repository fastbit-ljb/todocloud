package com.todocloud.app.data

import java.util.Base64
import org.json.JSONObject

/** Client-side account-switch guard only; the server still verifies every signature. */
internal fun sameTokenAccount(first: String, second: String): Boolean {
    fun subject(token: String): String? = runCatching {
        JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8))
            .getString("sub").takeIf { it.isNotBlank() }
    }.getOrNull()
    val expected = subject(first) ?: return false
    return expected == subject(second)
}
