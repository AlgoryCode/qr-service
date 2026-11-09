package com.algoryqr.staff

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
        private var container: StaffContainer? = null

        fun containerFor(context: Context): StaffContainer = container ?: synchronized(this) {
            container ?: StaffContainer(
                AndroidSessionStorage(context, "algoryqr_staff"),
                AndroidLocalStore(context),
                BuildConfig.API_BASE_URL,
            ).also { container = it }
        }
    }
}

/** Plain preferences: what is stored here is a convenience, not a secret. */
private class AndroidLocalStore(context: Context) : LocalStore {
    private val preferences = context.getSharedPreferences("algoryqr_staff_local", Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String?) = preferences.edit().putString(key, value).apply()
}
