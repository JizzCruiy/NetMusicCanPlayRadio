package com.github.netmusiccanplayradio.client;

import com.github.netmusiccanplayradio.NetMusicCanPlayRadio;
import com.github.netmusiccanplayradio.client.stream.ReconnectingIcyStream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件：断线重连提示 + 大喇叭"我的电台"入口按钮。
 * <p>
 * - 网络提示（docs/09 提案 3 + docs/10 反馈）：流进入重连状态超过 2 秒后，在 HUD 顶部
 *   overlay 提示"网络不佳，正在重连…"（节流：约 5 秒一次，防止刷屏；全局简化版，
 *   多流并发时统一提示）。
 * - 我的电台（docs/09 提案 2b）：在大喇叭界面底部加一个"我的电台"按钮，打开玩家自定义
 *   电台选择界面（数据来自 config/netmusiccanplayradio/stations.json，纯外部扩展，不侵入
 *   NetMusic 内部类，只调用其 public 方法 {@code BigMegaphoneScreen.applyPresetStation}）。
 */
@Mod.EventBusSubscriber(modid = NetMusicCanPlayRadio.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {
    /** 重连持续超过该时长（ms）才提示，避免闪一下就恢复的抖动 */
    private static final long NOTIFY_AFTER_MS = 2000;
    /** 两次提示的最小间隔（ms），防止刷屏 */
    private static final long NOTIFY_INTERVAL_MS = 5000;

    private static long lastNotifyAt;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof com.github.tartaricacid.netmusic.client.gui.BigMegaphoneScreen megaphoneScreen) {
            int leftPos = (screen.width - 240) / 2;
            int topPos = (screen.height - 180) / 2;
            // 大喇叭界面底部（原"选择预设电台"按钮下方）加一行"我的电台"入口
            event.addListener(Button.builder(
                            Component.translatable("gui.netmusiccanplayradio.my_stations"),
                            b -> Minecraft.getInstance().setScreen(new MyStationsScreen(megaphoneScreen)))
                    .pos(leftPos, topPos + 159)
                    .size(240, 20)
                    .build());
        }
    }
}
