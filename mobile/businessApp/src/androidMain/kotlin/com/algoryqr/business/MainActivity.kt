package com.algoryqr.business

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.algoryqr.shared.session.AndroidSessionStorage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = containerFor(applicationContext)
        setContent { App(container) }
    }

    private companion object {
        // One graph per process, so a rotation or a recreated activity keeps the same session and HTTP client.
        @Volatile
        private var container: BusinessContainer? = null

        fun containerFor(context: Context): BusinessContainer = container ?: synchronized(this) {
            container ?: BusinessContainer(AndroidSessionStorage(context, "algoryqr_business"), BuildConfig.API_BASE_URL)
                .also { container = it }
        }
    }
}
