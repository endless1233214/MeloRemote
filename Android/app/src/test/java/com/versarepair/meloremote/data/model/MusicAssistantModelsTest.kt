package com.versarepair.meloremote.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAssistantModelsTest {
    @Test
    fun correctedElapsedTimeAdvancesOnlyWhilePlaying() {
        val playing = MAPlayerQueue(
            queueId = "living-room",
            state = "playing",
            elapsedTime = 12.0,
            elapsedTimeLastUpdated = 100.0,
            playbackSpeed = 1.5,
        )
        val paused = playing.copy(state = "paused")

        assertEquals(27.0, playing.correctedElapsedTime(110.0), 0.001)
        assertEquals(12.0, paused.correctedElapsedTime(110.0), 0.001)
    }

    @Test
    fun mediaCapabilitiesMatchMusicAssistantRules() {
        val track = MAMediaItem(
            itemId = "42",
            provider = "spotify",
            name = "A Track",
            mediaType = "track",
            uri = "spotify://track/42",
        )
        val editablePlaylist = MAMediaItem(
            itemId = "7",
            provider = "library",
            name = "Favorites",
            mediaType = "playlist",
            uri = "library://playlist/7",
            isEditable = true,
            supportedMediaTypes = listOf("track"),
        )

        assertTrue(track.canPlay)
        assertTrue(track.canStartRadio)
        assertTrue(track.canBeFavorited)
        assertTrue(track.canBeAddedToPlaylist)
        assertTrue(editablePlaylist.canReceivePlaylistItems)
        assertTrue(editablePlaylist.canReceive(track))
        assertFalse(editablePlaylist.canReceive(track.copy(mediaType = "radio")))
    }

    @Test
    fun libraryAndPlaylistRestrictionsAreEnforced() {
        val libraryItemWithoutId = MAMediaItem(
            provider = "library",
            mediaType = "track",
            uri = "library://track/missing",
        )
        val nonNumericPlaylist = MAMediaItem(
            itemId = "not-an-integer",
            provider = "library",
            mediaType = "playlist",
            uri = "library://playlist/not-an-integer",
        )

        assertFalse(libraryItemWithoutId.canChangeLibraryMembership)
        assertFalse(nonNumericPlaylist.canReceivePlaylistItems)
    }

    @Test
    fun repeatModeCyclesAndUnknownValuesFallBackToOff() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
        assertEquals(RepeatMode.OFF, RepeatMode.fromWire("unexpected"))
    }

    @Test
    fun durationFormattingHandlesTracksAndLongPrograms() {
        assertEquals("0:00", 0.0.formattedDuration())
        assertEquals("3:06", 185.6.formattedDuration())
        assertEquals("1:01:02", 3662.0.formattedDuration())
    }
}
