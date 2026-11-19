package com.forgeprom.codestudio.presentation.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeprom.codestudio.core.ui.*

/**
 * استوديو تخصيص المظهر والتوافق البصري الشامل (AppearanceStudioBottomSheet)
 * يتيح للمستخدم ضبط المظهر والألوان والكثافة البصرية وتوافق الأجهزة من مكان واحد،
 * مع حفظ تلقائي فوري ومعاينة حية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceStudioBottomSheet(
    visualSettings: StudioVisualSettings,
    onUpdateSettings: (StudioVisualSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val visual = LocalStudioVisualSettings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = visual.surfaceBg,
        contentColor = visual.primaryText,
        modifier = Modifier.testTag("appearance_studio_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // رأس اللوحة
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "استوديو المظهر",
                            tint = visual.accentSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "استوديو المظهر والتجربة البصرية",
                                fontWeight = FontWeight.Bold,
                                color = visual.primaryText,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "تخصيص ذكي متوافق مع شاشات الهواتف والتابلت (Android 8–15+)",
                                color = visual.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = { onUpdateSettings(StudioVisualSettings()) },
                        modifier = Modifier.testTag("reset_appearance_button")
                    ) {
                        Text("افتراضي", fontSize = 11.sp, color = visual.accentSecondary)
                    }
                }
            }

            // 1. نمط الخلفية والسطح (Surface Mode)
            item {
                Text(
                    text = "1. سمة الخلفية والسطوع",
                    fontWeight = FontWeight.Bold,
                    color = visual.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeSurfaceMode.entries, key = { it.name }) { mode ->
                        FilterChip(
                            selected = visualSettings.surfaceMode == mode,
                            onClick = { onUpdateSettings(visualSettings.copy(surfaceMode = mode)) },
                            label = { Text(mode.labelAr, fontSize = 11.sp) },
                            modifier = Modifier.testTag("surface_mode_${mode.name}")
                        )
                    }
                }
            }

            // 2. الهوية اللونية وMaterial You
            item {
                Text(
                    text = "2. الهوية اللونية وألوان النظام (Accent & Material You)",
                    fontWeight = FontWeight.Bold,
                    color = visual.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Surface(
                    color = visual.recessedBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, visual.accentPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = supportsDynamicColor) {
                                onUpdateSettings(visualSettings.copy(useDynamicColor = !visualSettings.useDynamicColor))
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ألوان Material You التلقائية",
                                fontWeight = FontWeight.SemiBold,
                                color = visual.primaryText,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (supportsDynamicColor) "مزامنة ألوان التطبيق مع خلفية جهازك (Android 12+)"
                                else "غير مدعوم في هذا الإصدار — تُستخدم لوحة الألوان المختارة",
                                color = visual.secondaryText,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = visualSettings.useDynamicColor && supportsDynamicColor,
                            enabled = supportsDynamicColor,
                            onCheckedChange = {
                                onUpdateSettings(visualSettings.copy(useDynamicColor = it))
                            },
                            modifier = Modifier.testTag("dynamic_color_switch")
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AccentPalette.entries, key = { it.name }) { palette ->
                        val isSelected = visualSettings.accentPalette == palette && !visualSettings.useDynamicColor
                        Surface(
                            onClick = {
                                onUpdateSettings(
                                    visualSettings.copy(
                                        accentPalette = palette,
                                        useDynamicColor = false
                                    )
                                )
                            },
                            color = if (isSelected) palette.primary.copy(alpha = 0.22f) else visual.recessedBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) palette.accent else Color.Transparent
                            ),
                            modifier = Modifier.testTag("palette_${palette.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(palette.primary)
                                        .border(1.dp, palette.accent, CircleShape)
                                )
                                Text(
                                    text = palette.labelAr,
                                    color = visual.primaryText,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // 3. نمط البطاقات (3D / Flat / High Contrast)
            item {
                Text(
                    text = "3. هندسة البطاقات والشاشات",
                    fontWeight = FontWeight.Bold,
                    color = visual.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardVisualStyle.entries.forEach { style ->
                        FilterChip(
                            selected = visualSettings.cardStyle == style,
                            onClick = { onUpdateSettings(visualSettings.copy(cardStyle = style)) },
                            label = { Text(style.labelAr, fontSize = 10.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f).testTag("card_style_${style.name}")
                        )
                    }
                }
            }

            // 4. الكثافة وحجم الخط (UI Density & Accessibility Scale)
            item {
                Text(
                    text = "4. الكثافة البصرية وحجم النصوص",
                    fontWeight = FontWeight.Bold,
                    color = visual.primaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UiDensityMode.entries.forEach { density ->
                        FilterChip(
                            selected = visualSettings.densityMode == density,
                            onClick = { onUpdateSettings(visualSettings.copy(densityMode = density)) },
                            label = { Text(density.labelAr, fontSize = 10.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f).testTag("density_${density.name}")
                        )
                    }
                }
            }

            // 5. راحة العين ووضع الأداء الخفيف للأجهزة الاقتصادية
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(visualSettings.copy(isEyeComfortWarm = !visualSettings.isEyeComfortWarm))
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فلتر حماية العين الدافئ (Eye-Care)", fontWeight = FontWeight.SemiBold, color = visual.primaryText, fontSize = 12.sp)
                                Text("تقليل الإضاءة الزرقاء أثناء البرمجة وقراءة المواصفات الطويلة", color = visual.secondaryText, fontSize = 10.sp)
                            }
                            Switch(
                                checked = visualSettings.isEyeComfortWarm,
                                onCheckedChange = {
                                    onUpdateSettings(visualSettings.copy(isEyeComfortWarm = it))
                                },
                                modifier = Modifier.testTag("eye_comfort_switch")
                            )
                        }
                    }

                    Surface(
                        color = visual.recessedBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateSettings(visualSettings.copy(performanceLiteMode = !visualSettings.performanceLiteMode))
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("وضع الأداء الخفيف (Performance Lite)", fontWeight = FontWeight.SemiBold, color = visual.primaryText, fontSize = 12.sp)
                                Text("تقليل الظلال والتدرجات لإطالة عمر البطارية على الأجهزة الاقتصادية", color = visual.secondaryText, fontSize = 10.sp)
                            }
                            Switch(
                                checked = visualSettings.performanceLiteMode,
                                onCheckedChange = {
                                    onUpdateSettings(visualSettings.copy(performanceLiteMode = it))
                                },
                                modifier = Modifier.testTag("performance_lite_switch")
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = visual.accentPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("close_appearance_sheet")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اعتماد وحفظ التخصيصات", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}
