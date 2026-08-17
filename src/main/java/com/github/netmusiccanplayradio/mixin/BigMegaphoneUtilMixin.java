package com.github.netmusiccanplayradio.mixin;

import com.github.tartaricacid.netmusic.util.BigMegaphoneUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;
import java.net.URL;
import java.util.Locale;

/**
 * 放宽大喇叭的流地址校验。
 * <p>
 * NetMusic 的 {@link BigMegaphoneUtil#isValidStreamUrl(String)} 只放行 *.m3u8（或央广网 API 链接），
 * 本 Mixin 额外放行"无 .m3u8 后缀的 http(s) 直链"（Icecast/Shoutcast 电台流、.pls/.m3u 播放列表），
 * 使大喇叭 GUI / 服务端校验 / 预置电台过滤四处调用点一次性全部生效。
 * <p>
 * 注意：{@code remap = false} —— 注入的是 mod 类（不参与 MC 混淆映射）。
 * 该方法是静态方法，服务端与客户端都会应用本 Mixin（BigMegaphoneUtil 是双端 common 类）。
 */
@Mixin(value = BigMegaphoneUtil.class, remap = false)
public abstract class BigMegaphoneUtilMixin {
    @Inject(method = "isValidStreamUrl", at = @At("HEAD"), cancellable = true)
    private static void netmusiccanplayradio$allowDirectStreams(String url, CallbackInfoReturnable<Boolean> cir) {
        if (url == null) {
            return;
        }
        String trimmed = url.trim();
        if (trimmed.isBlank()) {
            return;
        }
        try {
            URL parsed = URI.create(trimmed).toURL();
            String protocol = parsed.getProtocol();
            boolean http = "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
            // .m3u8 仍走原逻辑（原本就合法）；非 .m3u8 的 http(s) 直链交给播放层 handler 链决定
            if (http && !parsed.getPath().toLowerCase(Locale.ROOT).endsWith(".m3u8")) {
                cir.setReturnValue(true);
            }
        } catch (Exception ignored) {
            // URL 解析失败 → 保持原逻辑（返回 false）
        }
    }
}
