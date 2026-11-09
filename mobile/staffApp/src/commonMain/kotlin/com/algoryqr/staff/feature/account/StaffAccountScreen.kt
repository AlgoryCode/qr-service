package com.algoryqr.staff.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.algoryqr.shared.auth.Roles
import com.algoryqr.shared.session.Session
import com.algoryqr.shared.ui.AppCard
import com.algoryqr.shared.ui.AppColors
import com.algoryqr.shared.ui.AppIcons
import com.algoryqr.shared.ui.AppScreen
import com.algoryqr.shared.ui.ConfirmDialog
import com.algoryqr.shared.ui.IconListRow
import com.algoryqr.shared.ui.KeyValueRow
import com.algoryqr.shared.ui.ScreenPadding
import com.algoryqr.shared.ui.SectionLabel
import com.algoryqr.shared.ui.SignOutOthersRow

private fun roleLabel(role: String): String = when (role) {
    Roles.KITCHEN -> "Mutfak"
    Roles.COURIER -> "Kurye"
    else -> "Garson"
}

@Composable
fun StaffAccountScreen(session: Session, onSignOut: () -> Unit, signOutOthers: suspend () -> Int) {
    var confirmSignOut by remember { mutableStateOf(false) }
    val profile = session.profile
    val icon = when (profile.role) {
        Roles.KITCHEN -> AppIcons.Kitchen
        Roles.COURIER -> AppIcons.Courier
        else -> AppIcons.Profile
    }

    AppScreen(title = "Hesap") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(ScreenPadding)) {
            AppCard {
                IconListRow(
                    icon = icon,
                    tone = AppColors.Red,
                    title = profile.displayName ?: profile.username ?: "Personel",
                    subtitle = roleLabel(profile.role),
                    onClick = null,
                )
                Spacer(Modifier.height(4.dp))
                profile.username?.let { KeyValueRow("Kullanıcı adı", it) }
                KeyValueRow("İşletme No", profile.merchantId?.toString() ?: "—")
                KeyValueRow("Şube No", profile.branchId?.toString() ?: "Atanmadı")
            }
            SectionLabel("Oturum", Modifier.padding(top = 16.dp))
            AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SignOutOthersRow(signOutOthers)
                IconListRow(AppIcons.SignOut, AppColors.Slate, "Çıkış yap", onClick = { confirmSignOut = true }, subtitle = "Vardiya sonunda cihazı bir sonrakine bırakın")
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Çıkış yapılsın mı?",
            message = "Tekrar girmek için kullanıcı adı ve şifreniz gerekecek.",
            confirmLabel = "Çıkış yap",
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}
