package io.github.luxiang0412.kelpie.core.herdr

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class L10nTest {
    @After fun reset() { L10n.force(null); L10n.update(Locale.getDefault()) }

    @Test fun followsSystemUntilForced() {
        L10n.force(null)
        L10n.update(Locale.SIMPLIFIED_CHINESE)
        assertEquals("中", t("中", "en"))
        L10n.update(Locale.US)
        assertEquals("en", t("中", "en"))
    }

    @Test fun forcedLanguageIgnoresSystemChanges() {
        L10n.update(Locale.US)
        L10n.force(Language.Chinese.forcedZh)
        assertEquals("中", t("中", "en"))
        L10n.update(Locale.GERMANY) // 系统语言变了也不动
        assertEquals("中", t("中", "en"))
        L10n.force(Language.System.forcedZh) // 回到跟随系统
        assertEquals("en", t("中", "en"))
    }
}
