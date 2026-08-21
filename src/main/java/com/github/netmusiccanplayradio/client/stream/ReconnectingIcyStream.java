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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可自动重连、按 ICY 协议剥离内嵌元数据的直播流输入流。
 * <p>
 * Icecast/Shoutcast 行为：若请求带 {@code Icy-MetaInt: 1}，服务器会在音频数据流中每隔
 * {@code Icy-MetaInt} 字节插入一个 1 字节长度字段 + 元数据块（长度 × 16 字节）。
 * 播放器解码时必须把这些元数据字节剥掉，否则解码器会错乱。
 * <p>
 * 重连策略（v0.2.0，遵循工业播放器实践，见 docs/10）：
 * <ul>
 *   <li>HTTP 4xx（403 封禁 / 404 不存在等）= 源站明确拒绝 → 立即失败，<b>不重试</b>
 *       （源站常按 IP 限制并发/异常连接，重试风暴会触发更严厉限制，甚至连累同 IP 其他播放器）；</li>
 *   <li>网络层错误（超时/重置/DNS）= 暂时性 → 指数退避重连，次数封顶
 *       {@link #MAX_RETRIES} 次（3 次，ExoPlayer 同区间）；</li>
 *   <li>初次连接失败会通过 {@link #throwIfInitialFailed()} 立即抛出，让上层走"播放失败"提示，
 *       而不是静默无声。</li>
 * </ul>
 * 支持批量 {@code read(byte[], off, len)}，且批量读取不会跨越 ICY 元数据边界。
 */
public class ReconnectingIcyStream extends InputStream {
    /** 重连次数封顶：3 次（ExoPlayer 默认区间 2~3），避免"重连风暴"触发源站 IP 限制 */
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_BACKOFF_MS = 1000;

    // 注意：不设 HttpRequest.timeout —— java.net.http 的请求超时对"流式响应体"的覆盖范围
    // 在 JDK 各版本行为不一致（见 OpenJDK JDK-8383522），实测在 Java 21 下会每 ~10 秒截断
    // 一次仍在读取的直播流（表现为周期性断流+重连）。连接建立超时由 NetMusic 的
    // NetWorker.HTTP_CLIENT.connectTimeout(5s) 兜底即可。

    /** 通用浏览器 UA：不暴露 mod 身份，避免源站按 UA 过滤（实测三种 UA 均 200，此处取最稳妥） */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    /**
     * 当前是否有流处于"重连中"（任意流，简化版）：-1 表示无；否则为进入重连的时间戳（ms）。
     * 供客户端 tick 检测后向玩家提示"网络不佳，正在重连…"（见 ReconnectNotifier）。
     */
    private static final AtomicLong RECONNECT_STARTED_AT = new AtomicLong(-1);

    private final URL url;
    private final int maxRetries;

    private InputStream current;
    private int icyMetaInterval;
    private int bytesSinceMeta;
    private int attempts;
    private boolean closed;
    /** 初次连接失败原因（null = 初次连接成功）；4xx 拒绝与网络错误都记录 */
    private IOException initialFailure;

    public ReconnectingIcyStream(URL url) {
        this(url, MAX_RETRIES);
    }

    public ReconnectingIcyStream(URL url, int maxRetries) {
        this.url = url;
        this.maxRetries = maxRetries;
        try {
            connect();
        } catch (IOException e) {
            NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Initial connect failed for {}: {}", url, e.getMessage());
            this.current = null;
            this.initialFailure = e;
        }
    }

    /**
     * 若初次连接失败则抛出原因，让上层（IcecastStreamHandler → NetMusic 播放层）
     * 立即给出"播放失败"提示，而不是静默无声。
     */
    public void throwIfInitialFailed() throws IOException {
        if (this.initialFailure != null) {
            throw this.initialFailure;
        }
    }

    /** 当前是否有流处于重连中（时间戳，-1 表示无） */
    public static long getReconnectStartedAt() {
        return RECONNECT_STARTED_AT.get();
    }

    /** 建立（或重建）HTTP 连接，解析 Icy-MetaInt */
    private void connect() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header("Icy-MetaInt", "1")          // 请求 ICY 元数据（服务器可忽略）
                .GET().build();
        HttpResponse<InputStream> response = NetWorker.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        // 4xx = 源站明确拒绝（403 封禁/404 不存在等），重连无意义且会加剧限制
        if (status >= 400 && status < 500) {
            throw new StreamRejectedException(status);
        }
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
                // 4xx 拒绝：立即结束，不重连（源站明确拒绝，重试无意义且加剧限制）
                if (e instanceof StreamRejectedException) {
                    NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Stream rejected (HTTP {}) for {}: no more retries",
                            ((StreamRejectedException) e).getStatusCode(), url);
                    return -1;
                }
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

    /** 指数退避重连；超过最大次数返回 false。4xx 拒绝不重试。 */
    private boolean reconnect() {
        if (closed || attempts >= maxRetries) {
            clearReconnectState();
            return false;
        }
        // 记录重连开始时间（供"网络不佳"提示使用；全局简化版：多流并发时取首个）
        RECONNECT_STARTED_AT.compareAndSet(-1, System.currentTimeMillis());
        long backoff = INITIAL_BACKOFF_MS << Math.min(attempts, 4);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            clearReconnectState();
            return false;
        }
        try {
            connect();
            clearReconnectState();
            return true;
        } catch (StreamRejectedException e) {
            // 4xx：不重试，直接结束
            NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Stream rejected (HTTP {}) for {}: no more retries",
                    e.getStatusCode(), url);
            clearReconnectState();
            return false;
        } catch (IOException e) {
            NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Reconnect {}/{} failed for {}: {}",
                    attempts, maxRetries, url, e.getMessage());
            return false;
        }
    }

    private static void clearReconnectState() {
        RECONNECT_STARTED_AT.set(-1);
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
        clearReconnectState();
        if (current != null) {
            current.close();
        }
    }

    /** 源站明确拒绝（HTTP 4xx）：区别于一般网络错误的信号，调用方据此不重试 */
    public static final class StreamRejectedException extends IOException {
        private final int statusCode;

        public StreamRejectedException(int statusCode) {
            super("Stream rejected by server: HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
