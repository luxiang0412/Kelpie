# UX-07a — 语音转文字可行性 Spike（研究结论，未实现功能）

> 结论：**不做实现**。原因见下。本轮未添加任何 Mic 入口（避免假可用控件）。

## 1. 现状事实（来自本仓库代码）

| 项 | 值 |
| --- | --- |
| minSdk / targetSdk | 26 / 34 |
| 依赖 | 无 speech 相关库；`Compose BOM 2026.09.00`、Material3 |
| 权限清单 | 仅 `INTERNET`、`FOREGROUND_SERVICE`、`POST_NOTIFICATIONS`；**没有** `RECORD_AUDIO` |
| 现有输入 | Material3 `OutlinedTextField`（状态式）+ 长 Prompt 全屏编辑 + 草稿 Room |

## 2. Android 语音方案对比

| 方案 | API | 离线保证 | 备注 |
| --- | --- | --- | --- |
| `SpeechRecognizer`（系统服务） | 8+ | **不保证** | 设备可能走网络；`EXTRA_PREFER_OFFLINE` 只是偏好，不是保证 |
| `SpeechRecognizer` + `createOnDeviceSpeechRecognizer` | **33+** | 需 `isOnDeviceRecognitionAvailable` 为 true | 才能真宣称"设备端离线" |
| Android 15 新 API | 35+ | 视实现 | targetSdk 34，不适用 |

## 3. 设备能力（实测受限）

- **真机 Redmi 22021211RC / MIUI**：本轮真机处于锁屏 + adb 曾离线，**未取得** `SpeechRecognizer` 实测证据；
  已记录为未验证，**不宣称**可用。
- **AVD `herdr_m2c1`（google_apis x86_64）**：模拟器镜像通常不含完整语音服务；
  若强行调用，多为 `ERROR_CLIENT`/无可用 recognizer —— 属**环境不足**，不能当作"设备不支持"的结论。

## 4. 隐私与权限

- 需新增 `RECORD_AUDIO` 运行时权限 → 必须在用户点 Mic 时申请，且要写清"音频可能交由系统语音服务处理"。
- 若服务联网，音频会离开设备；这与本项目"草稿/密钥不离开设备"的既有基调冲突，需要用户显式同意。
- 现有工程没有录音相关代码，也没有后台服务处理音频；本轮不引入。

## 5. 为什么本轮不做实现（UX-07b → SKIPPED）

任务书对 UX-07b 的门槛是「UX-07a 找到**当前项目可使用的本地系统语音识别路径**」。

- Android 13 以下的路径**不能保证离线**，且当前 targetSdk 34 的 AVD 无法验证 on-device recognizer；
- 真机本轮不可用（锁屏/离线），无法提供「真机可用」证据；
- 按任务书「若设备无可用语音服务，给出降级方案并标阻断」，因此：

**降级方案（已生效）**：用户仍可用**系统输入法的语音按键**把文字转写进 Composer ——
这是系统能力，App 不申请 `RECORD_AUDIO`、不上传音频、不新增权限面。

## 6. 若后续要做（前置清单）

1. 准备一台 Android 13+ 真机，实测 `SpeechRecognizer.isOnDeviceRecognitionAvailable` 与中文 `zh-CN` 支持；
2. 明确状态机：`Unavailable/Idle/PermissionNeeded/Preparing/Listening/Processing/Review/Error/Interrupted`；
3. 转写结果先进「复核」区域，用户确认后才写入草稿，**任何路径都不得直接 `sendPrompt`**；
4. 首次使用弹一次说明：音频可能由系统语音服务处理（可能联网）；
5. `Activity` 离开时 `stop/cancel/destroy`，不得后台录音。
