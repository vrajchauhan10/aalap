package com.music.aalap.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Where the music can come out: the phone's speaker, wired or USB headphones, Bluetooth.
 * Only devices that are connected now are listed (Android tells apps about those without any
 * permission); connecting a new Bluetooth device is done in Android's Bluetooth settings.
 * The choice goes to the player ([com.music.aalap.di.SongPlayer.setOutput]); when the chosen
 * device goes away, Android plays where it normally would.
 */
object OutputDevices {
    enum class Kind(val order: Int) { BLUETOOTH(0), USB(1), WIRED(2), HDMI(3), PHONE(4) }

    data class Output(val id: Int, val name: String, val kind: Kind)

    /** What kind of output an Android device type is, or null for ones music shouldn't go to
     *  (the earpiece, phone-call Bluetooth, telephony, inputs). Pure. */
    fun kindOf(type: Int): Kind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Kind.PHONE
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> Kind.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> Kind.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER -> Kind.BLUETOOTH
        AudioDeviceInfo.TYPE_HDMI -> Kind.HDMI
        else -> null
    }

    /** A name people recognise: the device's own name for Bluetooth/USB, else what it is. Pure. */
    fun label(kind: Kind, productName: String?): String {
        val own = productName?.trim().orEmpty()
        return when (kind) {
            Kind.PHONE -> "This phone"
            Kind.WIRED -> "Wired headphones"
            Kind.HDMI -> "HDMI"
            Kind.USB -> own.ifBlank { "USB audio" }
            Kind.BLUETOOTH -> own.ifBlank { "Bluetooth" }
        }
    }

    /**
     * Connected outputs, best first (Bluetooth, USB, wired, the phone), one entry per device
     * (a Bluetooth device can appear once per profile; the phone's own name repeats).
     */
    fun <T> pick(devices: List<T>, type: (T) -> Int, name: (T) -> String?, id: (T) -> Int): List<Pair<Output, T>> =
        devices.mapNotNull { d -> kindOf(type(d))?.let { k -> Output(id(d), label(k, name(d)), k) to d } }
            .distinctBy { (o, _) -> o.kind to o.name }
            .sortedBy { (o, _) -> o.kind.order }

    /** Where Android sends music when nothing is chosen: the best connected output. Pure. */
    fun <T> defaultOf(outputs: List<Pair<Output, T>>): Output? = outputs.firstOrNull()?.first

    /** Which sound settings an output uses: its own name, or "" for the phone itself. Pure. */
    fun soundKey(output: Output?): String =
        if (output == null || output.kind == Kind.PHONE) "" else "${output.kind}:${output.name}"

    fun connected(context: Context): List<Pair<Output, AudioDeviceInfo>> {
        val am = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return pick(
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList(),
            type = { it.type }, name = { it.productName?.toString() }, id = { it.id },
        )
    }

    // Android 12+ Bluetooth LE Audio types, by value so older SDK stubs aren't needed.
    private const val TYPE_BLE_HEADSET = 26
    private const val TYPE_BLE_SPEAKER = 27
}
