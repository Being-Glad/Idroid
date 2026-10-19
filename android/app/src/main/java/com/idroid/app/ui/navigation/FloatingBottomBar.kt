package com.idroid.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.ui.*

/**
 * Premium floating pill bottom navigation bar designed with thumb-zone ergonomics.
 * Follows Design-Motion-Principles (Emil Kowalski speed + Jakub Krehel production polish):
 * - Spring scale on tap (0.92f)
 * - Tactile haptic feedback
 * - Tinted pill active indicator
 * - 48dp+ tap targets
 */
@Composable
fun FloatingBottomBar(
    currentTab: CompanionNavTab,
    onTabSelected: (CompanionNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianSurface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.8f)),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(68.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = SolarOrange.copy(alpha = 0.15f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompanionNavTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                FloatingNavItem(
                    tab = tab,
                    isSelected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    tab: CompanionNavTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "nav_item_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) SolarOrange else TextMuted,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "nav_item_icon_color"
    )

    val pillBackground by animateColorAsState(
        targetValue = if (isSelected) SolarOrange.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "nav_item_pill_bg"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(pillBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = tab.title,
                fontFamily = InterFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                color = if (isSelected) PureWhite else TextMuted,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
