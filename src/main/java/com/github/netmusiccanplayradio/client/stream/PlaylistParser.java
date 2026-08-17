package com.github.netmusiccanplayradio.client.stream;

import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简播放列表解析器：支持 .pls 与 .m3u 的常见形式，返回第一个可用的流地址。
 * <p>
 * 这是社区播放列表的常用格式；复杂变体（.xspf / 加密 m3u8）不在本版本支持范围内，
 * 解析失败时 {@link IcecastStreamHandler} 会抛出明确错误。
 */
final class PlaylistParser {
    private static final Pattern PLS_FILE = Pattern.compile("(?im)^\\s*File\\d+\\s*=\\s*(.+?)\\s*$");
    private static final Pattern PLS_STREAM = Pattern.compile("(?im)^\\s*Stream\\d+\\s*=\\s*(.+?)\\s*$");
    /** #EXTINF 之后、下一行才是 #EXTINF 对应的 URL */
    private static final Pattern M3U_URL = Pattern.compile("(?im)^\\s*(https?://\\S+)\\s*$");

    private PlaylistParser() {
    }

    /**
     * @param body   播放列表内容
     * @param baseUrl 播放列表自身 URL（用于解析相对地址，本版本仅支持绝对地址）
     * @return 第一个 http(s) 流地址，解析不到返回 null
     */
    static String parse(String body, URL baseUrl) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("[playlist]")) {
            return firstMatch(body, PLS_FILE, PLS_STREAM);
        }
        // .m3u 或未知：先找 #EXTINF，其下一行是 URL；没有 EXTINF 就找任意 http 行
        String extinfUrl = extractAfterExtInf(body);
        if (extinfUrl != null) {
            return extinfUrl;
        }
        return firstMatch(body, M3U_URL);
    }

    private static String extractAfterExtInf(String body) {
        String[] lines = body.split("\\r?\\n");
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].toUpperCase(Locale.ROOT).startsWith("#EXTINF")) {
                String candidate = lines[i + 1].trim();
                if (candidate.toLowerCase(Locale.ROOT).startsWith("http")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String firstMatch(String body, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(body);
            while (matcher.find()) {
                String candidate = matcher.group(1).trim();
                if (candidate.toLowerCase(Locale.ROOT).startsWith("http")) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
