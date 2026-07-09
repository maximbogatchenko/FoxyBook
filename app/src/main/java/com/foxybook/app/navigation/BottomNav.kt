package com.foxybook.app.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
