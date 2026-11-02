package io.github.luxiang0412.kelpie.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalsPathTest {
    @Test fun blankMeansDefault() = assertEquals(ApprovalsPath.DEFAULT, ApprovalsPath.normalize("  ").getOrThrow())

    @Test fun acceptsAbsoluteAndHome() {
        assertEquals("/srv/kelpie", ApprovalsPath.normalize("/srv/kelpie/").getOrThrow())
        assertEquals("~/appr", ApprovalsPath.normalize(" ~/appr ").getOrThrow())
        assertEquals("~", ApprovalsPath.normalize("~").getOrThrow())
        assertEquals("/", ApprovalsPath.normalize("/").getOrThrow())
    }

    @Test fun rejectsRelativeTraversalAndControlChars() {
        assertTrue(ApprovalsPath.normalize("appr").isFailure)
        assertTrue(ApprovalsPath.normalize("~user/appr").isFailure)
        assertTrue(ApprovalsPath.normalize("/srv/../etc").isFailure)
        assertTrue(ApprovalsPath.normalize("/srv/a\nb").isFailure)
    }

    @Test fun resolvesHomeOnlyWhenNeeded() {
        var calls = 0
        val home = { calls++; "/Users/lx/" }
        assertEquals("/Users/lx/.local/state/kelpie/approvals", ApprovalsPath.resolve(ApprovalsPath.DEFAULT, home))
        assertEquals("/Users/lx", ApprovalsPath.resolve("~", home))
        assertEquals("/srv/kelpie", ApprovalsPath.resolve("/srv/kelpie", home))
        assertEquals(2, calls)
    }
}
