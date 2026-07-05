package org.opentvsearch.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShouldAutoLaunchVoiceTest {

    @Test
    fun `search intent with no query launches voice`() {
        assertThat(
            shouldAutoLaunchVoice(isSearchIntent = true, hasQuery = false, voiceOnLaunchPref = false),
        ).isTrue()
    }

    @Test
    fun `search intent with query does not launch voice`() {
        assertThat(
            shouldAutoLaunchVoice(isSearchIntent = true, hasQuery = true, voiceOnLaunchPref = true),
        ).isFalse()
    }

    @Test
    fun `launcher open with voice-on-launch and no query launches voice`() {
        assertThat(
            shouldAutoLaunchVoice(isSearchIntent = false, hasQuery = false, voiceOnLaunchPref = true),
        ).isTrue()
    }

    @Test
    fun `launcher open without voice-on-launch does not launch voice`() {
        assertThat(
            shouldAutoLaunchVoice(isSearchIntent = false, hasQuery = false, voiceOnLaunchPref = false),
        ).isFalse()
    }
}
