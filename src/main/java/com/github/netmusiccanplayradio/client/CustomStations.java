package com.github.netmusiccanplayradio.client;

import com.github.netmusiccanplayradio.NetMusicCanPlayRadio;
import com.github.tartaricacid.netmusic.util.BigMegaphoneUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 玩家自定义电台配置读写（预设电台管理功能）。
 * <p>
 * 数据文件：{@code config/netmusiccanplayradio/stations.json}，格式与 NetMusic 内置预置一致：
 * <pre>
 * [ { "name": "幻想乡电台", "url": "https://stream.gensokyoradio.net/3" }, ... ]
 * </pre>
 * 校验复用 NetMusic 的 {@link BigMegaphoneUtil#isValidStreamUrl}（本 mod 的 Mixin 已放行
 * 非 .m3u8 的 http(s) 直链，因此可填任意电台直链/播放列表）。
 * 玩家台由 {@code BigMegaphonePresetManagerMixin} 合并进 NetMusic 的预设列表，
 * 在大喇叭"选择预设电台"页面与内置预置一起显示。
 */
public final class CustomStations {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CustomStations() {
    }

    public static Path getConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("netmusiccanplayradio/stations.json");
    }

    /** 读取玩家自定义电台；文件不存在/损坏返回空列表 */
    public static List<Station> load() {
        Path file = getConfigFile();
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Station> loaded = GSON.fromJson(reader, new TypeToken<List<Station>>() {
            }.getType());
            if (loaded == null) {
                return Collections.emptyList();
            }
            List<Station> valid = new ArrayList<>();
            for (Station station : loaded) {
                if (station == null || station.name() == null || station.name().isBlank()
                        || station.url() == null || station.url().isBlank()) {
                    continue;
                }
                String name = station.name().trim();
                String url = station.url().trim();
                if (BigMegaphoneUtil.isValidStreamUrl(url)) {
                    valid.add(new Station(name, url));
                }
            }
            return valid;
        } catch (IOException | RuntimeException e) {
            NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Failed to load custom stations from {}: {}",
                    getConfigFile(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 追加一个玩家电台并保存 */
    public static void add(String name, String url) {
        List<Station> all = new ArrayList<>(load());
        all.add(new Station(name.trim(), url.trim()));
        save(all);
    }

    /** 按索引更新一个玩家电台并保存 */
    public static void update(int index, String name, String url) {
        List<Station> all = new ArrayList<>(load());
        if (index >= 0 && index < all.size()) {
            all.set(index, new Station(name.trim(), url.trim()));
            save(all);
        }
    }

    /** 按索引删除一个玩家电台并保存 */
    public static void remove(int index) {
        List<Station> all = new ArrayList<>(load());
        if (index >= 0 && index < all.size()) {
            all.remove(index);
            save(all);
        }
    }

    /** 判断某台是否为玩家自定义台（按 name+url 匹配） */
    public static boolean isCustom(String name, String url) {
        for (Station station : load()) {
            if (station.name().equals(name) && station.url().equals(url)) {
                return true;
            }
        }
        return false;
    }

    private static void save(List<Station> stations) {
        try {
            Path file = getConfigFile();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(stations, writer);
            }
        } catch (IOException e) {
            NetMusicCanPlayRadio.LOGGER.warn("[NetMusicCanPlayRadio] Failed to save custom stations to {}: {}",
                    getConfigFile(), e.getMessage());
        }
    }

    public record Station(String name, String url) {
    }
}
