package com.ran.app

import com.ran.app.data.RESET_PASSPHRASE
import com.ran.app.data.isResetAuthorised
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the star-reset guard.
 *
 * The passphrase is not a security control — it ships inside the APK and anyone
 * unpacking the binary can read it. What it has to be is reliable in the only
 * direction that matters here: it must not accept anything other than the exact
 * string, because the failure mode is a child wiping a month of progress.
 */
class StarResetTest {

    @Test
    fun `the exact passphrase is accepted`() {
        assertTrue(isResetAuthorised("行微易成54321"))
    }

    @Test
    fun `near misses are rejected`() {
        val wrong = listOf(
            "",                        // empty
            "行微易成",                 // missing digits
            "行微易成543210",           // extra digit
            "行微易成 54321",           // inserted space
            " 行微易成54321",           // leading space
            "行微易成54321 ",           // trailing space
            "行微易成5432l",            // letter for digit
            "行微易成54321\n",          // newline appended
            "\n行微易成54321",          // newline prepended
            "xingweiyicheng54321",     // pinyin instead of characters
            "行微易成5432",            // one digit short
            "行微易成５４３２１"          // full-width digits
        )
        for (candidate in wrong) {
            assertFalse(
                "should have been rejected: '$candidate'",
                isResetAuthorised(candidate)
            )
        }
    }

    @Test
    fun `the check does not mutate its input`() {
        val entry = "行微易成54321"
        isResetAuthorised(entry)
        assertTrue("passphrase string was altered", entry == RESET_PASSPHRASE)
    }
}