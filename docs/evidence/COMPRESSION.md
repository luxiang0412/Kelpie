# 证据截图压缩记录（TinyPNG）

- 处理日期：2026-11-01
- 工具：TinyPNG API（`tools/compress_png.py`，两个 key 轮换）
- 结果：**87 张，77.2 MB → 32.3 MB（省 58%）**
- 质量：仅做有损调色板压缩，文字/线条/对比度肉眼无损（抽检 rel011-board、claude-tools15、table1）

| 文件 | 压缩前 | 压缩后 |
| --- | --- | --- |
| `docs/evidence/real-conv.png` | 2.98 MB | 0.62 MB |
| `docs/evidence/m2c1-conversation-dark.png` | 3.13 MB | 1.31 MB |
| `docs/evidence/m1-12-techsheet-dark.png` | 3.02 MB | 1.20 MB |
| `docs/evidence/m1-11-conversation-dark.png` | 3.04 MB | 1.27 MB |
| `docs/evidence/m1-10-board-dark.png` | 2.87 MB | 1.10 MB |
| `docs/evidence/m2c2-composer-dark.png` | 3.02 MB | 1.30 MB |
| `docs/evidence/m2c2v-font-max-dark.png` | 3.06 MB | 1.36 MB |
| `docs/evidence/ux00a2-conv-light.png` | 3.13 MB | 1.47 MB |
| `docs/evidence/m2c2v-sent.png` | 3.13 MB | 1.48 MB |
| `docs/evidence/m2c2v-conv-before.png` | 3.12 MB | 1.47 MB |
| `docs/evidence/m2c2v-draft-restore.png` | 3.12 MB | 1.47 MB |
| `docs/evidence/m2a-board-light.png` | 2.88 MB | 1.24 MB |
| `docs/evidence/m2c2v-typed.png` | 3.09 MB | 1.46 MB |
| `docs/evidence/m2b-markdown-light.png` | 3.06 MB | 1.43 MB |
| `docs/evidence/m2c2v-reply.png` | 3.11 MB | 1.48 MB |
| `docs/evidence/ux01b-inline-code.png` | 3.08 MB | 1.46 MB |
| `docs/evidence/m1-14-conversation-light.png` | 3.05 MB | 1.44 MB |
| `docs/evidence/ux01a-after-font13.png` | 3.08 MB | 1.47 MB |
| `docs/evidence/m2c2v-font-max.png` | 3.07 MB | 1.48 MB |
| `docs/evidence/m2c2v-board-before.png` | 2.88 MB | 1.38 MB |
| `docs/evidence/m1-13-board-light.png` | 2.87 MB | 1.40 MB |
| `docs/evidence/m2c2v-rotated.png` | 1.78 MB | 0.85 MB |
| `docs/evidence/real-tool-collapsed.png` | 0.38 MB | 0.10 MB |
| `docs/evidence/ux03c-toolcard.png` | 0.36 MB | 0.10 MB |
| `docs/evidence/claude-conv7.png` | 0.34 MB | 0.09 MB |
| `docs/evidence/real-proto-chat-light.png` | 0.32 MB | 0.09 MB |
| `docs/evidence/audit-conv2-dark.png` | 0.32 MB | 0.08 MB |
| `docs/evidence/real-composer-before.png` | 0.32 MB | 0.09 MB |
| `docs/evidence/real-composer-after.png` | 0.31 MB | 0.08 MB |
| `docs/evidence/tool-fulloutput.png` | 0.31 MB | 0.09 MB |
| `docs/evidence/tool-expanded-long.png` | 0.31 MB | 0.09 MB |
| `docs/evidence/real-tool-expanded.png` | 0.31 MB | 0.08 MB |
| `docs/evidence/audit-conv2.png` | 0.30 MB | 0.08 MB |
| `docs/evidence/table1.png` | 0.30 MB | 0.08 MB |
| `docs/design/ux-d0/prototype/HERDR-UX-D0-visual-contact-sheet.png` | 0.31 MB | 0.09 MB |
| `docs/evidence/reenter-conv.png` | 0.29 MB | 0.08 MB |
| `docs/evidence/table-dark.png` | 0.27 MB | 0.07 MB |
| `docs/evidence/ime-before.png` | 0.27 MB | 0.07 MB |
| `docs/evidence/ux03b-avd-p2.png` | 0.27 MB | 0.07 MB |
| `docs/evidence/ux06-before-search.png` | 0.27 MB | 0.08 MB |
| `docs/evidence/ux06-search-open.png` | 0.27 MB | 0.08 MB |
| `docs/evidence/ux03d-avd.png` | 0.26 MB | 0.07 MB |
| `docs/evidence/audit-p5-320-1.6.png` | 0.25 MB | 0.06 MB |
| `docs/evidence/host-add-dialog.png` | 0.27 MB | 0.08 MB |
| `docs/evidence/ux-p3-fixture-after.png` | 0.25 MB | 0.06 MB |
| `docs/evidence/ux-p3-toolcard-after.png` | 0.24 MB | 0.06 MB |
| `docs/evidence/claude-tool-open2.png` | 0.25 MB | 0.07 MB |
| `docs/evidence/claude-tools15.png` | 0.23 MB | 0.06 MB |
| `docs/evidence/rel011-board.png` | 0.24 MB | 0.07 MB |
| `docs/evidence/ux03ab-spacing.png` | 0.22 MB | 0.06 MB |
| `docs/evidence/m1-06-after-recovery.png` | 0.23 MB | 0.07 MB |
| `docs/evidence/real-hosts.png` | 0.22 MB | 0.06 MB |
| `docs/evidence/ux04a-avd.png` | 0.20 MB | 0.05 MB |
| `docs/evidence/ux00a-v2-conv-light.png` | 0.20 MB | 0.05 MB |
| `docs/evidence/ime-after.png` | 0.18 MB | 0.05 MB |
| `docs/evidence/ux04a-v2-composer.png` | 0.17 MB | 0.05 MB |
| `docs/evidence/m1-02-connect.png` | 0.15 MB | 0.04 MB |
| `docs/design/ux-d0/prototype/HERDR-UX-D0-dark-three-screens.png` | 0.16 MB | 0.05 MB |
| `docs/design/ux-d0/prototype/HERDR-UX-D0-light-three-screens.png` | 0.15 MB | 0.05 MB |
| `docs/evidence/host-editor-v2.png` | 0.14 MB | 0.04 MB |
| `docs/evidence/m2-09-transcript-autorefresh.png` | 0.14 MB | 0.04 MB |
| `docs/evidence/real-long-editor.png` | 0.13 MB | 0.04 MB |
| `docs/evidence/ux08a-hosts.png` | 0.10 MB | 0.03 MB |
| `docs/evidence/real-proto-board-dark.png` | 0.09 MB | 0.02 MB |
| `docs/evidence/real-proto-board-light.png` | 0.09 MB | 0.02 MB |
| `docs/evidence/real-board-spec.png` | 0.08 MB | 0.02 MB |
| `docs/evidence/ux05b-lang.png` | 0.08 MB | 0.02 MB |
| `docs/evidence/ux03a-avd-p1.png` | 0.08 MB | 0.02 MB |
| `docs/evidence/ux02b-proto-p1.png` | 0.07 MB | 0.02 MB |
| `docs/evidence/ux05a-hosts-en.png` | 0.07 MB | 0.02 MB |
| `docs/evidence/ux08b-empty-p1.png` | 0.06 MB | 0.01 MB |
| `docs/evidence/ux02c-banner-nodata.png` | 0.06 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-chat-zh-dark.png` | 0.06 MB | 0.02 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-chat-zh-light.png` | 0.06 MB | 0.02 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-board-en-dark-320dp-1p6x.png` | 0.05 MB | 0.02 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-chat-en-dark.png` | 0.05 MB | 0.02 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-board-en-dark.png` | 0.05 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-chat-en-light.png` | 0.05 MB | 0.02 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-focus-zh-dark.png` | 0.05 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-chat-en-dark-320dp-1p6x.png` | 0.05 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-board-en-light.png` | 0.05 MB | 0.02 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-focus-zh-light.png` | 0.05 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-board-zh-dark.png` | 0.05 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-focus-en-dark-320dp-1p6x.png` | 0.04 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-board-zh-light.png` | 0.04 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-focus-en-dark.png` | 0.04 MB | 0.01 MB |
| `docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-focus-en-light.png` | 0.04 MB | 0.01 MB |
