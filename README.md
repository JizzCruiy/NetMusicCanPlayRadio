# NetMusicCanPlayRadio

> 网络音乐机（Net Music）附属模组：让大喇叭直接播放 **Icecast/Shoutcast 无限直播流**（幻想乡电台 Gensokyo Radio、touhou.fm、R/a/dio 等）与 **.pls/.m3u 播放列表**，无需自建转码服务器。
>
> A Net Music addon that lets the Big Megaphone play **Icecast/Shoutcast live streams** (Gensokyo Radio, touhou.fm, R/a/dio, ...) and **.pls/.m3u playlists** directly — no transcoding server required.

> ⚠️ **本模组为社区第三方附属模组，与 Net Music 原作者无关、非官方作品。需要先安装 Net Music 才能使用。问题请提交到本仓库 issue 区，勿骚扰原模组作者。**

---

## 功能 / Features

- 大喇叭 URL 校验放宽：接受任意 http(s) 直播流直链（不再只认 .m3u8）；
- 新增 Icecast/Shoutcast 流处理器（通过 NetMusic 官方扩展点 `AudioStreamHandlerManager.registerHandler` 注册）：
  - 自动剥离 **ICY 元数据**（Shoutcast 内嵌"正在播放"数据，不剥离会导致解码错乱）；
  - **断流自动重连**（指数退避，适合 24/7 电台）；
  - **.pls / .m3u 播放列表**自动解析出真实流地址；
  - 无扩展名 URL（电台 mount 点）自动识别为直播流；
- 不改变 .m3u8（HLS）播放路径：AAC + MPEG-TS 无加密 m3u8 仍由 NetMusic 原生支持。

## 前置依赖 / Dependencies

| 模组 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.20.1 | Java 17 |
| Forge | 47.x | ForgeGradle 6 |
| **Net Music（网络音乐机）** | **1.5.1+，必装** | 本模组无法脱离其运行（mods.toml 已声明 mandatory 依赖） |

> 服务端与客户端都需要安装。

## 安装 / Installation

1. 确认已安装 Net Music 1.5.1+；
2. 将本模组 jar 放入 `mods/` 目录；
3. 游戏中放置大喇叭，在 URL 栏粘贴电台直链（如 `https://stream.gensokyoradio.net/1/`）或 .pls/.m3u 播放列表链接，命名后开始广播。

## 支持的源 / Supported Sources

| 类型 | 示例 | 说明 |
|---|---|---|
| Icecast MP3 电台 | Gensokyo Radio、touhou.fm、R/a/dio、SomaFM | 最常用，直接可播 |
| Shoutcast 电台 | 带 ICY 元数据的流 | 自动剥离元数据 |
| .pls / .m3u 播放列表 | 各类电台页提供的播放列表链接 | 自动解析真实流地址 |
| HLS（.m3u8） | 自建转码服务器产出的 AAC+TS 无加密流 | 走 NetMusic 原生路径，不受影响 |

> 已知不支持：OGG/Opus 源（NetMusic 无对应解码器，请先经服务端 ffmpeg 转码为 MP3/AAC）、加密 HLS、B站直播流（FLV/加密 HLS，见项目 issue 区讨论）。

## 从源码构建 / Building from Source

```bash
git clone <本仓库>
cd NetMusicCanPlayRadio
./gradlew build          # 产物在 build/libs/
```

> 需要 JDK 17。依赖 Net Music 1.5.1-forge+mc1.20.1 通过 Modrinth Maven 自动拉取，无需手动下载 jar。

## 技术细节 / Technical Details

- 挂接点：`AudioStreamHandlerManager.registerHandler`（NetMusic 官方 wiki 开放的扩展 API，在 `FMLLoadCompleteEvent` 冻结列表前于 mod 构造函数注册）；
- 校验放宽：单个 Mixin 注入 `BigMegaphoneUtil.isValidStreamUrl`（`remap=false`），GUI / 服务端 / 预置电台过滤四处校验点一次性生效；
- 优先级：Icecast 处理器优先级 50，介于 NetMusic 的 M3u8Handler(100) 与 DirectHttpHandler(0) 之间。

## 分支说明 / Branches

| 分支 | MC 版本 | 加载器 | 状态 |
|---|---|---|---|
| `main` | 1.20.1 | Forge | 当前版本 |
| `1.21.1-neoforge` | 1.21.1 | NeoForge | 计划中 |

## AI 使用声明 / AI Usage Disclosure

本项目部分代码由 AI 辅助生成，并经人工审查、测试与整合。AI 仅作为开发辅助工具；项目设计、功能决策、测试与维护均由作者（JiltCruiy）负责。特此如实披露。

Some code in this project was generated with the assistance of AI and has been reviewed, tested, and integrated by a human. AI was used solely as a development aid; design, testing, and maintenance are the responsibility of the author (JiltCruiy). This disclosure is provided to comply with platform AI-content policies.

## 开源协议 / License

[MIT License](LICENSE) - 可自由使用、修改、分发，需保留版权声明。

## 致谢 / Credits

- [Net Music](https://github.com/TartaricAcid/NetMusic) - 网络音乐机，由 TartaricAcid 开发（代码 BSD-3-Clause，资源 CC BY-NC-SA 4.0）

本模组不包含也不分发 Net Music 的任何代码或资源文件。
