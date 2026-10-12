package com.example.localreader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.localreader.ui.group.GroupScreen
import com.example.localreader.ui.reader.ReaderScreen
import com.example.localreader.ui.settings.SettingsScreen
import com.example.localreader.ui.shelf.ShelfScreen

object Destinations {
    const val SHELF = "shelf"
    const val GROUPS = "groups"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}"
    fun reader(bookId: Long) = "reader/$bookId"
}

/** 底部导航切换：保存/恢复各 Tab 状态 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Destinations.SHELF,
        modifier = modifier
    ) {
        composable(Destinations.SHELF) {
            ShelfScreen(onOpenBook = { bookId -> navController.navigate(Destinations.reader(bookId)) })
        }
        composable(Destinations.GROUPS) {
            GroupScreen(onNavigateToShelf = { navController.navigateToTab(Destinations.SHELF) })
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen()
        }
        composable(
            route = Destinations.READER,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) {
            ReaderScreen(onBack = { navController.popBackStack() })
        }
    }
}
