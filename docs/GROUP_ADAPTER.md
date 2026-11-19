# OCHD 64 槽 MFU 适配器

适用版本为 GTNH 2.9.0 正式版、OpenComputers 1.12.68-GTNH。客户端和服务器都替换成 `oc-hd-panel-0.5.0-alpha4-gtnh290.jar`。Chromium 运行时沿用。原 16 槽适配器升级后可直接使用其余 48 槽，原槽号、绑定和存档格式兼容。

## 接线与分组

1. 合成「OCHD 64槽 MFU 适配器」，中心是 OC 原版适配器，四角是铁锭，上下左右各放一个红石。把新适配器用 OC 线缆接到电脑。
2. 拿原版 MFU 潜行右键机器要接入的面，沿用 OC 的绑定动作。对 BEC 来说，可把一整组的 16 个传送节点分别绑定到 16 张 MFU 上。
3. 可在铁砧把 MFU 重命名为 `组名/设备名`，如 `bec-a/node-01`。没有组名前缀时进入 `default` 组。右键打开界面，四个 4×4 区块依次是 1–16、17–32、33–48、49–64。槽位区块用于排布，Lua 按 MFU 的命名筛选逻辑组。
4. 从 OC 读取 `ochd_adapter.list()` 或 `ochd_adapter.list("bec-a")`。返回以 1–64 为键的稀疏表，有 MFU 才出现对应项，因此 Lua 遍历用 `pairs`。同一条 OC 网络可接多台适配器，用 `component.list("ochd_adapter", true)` 枚举，再用 `component.proxy(address)` 获取各台适配器。

```lua
local component = require("component")
local adapter = component.ochd_adapter
local nodes = {}
for slot, row in pairs(adapter.list("bec-a")) do
  if row.status == "online" and row.type == "bec_io_node" then
    nodes[slot] = component.proxy(row.address)
    print(slot, row.label, row.address)
  else
    print(slot, row.label, row.status)
  end
end
```

条目包含 `slot`、`group`、`label`、`status`。已绑定卡另有 `x`、`y`、`z`、`dimension` 和 `side`。在线且可见的目标提供 `address` 和 `type`。可将组件地址填进原有 BEC 绑定表，现有 `bec_io_node` 调用方式仍然适用。机器组件仍按原版 OC 规则计入 CPU 组件数，满载前检查 CPU 与组件总线容量。

同维度内无距离上限，目标所在区块需要已加载。离线时返回 `unloaded`，区块重新加载后会自动重连。同坐标、同面的重复卡只连接最靠前的槽。跨维度卡显示 `other_dimension`。适配器使用已注册的 OC 目标驱动，因此机器本身需要适配器驱动。OC 原生方块若已在同一网络且可见，可以列出已有地址。远程绑定不会把另一套 OC 网络接进来。

目标驱动的状态随适配器存档。用新适配器替换旧适配器时，目标会获得新地址，需要通过 `list` 重新绑定。建议在 BEC 当前批次结束且暂停后逐组迁移。先验证读状态，再用一两台节点试生产。

## BEC 批量操作

`becStatus([group])` 返回和 `list` 相同的槽位表，并给在线 BEC 节点加上 `state`、`parallel`、`min`、`max`、`progress`、`maxProgress` 和 `workAllowed`。`admitted=true` 表示已有任务、接单许可关闭，且节点停在立即暂停或蜂群等级不足状态。BEC 程序还应按本批配额和配方凝聚物需求核对实际接单。

`becConfigure(plan)` 一次设置一块适配器上的最多 64 台，计划以槽号为键，值为 `{address=原绑定地址, min=最小并行, max=最大并行}`。地址要与槽位相符，整份计划中的节点都必须空闲且关闭接单。检测到失联、占用、重复物理节点或计划地址错误时整份拒绝。返回状态 `configured` 和逐台读回结果时才可进入下一阶段。写入中途失败会尝试恢复已碰过的节点，返回 `restored` 或 `uncertain`。两种状态都应暂停并检查回读，再决定如何续跑。

```lua
-- 例：上层已确定本批配额；保持该批原料、周期和日志由 BEC 程序管理。
local snapshot = adapter.becStatus("bec-a")
local plan = {}
for slot, quota in pairs(quotas) do
  local node = assert(snapshot[slot], "槽位未绑定")
  assert(node.state == "idle" and node.workAllowed == false)
  plan[slot] = {address = node.address, min = quota, max = quota}
end
local receipt = adapter.becConfigure(plan)
assert(receipt.status == "configured", "整组并行配置未确认")
```

`becSetAdmission(slot, address, allowed[, quota])` 对一个指定节点开关接单并回读许可。开启前会检查空闲、精确配额及物理立即暂停控制仓是否正在接收红石。按现有流程一次只为一个节点开放，等该节点拿到任务后关闭许可，再通过 `becStatus` 和原生 `getRequiredCondensate` 核对实际接单，然后进入下一台。这项操作和流程日志仍由调用方管理。现有 BEC Lua 需要接入这些接口后才会走批量路径。

超过 64 个节点时可继续接第二块适配器。每块独立编号及回执，由同一个 BEC 程序按适配器地址分发计划。每块计划应使用不同的物理节点。各块独立配置，先确认所有回执，再开始接单。配方和原料能否分给多个装置取决于原有 BEC 和 AE 布线。

## BEC 后续组件建议

| 优先级 | 组件 | 对应现有流程 |
| --- | --- | --- |
| 1 | 定量供液调度器 | 对接现有主网端口、转运器和独立子网，按流体和缺额合并查询及调度，回报实际移动量，余液按现有流程归网。部分成功交给既有流水核对，避免重复搬运。 |
| 2 | 蜂群换盘控制器 | 复用固定等级盘位和两路 ME-IO，提供旧盘回收、目标盘装入及数量核对的分步回执。现有 `getAllStacks` 已批量读取，主要优化点是减少后续信号与状态确认的往返。 |

编译和并行配置回归测试已通过。游戏内接单、64 台全载 TPS 与实际提速仍待验证，机器配方时间由原生 BEC 决定。
