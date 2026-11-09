package com.algoryqr.staff

import androidx.compose.ui.window.ComposeUIViewController
import com.algoryqr.shared.session.IosSessionStorage
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

// Device and simulator builds use stage until a release config exists.
private const val API_BASE_URL = "https://stage.qrapi.algorycode.com"

/** Plain user defaults: what is stored here is a convenience, not a secret. */
private class IosLocalStore : LocalStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun put(key: String, value: String?) = defaults.setObject(value, key)
}

private val container by lazy { StaffContainer(IosSessionStorage("com.algoryqr.staff"), IosLocalStore(), API_BASE_URL) }

@Suppress("FunctionName", "unused") // Called from Swift.
fun MainViewController(): UIViewController = ComposeUIViewController { App(container) }
