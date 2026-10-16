# alpha8 Chromium 更新

Windows x64 完整客户端现使用 Chromium / CEF，原生 1080p、WebGL GPU 渲染、右键/中键拖动及双缓冲共享内存传帧。默认缩放100%。安装、重建、边界与验证详见 [CHROMIUM.md](CHROMIUM.md)。下文的 JavaFX 说明仅适用于 alpha5–alpha7 旧运行时。

# OCHD HTML 应用原型 · 0.4.0-alpha7

alpha7 的多能源供电及 alpha6 的刷新、亮度与开关行为见 [屏幕设置说明](DISPLAY_POWER.md)。alpha5 的 Lua 和页面包保持兼容。

玩家可以上传自己制作的HTML/CSS/JavaScript页面包，网页接收OC数据、提交事件并接收Lua回执。它是通用接口，示例看板不写死在Java中。原有BEC页面、绑定草稿、1080p和墙体布局继续保留。

## 安装和第一次测试

这是独立测试版。先退出占用该屏幕的OC页面程序。客户端与服务器都替换为 `oc-hd-panel-0.4.0-alpha7-html.jar`，两端不要同时保留旧JAR。服务器无需浏览器运行时；客户端目前只提供 **Windows x64 + Java 21 / 25** 已测运行时。

客户端下载 `ochd-html-client-win-x64.zip`，先把旧OCHD JAR移出mods，再将客户端包解压到游戏目录（有 `mods` 的那层）。包内同时包含新版JAR和浏览器运行时，得到：

```text
游戏目录/
  mods/oc-hd-panel-0.4.0-alpha7-html.jar
  ochd-web-runtime/
    host.jar
    lib/javafx-web.jar
    lib/…
```

原型使用OpenJFX 21.0.12 WebKit，在一个独立、无可见窗口的客户端Java进程中运行。宿主窗口设为不可聚焦，避免后台启动抢走游戏输入；实际游戏行为仍需验收。JavaFX不加入Minecraft类路径。客户端用Java21启动；如果必须让Minecraft使用另一个Java，可以用JVM参数 `-Dochd.web.java="你的Java21目录/bin/javaw.exe"` 指定浏览器进程。JAR本身保留Java8字节码，服务器不加载JavaFX。Linux/macOS客户端暂不提供运行包；缺少runtime时普通页面仍可用，HTML页显示原因。

在游戏OC中执行：

```sh
wget -f https://fastly.jsdelivr.net/gh/Alivn-chao/oc-hd-panel@v0.4.0-alpha7-html/oc/install_web_demo.lua /home/ochd_install_web_demo.lua
lua /home/ochd_install_web_demo.lua
```

安装器只写示例及网页辅助库，不操作机器、不启动程序；重复安装一致时不重复写入，有差异会备份并在失败时回滚。安装完成后：

```sh
lua /home/ochd_web_demo.lua
```

示例要求OC只接入一块OCHD墙的主组件。右键屏幕可输入、Ctrl+V粘贴、点按钮，Esc退出交互，F6切换全屏；在OC键盘按Q停止示例并释放屏幕。示例实际读取 `computer.uptime/freeMemory/totalMemory`，网页“保存目标”仅修改本程序的演示参数，不控制机器。

## 页面包

```text
/home/ochd_apps/my_dashboard/
  app.json
  index.html
  style.css
  main.js
```

`app.json` 是JSON数据，不执行Lua：

```json
{"id":"my-dashboard","title":"我的看板","entry":"index.html","files":["index.html","style.css","main.js"],"events":["set_target"]}
```

文件列表明确列出要上传的资源。当前最多32个文本资源，只支持html/css/js/svg/json/txt，总编码包不超过256KiB；路径禁止绝对路径、`..`和反斜杠。图片可用内嵌data URL或SVG。普通`script src`、`link href`及CSS `url()`中的包内相对路径会转成内存资源；动态import、相对fetch、CSS引号形式@import尚未实现。

玩家可通过OC互联网卡下载别人分享的页面文件，或手动放入OC硬盘。改包后由自己的Lua重新调用setWebApp；不是让每位观看者手动安装页面。模组按SHA256同步并校验页面包，观看者客户端按内容版本缓存。公开分享时只分享页面包/Lua，不包含设备配置和凭据。

## Lua接口（已实现）

```lua
local W=assert(loadfile('/home/ochd_web.lua'))()
local panel=require('component').proxy('自己的屏幕地址')
local package=W.package('/home/ochd_apps/my_dashboard')
local ok,reason=panel.setWebApp(package)
-- 检查返回值；被节流时稍后重试，不要紧接着调用publish。
panel.publish({energy=128400000,capacity=500000000,history={1,2,3}})
```

`setWebApp`验证并保存一份完整包，绑定调用电脑，但不会执行机器操作。`publish`每次是完整JSON数据快照，最多每10个服务器tick接受一次；不变的数据不提升修订号。最大16KiB、深度8、512个值，有限数值/布尔/字符串/嵌套表；连续数字索引表转换成JSON数组，根必须为对象。页面额外收到 `_online` 布尔标志。即使数据不变也要持续publish作为心跳，长期停止后页面离线。

这两种调用返回 `true` 成功、`false,"rate limited"` 节流、`nil,原因` 失败。其他电脑不能抢占已有绑定；正常退出可 `panel.release()`。不要在同一块屏幕上同时运行两个程序。

## JavaScript接口（已实现）

```js
OC.onData(data => {
  document.querySelector('#energy').textContent = data.energy;
});

async function saveTarget(target) {
  try {
    const result = await OC.emit('set_target', {target});
    console.log(result.message);
  } catch (result) {
    console.log(result.message);
  }
}
```

`onData`订阅数据，已有快照时立即交付；`emit`返回Promise，只有app.json声明过的事件可发送。事件最多4KiB、同一墙一次一个待回执操作、客户端限制发送频率。只有玩家正打开该屏幕的交互页时才接受网页事件；后台网页不能自动控制机器。

Lua收到：

```text
hd_panel_web, 屏幕地址, 事件名, 玩家名, 请求编号, JSON参数文本
```

解析参数并按程序自己的规则校验后回执：

```lua
local args=W.decode(jsonText)
-- 自己校验类型、范围、设备状态，再执行允许的操作。
panel.ack(requestId,true,'保存完成')
```

成功ack让Promise resolve；失败ack或超时reject。不自动重试操作，超时不等于Lua未执行。服务器核对墙距离、OC用户权限、页面版本、事件白名单、待回执和节流条件。网页拿不到组件代理、完整电脑配置、token或Java对象；Lua自行选择发布哪些数据。

## 性能与边界

- 浏览器、排版、JS、图表和画面生成在玩家客户端独立进程。服务器保存页面包、发布数据、验证事件；不会渲染网页。
- 页面资源按内容版本单独分块下载，普通方块数据包只同步摘要和数据，不反复携带HTML。缓存最多8包约2MiB，在内存中；每面墙保留一份当前页面包到存档NBT。
- **原型每客户端同时只运行一个网页视图。** 同一面墙共用一个纹理；世界观看目标30帧/秒，打开交互页目标60帧/秒；浏览器连续产帧，最多两个可复用像素缓冲，静止画面不重复传输，实际帧率取决于客户端硬件。优先Direct3D并自动回退软件绘制；实际帧率由客户端硬件决定，OC数据提交仍受服务器节流限制。
- 15秒未使用视图会关闭浏览器；运行时无响应约20秒关闭。尚未实现观看者订阅驱动的OC自动停采；Lua运行频率仍由作者负责。示例每秒一份OC自身指标。
- 支持真实WebKit HTML/CSS/JS、输入、SVG、Canvas。不是完整Chrome：未承诺WebGL、音视频、开发者工具、所有现代CSS/JS特性。原生下拉弹出菜单、右键菜单、IME仍待单独适配/验收。原型禁止外网请求、弹窗、iframe、任意导航；页面资源和JS应随包提供，不引用CDN。
- 只测试自己的或信任来源的页面。离屏进程、CSP和有限桥接减少耦合，并不等于浏览器漏洞的完整安全隔离。暂不提供公共页面商店、任意网站浏览或服务器向客户端分发可执行Java。

## 参考与验证

参考用户提供的 [WebDisplays](https://github.com/montoyo/webdisplays) 中ScreenRenderer的浏览器纹理和TileEntityScreen的输入转发思路；没有复制其代码。该公开主线面向Minecraft1.12.2/MCEF。原型改用独立WebKit进程以避开GTNH1.7.10的CEF/类加载兼容问题。

浏览器API参考 [OpenJFX WebEngine](https://openjfx.io/javadoc/21/javafx.web/javafx/scene/web/WebEngine.html)。运行包附OpenJFX和Gson原始许可，依赖URL/摘要见web-runtime manifests；Host源码包含在运行包中。`tools/web/build_runtime.py`使用Java21构建。

已经通过真实Windows WebKit离屏测试：加载多文件HTML/CSS/JS、OC快照进入DOM、事件/ack Promise、鼠标点击、数字键入、粘贴、SVG/Canvas和1920×1080 PNG。Java协议/边界测试及Lua解析/示例流程模拟通过；实际Minecraft侧的进程启动器、无损像素读取和事件队列也通过独立进程探针验证。**尚未在实际GTNH客户端/专用服务器验证**，不能将离屏渲染或模拟称为游戏验收。

## 网页显示与缩放

右键网页屏幕默认铺满可用窗口，贴在金属外框上沿的独立白蓝页签提供「全屏显示/还原窗口」、缩小、当前比例、放大、「重置」和「返回」。保留16:9画面比例，因此部分窗口有留边。默认网页缩放125%，支持50%–250%，每次25%；缩放改变实际浏览器页面布局，鼠标和输入仍对应页面。F6切换显示区域，Ctrl+滚轮或Ctrl+加减号调整页面缩放，Ctrl+0或「重置」回到100%。这两个设置仅保存在本次客户端会话，不修改OC或机器配置。

原有 BEC 看板也默认打开大窗口，右上角提供「全屏显示 / 还原窗口」按钮，绑定页同样可用。

### alpha3 OC示例修复

OC组件回调是带有 `__call` 的table，旧示例只允许function，导致已装正确alpha3 JAR也误报版本不符。`v0.4.0-alpha3-lua-fix1` 改用 `component.methods` 判断接口是否存在（支持false表示非direct方法），无需替换JAR或浏览器运行时。重新下载上面的安装器并运行，再启动示例即可。

## alpha4 输入与外框修复

旧版高频鼠标移动与重复截图会塞满32项运行时队列，导致点击被丢弃。alpha4将过时移动/数据/缩放请求合并，鼠标移动在alpha4最多约30次/秒，alpha5最多约60次/秒，单客户端同时只等待一个截图；运行时对离散按键/按下/松开保留顺序并施加背压，避免静默丢弃。Minecraft渲染线程不等待浏览器进程。

HTML交互窗口复用原alpha4的FramePainter金属外框，边框在页面外绘制，不改变HTML可点击区域。世界中方块外壳、接缝贴图与几何保留原alpha4文件与边界。

升级必须同时替换客户端JAR与 `ochd-web-runtime/host.jar`（推荐重新解压完整客户端ZIP）；只替换JAR会缺少截图完成回执，无法正常持续刷新。服务器只替换JAR。不需要重装Lua，alpha3-lua-fix1脚本仍适用。

## alpha5 滚动与窗口页签

移除占满窗口宽度的工具栏。全屏、缩小、比例、放大、100%重置、返回，改成贴在金属外框上沿的六个凸起页签，保留白蓝配色、悬停提示和原金属外框。

原先在浏览器UI线程同步做PNG编码，改为下一帧异步截图、后台快速无损ARGB压缩，并在客户端直接读取像素，保持1920×1080、无画质降级。截图时浏览器仍可接收后续输入。客户端默认优先Direct3D，失败自动回退软件；需要强制软件时加Java参数 `-Dochd.web.prism=sw`。

同一台电脑、同一滚动测试页、Java25，26次截图舍弃前6次预热后的20次结果（请求到本地收到帧，非整套Minecraft端到端延迟）：

| 模式 | 中位数 | P95 |
|---|---:|---:|
| alpha4 PNG/软件绘制 | 157.74ms | 162.07ms |
| alpha5 后台无损像素/软件绘制 | 55.58ms | 64.15ms |
| alpha5 后台无损像素/Direct3D优先 | 32.26ms | 36.93ms |

这是本机离屏测试，不能当成所有玩家机器的性能保证。需同时更新JAR与运行时；原Lua和页面包可继续使用。

## NEON YIELD 兼容性页面

[页面源码](../web-apps/defi-yield/README.md) · [预览](defi-yield-preview.png)

基于用户给定DeFi prompt和截图，包含青色网格、青紫渐变、霓虹辉光、APY计算器/滑块、资金池筛选/搜索/收藏、Canvas/SVG、模拟钱包/存入、真实OC数据回传。自包含静态字体，不依赖外网资源。现有alpha5直接使用，无需更换JAR。

先在旧示例的OC终端按Q退出，再执行：

```sh
wget -f https://fastly.jsdelivr.net/gh/Alivn-chao/oc-hd-panel@defi-yield-page-v1/oc/install_defi_yield.lua /home/ochd_install_defi.lua
lua /home/ochd_install_defi.lua
lua /home/ochd_defi_yield.lua
```

安装在独立 `/home/ochd_apps/defi-yield`，不覆盖原示例。市场/钱包/余额均为模拟，OC运行时间/内存为实际数据。测试覆盖真实WebKit渲染/交互与Lua52协议验证，尚未GTNH实机验收。
