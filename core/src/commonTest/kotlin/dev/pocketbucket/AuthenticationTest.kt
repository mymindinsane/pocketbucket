package dev.pocketbucket

import dev.pocketbucket.auth.*
import kotlin.test.*

class AuthenticationTest {
    @Test fun basicUsesUtf8Base64() {
        assertEquals("Basic YWxleDpwQHNz", Credentials.Basic("alex", "p@ss").authorizationHeader())
        assertEquals("Basic YWxleDrDqQ==", Credentials.Basic("alex", "é").authorizationHeader())
    }
    @Test fun tokenUsesBearerNotCloudSchema() {
        assertEquals("Bearer fictional-token", Credentials.Token("fictional-token").authorizationHeader())
    }
    @Test fun credentialsAreRedacted() {
        val auth = Credentials.Basic("fictional-user", "fictional-password")
        assertFalse(auth.toString().contains("fictional"))
        assertFalse(Connection(ServerUrl.parse("https://code.example.org"), auth).toString().contains("fictional"))
        assertFalse(Credentials.Token("fictional-token").toString().contains("fictional"))
    }
    @Test fun validatesCredentialsWithoutEchoingInput() {
        for (name in listOf("", "a:b", "a\nb")) assertFailsWith<IllegalArgumentException> { Credentials.Basic(name, "pass") }
        for (token in listOf("", "a\r\nb", "a b")) {
            val error = assertFailsWith<IllegalArgumentException> { Credentials.Token(token) }
            assertFalse(error.message.orEmpty().contains("a\r\nb"))
        }
    }
    @Test fun emptyPasswordAndBlankOrControlCharacterUsernamesAreRejected() {
        for (username in listOf(" ", "\t", "alex\u0000demo", "alex\rdemo")) {
            val error = assertFailsWith<IllegalArgumentException> { Credentials.Basic(username, "fictional-secret") }
            assertFalse(error.message.orEmpty().contains("fictional-secret"))
        }
        assertFailsWith<IllegalArgumentException> { Credentials.Basic("alex.demo", "") }
        for (token in listOf("fictional-token\t", "fictional\u0000token", " fictional-token")) {
            assertFailsWith<IllegalArgumentException> { Credentials.Token(token) }
        }
    }
    @Test fun passwordWhitespaceAndColonsArePreservedRatherThanTrimmed() {
        // Spaces and colons are valid password characters, even though the username cannot contain a colon.
        assertEquals("Basic YWxleDogcGFzcyA=", Credentials.Basic("alex", " pass ").authorizationHeader())
        assertEquals("Basic YWxleDphOmI=", Credentials.Basic("alex", "a:b").authorizationHeader())
    }
    @Test fun httpsAndContextPathAreSupported() {
        assertEquals("https://code.example.org/bitbucket", ServerUrl.parse(" https://code.example.org/bitbucket/ ").value)
    }
    @Test fun rejectsUnsafeOrInvalidUrls() {
        for (url in listOf("", "code.example.org", "http://code.example.org", "ftp://code.example.org",
            "https://user:pass@code.example.org", "https://code.example.org?token=secret",
            "https://code.example.org/#fragment", "https://code.example.org?", "https://code.example.org#")) {
            assertFailsWith<IllegalArgumentException>(url) { ServerUrl.parse(url) }
        }
    }
}
