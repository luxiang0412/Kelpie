package dev.herdr.app

import androidx.activity.ComponentActivity

/**
 * 测试宿主 Activity（仅 debug 变体）。
 *
 * 背景（ISSUES I-59）：MIUI 拒绝 instrumentation 启动“测试包自己声明的 ComponentActivity”
 * （logcat: `MIUILOG- Permission Denied Activity`），导致 Compose 测试挂起。
 * 把宿主放到 app 包（已安装、可启动）里，测试即可在真机上运行。
 */
class TestHostActivity : ComponentActivity()
