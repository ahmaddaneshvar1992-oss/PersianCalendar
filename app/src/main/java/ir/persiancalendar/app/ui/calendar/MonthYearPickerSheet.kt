package ir.persiancalendar.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.persiancalendar.app.R
import ir.persiancalendar.app.core.calendar.PersianText

/**
 * انتخابگر سریع سال و ماه — نسخهٔ ۱.۱۱.۳۹ (کار ۱)
 *
 * ضربه روی کارت سربرگ («<ماه> <سال>») این bottom sheet را باز می‌کند:
 *  - بالا: فلش سال قبل/بعد + لمس عدد سال برای تایپ مستقیم یا انتخاب از فهرست
 *  - دکمهٔ «امروز»: برگشت به ماه و سال جاری
 *  - پایین: شبکهٔ ۳×۴ ماه‌های شمسی (فروردین تا اسفند)
 * ماه جاری هایلایت است، ماه واقعی «امروز» نشانگر کوچک دارد و انتخاب یک ماه،
 * sheet را می‌بندد و با انیمیشن کوتاه Pager به همان ماه می‌رود.
 * بازهٔ سال‌ها از موتور تقویم می‌آید (MonthYearPickerLogic) و بیرون از آن
 * غیرفعال است؛ ورودی تایپی نامعتبر کرش نمی‌کند (فقط پیام خطا).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPickerSheet(
    viewJy: Int,
    viewJm: Int,
    todayJy: Int,
    todayJm: Int,
    persianDigits: Boolean,
    onDismiss: () -> Unit,
    onPick: (jy: Int, jm: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetYear by remember { mutableStateOf(MonthYearPickerLogic.clampYear(viewJy)) }
    var typing by remember { mutableStateOf(false) }
    var yearInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = sheetYear - MonthYearPickerLogic.MIN_YEAR)
    val yearCd = stringResource(R.string.picker_year_cd)

    fun digits(s: String): String = if (persianDigits) PersianText.toPersianDigits(s) else s

    // وقتی فهرست سال‌ها باز می‌شود، روی سال انتخاب‌شده بنشیند
    LaunchedEffect(typing, sheetYear) {
        if (!typing) {
            val idx = (sheetYear - MonthYearPickerLogic.MIN_YEAR).coerceAtLeast(0)
            runCatching { listState.scrollToItem(idx) }
        }
    }

    fun submitTypedYear() {
        val parsed = MonthYearPickerLogic.parseYearInput(yearInput)
        if (parsed == null || !MonthYearPickerLogic.isYearInRange(parsed)) {
            inputError = true // بدون کرش — فقط خطا
        } else {
            inputError = false
            sheetYear = parsed
            typing = false
            yearInput = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            // ---------- انتخاب سال ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (MonthYearPickerLogic.canGoToPrevYear(sheetYear)) sheetYear -= 1 },
                    enabled = MonthYearPickerLogic.canGoToPrevYear(sheetYear)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.picker_prev_year),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            onClickLabel = stringResource(R.string.picker_type_year_hint),
                            role = Role.Button
                        ) {
                            typing = true
                            yearInput = ""
                            inputError = false
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = digits(sheetYear.toString()),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = yearCd
                        }
                    )
                    Text(
                        text = stringResource(R.string.picker_year_list_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { if (MonthYearPickerLogic.canGoToNextYear(sheetYear)) sheetYear += 1 },
                    enabled = MonthYearPickerLogic.canGoToNextYear(sheetYear)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.picker_next_year),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (typing) {
                // ---------- تایپ مستقیم سال ----------
                OutlinedTextField(
                    value = yearInput,
                    onValueChange = { raw ->
                        // فقط ارقام (فارسی/عربی/لاتین) نگه داشته می‌شود
                        val filtered = raw.filter {
                            it in '۰'..'۹' || it in '٠'..'٩' || it in '0'..'9'
                        }
                        yearInput = filtered.take(4)
                        inputError = false
                    },
                    isError = inputError,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        textAlign = TextAlign.Center
                    ),
                    label = { Text(stringResource(R.string.picker_type_year_hint)) },
                    supportingText = {
                        if (inputError) {
                            Text(stringResource(R.string.picker_year_invalid))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitTypedYear() }),
                    trailingIcon = {
                        IconButton(onClick = { submitTypedYear() }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.picker_year_submit)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // ---------- فهرست سال‌ها (کشویی افقی) ----------
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val years = MonthYearPickerLogic.MIN_YEAR..MonthYearPickerLogic.MAX_YEAR
                    itemsIndexed(years.toList()) { _, y ->
                        FilterChip(
                            selected = y == sheetYear,
                            onClick = { sheetYear = y },
                            label = { Text(digits(y.toString())) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- دکمهٔ «امروز» ----------
            Row(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { onPick(todayJy, todayJm) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.semantics {
                        role = Role.Button
                    }
                ) {
                    Icon(
                        Icons.Filled.Today,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.picker_today))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- شبکهٔ ۳×۴ ماه‌ها ----------
            Text(
                text = stringResource(R.string.picker_months_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val m = row * 3 + col + 1
                            val isViewMonth = sheetYear == viewJy && m == viewJm
                            val isTodayMonth = MonthYearPickerLogic.isCurrentMonth(
                                sheetYear, m, todayJy, todayJm
                            )
                            val monthName = PersianText.jalaliMonths[m - 1]
                            val cd = buildString {
                                append(monthName)
                                append(" ")
                                append(digits(sheetYear.toString()))
                                if (isViewMonth) append("، ماه جاری")
                                if (isTodayMonth) append("، ماه امروز")
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isViewMonth) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isViewMonth) androidx.compose.foundation.BorderStroke(
                                    2.dp, MaterialTheme.colorScheme.primary
                                ) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clickable(role = Role.Button) { onPick(sheetYear, m) }
                                    .semantics { contentDescription = cd }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = monthName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isViewMonth) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isTodayMonth) {
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
                                        )
                                    } else {
                                        Spacer(Modifier.height(9.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
