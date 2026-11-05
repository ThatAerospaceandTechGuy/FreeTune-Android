package com.freetune.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOpsTest {

    @Test
    fun movesAnItemDown() {
        assertEquals(
            listOf("a", "c", "b"),
            QueueOps.move(listOf("a", "b", "c"), from = 1, to = 2)
        )
    }

    @Test
    fun movesAnItemUp() {
        assertEquals(
            listOf("b", "a", "c"),
            QueueOps.move(listOf("a", "b", "c"), from = 1, to = 0)
        )
    }

    @Test
    fun removesAnItem() {
        assertEquals(
            listOf("a", "c"),
            QueueOps.removeAt(listOf("a", "b", "c"), index = 1)
        )
    }

    @Test
    fun ignoresOutOfBoundsChanges() {
        val items = listOf("a")
        assertEquals(items, QueueOps.removeAt(items, index = 9))
        assertEquals(items, QueueOps.move(items, from = 0, to = 5))
        assertEquals(items, QueueOps.move(items, from = 0, to = 0))
    }
}
