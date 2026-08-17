package com.github.netmusiccanplayradio.client.stream;

import com.github.netmusiccanplayradio.NetMusicCanPlayRadio;
import com.github.tartaricacid.netmusic.api.NetWorker;
import com.github.tartaricacid.netmusic.client.api.IAudioStreamHandler;
import com.google.common.net.HttpHeaders;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Icecast / Shoutcast 无限直播流处理器。
 * <p>
 * 与 NetMusic 自带 {@code DirectHttpHandler} 的区别：
 * <ul>
 *   <li>无扩展名的 URL（电台 mount 点，如 stream.gensokyoradio.net/...）判定为直播流，由本处理器负责；</li>
 *   <li>支持 .pls / .m3u 播放列表，解析出真实流地址再播；</li>
 *   <li>请求带 {@code Icy-MetaInt} 头，并按协议剥离内嵌的 ICY 元数据（否则解码器会吃到元数据字节而错乱）；</li>
 *   <li>断流/网络错误时自动重连（带指数退避），适合 24/7 电台。</li>
 * </ul>
 * <p>
 * 优先级取 50：高于 {@code DirectHttpHandler}(0)、低于 {@code M3u8Handler}(100)，因此
 * .m3u8 仍由 NetMusic 的 HLS 处理器负责，普通音频文件直链仍由 DirectHttpHandler 负责。
 */
public class IcecastStreamHandler implements IAudioStreamHandler {
    /** 常见音频文件扩展名：带这些扩展名的直链交给 DirectHttpHandler 正常下载播放 */
    private static final Set<String> AUDIO_FILE_EXTS = Set.of(
            ".mp3", ".ogg", ".flac", ".wav", ".aac", ".m4a", ".opus", ".aiff", ".wma", ".mid", ".midi");

    /** 播放列表扩展名：本处理器负责解析 */
    private static final Set<String> PLAYLIST_EXTS = Set.of(".pls", ".m3u");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    @Override
    public boolean canHandle(URL url) {
        String protocol = url.getProtocol();
        if (!HTTP.equalsIgnoreCase(protocol) && !HTTPS.equalsIgnoreCase(protocol)) {
            return false;
        }
        String path = url.getPath().toLowerCase(Locale.ROOT);
        // .m3u8 (HLS) 交给 NetMusic 的 M3u8Handler
        if (path.endsWith(".m3u8")) {
            return false;
        }
        // .pls / .m3u 播放列表：由我们解析
        for (String ext : PLAYLIST_EXTS) {
            if (path.endsWith(ext)) {
                return true;
            }
        }
        // 其余 http(s)：无常见音频扩展名的当作直播流（电台 mount 点通常没有扩展名）
        for (String ext : AUDIO_FILE_EXTS) {
            if (path.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public AudioInputStream handle(URL url) throws UnsupportedAudioFileException, IOException {
        URL streamUrl = resolvePlaylistIfNeeded(url);
        ReconnectingIcyStream stream = new ReconnectingIcyStream(streamUrl);
        // 大缓冲：让 javazoom/jaad 的 SPI 嗅探器有足够的 mark/reset 空间识别格式
        BufferedInputStream bis = new BufferedInputStream(stream, 128 * 1024);
        return AudioSystem.getAudioInputStream(bis);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    /**
     * 若 URL 是 .pls/.m3u 播放列表，则请求并解析出真实流地址（最多展开一层，防止循环）。
     */
    private URL resolvePlaylistIfNeeded(URL url) throws IOException {
        String path = url.getPath().toLowerCase(Locale.ROOT);
        boolean isPlaylist = false;
        for (String ext : PLAYLIST_EXTS) {
            if (path.endsWith(ext)) {
                isPlaylist = true;
                break;
            }
        }
        if (!isPlaylist) {
            return url;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(CONNECT_TIMEOUT)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 NetMusicCanPlayRadio/0.1")
                .GET().build();
        HttpResponse<String> response = NetWorker.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Playlist request failed: " + response.statusCode());
        }
        String body = response.body();
        String resolved = PlaylistParser.parse(body, url);
        if (resolved == null) {
            throw new IOException("无法从播放列表解析出流地址: " + url);
        }
        NetMusicCanPlayRadio.LOGGER.info("[NetMusicCanPlayRadio] Playlist {} -> stream {}", url, resolved);
        return URI.create(resolved).toURL();
    }
}
