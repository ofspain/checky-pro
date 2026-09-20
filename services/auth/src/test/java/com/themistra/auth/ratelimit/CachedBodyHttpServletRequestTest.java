package com.themistra.auth.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CachedBodyHttpServletRequest} — T31. Proves the specific guarantee
 * {@link RateLimitFilter} depends on: the body can be read in full more than once, each time from
 * the start, unlike a raw {@code ServletInputStream}.
 */
class CachedBodyHttpServletRequestTest {

    private static final String BODY = "{\"token\":\"abc123\",\"newPassword\":\"correct-horse-battery\"}";

    @Test
    void getInputStreamCanBeReadInFullMoreThanOnce() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(raw);

        String firstRead = StreamUtils.copyToString(cached.getInputStream(), StandardCharsets.UTF_8);
        String secondRead = StreamUtils.copyToString(cached.getInputStream(), StandardCharsets.UTF_8);

        assertThat(firstRead).isEqualTo(BODY);
        assertThat(secondRead).isEqualTo(BODY);
    }

    @Test
    void getReaderReturnsTheFullBodyText() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(raw);

        BufferedReader reader = cached.getReader();
        String read = reader.lines().reduce("", (a, b) -> a + b);

        assertThat(read).isEqualTo(BODY);
    }

    @Test
    void handlesAnEmptyBodyWithoutError() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setContent(new byte[0]);
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(raw);

        String read = StreamUtils.copyToString(cached.getInputStream(), StandardCharsets.UTF_8);

        assertThat(read).isEmpty();
    }
}
