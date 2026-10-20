# OCHD 0.5.0-alpha1：MCP 接入机箱与大容量硬盘

基于 `v0.4.0-alpha8-input-fix1`，保留 Chromium 屏幕及输入修复。此版是可编译的功能预览；已完成自动化测试，**尚未完成 GTNH 联机实测与性能基准测试**。不要把它视为生产稳定版。

## 两个新物品

- **OCHD MCP 接入机箱**（`ochdpanel:mcp_case`）：独立方块，放在主机旁边或通过 OC 线缆接入同一网络，不占原主机卡槽。它不是第二台执行程序的电脑；程序仍在原 OC 电脑内运行。一个接入机箱可接入最多 64 台已加载电脑。
- **OCHD 大容量硬盘**（`ochdpanel:large_disk`）：插入原有 HDD 槽，默认新盘 64 MiB，服务端 `mcp.diskMiB` 可配置 1–1024 MiB。容量记录在物品上；改配置只影响新盘。沿用 OC 文件系统和默认磁盘调用预算，不宣称提升 Lua 运算速度。

测试配方：机箱为 `铁/红石/铁，红石/末影珍珠/红石，铁/红石/铁`；硬盘为 `铁/铁/铁，钻石/末影珍珠/钻石，铁/铁/铁`。机箱暂用现有机壳纹理，硬盘暂用原版唱片图标。

## 安装

1. 关闭服务端与客户端，备份世界和原 OCHD jar。两端用 `oc-hd-panel-0.5.0-alpha1-mcp.jar` 替换原 OCHD jar，不能同时保留两个启用版本。
2. 此包不包含 Chromium 原生运行时。已经安装 alpha8 input-fix1 的客户端继续使用现有 `ochd-web-runtime`；服务端不安装浏览器运行时。
3. 在游戏中由需要使用 MCP 的玩家放置接入机箱，用 OC 线缆连接原电脑。放置者成为所有者。潜行右键可关闭/开启接入，关闭不会转移所有权。仍受 OC 用户访问权限约束。
4. 大硬盘是空白新盘，不会自动迁移旧盘或安装 OpenOS。可先保留原系统盘使用 MCP，之后再做备份迁移；不要直接丢弃旧盘。
5. 客户端启动时自动建立随机端口的本机桥接，配置写入 `.minecraft/config/ochd-mcp-client.json`。此文件包含本机令牌，不要上传 GitHub。只绑定 `127.0.0.1`，服务器无需开放额外端口。
6. 用 Python 3.9+ 运行 `mcp/ochd_mcp.py --config <上述文件的完整路径>`，作为 MCP 的 STDIO 服务。Python 程序只使用标准库。Codex 配置例子见下节。
7. 初次接入由 MCP 执行：`oc_list` → `oc_components` 找到 `openOS=true` 的可写系统盘 → `oc_power(stop)` → 等 `oc_status.running=false` → `oc_install_agent` → `oc_power(start)`。这一步自动安装后台助手，不需要用户在 OC 终端输入 Lua。
8. `agentOnline=true` 后即可读取日志、修改程序、运行命令。客户端必须登录服务器，目标电脑及机箱必须在已加载区块和玩家当前维度；本版不提供离线中继或自动加载区块。

## Codex 配置

在现有 `config.toml` 中合并以下独立条目，按实际路径替换；不要覆盖其他配置。路径建议使用 `/`。保存后重启对应 MCP 连接。

```toml
[mcp_servers.ochd]
command = "C:/path/to/python.exe"
args = ["C:/path/to/mcp/ochd_mcp.py", "--config", "C:/path/to/.minecraft/config/ochd-mcp-client.json"]
startup_timeout_sec = 10
tool_timeout_sec = 180
```

配置依据：[Codex MCP 官方文档](https://developers.openai.com/codex/mcp)。桥接程序使用 [MCP 2025-06-18](https://github.com/modelcontextprotocol/modelcontextprotocol/tree/main/docs/specification/2025-06-18) 的 STDIO 初始化与工具协议；不需要 OpenAI API 密钥。

## 接口

| 工具 | 用途 |
|---|---|
| `oc_list` / `oc_status` | 电脑发现、运行/暂停状态、CPU 时间、最后错误 |
| `oc_components` | 分页查看组件、方法、硬盘容量和 OpenOS 标记 |
| `oc_power` / `oc_install_agent` | 开关 OC 电脑、安装后台助手 |
| `oc_runtime` | 内存、电量、进程、挂载目录 |
| `oc_files` / `oc_read` | 分页目录、分块文件或日志读取，支持二进制 hex |
| `oc_write` | UTF-8 文件分块暂存、版本检查、提交与备份 |
| `oc_run` / `oc_job` | 异步 OpenOS 命令、输出和结果查询 |
| `oc_invoke` | 通过 OC 调度器调用其他组件的方法 |

不绑定 BEC；任何通过 OC 暴露接口的机器都可以按其能力接入。无法读取机器模组没有提供的内部状态，也不提供宿主服务器 shell。正在运行的旧程序若没有写日志文件，其历史终端输出不会被追溯捕获；通过 `oc_run` 启动的命令有 stdout/stderr 捕获。直接调用 GPU 绘图的程序不会自动转换成文本日志。

## 性能和行为限制

- 本机 HTTP 工作者只排队；Forge 发包在客户端 tick，世界/OC 操作在服务端 tick。
- 服务端队列 16，每 tick 最多处理 2 个请求，并在单次操作后检查 2 ms 批次预算。单次操作不是可抢占的硬性 2 ms 保证；仍需实机测量。
- 每玩家每秒最多 12 个请求；每电脑最多 8 个待完成任务；任务结果保留至多 5 分钟、最多约 32 条，不是永久日志库。
- 助手每 0.5 秒查看一次请求，最多查看 8 个 MCP 机箱。空闲时不扫描文件。组件发现只访问 OC 网络，不扫描全世界。
- 文件读取每块最多 8 KiB，文件编辑最大 1 MiB；新硬盘总容量不受此编辑接口限制。大日志可按偏移读取。
- 编辑版本为大小与 Adler-32 校验值，用于发现并发修改，并非加密签名。提交前再次检查；与其他 Lua 程序并发写入时不提供跨进程锁。
- 命令最多同时 2 个、默认 30 秒，最大 120 秒；输出最多 16 KiB。超时停止命令线程，不能撤销已发生的机器操作或保证终止命令自行分离的子线程。
- 网络断开/超时后，不自动重发写入或命令。先查询原任务 ID；已接受的命令可能已执行。
- 写入暂存与备份放在目标目录内的 `.ochd-mcp/` 子目录，避免被 OpenOS 的 `/boot` 遍历当作脚本执行。提交后的备份需定期人工或受控清理。

本版用于观测和维护，并不是新的原生自动化引擎；不承诺自动提升 TPS/FPS 或 Lua 执行速度。服务器后台只在首次安装/升级模组时需要。

## 回退

先备份新硬盘中的重要文件。卸载新版本会让新增方块/物品不可用，因此恢复旧 jar 时应同时恢复安装前世界备份。仅停用 MCP 可潜行右键关闭机箱，或断开线缆；不需要回退屏幕模组。后台助手可从 OC 系统盘删除 `/boot/99_ochd_mcp.lua` 后重启电脑；其安装备份位于系统盘 `/.ochd-mcp/agent-backup.lua`。
