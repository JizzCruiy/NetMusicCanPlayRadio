package com.github.netmusiccanplayradio.mixin;

import com.github.netmusiccanplayradio.client.CustomStations;
import com.github.tartaricacid.netmusic.client.gui.BigMegaphonePresetManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将玩家自定义电台合并进 NetMusic 的预设电台列表。
 * <p>
 * NetMusic 的 {@link BigMegaphonePresetManager} 在 {@code loadBundledStations} 时把
 * {@code assets/netmusic/broadcasting_presets.json} 读入静态列表 {@code STATIONS}（只读、
 * 无注入点）。本 Mixin 在该方法返回后，把 {@code config/netmusiccanplayradio/stations.json}
 * 里的玩家自定义台追加进 {@code STATIONS}，于是 NetMusic 的"选择预设电台"页面
 * （{@code BigMegaphonePresetPickerScreen}）自动显示"内置 + 玩家"合并列表，分页一并生效。
 * <p>
 * 保存内置快照一次（首次加载时），此后每次 {@code loadBundledStations()}（管理界面保存后
 * 会主动重调该 public 方法刷新）都用"内置快照 + 最新玩家配置"重建列表，实现添加/修改/删除后即时生效。
 * <p>
 * 注意：{@code remap = false} —— 注入的是 mod 类（不参与 MC 混淆映射）。
 */
@Mixin(value = BigMegaphonePresetManager.class, remap = false)
public abstract class BigMegaphonePresetManagerMixin {
    /** NetMusic 的静态预设列表（@Shadow 可读写 private static 字段） */
    @Shadow
    private static List<BigMegaphonePresetManager.PresetStation> STATIONS;

    /** 内置预置快照（首次加载时保存，避免后续 merge 后再次被覆盖） */
    private static List<BigMegaphonePresetManager.PresetStation> builtinStations = new ArrayList<>();

    @Inject(method = "loadBundledStations(Lnet/minecraft/server/packs/resources/ResourceManager;)V",
            at = @At("RETURN"))
    private static void netmusiccanplayradio$mergeCustomStations(ResourceManager manager, CallbackInfo ci) {
        if (builtinStations.isEmpty() && STATIONS != null) {
            builtinStations = new ArrayList<>(STATIONS);
        }
        List<BigMegaphonePresetManager.PresetStation> merged = new ArrayList<>(builtinStations);
        for (CustomStations.Station station : CustomStations.load()) {
            merged.add(new BigMegaphonePresetManager.PresetStation(station.name(), station.url()));
        }
        STATIONS = Collections.unmodifiableList(merged);
    }
}
