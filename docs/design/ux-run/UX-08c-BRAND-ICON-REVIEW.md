# UX-08c — App 图标候选（PROPOSAL，不是最终资产）

> 状态：**PROPOSAL**。本轮**未替换**任何应用图标，也**未提交**任何第三方素材。
> 现有 `ic_launcher` 仍是 Android 默认占位（见 `app/src/main/res/mipmap-*`）。

## 约束（来自任务书 BRAND-ICON-001）

- 方向必须是 **Focus Orbit**（聚焦/轨道），不得是终端提示符 `>_`、也不得是通用机器人头像；
- 需要能出 **单色（monochrome, Android 13 主题图标）** 版本；
- 不得使用无授权素材；无合法资源时只出方案文档。

## 三个方向（都为文字描述 + 可自绘矢量，0 依赖）

| # | 名称 | 构成 | 单色版 | 适用性 |
| --- | --- | --- | --- | --- |
| A | **Single Orbit** | 一个 3px 圆环 + 环上一个小圆点（agent），圆心留空 | 环+点纯黑/纯白 | 最简洁，16dp 下仍可辨；与「一个 agent 在跑」语义直接 |
| B | **Orbit Pair** | 两个同心不等宽圆环 + 一个外环高亮点 | 两环粗细区分 | 表达多 agent/多主机，但 16dp 下易糊 |
| C | **Focus Wedge** | 圆心 + 一圈 60° 扇形高亮（"当前焦点"） | 扇形用实心块 | 表意"聚焦"最强，但与"loading"视觉近似，易误读 |

推荐：**A**。理由：16dp/24dp/48dp 三档下轮廓损失最小，单色版无需渐变，且不与任何现有 App 图标混淆。

## 交付边界

- 本轮**只出方案**，未生成 PNG/Vector 资产、未改 `AndroidManifest` 图标、未做 Adaptive Icon；
- 若用户选定方向后，再产出：
  - `ic_launcher_foreground.xml`（108dp 视口，安全区 66dp）
  - `ic_launcher_background`（纯色）
  - `ic_launcher_monochrome.xml`（Android 13 主题图标）
  - 各 dpi 的 legacy fallback（可选，minSdk 26 需要 mipmap 位图或 vector 兼容方案）
- SplashScreen：保持系统默认（Android 12+ `SplashScreen` API），**不添加**等待动画或虚假连接状态。
