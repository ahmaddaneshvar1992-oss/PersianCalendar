package ir.persiancalendar.app.ui.dev

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * نسخهٔ عمومی (فلاور «public») — نسخهٔ ۱.۱۱.۳۰ (بازرسی امنیتی مرحلهٔ دوم:
 * «پنل ادمین هنوز داخل اپ است»).
 *
 * در این فلاور هیچ پنل مدیریتی، رمز پنل یا توکنی وجود ندارد — کد همهٔ آن‌ها
 * فقط در فلاور «admin» (نسخهٔ شخصی مدیر) کامپایل می‌شود:
 *  - DevGate.enabled همیشه false است، پس ورود مخفی ۵ لمسی هم هیچ کاری نمی‌کند؛
 *  - DevPanel هرگز باز نمی‌شود (فقط برای جاافتادن کامپایل MainScreen است).
 * دور زدن با دستکاری فلگ یا دیتا هم ممکن نیست — چیزی برای دور زدن وجود ندارد.
 */
object DevGate {
    const val enabled = false
}

@Composable
fun DevPanel(onBack: () -> Unit) {
    // در نسخهٔ عمومی هرگز نمایش داده نمی‌شود (DevGate.enabled=false) —
    // اگر روزی به هر دلیل باز شود، بلافاصله بسته می‌شود.
    Box(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(Unit) { onBack() }
    }
}
