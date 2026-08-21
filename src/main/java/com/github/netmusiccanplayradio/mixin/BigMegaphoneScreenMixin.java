package com.github.netmusiccanplayradio.mixin;

import com.github.tartaricacid.netmusic.client.gui.BigMegaphoneScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 大喇叭 URL 输入框的占位提示文字改为"输入电台直链或 m3u8 地址"。
 * <p>
 * 背景：资源覆盖 NetMusic 的 lang 文件（assets/netmusic/lang/zh_cn.json 只写一个 key）
 * 经实测不生效 —— MC 的 ClientLanguage 用 {@code listResources} 加载语言，同名文件
 * 按包去重保留（NetMusic 的文件胜出），与预置文件的 {@code getResource} 语义不同。
 * 因此改为把渲染时的翻译 key 从 {@code gui.netmusic.big_megaphone.url.tips}
 * 重定向到本 mod 命名空间下的 {@code gui.netmusiccanplayradio.url.tips}（无冲突，必生效）。
 * <p>
 * 注意：此处<b>必须用默认 remap（不能 false）</b> —— 目标 {@code Component.translatable}
 * 是 MC 类方法，Modrinth 发布的 NetMusic jar 是 SRG 映射（运行时该方法名为
 * {@code m_237115_}），remap=false 会导致 target 匹配不到而崩溃；目标类
 * BigMegaphoneScreen 是 mod 类（不在映射表），类名与方法名不受影响。
 */
@Mixin(value = BigMegaphoneScreen.class)
public abstract class BigMegaphoneScreenMixin {
    /** render 中所有 Component.translatable(String) 调用都会经过这里（含 url.tips 与 name.tips），按 key 分流 */
    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private static MutableComponent netmusiccanplayradio$redirectUrlTips(String key) {
        if ("gui.netmusic.big_megaphone.url.tips".equals(key)) {
            return Component.translatable("gui.netmusiccanplayradio.url.tips");
        }
        return Component.translatable(key);
    }
}
