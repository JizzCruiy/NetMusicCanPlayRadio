package com.github.netmusiccanplayradio.client;

import com.github.netmusiccanplayradio.NetMusicCanPlayRadio;
import com.github.tartaricacid.netmusic.util.BigMegaphoneUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.Util;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 玩家自定义电台管理（docs/09 提案 2b）。
 * <p>
 * 读取 {@code config/netmusiccanplayradio/stations.json}，格式与 NetMusic 内置预置一致：
 * <pre>
 * [
 *   { "name": "幻想乡电台", "url": "https://stream.gensokyoradio.net/3" },
 *   { "name": "SomaFM",     "url": "https://ice1.somafm.com/groovesalad-128-mp3" }
 * ]
 * </pre>
 * 校验复用 NetMusic 的 {@link BigMegaphoneUtil#isValidStreamUrl}（本 mod 的 Mixin 已放行
 * 非 .m3u8 的 http(s) 直链，因此这里可以填写任意电台直链/播放列表）。
 * 每次选择界面打开时重新读取，玩家在游戏外编辑文件后重开界面即生效。
 */
public final class CustomStationManager {
    private static final Gson GSON = new Gson();

    private CustomStationManager() {
    }

    public static List<Station> loadStations() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("netmusiccanplayradio/stations.json");
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
                if (station == null || Util.isBlank(station.name()) || Util.isBlank(station.url())) {
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
                    file, e.getMessage());
            return Collections.emptyList();
        }
    }

    public record Station(String name, String url) {
    }
}
