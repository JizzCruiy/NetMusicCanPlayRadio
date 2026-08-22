package com.github.netmusiccanplayradio.mixin;

import com.github.netmusiccanplayradio.client.CustomStations;
import com.github.tartaricacid.netmusic.client.gui.BigMegaphonePresetManager;
import com.github.tartaricacid.netmusic.util.BigMegaphoneUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 用"本 mod 内置预置 + 玩家自定义台"整体替换 NetMusic 的预设电台列表。
 * <p>
 * 内置预置读取本 mod 自有命名空间的 {@code assets/netmusiccanplayradio/broadcasting_presets.json}
 * （不覆盖 NetMusic 的 {@code assets/netmusic/broadcasting_presets.json}）：
 * 资源覆盖在 Forge/NeoForge 的包优先级行为不一致（实测 1.20.1 覆盖不生效、1.21.1 生效），
 * 改为 Mixin 注入后两端行为完全一致，且不再依赖平台资源顺序。
 * <p>
 * NetMusic 的 {@link BigMegaphonePresetManager} 在 {@code loadBundledStations} 时把原版
 * 预置读入静态列表 {@code STATIONS}；本 Mixin 在该方法返回后，把 {@code STATIONS} 整体
 * 替换为"本 mod 内置预置 + {@code config/netmusiccanplayradio/stations.json} 玩家台"，
 * NetMusic 的"选择预设电台"页面（含分页）自动显示合并结果。此后每次
 * {@code loadBundledStations()}（管理界面保存后主动重调该 public 方法刷新）都用
 * "内置预置 + 最新玩家配置"重建列表，添加/修改/删除即时生效。
 * <p>
 * 注意：{@code remap = false} —— 注入的是 mod 类（不参与 MC 混淆映射）。
 */
@Mixin(value = BigMegaphonePresetManager.class, remap = false)
public abstract class BigMegaphonePresetManagerMixin {
    /** 本 mod 内置预置文件（自有命名空间，无覆盖冲突） */
    private static final ResourceLocation OWN_PRESETS = ResourceLocation.fromNamespaceAndPath("netmusiccanplayradio", "broadcasting_presets.json");
    private static final Gson GSON = new Gson();

    /** NetMusic 的静态预设列表（@Shadow 可读写 private static 字段） */
    @Shadow
    private static List<BigMegaphonePresetManager.PresetStation> STATIONS;

    /** 本 mod 内置预置（首次加载时读取一次） */
    private static List<BigMegaphonePresetManager.PresetStation> builtinStations = new ArrayList<>();

    @Inject(method = "loadBundledStations(Lnet/minecraft/server/packs/resources/ResourceManager;)V",
            at = @At("RETURN"))
    private static void netmusiccanplayradio$mergeCustomStations(ResourceManager manager, CallbackInfo ci) {
        if (builtinStations.isEmpty()) {
            builtinStations = loadOwnPresets(manager);
        }
        List<BigMegaphonePresetManager.PresetStation> merged = new ArrayList<>(builtinStations);
        for (CustomStations.Station station : CustomStations.load()) {
            merged.add(new BigMegaphonePresetManager.PresetStation(station.name(), station.url()));
        }
        STATIONS = Collections.unmodifiableList(merged);
    }

    /** 读取本 mod 内置预置；校验与 NetMusic 原版一致（isValidStreamUrl，本 mod 已放行非 .m3u8 直链） */
    private static List<BigMegaphonePresetManager.PresetStation> loadOwnPresets(ResourceManager manager) {
        Optional<Resource> optional = manager.getResource(OWN_PRESETS);
        if (optional.isEmpty()) {
            return Collections.emptyList();
        }
        try (InputStreamReader reader = new InputStreamReader(optional.get().open(), StandardCharsets.UTF_8)) {
            List<BigMegaphonePresetManager.PresetStation> loaded = GSON.fromJson(reader,
                    new TypeToken<List<BigMegaphonePresetManager.PresetStation>>() {
                    }.getType());
            if (loaded == null) {
                return Collections.emptyList();
            }
            List<BigMegaphonePresetManager.PresetStation> valid = new ArrayList<>();
            for (BigMegaphonePresetManager.PresetStation station : loaded) {
                if (station == null || station.name() == null || station.url() == null) {
                    continue;
                }
                String name = station.name().trim();
                String url = station.url().trim();
                if (!name.isBlank() && BigMegaphoneUtil.isValidStreamUrl(url)) {
                    valid.add(new BigMegaphonePresetManager.PresetStation(name, url));
                }
            }
            return valid;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
