# 通用高清屏幕 API（alpha3）

OC 组件类型 `hd_panel`，每个已拼接的完整矩形只有一个可见组件。最多 8×6 物理方块，共用 1280×720 画布。多个独立屏幕墙请用 `component.proxy(完整地址)`。

## 方法

| 方法 | 返回与用途 |
| --- | --- |
| `getResolution()` | `1280,720`，画布像素大小 |
| `getWallSize()` | 列数、行数，物理屏幕墙大小 |
| `setUI(layout)` | `true` 成功并绑定当前电脑；`false,"rate limited"` 频率受限；`nil,原因` 布局无效或已被其他电脑绑定 |
| `updateUI(values)` | `true` 成功；`false,"rate limited"` 频率受限；未 setUI 或绑定冲突返回 `nil,原因` |
| `ack(requestId,success,message)` | 对本电脑收到的待处理点击回执，成功匹配返回 `true`，编号不匹配返回 `false` |
| `release()` | 当前绑定电脑解除绑定，保留最后页面但离线；其他电脑返回 `false` |
| `clearUI()` | 当前绑定电脑或未绑定时清空页面与数据、解除绑定、回到欢迎页；其他电脑不能清空已绑定屏幕 |

`setUI` 与 `updateUI` 共用 10 个服务器 tick 的最小间隔，不要紧接着调用。先定义一次布局，之后定期只更新数据；频率受限返回 `false` 时稍后重试。低 TPS 下 0.6 秒未必达到 10 tick，应检查返回值。每次 `updateUI` 是完整的数据快照，未提供的控件值变为空；按钮/开关默认禁用。即使数值未变也要持续提交心跳，100 tick 不更新或电脑停止则离线。

## 布局

```lua
{
  title="控制台", subtitle="产线 A",
  pages={
    {id="main", label="总览", widgets={
      {id="energy", type="value", x=40,y=124,w=360,h=112,
       label="储能", unit=" EU"},
      {id="stock", type="progress", x=40,y=260,w=1200,h=160,
       label="库存"},
      {id="run", type="toggle", x=40,y=454,w=300,h=60,
       label="产线", confirm="确认切换产线状态？"}
    }}
  }
}
```

标题最多 48 字符，副标题最多 80 字符。`pages` 和 `widgets` 是按 Lua 数字索引排列的数组，必须至少一页，最多 4 页，全部页面共 32 个控件。

页面 ID 在页面之间唯一；控件 ID 在所有页面之间唯一。ID 长度 1–32，仅 ASCII 字母、数字、`.`、`_`、`-`。页签 `label` 最多 16 字符；控件 `label/unit` 最多 48，`confirm` 最多 128。未知字段被丢弃。

控件矩形使用画布像素坐标，以左上角为原点：x≥20、y≥96、w≥60、h≥40，且 x+w≤1260、y+h≤620；位置与尺寸必须是整数。数值卡片至少高 90，进度条至少高 110。顶部页签和下方回执区域由屏幕保留。重叠控件按声明顺序绘制，点击最后一个命中的按钮/开关。

| `type` | 显示和数据 |
| --- | --- |
| `text` | 普通文字卡片，显示 `text`；不足 85 高时为单行 |
| `value` | 数值卡片，显示 `text` 和布局 `unit`；程序自行格式化 EU、百分比等 |
| `progress` | 显示 `text`，按 `value/max` 填充进度条 |
| `button` | 点击事件，数值 0；`enabled=true` 才可用 |
| `toggle` | `active` 显示实际开/关，点击请求目标值 0 或 1；`enabled=true` 才可用 |

按钮/开关的 `confirm` 非空时先显示确认弹窗。切换布局会清空旧值、取消未确认的旧请求。当前版本不支持任意图片、外部网页、CSS、JavaScript 或通用文字输入控件；扩展新的控件类型需修改模组。

## 数据更新

```lua
p.updateUI({
  energy={text="128.4M"},
  stock={text="2500 / 10000",value=2500,max=10000},
  run={enabled=true,active=false}
})
```

值表按控件 ID 索引，只接收已声明控件。`text` 最多 128 字符；数值归一化为有限的 0..1e18，负值/NaN 变 0，过大值截断；要显示负数或单位请用格式化的 `text`。`enabled/active` 仅接受布尔 true。进度超出 max 时显示满条，max≤0 显示空条。`toggle` 点击后不会自行改变真实状态，必须由程序执行并更新 `active`。

## 事件与回执

```lua
local e={event.pull(0.1)}
if e[1]=="hd_panel_event" and e[2]==p.address then
  local id,player,requestId,target=e[3],e[4],e[5],e[6]
  if id=="run" then
    -- 在这里验证操作条件、实际读写自己的机器。
    -- 成功后更新真实状态，并回复；失败时 success=false。
    p.ack(requestId,true,"操作完成")
  else
    p.ack(requestId,false,"未知操作")
  end
end
```

事件顺序：`hd_panel_event,屏幕组件地址,控件ID,玩家名,请求编号,数值`。按钮数值为 0，开关数值为目标状态 0/1。模组检查墙距离≤8 格、OC 电脑用户权限、应用在线、控件仍启用、操作值合法以及节流；但具体机器的可操作条件仍必须在 Lua 中校验。

一面墙同时只处理一个待回执请求。服务器超过 160 tick 不收到回执时返回未确认；客户端超过 12 秒显示结果未确认，低 TPS 下可能在之后执行。不要因超时自动重复危险动作。关闭窗口不撤销已提交的动作。消息最多 128 字符。只有绑定电脑、匹配编号的 `ack` 生效。

推荐把 UI 适配层放到自己的自动化主循环：定时读取数据并 updateUI；处理事件时通过原控制入口执行动作。完整的无硬件例子见 [`oc/automation_demo.lua`](../oc/automation_demo.lua)。不要重复启动同一台机器的两个控制服务。

## 兼容的 BEC 模板

`setPage("bec_dashboard")` / `update(data)` 保留 alpha2 模板接口，使用 `hd_panel_action`，动作名 `refresh/pause/resume/patterns/cache_target`，事件字段与回执编号相同。调用 BEC 接口会切换到 BEC 页面；调用 `setUI` 会切换到通用页面。新放置屏幕不会默认进入 BEC 模板。旧存档保留最后页面；可在绑定程序中调用 `clearUI()` 重置。
