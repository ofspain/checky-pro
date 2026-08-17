package com.themistra.auth.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Buffers the full request body up front so it can be read any number of times (T31) — needed
 * because {@code POST /accounts/password-reset}'s body is JSON, and {@link RateLimitFilter} must
 * peek at its {@code token} field before the real controller's own {@code @RequestBody}
 * deserialization runs downstream. Unlike Spring's own {@code ContentCachingRequestWrapper} (which
 * only caches what's read *through* it after the fact, and is therefore built for logging, not for
 * a caller that reads first), this wrapper fully buffers in the constructor and serves every
 * subsequent read from that buffer, so both this filter and the downstream MVC binding see the
 * complete body independently.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = StreamUtils.copyToByteArray(request.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Synchronous read only; this wrapper never needs async I/O notifications.
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
