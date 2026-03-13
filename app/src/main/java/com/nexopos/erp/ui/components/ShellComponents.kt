package com.nexopos.erp.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

data class ShellDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    companion object {
        fun sell(route: String) = ShellDestination(route, R.string.shell_tab_sell, Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale)
        fun orders(route: String) = ShellDestination(route, R.string.shell_tab_orders, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong)
        fun products(route: String) = ShellDestination(route, R.string.shell_tab_products, Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
        fun customers(route: String) = ShellDestination(route, R.string.shell_tab_customers, Icons.Filled.People, Icons.Outlined.People)
        fun more(route: String) = ShellDestination(route, R.string.shell_tab_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    }
}

data class QuickActionItem(
    val id: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    companion object {
        val Scan = QuickActionItem("scan", R.string.quick_action_scan, Icons.Filled.QrCodeScanner)
        val NewOrder = QuickActionItem("new_order", R.string.quick_action_new_order, Icons.AutoMirrored.Outlined.ReceiptLong)
        val AddCustom = QuickActionItem("custom_item", R.string.quick_action_custom_item, Icons.Filled.AddCircleOutline)
        val CustomerLookup = QuickActionItem("customer_lookup", R.string.quick_action_customer_lookup, Icons.Outlined.Search)
    }
}

data class HubActionItem(
    val id: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    @StringRes val subtitleRes: Int? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable (() -> Unit))? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.appColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.appColors.background,
            titleContentColor = MaterialTheme.appColors.text,
            navigationIconContentColor = MaterialTheme.appColors.text,
            actionIconContentColor = MaterialTheme.appColors.text
        )
    )
}

@Composable
fun AppBottomNav(
    destinations: List<ShellDestination>,
    currentRoute: String?,
    onNavigate: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = MaterialTheme.appColors.surfaceRaised
    ) {
        destinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.appColors.primary,
                    selectedTextColor = MaterialTheme.appColors.primary,
                    unselectedIconColor = MaterialTheme.appColors.muted,
                    unselectedTextColor = MaterialTheme.appColors.muted,
                    indicatorColor = MaterialTheme.appColors.surfaceOverlay
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitcherSheet(
    destinations: List<ShellDestination>,
    onDismiss: () -> Unit,
    onDestinationSelected: (ShellDestination) -> Unit,
    hubPrimary: List<HubActionItem> = emptyList(),
    hubModules: List<HubActionItem> = emptyList()
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.appColors.surfaceOverlay,
        shape = RoundedCornerShape(
            topStart = MaterialTheme.appRadii.large,
            topEnd = MaterialTheme.appRadii.large
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.appSpacing.screen,
                    end = MaterialTheme.appSpacing.screen,
                    bottom = MaterialTheme.appSpacing.xxl
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
        ) {
            Text(
                text = stringResource(R.string.quick_actions_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.appColors.text
            )
            destinations.forEach { destination ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDestinationSelected(destination) },
                    containerColor = MaterialTheme.appColors.surfaceRaised
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
                            color = MaterialTheme.appColors.surfaceOverlay
                        ) {
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = null,
                                tint = MaterialTheme.appColors.primary,
                                modifier = Modifier.padding(MaterialTheme.appSpacing.m)
                            )
                        }
                        Text(
                            text = stringResource(destination.labelRes),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.appColors.text,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (hubPrimary.isNotEmpty() || hubModules.isNotEmpty()) {
                val showHubHeader = destinations.isNotEmpty()
                if (showHubHeader) {
                    HorizontalDivider(color = MaterialTheme.appColors.divider)
                    Text(
                        text = stringResource(R.string.more_hub_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.appColors.text
                    )
                }
                hubPrimary.forEach { item ->
                    HubActionCard(item)
                }
                if (hubModules.isNotEmpty()) {
                    if (showHubHeader) {
                        Text(
                            text = stringResource(R.string.more_modules_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.appColors.text
                        )
                    }
                    hubModules.forEach { item ->
                        HubActionCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubActionCard(item: HubActionItem) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = item.onClick,
        containerColor = MaterialTheme.appColors.surfaceRaised
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
        ) {
            Surface(
                shape = RoundedCornerShape(MaterialTheme.appRadii.medium),
                color = MaterialTheme.appColors.surfaceOverlay
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.appColors.primary,
                    modifier = Modifier.padding(MaterialTheme.appSpacing.m)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
            ) {
                Text(
                    text = stringResource(item.labelRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.appColors.text
                )
                item.subtitleRes?.let { subtitleRes ->
                    Text(
                        text = stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.appRadii.fab),
        containerColor = MaterialTheme.appColors.primary,
        contentColor = MaterialTheme.appColors.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}
