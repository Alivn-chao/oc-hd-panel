# NEON YIELD · OCHD compatibility page

A self-contained neon-on-dark DeFi interface based on the user's reference screenshots and design prompt. The reference URL could not be read in this environment; this is an original implementation of the visible style, not a copy of that site's source.

Bundled features: Neon Orbit static bold font derived from Orbitron (SIL OFL; OFL.txt and FONT-NOTICE.txt included), CSS Grid/Flex, cyan text/border glow, cyan-magenta gradients, sticky nav, APY calculator, native range input, pool search/filter/favorites, dialogs, Canvas projection, SVG allocation, simulated wallet/deposit, OC publish/event/ack.

All market metrics, APYs, tokens and balances are fictional. No real wallet or external network connection. The calculator uses effective annual APY: principal * ((1 + APY/100)^(days/365) - 1). OC uptime and memory are real when connected.

Stop the previous demo with Q at its OC terminal before running this page. Install the self-contained `oc/install_defi_yield.lua` then run `lua /home/ochd_defi_yield.lua`. It installs to a separate `/home/ochd_apps/defi-yield` directory and preserves the original demo. It reuses the existing `ochd_web.lua` helper. OCHD alpha5 recommended for low-latency input; no mod change required.

Font source: https://github.com/google/fonts/tree/main/ofl/orbitron
Reference supplied by user: https://uupm.cc/demo/defi-yield

Variable weight rendering was inconsistent in the tested WebKit build; a renamed static bold instance is used for faithful rendering. Host compatibility testing covers Java25/Direct3D, calculators, native range dragging, text paste, pool filtering/search/favorites, SVG/Canvas, wallet/deposit OC event+ack, and 75%/125%/200% responsive zoom. Lua52 tests use callable-table OC callbacks and validate virtual balance changes plus rejected deposits. These tests do not replace GTNH in-game acceptance.
