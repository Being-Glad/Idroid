package com.idroid.app.ui.widgets

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.model.DashboardCategory
import com.idroid.app.model.DashboardControl
import com.idroid.app.model.DashboardRegistry
import com.idroid.app.ui.*
import com.idroid.app.utils.DashboardPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDashboardModal(
    enabledIds: Set<String>,
    onEnabledIdsChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    pillOrder: List<String> = emptyList(),
    circleOrder: List<String> = emptyList(),
    onPillOrderChanged: ((List<String>) -> Unit)? = null,
    onCircleOrderChanged: ((List<String>) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var activeIds by remember(enabledIds) { mutableStateOf(enabledIds) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        scrimColor = ObsidianVoid.copy(alpha = 0.85f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(ObsidianBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Header Row: Icon, Title, Reset, and Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SolarOrange.copy(alpha = 0.15f))
                            .border(1.dp, SolarOrange.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = SolarOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Add / Remove Controls",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        Text(
                            text = "${activeIds.size} of ${DashboardRegistry.ALL_CONTROLS.size} controls active",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset to Defaults Button
                    val resetShape = RoundedCornerShape(10.dp)
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            DashboardPreferences.resetToDefaults(context)
                            val defs = DashboardRegistry.DEFAULT_ENABLED_IDS
                            val defPills = DashboardPreferences.getPillOrder(context, defs)
                            val defCircles = DashboardPreferences.getCircleOrder(context, defs)
                            activeIds = defs
                            onEnabledIdsChanged(defs)
                            onPillOrderChanged?.invoke(defPills)
                            onCircleOrderChanged?.invoke(defCircles)
                            Toast.makeText(context, "Reset to defaults", Toast.LENGTH_SHORT).show()
                        },
                        color = ObsidianSurfaceVariant,
                        shape = resetShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.clip(resetShape)
                    ) {
                        Text(
                            text = "Reset",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily,
                            color = SolarOrange,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurfaceVariant)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable List of Categories & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCategory.values().forEach { category ->
                    val controlsInCategory = DashboardRegistry.ALL_CONTROLS.filter { it.category == category }
                    if (controlsInCategory.isNotEmpty()) {
                        CategoryBlock(
                            category = category,
                            controls = controlsInCategory,
                            activeIds = activeIds,
                            onToggle = { id, enabled ->
                                DashboardPreferences.setControlEnabled(context, id, enabled)
                                val updated = if (enabled) activeIds + id else activeIds - id
                                activeIds = updated
                                onEnabledIdsChanged(updated)
                                val newPills = DashboardPreferences.getPillOrder(context, updated)
                                val newCircles = DashboardPreferences.getCircleOrder(context, updated)
                                onPillOrderChanged?.invoke(newPills)
                                onCircleOrderChanged?.invoke(newCircles)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun CategoryBlock(
    category: DashboardCategory,
    controls: List<DashboardControl>,
    activeIds: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = SolarOrange,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = category.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                color = PureWhite
            )
        }

        // Card Container with Rows
        val cardShape = RoundedCornerShape(16.dp)
        Surface(
            color = ObsidianSurfaceVariant.copy(alpha = 0.5f),
            shape = cardShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                controls.forEachIndexed { index, control ->
                    val isChecked = activeIds.contains(control.id)
                    val rowShape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .clickable {
                                onToggle(control.id, !isChecked)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChecked) SolarOrange.copy(alpha = 0.15f) else ObsidianBorder.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    control.icon,
                                    contentDescription = null,
                                    tint = if (isChecked) SolarOrange else TextMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = control.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFontFamily,
                                    color = PureWhite
                                )
                                Text(
                                    text = control.description,
                                    fontSize = 11.5.sp,
                                    fontFamily = InterFontFamily,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Switch(
                            checked = isChecked,
                            onCheckedChange = { onToggle(control.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = SolarOrange,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = ObsidianBorder
                            )
                        )
                    }

                    if (index < controls.size - 1) {
                        HorizontalDivider(
                            color = ObsidianBorder.copy(alpha = 0.4f),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
