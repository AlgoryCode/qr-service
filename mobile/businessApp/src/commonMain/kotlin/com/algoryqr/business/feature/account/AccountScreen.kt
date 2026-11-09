package com.algoryqr.business.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.algoryqr.business.data.Branch
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

@Composable
fun AccountScreen(session: Session, branch: Branch, onBack: () -> Unit, onSignOut: () -> Unit, signOutOthers: suspend () -> Int) {
    var confirmSignOut by remember { mutableStateOf(false) }
    val profile = session.profile

    AppScreen(title = "Hesap", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(ScreenPadding)) {
            AppCard {
                IconListRow(
                    icon = AppIcons.Business,
                    tone = AppColors.Red,
                    title = profile.displayName ?: "İşletme sahibi",
                    subtitle = profile.email ?: profile.username,
                    onClick = null,
                )
                Spacer(Modifier.height(4.dp))
                // Staff sign in with this number, so it is shown where the owner can read it out to them.
                KeyValueRow("İşletme No", profile.merchantId?.toString() ?: "—", valueColor = AppColors.AccentText)
            }

            SectionLabel("Şube", Modifier.padding(top = 16.dp))
            AppCard {
                Text(branch.name, style = MaterialTheme.typography.titleMedium)
                branch.address?.let { Text(it, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                branch.phone?.let { KeyValueRow("Telefon", it) }
                KeyValueRow("Mutfak ekranı", if (branch.kitchenEnabled) "Açık" else "Kapalı")
                KeyValueRow("Kurye", if (branch.courierEnabled) "Açık" else "Kapalı")
            }

            SectionLabel("Oturum", Modifier.padding(top = 16.dp))
            AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SignOutOthersRow(signOutOthers)
                IconListRow(AppIcons.SignOut, AppColors.Slate, "Çıkış yap", onClick = { confirmSignOut = true })
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Kategori, görsel ve paket yönetimi için AlgoryQR web panelini kullanın.",
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Çıkış yapılsın mı?",
            message = "Tekrar girmek için e-posta ve şifreniz gerekecek.",
            confirmLabel = "Çıkış yap",
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}
