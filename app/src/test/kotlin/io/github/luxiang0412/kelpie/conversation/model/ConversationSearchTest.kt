package io.github.luxiang0412.kelpie.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationSearchTest {
    private val items = listOf(
        ConversationItem.User(0, "Run the Tests please"),
        ConversationItem.Assistant(1, "Sure, running tests."),
        ConversationItem.Tool(2, "Bash", "./gradlew test", "BUILD SUCCESSFUL", false, true),
        ConversationItem.Thinking(3, "nothing here"),
    )

    @Test fun caseInsensitiveAcrossKinds() = assertEquals(listOf(0, 1, 2), ConversationSearch.matches(items, "test"))
    @Test fun toolResultIsSearched() = assertEquals(listOf(2), ConversationSearch.matches(items, "successful"))
    @Test fun blankQueryMatchesNothing() = assertEquals(emptyList<Int>(), ConversationSearch.matches(items, "  "))
}
