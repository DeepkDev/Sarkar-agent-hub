package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contracts.CapabilityStatus
import com.example.contracts.PermissionLevel
import com.example.contracts.TaskStatus
import com.example.ui.theme.*

@Composable
fun StatusBadge(
    status: CapabilityStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status) {
        CapabilityStatus.IMPLEMENTED -> NeonEmerald.copy(alpha = 0.15f) to NeonEmerald
        CapabilityStatus.PLACEHOLDER -> AlertAmber.copy(alpha = 0.15f) to AlertAmber
        CapabilityStatus.NOT_CONFIGURED -> TextTertiary.copy(alpha = 0.2f) to TextSecondary
        CapabilityStatus.DISABLED -> CrimsonRuby.copy(alpha = 0.15f) to CrimsonRuby
    }

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("status_badge_${status.name.lowercase()}")
    ) {
        Text(
            text = status.name,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TaskStatusBadge(
    status: TaskStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status) {
        TaskStatus.COMPLETED -> NeonEmerald.copy(alpha = 0.15f) to NeonEmerald
        TaskStatus.EXECUTING, TaskStatus.PLANNING, TaskStatus.VERIFYING -> CyberCyan.copy(alpha = 0.15f) to CyberCyan
        TaskStatus.WAITING_FOR_PERMISSION -> AlertAmber.copy(alpha = 0.2f) to AlertAmber
        TaskStatus.PENDING -> TextTertiary.copy(alpha = 0.2f) to TextSecondary
        TaskStatus.FAILED -> CrimsonRuby.copy(alpha = 0.15f) to CrimsonRuby
        TaskStatus.CANCELLED, TaskStatus.EXPIRED -> TextTertiary.copy(alpha = 0.2f) to TextTertiary
        TaskStatus.PAUSED -> AlertAmber.copy(alpha = 0.15f) to AlertAmber
    }

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("task_status_${status.name.lowercase()}")
    ) {
        Text(
            text = status.name.replace("_", " "),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PermissionLevelBadge(
    level: PermissionLevel,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (level) {
        PermissionLevel.PUBLIC, PermissionLevel.SAFE -> NeonEmerald.copy(alpha = 0.15f) to NeonEmerald
        PermissionLevel.CONFIRMATION_REQUIRED -> AlertAmber.copy(alpha = 0.2f) to AlertAmber
        PermissionLevel.RESTRICTED -> CrimsonRuby.copy(alpha = 0.2f) to CrimsonRuby
    }

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = level.name.replace("_", " "),
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    accentColor: Color = CyberCyan,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
