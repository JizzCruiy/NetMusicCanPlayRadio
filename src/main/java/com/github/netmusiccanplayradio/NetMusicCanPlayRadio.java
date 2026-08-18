package com.github.netmusiccanplayradio;

import com.github.netmusiccanplayradio.client.stream.IcecastStreamHandler;
import com.github.tartaricacid.netmusic.client.api.AudioStreamHandlerManager;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;

/**
 * NetMusicCanPlayRadio —— Net Music (网络音乐机) 的附属模组
 * <p>
 * 作用：让大喇叭支持 Icecast/Shoutcast 无限直播流（幻想乡电台等）、.pls/.m3u 播放列表直链。
 * <p>
 * 挂接方式：使用 NetMusic 官方 wiki 开放的扩展点 {@link AudioStreamHandlerManager#registerHandler}。
 * NetMusic 的 handler 列表在 {@code FMLLoadCompleteEvent} 时冻结，而 mod 构造函数在一切 FML
 * 事件之前执行，因此在构造函数里注册必定生效（这是 NetMusic 作者文档推荐的做法）。
 */
@Mod(NetMusicCanPlayRadio.MOD_ID)
public class NetMusicCanPlayRadio {
    public static final String MOD_ID = "netmusiccanplayradio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NetMusicCanPlayRadio() {
        // 音频解码只在客户端发生；服务端无需注册（且避免在服务端加载客户端侧类）
        if (FMLLoader.getDist() == Dist.CLIENT) {
            try {
                AudioStreamHandlerManager.registerHandler(new IcecastStreamHandler());
                LOGGER.info("[NetMusicCanPlayRadio] IcecastStreamHandler registered");
            } catch (Throwable t) {
                // neoforge.mods.toml 已声明 required 依赖，正常不会走到这里；防御性兜底避免连锁崩溃
                LOGGER.error("[NetMusicCanPlayRadio] Failed to register IcecastStreamHandler (NetMusic missing?)", t);
            }
        }
    }
}
