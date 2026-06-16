package com.foxybook.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.foxybook.app.features.details.BookDetailsEvent
import com.foxybook.app.features.details.BookDetailsScreen
import com.foxybook.app.features.details.BookDetailsViewModel
import com.foxybook.app.features.library.LibraryScreen
import com.foxybook.app.features.library.LibraryViewModel
import com.foxybook.app.features.reader.ReaderScreen
import com.foxybook.app.features.reader.ReaderViewModel
import com.foxybook.app.features.search.SearchScreen
import com.foxybook.app.features.search.SearchViewModel
import com.foxybook.app.features.series.SeriesDetailsScreen
import com.foxybook.app.features.series.SeriesDetailsViewModel
import com.foxybook.app.features.settings.SettingsScreen
import com.foxybook.app.features.settings.SettingsViewModel
import com.foxybook.app.core.updater.UpdateChecker
import com.foxybook.app.features.update.UpdateDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.net.URLDecoder

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val updateChecker: UpdateChecker = koinInject()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf(Routes.SEARCH, Routes.LIBRARY, Routes.SETTINGS)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BottomNav(navController)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SEARCH,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Routes.SEARCH) {
                val vm: SearchViewModel = koinViewModel()
                SearchScreen(
                    viewModel = vm,
                    onBookClick = { book ->
                        navController.navigate(Routes.bookDetails(book.id, book.title, book.author, book.coverUrl))
                    },
                    onSeriesClick = { seriesId, seriesTitle ->
                        navController.navigate(Routes.seriesDetails(seriesId, seriesTitle))
                    }
                )
            }

            composable(Routes.LIBRARY) {
                val vm: LibraryViewModel = koinViewModel()
                LibraryScreen(
                    viewModel = vm,
                    onBookClick = { filePath, bookId, format ->
                        navController.navigate(Routes.reader(bookId, format, filePath))
                    }
                )
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = koinViewModel()
                SettingsScreen(viewModel = vm)
            }

            composable(
                route = Routes.BOOK_DETAILS,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.IntType },
                    navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("author") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("cover") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                val title = backStackEntry.arguments?.getString("title")
                val author = backStackEntry.arguments?.getString("author")
                val cover = backStackEntry.arguments?.getString("cover")

                val initialBook = if (title != null && author != null) {
                    com.foxybook.app.core.models.Book(
                        id = bookId,
                        title = title,
                        author = author,
                        link = "/b/$bookId",
                        sendLink = "/send/$bookId",
                        coverUrl = cover ?: ""
                    )
                } else null

                val vm: BookDetailsViewModel = koinViewModel()
                LaunchedEffect(bookId) {
                    vm.onEvent(BookDetailsEvent.LoadBook(bookId, initialBook))
                }

                BookDetailsScreen(
                    bookId = bookId,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onReadBook = { filePath, format ->
                        navController.navigate(Routes.reader(bookId, format, filePath))
                    },
                    onGoToSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(
                route = Routes.SERIES_DETAILS,
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("seriesTitle") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val seriesId = backStackEntry.arguments?.getString("seriesId") ?: return@composable
                val rawTitle = backStackEntry.arguments?.getString("seriesTitle") ?: return@composable
                val seriesTitle = try { URLDecoder.decode(rawTitle, "UTF-8") } catch (_: Exception) { rawTitle.replace("+", " ") }

                val vm: SeriesDetailsViewModel = koinViewModel()
                SeriesDetailsScreen(
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onBookClick = { book ->
                        navController.navigate(Routes.bookDetails(book.id, book.title, book.author, book.coverUrl))
                    }
                )
            }

            composable(
                route = Routes.READER,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.IntType },
                    navArgument("bookFormat") { type = NavType.StringType },
                    navArgument("filePath") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                val bookFormat = backStackEntry.arguments?.getString("bookFormat") ?: return@composable
                val rawFilePath = backStackEntry.arguments?.getString("filePath") ?: return@composable
                val filePath = try { URLDecoder.decode(rawFilePath, "UTF-8") } catch (_: Exception) { rawFilePath }

                val vm: ReaderViewModel = koinViewModel()
                ReaderScreen(
                    filePath = filePath,
                    bookId = bookId,
                    bookFormat = bookFormat,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

    UpdateDialog(updateChecker = updateChecker)
}
