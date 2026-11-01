package com.music.aalap.audio

import android.media.AudioDeviceInfo
import com.music.aalap.audio.OutputDevices.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The output list: what counts as a music output, its name, its order, no duplicates. */
class OutputDevicesTest {
    private data class Dev(val type: Int, val name: String?, val id: Int)
    private fun pick(vararg d: Dev) = OutputDevices.pick(d.toList(), { it.type }, { it.name }, { it.id }).map { it.first }

    @Test fun musicOutputsOnly() {
        assertEquals(Kind.PHONE, OutputDevices.kindOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(Kind.BLUETOOTH, OutputDevices.kindOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(Kind.WIRED, OutputDevices.kindOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(Kind.USB, OutputDevices.kindOf(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(Kind.BLUETOOTH, OutputDevices.kindOf(26)) // LE Audio headset
        assertNull(OutputDevices.kindOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertNull(OutputDevices.kindOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)) // calls, not music
        assertNull(OutputDevices.kindOf(AudioDeviceInfo.TYPE_TELEPHONY))
    }

    @Test fun names() {
        assertEquals("This phone", OutputDevices.label(Kind.PHONE, "SM-S721B"))
        assertEquals("Galaxy Buds2 Pro", OutputDevices.label(Kind.BLUETOOTH, " Galaxy Buds2 Pro "))
        assertEquals("Bluetooth", OutputDevices.label(Kind.BLUETOOTH, null))
        assertEquals("Wired headphones", OutputDevices.label(Kind.WIRED, "h2w"))
        assertEquals("USB audio", OutputDevices.label(Kind.USB, ""))
    }

    @Test fun bestFirstAndOnePerDevice() {
        val list = pick(
            Dev(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "SM-S721B", 2),
            Dev(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "SM-S721B", 3), // the speaker listed twice
            Dev(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "SM-S721B", 1),
            Dev(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "h2w", 9),
            Dev(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds", 7),
        )
        assertEquals(listOf("Buds", "Wired headphones", "This phone"), list.map { it.name })
        assertEquals(7, OutputDevices.defaultOf(list.map { it to Unit })?.id)
    }

    @Test fun speakerOnly() {
        val list = pick(Dev(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null, 2))
        assertEquals(listOf("This phone"), list.map { it.name })
    }

    @Test fun nothingConnected() {
        assertNull(OutputDevices.defaultOf(emptyList<Pair<OutputDevices.Output, Unit>>()))
    }

    @Test fun soundSettingsKeyPerOutput() {
        assertEquals("", OutputDevices.soundKey(null))
        assertEquals("", OutputDevices.soundKey(OutputDevices.Output(1, "This phone", OutputDevices.Kind.PHONE)))
        assertEquals("BLUETOOTH:Galaxy Buds", OutputDevices.soundKey(OutputDevices.Output(7, "Galaxy Buds", OutputDevices.Kind.BLUETOOTH)))
    }

    @Test fun anOutputReadsThePhonesSettingsUntilItHasItsOwn() {
        val SO = com.music.aalap.data.preferences.SoundOutput
        assertEquals("eq_preset", SO.readKey("eq_preset", "") { true })
        assertEquals("eq_preset", SO.readKey("eq_preset", "BLUETOOTH:Buds") { false })
        assertEquals("eq_preset@BLUETOOTH:Buds", SO.readKey("eq_preset", "BLUETOOTH:Buds") { it == "eq_preset@BLUETOOTH:Buds" })
        assertEquals("eq_preset", SO.writeKey("eq_preset", ""))
        assertEquals("eq_preset@BLUETOOTH:Buds", SO.writeKey("eq_preset", "BLUETOOTH:Buds"))
    }
}
