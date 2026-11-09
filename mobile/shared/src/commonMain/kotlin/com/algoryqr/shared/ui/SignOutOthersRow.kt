package com.algoryqr.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import kotlinx.coroutines.launch

/**
 * "Diğer cihazlardan çıkış yap": after a confirmation closes every other session of this account and says how many
 * there were. This device stays signed in; the others are sent to sign in again on their next refresh.
 */
@Composable
fun SignOutOthersRow(signOutOthers: suspend () -> Int) {
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    IconListRow(
        AppIcons.Lock,
        AppColors.Amber,
        "Diğer cihazlardan çıkış yap",
        onClick = { if (!busy) confirming = true },
        subtitle = result ?: "Kaybolan ya da başkasında kalan cihazlar için",
    )

    if (confirming) {
        ConfirmDialog(
            title = "Diğer cihazlardan çıkılsın mı?",
            message = "Bu cihaz açık kalır. Hesabınızla açık olan diğer cihazlar en geç 15 dakika içinde giriş ekranına döner.",
            confirmLabel = "Çıkış yap",
            onConfirm = {
                confirming = false
                busy = true
                scope.launch {
                    result = safeCall { signOutOthers() }.fold(
                        onSuccess = { count -> if (count == 0) "Başka açık cihaz yoktu" else "$count cihazdan çıkış yapıldı" },
                        onFailure = { error -> error.userMessage() },
                    )
                    busy = false
                }
            },
            onDismiss = { confirming = false },
        )
    }
}
