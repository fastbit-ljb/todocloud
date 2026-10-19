package com.todocloud.app.data

import java.util.Base64
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccessTokenIdentityTest {
    private fun token(id: Int, expiry: Int) = "header." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"sub\":\"$id\",\"exp\":$expiry}".toByteArray()) + ".signature"

    @Test fun refreshedTokenKeepsAccountButSwitchingAccountsIsRejected() {
        assertTrue(sameTokenAccount(token(1, 10), token(1, 20)))
        assertFalse(sameTokenAccount(token(1, 10), token(2, 20)))
        assertFalse(sameTokenAccount("invalid", token(1, 20)))
        assertFalse(sameTokenAccount("invalid", "invalid"))
    }
}
