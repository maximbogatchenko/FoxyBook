package com.foxybook.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import com.foxybook.app.R
import com.foxybook.app.navigation.Routes

@Composable
fun BottomNav(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val newScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.NEW_BOOKS) 1.15f else 1f,
        animationSpec = tween(300), label = "new"
    )
    val searchScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.SEARCH) 1.15f else 1f,
        animationSpec = tween(300), label = "search"
    )
    val libScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.LIBRARY) 1.15f else 1f,
        animationSpec = tween(300), label = "lib"
    )
    val settingsScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.SETTINGS) 1.15f else 1f,
        animationSpec = tween(300), label = "settings"
    )

    NavigationBar {
        NavigationBarItem(
            icon = {
                Box(Modifier.size(24.dp).scale(newScale), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Whatshot, contentDescription = null)
                }
            },
            label = { Text(stringResource(R.string.nav_new_books)) },
            selected = currentRoute == Routes.NEW_BOOKS,
            onClick = {
                if (currentRoute != Routes.NEW_BOOKS) {
                    navController.navigate(Routes.NEW_BOOKS) {
                        popUpTo(Routes.NEW_BOOKS) { inclusive = true }
                    }
                }
            }
        )
        NavigationBarItem(
            icon = {
                Box(Modifier.size(24.dp).scale(searchScale), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            label = { Text(stringResource(R.string.nav_search)) },
            selected = currentRoute == Routes.SEARCH,
            onClick = {
                if (currentRoute != Routes.SEARCH) {
                    navController.navigate(Routes.SEARCH) {
                        popUpTo(Routes.NEW_BOOKS)
                    }
                }
            }
        )
        NavigationBarItem(
            icon = {
                Box(Modifier.size(24.dp).scale(libScale), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoStories, contentDescription = null)
                }
            },
            label = { Text(stringResource(R.string.nav_library)) },
            selected = currentRoute == Routes.LIBRARY,
            onClick = {
                if (currentRoute != Routes.LIBRARY) {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.NEW_BOOKS)
                    }
                }
            }
        )
        NavigationBarItem(
            icon = {
                Box(Modifier.size(24.dp).scale(settingsScale), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }
            },
            label = { Text(stringResource(R.string.nav_settings)) },
            selected = currentRoute == Routes.SETTINGS,
            onClick = {
                if (currentRoute != Routes.SETTINGS) {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(Routes.NEW_BOOKS)
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  Navigation Rail (landscape mode) — compact, icon-only
// ─────────────────────────────────────────────────────────────────

@Composable
fun NavigationRailSide(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val newScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.NEW_BOOKS) 1.15f else 1f,
        animationSpec = tween(300), label = "rail_new"
    )
    val searchScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.SEARCH) 1.15f else 1f,
        animationSpec = tween(300), label = "rail_search"
    )
    val libScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.LIBRARY) 1.15f else 1f,
        animationSpec = tween(300), label = "rail_lib"
    )
    val settingsScale by animateFloatAsState(
        targetValue = if (currentRoute == Routes.SETTINGS) 1.15f else 1f,
        animationSpec = tween(300), label = "rail_settings"
    )

    @Composable
    fun RailItem(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        isSelected: Boolean,
        scale: Float,
        onClick: () -> Unit
    ) {
        val bgColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            animationSpec = tween(300), label = "rail_bg"
        )
        val iconTint by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            animationSpec = tween(300), label = "rail_tint"
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp).scale(scale),
                tint = iconTint
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RailItem(
            icon = Icons.Default.Whatshot,
            contentDescription = stringResource(R.string.nav_new_books),
            isSelected = currentRoute == Routes.NEW_BOOKS,
            scale = newScale,
            onClick = {
                if (currentRoute != Routes.NEW_BOOKS) {
                    navController.navigate(Routes.NEW_BOOKS) {
                        popUpTo(Routes.NEW_BOOKS) { inclusive = true }
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        RailItem(
            icon = Icons.Default.Search,
            contentDescription = stringResource(R.string.nav_search),
            isSelected = currentRoute == Routes.SEARCH,
            scale = searchScale,
            onClick = {
                if (currentRoute != Routes.SEARCH) {
                    navController.navigate(Routes.SEARCH) {
                        popUpTo(Routes.NEW_BOOKS)
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        RailItem(
            icon = Icons.Default.AutoStories,
            contentDescription = stringResource(R.string.nav_library),
            isSelected = currentRoute == Routes.LIBRARY,
            scale = libScale,
            onClick = {
                if (currentRoute != Routes.LIBRARY) {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.NEW_BOOKS)
                    }
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        RailItem(
            icon = Icons.Default.Settings,
            contentDescription = stringResource(R.string.nav_settings),
            isSelected = currentRoute == Routes.SETTINGS,
            scale = settingsScale,
            onClick = {
                if (currentRoute != Routes.SETTINGS) {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(Routes.NEW_BOOKS)
                    }
                }
            }
        )
    }
}
