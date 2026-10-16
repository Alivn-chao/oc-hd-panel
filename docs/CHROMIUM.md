# Chromium / CEF runtime (alpha8)

Windows x64, Java 21 or 25. Replace the old OCHD JAR and extract the complete
client ZIP into the Minecraft game directory (the directory containing mods/).
The server uses only the mod JAR. Update both client and server JARs to alpha8;
Forge 1.7.10 checks mod versions even though the packet protocol is unchanged.
Restart the client after replacing files.
Existing OC Lua packages and the BEC V2 web page remain compatible.

The isolated browser now uses JCEF/CEF 146 with GPU WebGL instead of JavaFX
WebKit. Each client renders its own page locally. No browser downloads happen
at launch. The runtime includes pinned upstream binaries and license notices;
rebuild with tools/web/build_cef_runtime.py and OCHD_BUILD_JAVA_HOME.

Rendering uses a 1280x720 logical viewport at device scale factor 1.5, producing
1920x1080 native pixels. This is not an enlarged 720p screenshot. Default page
zoom is 100%; the zoom controls retain their previous meaning. The BEC canvas
can see devicePixelRatio=1.5 and allocate its own native-resolution backing store.

Left drag rotates the BEC model; right or middle drag pans; the wheel zooms the
model; Ctrl+wheel zooms the page; Ctrl+V pastes into a focused text field.
All three buttons are forwarded, with their pressed state and modifier keys.

Paint events replace periodic full-page snapshots. Two acknowledged shared
memory slots carry native BGRA pixels to the Minecraft process. A slot cannot
be overwritten until the receiving process has copied and acknowledged it.
When both slots are busy, only the newest pending paint is retained. Static
pages generate no repeated screenshots. Heartbeats keep idle hosts alive.

This is shared **CPU memory**, not shared GPU textures: GPU rendering still
requires one readback and the Minecraft texture upload. The pinned upstream
JCEF API exposes onPaint but no OnAcceleratedPaint/shared texture handle.
True GPU-to-GPU sharing needs a custom native CEF binding plus Windows
Direct3D/OpenGL synchronization; it is not enabled or claimed in this version.

Only in-memory page resources are allowed. CSP and CEF request handlers block
remote networking, file URLs, navigation, popups and page context menus. The
page gets the existing bounded OC JSON bridge, not Java objects. The test-only
eval bridge and relaxed eval CSP require -Dochd.selftest=true and are absent
from production launches. JCEF's upstream Windows helper is built without the
Chromium sandbox; local-resource/bridge restrictions are not an OS sandbox.

Validation uses the actual BEC preview 2026-10-16.1 page. The renderer and the
unmasked WebGL GPU name must be checked; a successful WebGL context alone can
also be backed by software. Browser timings do not include Minecraft rendering,
shader packs, server tick delays or all players' hardware. Target 60 FPS is not
a game FPS guarantee. See build/ benchmark output for the local run's results.

## Local validation, 2026-10-16

Windows x64, Java 21, NVIDIA RTX 5070, BEC page tag
`bec-v2-ochd-preview-2026-10-16.1` (81b7724), containing 44,418 source mesh faces:

- alpha7 WebKit selected Canvas 2D, with six rotation redraws taking
  270/343/384/354/399/371 ms.
- Chromium selected WebGL. The unmasked renderer identified ANGLE / RTX 5070 /
  Direct3D 11, rather than SwiftShader. Rotation redraw submission was about
  1.7–3.1 ms. This JS timing does not wait for GPU completion.
- Production WebHostProcess + mapped frames, 256 MiB receiver heap, continuous
  BEC model dragging: 289 and 284 frames in two five-second runs (57.8 / 56.8
  FPS); 285 and 282 frames respectively had changing model-region checksums.
  This includes Chromium rendering/readback, mapping and Java frame reception;
  it excludes Minecraft GL upload and in-game rendering.
- Real CEF tests passed native 1920x1080 output, right/middle panning, page zoom
  with matching clicks, range dragging, paste, OC data/event/ack, saturated
  input followed by a click, and the existing HTML demo. A separate static page
  verified no redundant paint frames; the BEC UI itself contains animations.
- 52 Java tests pass, including independent mapped-slot pixels, bounds and closure.

Reproduce with `tools/web/test_bec.py --page <BEC web_page.html> --assert-pan`,
`tools/web/test_host.py`, and `tools/web/ClientProbe.java`. Set OCHD_TEST_JAVA
and OCHD_TEST_CEF; set OCHD_TEST_MAPPING=1 for mapped-frame tests. These are
runtime/bridge tests, not GTNH multiplayer or production-machine acceptance.

Upstream:
- https://github.com/jcefmaven/jcefmaven/releases/tag/146.0.10
- https://github.com/chromiumembedded/java-cef
- https://github.com/chromiumembedded/cef
