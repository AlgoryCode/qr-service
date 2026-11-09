package com.algoryqr.business

import androidx.compose.ui.window.ComposeUIViewController
import com.algoryqr.shared.session.IosSessionStorage
import platform.UIKit.UIViewController

// Device and simulator builds use stage until a release config exists.
private const val API_BASE_URL = "https://stage.qrapi.algorycode.com"

private val container by lazy { BusinessContainer(IosSessionStorage("com.algoryqr.business"), API_BASE_URL) }

@Suppress("FunctionName", "unused") // Called from Swift.
fun MainViewController(): UIViewController = ComposeUIViewController { App(container) }
