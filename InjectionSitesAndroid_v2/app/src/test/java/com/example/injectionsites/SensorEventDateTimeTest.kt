package com.example.injectionsites

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorEventDateTimeTest {
    @Test
    fun laterEventRemainsCurrentWhenOlderSensorIsInsertedAfterwards() {
        val now = 2_000_000_000_000L
        val laterEvent = RecordItem(BodyArea.LEFT_ARM, 0, EntryMode.SENSORE, null, now - 86_400_000L, 1_000L, "later-event")
        val olderEventInsertedLater = RecordItem(BodyArea.RIGHT_ARM, 0, EntryMode.SENSORE, null, now - 12 * 86_400_000L, 2_000L, "older-event")
        val insertionOrder = listOf(olderEventInsertedLater, laterEvent)

        assertEquals("later-event", activeSensor(insertionOrder)?.id)
        assertEquals(listOf("later-event", "older-event"), orderedRecords(insertionOrder).map { it.id })
        assertEquals(laterEvent.eventDateTime, laterEvent.time)
        assertEquals(0, sensorVisualStage(laterEvent.eventDateTime, now, DefaultSettings))
        assertEquals(1, sensorVisualStage(olderEventInsertedLater.eventDateTime, now, DefaultSettings))
    }

    @Test
    fun abdomenDescriptionsFollowTouchedScreenSideForEveryAvatar() {
        AvatarStyle.entries.forEach { _ ->
            (0 until 8).forEach { index ->
                val expectedSide = if (index % 4 < 2) "sinistra" else "destra"
                assertTrue(displayZoneName(BodyArea.ABDOMEN, index).contains(expectedSide))
            }
        }
    }

    @Test
    fun fitUsesOneAspectPreservingTransformAtDifferentScreenSizes() {
        val images = listOf(901 to 1746, 1024 to 1536)
        val screens = listOf(Size(360f, 780f), Size(720f, 1280f), Size(1080f, 2400f))
        images.forEach { (width, height) -> screens.forEach { screen ->
            val placement = fitImage(width, height, screen)
            assertTrue(placement.width <= screen.width && placement.height <= screen.height)
            assertEquals(width.toDouble() / height, placement.width.toDouble() / placement.height, 0.004)
        } }
    }
}
