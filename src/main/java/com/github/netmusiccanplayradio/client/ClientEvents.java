package com.github.netmusiccanplayradio.client;

import com.github.netmusiccanplayradio.NetMusicCanPlayRadio;
import com.github.netmusiccanplayradio.client.stream.ReconnectingIcyStream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端事件：断线重连提示（docs/09 提案 3 + docs/10 反馈）。
 * <p>
 * 流进入重连状态超过 2 秒后，在 HUD 顶部 overlay 提示"网络不佳，正在重连…"
 * （节流：约 5 秒一次，防止刷屏；全局简化版，多流并发时统一提示）。
 * <p>
 * NeoForge 1.21.1：使用 {@link ClientTickEvent.Post}（游戏总线），
 * 无需像 Forge 那样判断 phase。
 */
@EventBusSubscriber(modid = NetMusicCanPlayRadio.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {
    /** 重连持续超过该时长（ms）才提示，避免闪一下就恢复的抖动 */
    private static final long NOTIFY_AFTER_MS = 2000;
    /** 两次提示的最小间隔（ms），防止刷屏 */
    private static final long NOTIFY_INTERVAL_MS = 5000;

    private static long lastNotifyAt;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // 只在游戏中（有玩家、不在任何界面）提示
        if (mc.player == null || mc.screen != null) {
            return;
        }
        long startedAt = ReconnectingIcyStream.getReconnectStartedAt();
        if (startedAt <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - startedAt >= NOTIFY_AFTER_MS && now - lastNotifyAt >= NOTIFY_INTERVAL_MS) {
            MutableComponent message = Component.translatable("message.netmusiccanplayradio.reconnecting")
                    .withStyle(ChatFormatting.YELLOW);
            mc.gui.setOverlayMessage(message, false);
            lastNotifyAt = now;
        }
    }
}
