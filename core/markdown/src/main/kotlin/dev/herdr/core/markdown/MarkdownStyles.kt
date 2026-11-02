package dev.herdr.core.markdown

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Markdown 正文与标题字阶：正文 = 规范 bodyReading（15/24）；手机上标题只比正文略大。 */
internal val MdBody = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
internal val MdBodySmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
internal val MdLabel = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
internal val MdCaption = TextStyle(fontSize = 12.sp, lineHeight = 17.sp)
internal val MdH1Style = TextStyle(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
internal val MdH2Style = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
internal val MdH3Style = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
internal val MonoStyle = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)
