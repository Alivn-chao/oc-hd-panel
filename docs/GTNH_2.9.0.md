# GTNH 2.9.0 正式版适配

OCHD 版本为 `0.5.0-alpha4-gtnh290`。64 槽适配器和 BEC 接口见 [MFU 适配器说明](GROUP_ADAPTER.md)。

## 目标依赖

版本以官方 [2.9.0 发布](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/releases/tag/2.9.0) 和该标签的 [模组清单](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/blob/2.9.0/README.md) 为准，未使用持续变化的 master 或 nightly。

| 依赖 | 正式版版本 | 用途 |
| --- | --- | --- |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 | 目标游戏与模组加载器 |
| OpenComputers | 1.12.68-GTNH | 屏幕组件、MCP、硬盘与 OC 供电 |
| Applied Energistics 2 | rv3-beta-1095-GTNH | AE 网络供电 |
| CoFH Core | 3.1.4-329 | RF 接口，沿用既有依赖 |
| IndustrialCraft 2 | 2.2.828 experimental | EU 接口，沿用既有依赖 |
| lwjgl3ify | 3.0.42 | 正式包客户端环境，尚未启动实测 |
| Angelica | 2.2.43 | 正式包渲染环境，尚未启动实测 |

已使用以下官方文件重新编译：

- [OpenComputers JAR](https://github.com/GTNewHorizons/OpenComputers/releases/download/1.12.68-GTNH/OpenComputers-1.12.68-GTNH.jar)，SHA-256：`06ebdccfb5801748a455adfd53b2197d848b48b1711e1f2c22dc4dd5645f7d56`
- [AE2 API JAR](https://github.com/GTNewHorizons/Applied-Energistics-2-Unofficial/releases/download/rv3-beta-1095-GTNH/appliedenergistics2-rv3-beta-1095-GTNH-api.jar)，SHA-256：`023db89800b1abe13ad3c304ba2ad948312a353ddbad6046a42373325447d212`

构建命令：Java 25 启动 Gradle，执行 `gradlew.bat test reobfJar`。Java 源码工具链为 21，发行 JAR 为 Java 8 字节码。旧机箱、大硬盘的注册名及磁盘路径保持原样；网络消息编号和页面协议未改变。最低 OC 版本提高到 `1.12.68-GTNH`，不要把此 JAR 安装回仍用 OC 1.12.61 的 beta3 实例。

## 升级

1. 退出游戏、关闭服务器，备份原 OCHD JAR、世界存档与 OC 硬盘数据。
2. 客户端和服务器均使用 `mods/oc-hd-panel-0.5.0-alpha4-gtnh290.jar` 替换旧 OCHD JAR，mods 下只能启用一个版本。
3. 客户端保留原有 `ochd-web-runtime`。首次安装需先按 [Chromium 安装说明](CHROMIUM.md) 配置运行时；本升级包不包含原生浏览器。服务器无需安装浏览器。
4. MCP Python 连接程序和已有 OC 后台助手可沿用；此版本没有修改助手脚本，也无需再次停止 OC 安装助手。本机连接配置随客户端启动自动更新，包含令牌，请勿上传。
5. 原页面、Lua 程序和旧磁盘保留。新大硬盘仍为空盘，不自动迁移旧盘。

MCP 接入步骤见 [MCP.md](MCP.md)。安装包不包含 GTNH、OC 或 AE2 本体，不需要手动用上面的编译依赖覆盖整合包内的模组。

## 验证结果与边界

- 用正式版 OC 与 AE2 API 编译并完成 `reobfJar`。
- 56 项 Java 回归测试通过，覆盖页面、供电、传帧以及 64 槽并行配额预检查和出错恢复。
- 18 项 Python/Lua MCP 模拟测试通过，覆盖协议、读写、备份、并发冲突和命令执行等行为。
- 尚未在完整 GTNH 2.9.0 客户端/服务端启动验收；上述测试不能替代 Forge 加载、Angelica 渲染、CEF GPU 传帧及多人游戏实测。没有据此宣称帧率提升。
