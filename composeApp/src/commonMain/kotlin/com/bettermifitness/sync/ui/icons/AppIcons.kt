package com.bettermifitness.sync.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import mi_fitness_app.composeapp.generated.resources.Res
import mi_fitness_app.composeapp.generated.resources.ic_arrow_back_24
import mi_fitness_app.composeapp.generated.resources.ic_bedtime_24
import mi_fitness_app.composeapp.generated.resources.ic_bloodtype_24
import mi_fitness_app.composeapp.generated.resources.ic_check_circle_24
import mi_fitness_app.composeapp.generated.resources.ic_content_copy_24
import mi_fitness_app.composeapp.generated.resources.ic_content_paste_24
import mi_fitness_app.composeapp.generated.resources.ic_directions_walk_24
import mi_fitness_app.composeapp.generated.resources.ic_error_outline_24
import mi_fitness_app.composeapp.generated.resources.ic_favorite_24
import mi_fitness_app.composeapp.generated.resources.ic_fitness_center_24
import mi_fitness_app.composeapp.generated.resources.ic_local_fire_department_24
import mi_fitness_app.composeapp.generated.resources.ic_logout_24
import mi_fitness_app.composeapp.generated.resources.ic_mail_24
import mi_fitness_app.composeapp.generated.resources.ic_monitor_heart_24
import mi_fitness_app.composeapp.generated.resources.ic_monitor_weight_24
import mi_fitness_app.composeapp.generated.resources.ic_straighten_24
import mi_fitness_app.composeapp.generated.resources.ic_sync_24
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Official Material Symbols (Android vector XML) via composeResources.
 * Tint with [AppIcon]; iOS requires Xcode phase `syncComposeResourcesForIos`.
 */
object AppIcons {
    val Mail: DrawableResource get() = Res.drawable.ic_mail_24
    val Sync: DrawableResource get() = Res.drawable.ic_sync_24
    val ArrowBack: DrawableResource get() = Res.drawable.ic_arrow_back_24
    val Logout: DrawableResource get() = Res.drawable.ic_logout_24
    val ContentPaste: DrawableResource get() = Res.drawable.ic_content_paste_24
    val ContentCopy: DrawableResource get() = Res.drawable.ic_content_copy_24
    val CheckCircle: DrawableResource get() = Res.drawable.ic_check_circle_24
    val ErrorOutline: DrawableResource get() = Res.drawable.ic_error_outline_24
    val MonitorHeart: DrawableResource get() = Res.drawable.ic_monitor_heart_24
    val Favorite: DrawableResource get() = Res.drawable.ic_favorite_24
    val Bedtime: DrawableResource get() = Res.drawable.ic_bedtime_24
    val DirectionsWalk: DrawableResource get() = Res.drawable.ic_directions_walk_24
    val Straighten: DrawableResource get() = Res.drawable.ic_straighten_24
    val LocalFireDepartment: DrawableResource get() = Res.drawable.ic_local_fire_department_24
    val Bloodtype: DrawableResource get() = Res.drawable.ic_bloodtype_24
    val MonitorWeight: DrawableResource get() = Res.drawable.ic_monitor_weight_24
    val FitnessCenter: DrawableResource get() = Res.drawable.ic_fitness_center_24
}

@Composable
fun AppIcon(
    icon: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
