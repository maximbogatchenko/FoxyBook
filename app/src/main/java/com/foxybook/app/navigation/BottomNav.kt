package com.foxybook.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import com.foxybook.app.navigation.Routes

@Composable
fun BottomNav(navController: androidx.navigation.NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Поиск") },
            selected = currentRoute == Routes.SEARCH,
            onClick = {
                navController.navigate(Routes.SEARCH) {
                    popUpTo(Routes.SEARCH) { inclusive = true }
                }
            },
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
            label = { Text("Библиотека") },
            selected = currentRoute == Routes.LIBRARY,
            onClick = {
                navController.navigate(Routes.LIBRARY) {
                    popUpTo(Routes.SEARCH)
                }
            },
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Настройки") },
            selected = currentRoute == Routes.SETTINGS,
            onClick = {
                navController.navigate(Routes.SETTINGS) {
                    popUpTo(Routes.SEARCH)
                }
            },
        )
    }
}
