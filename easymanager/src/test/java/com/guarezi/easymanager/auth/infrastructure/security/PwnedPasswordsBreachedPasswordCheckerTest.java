package com.guarezi.easymanager.auth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// sha1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8 (40 hex
// chars), used here to verify the k-anonymity split (5-char prefix sent
// to the API, 35-char suffix matched locally) without ever sending the
// real password itself over the wire.
@RestClientTest(PwnedPasswordsBreachedPasswordChecker.class)
class PwnedPasswordsBreachedPasswordCheckerTest {

    @Autowired
    private PwnedPasswordsBreachedPasswordChecker checker;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void reportsBreachedWhenSuffixMatchesAResponseLine() {
        server.expect(requestTo("https://api.pwnedpasswords.com/range/5BAA6"))
                .andRespond(withSuccess(
                        "1E4C9B93F3F0682250B6CF8331B7EE68FD8:12345\r\nAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:1",
                        MediaType.TEXT_PLAIN));

        assertThat(checker.isBreached("password")).isTrue();
    }

    @Test
    void reportsNotBreachedWhenNoSuffixMatches() {
        server.expect(requestTo("https://api.pwnedpasswords.com/range/5BAA6"))
                .andRespond(withSuccess(
                        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:1",
                        MediaType.TEXT_PLAIN));

        assertThat(checker.isBreached("password")).isFalse();
    }
}
