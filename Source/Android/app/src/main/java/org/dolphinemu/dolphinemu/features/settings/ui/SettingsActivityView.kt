package org.dolphinemu.dolphinemu.features.settings.ui

import android.os.Bundle
import org.dolphinemu.dolphinemu.features.settings.model.Settings

/**
 * Abstraction for the Activity that manages SettingsFragments.
 */
interface SettingsActivityView {
    /**
     * Show a new SettingsFragment.
     *
     * @param menuTag    Identifier for the settings group that should be displayed.
     * @param addToStack Whether or not this fragment should replace a previous one.
     */
    fun showSettingsFragment(
        menuTag: MenuTag,
        extras: Bundle?,
        addToStack: Boolean,
        gameId: String
    )

    /**
     * Used to provide the Activity with Settings HashMaps if a Fragment already
     * has one; for example, if a rotation occurs, the Fragment will not be killed,
     * but the Activity will, so the Activity needs to have its HashMaps resupplied.
     *
     * @param settings The ArrayList of all the Settings HashMaps.
     */
    fun setSettings(settings: Settings)

    /**
     * Display a popup text message on screen.
     *
     * @param message The contents of the onscreen message.
     */
    fun showToastMessage(message: String)

    /**
     * Called by a containing Fragment to tell the containing Activity that a GCPad's setting
     * was modified.
     *
     * @param menuTag Identifier for the GCPad that was modified.
     * @param value   New setting for the GCPad.
     */
    fun onGcPadSettingChanged(menuTag: MenuTag, value: Int)

    /**
     * Called by a containing Fragment to tell the containing Activity that a Wiimote's setting
     * was modified.
     *
     * @param menuTag Identifier for Wiimote that was modified.
     * @param value   New setting for the Wiimote.
     */
    fun onWiimoteSettingChanged(menuTag: MenuTag, value: Int)

    /**
     * Called by a containing Fragment to tell the containing Activity that an extension setting
     * was modified.
     *
     * @param menuTag Identifier for the extension that was modified.
     * @param value   New setting for the extension.
     */
    fun onExtensionSettingChanged(menuTag: MenuTag, value: Int)

    /**
     * Show a hint to the user that the app needs write to external storage access
     */
    fun showPermissionNeededHint()

    /**
     * Show a hint to the user that the app needs the external storage to be mounted
     */
    fun showExternalStorageNotMountedHint()
}
