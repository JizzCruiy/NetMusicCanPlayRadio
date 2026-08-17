package com.github.netmusiccanplayradio.client.stream;

import com.github.netmusiccanplayradio.NetMusicCanPlayRadio;
import com.github.tartaricacid.netmusic.api.NetWorker;
import com.google.common.net.HttpHeaders;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 可自动重连、并按 ICY 协议剥离内嵌元数据的直播流输入流。
 * <p>
 * Icecast/Shoutcast 行为：若请求带 {@code Icy-MetaInt: 1}，服务器会在音频数据流中每隔
 * {@code Icy-MetaInt} 字节插入一个 1 字节长度字段 + 元数据块（长度 × 16 字节）。
 * 播放器解码时必须把这些元数据字节剥掉，否则解码器会错乱。
 * <p>
 * 断流/网络错误时：按指数退避重新建立连接（最多 {@link #MAX_RETRIES} 次），
 * 重连后重新解析 {@code Icy-MetaInt}（服务器可能改变该值）。
 * <p>
 * 支持批量 {@code read(byte[], off, len)}，且批量读取不会跨越 ICY 元数据边界。
 */
public class ReconnectingIcyStream extends InputStream {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_BACKOFF_MS = 1000;

    private final URL url;
    private final int maxRetries;

    private InputStream current;
    private int icyMetaInterval;
    private int bytesSinceMeta;
    private int attempts;
    private boolean closed;

    public ReconnectingIcyStream(URL url) {
        this(url, MAX_RETRIES);
    }

    public ReconnectingIcyStream(URL url, int maxRetries) {
        this.url = url;
        this.maxRetries = maxRetries;
        try {
            connect();
        } catch (IOException e) {
            NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Initial connect failed for {}", url, e);
            this.current = null;
        }
    }

    /** 建立（或重建）HTTP 连接，解析 Icy-MetaInt */
    private void connect() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(CONNECT_TIMEOUT)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 NetMusicCanPlayRadio/0.1")
                .header("Icy-MetaInt", "1")          // 请求 ICY 元数据（服务器可忽略）
                .GET().build();
        HttpResponse<InputStream> response = NetWorker.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        // 直播流通常返回 200（不支持 Range）；Shoutcast v1 可能返回 200 + ICY 头
        if (status < 200 || status >= 300) {
            throw new IOException("Stream request failed: HTTP " + status);
        }
        this.current = response.body();
        this.icyMetaInterval = parseIcyMetaInt(response);
        this.bytesSinceMeta = 0;
        this.attempts++;
        NetMusicCanPlayRadio.LOGGER.debug("[NetMusicCanPlayRadio] Connected {} (icy-meta-int={}, attempt={})",
                url, icyMetaInterval, attempts);
    }

    /** 从响应头解析 Icy-MetaInt，解析失败按 0 处理（无内嵌元数据） */
    private static int parseIcyMetaInt(HttpResponse<?> response) {
        try {
            String value = response.headers().firstValue("Icy-MetaInt").orElse(null);
            if (value == null) {
                value = response.headers().firstValue("icy-metaint").orElse(null);
            }
            if (value == null) {
                return 0;
            }
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n == -1 ? -1 : (one[0] & 0xFF);
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }
        while (true) {
            if (closed) {
                return -1;
            }
            if (current == null) {
                if (!reconnect()) {
                    return -1;
                }
                continue;
            }
            try {
                int n = readBulk(b, off, len);
                if (n > 0) {
                    return n;
                }
                if (n == -1 && !reconnect()) {
                    return -1;
                }
                // n == 0（如刚好停在元数据边界）→ 继续循环
            } catch (IOException e) {
                NetMusicCanPlayRadio.LOGGER.debug("[NetMusicCanPlayRadio] Read error on {}: {}", url, e.getMessage());
                if (!reconnect()) {
                    throw e;
                }
            }
        }
    }

    /**
     * 批量读取音频字节；不会跨越 ICY 元数据边界。
     * 返回 -1 表示底层流 EOF（等待外层重连），0 表示本次没有可取字节（元数据边界已消费）。
     */
    private int readBulk(byte[] b, int off, int len) throws IOException {
        if (icyMetaInterval <= 0) {
            return current.read(b, off, len);
        }
        int available = icyMetaInterval - bytesSinceMeta;
        if (available <= 0) {
            consumeMetaBlock();
            return 0;
        }
        int toRead = Math.min(len, available);
        int n = current.read(b, off, toRead);
        if (n > 0) {
            bytesSinceMeta += n;
        }
        return n;
    }

    /** 消费一个 ICY 元数据块（1 字节长度 + 长度×16 字节数据） */
    private void consumeMetaBlock() throws IOException {
        int metaLen = current.read();
        if (metaLen == -1) {
            return;
        }
        skipFully(current, metaLen * 16L);
        bytesSinceMeta = 0;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    return;
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    /** 指数退避重连；超过最大次数返回 false */
    private boolean reconnect() {
        if (closed || attempts >= maxRetries) {
            return false;
        }
        long backoff = INITIAL_BACKOFF_MS << Math.min(attempts, 4);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            connect();
            return true;
        } catch (IOException e) {
            NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Reconnect {}/{} failed for {}: {}",
                    attempts, maxRetries, url, e.getMessage());
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        if (current != null) {
            current.close();
        }
    }
}
