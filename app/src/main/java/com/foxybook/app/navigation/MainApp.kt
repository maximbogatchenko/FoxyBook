package com.foxybook.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.foxybook.app.features.newbooks.NewBooksScreen
import com.foxybook.app.features.newbooks.NewBooksViewModel
import com.foxybook.app.features.splash.SplashScreen
import com.foxybook.app.features.details.BookDetailsEvent
import com.foxybook.app.features.details.BookDetailsScreen
import com.foxybook.app.features.details.BookDetailsViewModel
import com.foxybook.app.features.library.LibraryScreen
import com.foxybook.app.features.library.LibraryViewModel
import com.foxybook.app.features.reader.ReaderScreen
import com.foxybook.app.features.reader.ReaderViewModel
import com.foxybook.app.features.author.AuthorBooksScreen
import com.foxybook.app.features.author.AuthorBooksViewModel
import com.foxybook.app.features.search.SearchScreen
import com.foxybook.app.features.search.SearchViewModel
import com.foxybook.app.features.series.SeriesDetailsScreen
import com.foxybook.app.features.series.SeriesDetailsViewModel
import com.foxybook.app.features.settings.SettingsScreen
import com.foxybook.app.features.settings.SettingsViewModel
import com.foxybook.app.core.models.BookCache
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.updater.UpdateChecker
import com.foxybook.app.features.details.openBookExternally
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalContext
import java.net.URLDecoder

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val updateChecker: UpdateChecker = koinInject()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf(Routes.NEW_BOOKS, Routes.SEARCH, Routes.LIBRARY, Routes.SETTINGS)

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
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(
                route = Routes.SPLASH,
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) }
            ) {
                SplashScreen(
                    updateChecker = updateChecker,
                    onLoadingComplete = {
                        navController.navigate(Routes.NEW_BOOKS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = Routes.NEW_BOOKS,
                enterTransition = {
                    slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))
                },
                exitTransition = {
                    slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200))
                },
                popEnterTransition = {
                    slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(200))
                }
            ) {
                val vm: NewBooksViewModel = koinViewModel()
                NewBooksScreen(
                    viewModel = vm,
                    onBookClick = { book ->
                        BookCache.put(book)
                        navController.navigate(Routes.bookDetails(book.id))
                    }
                )
            }

            composable(
                route = Routes.SEARCH,
                enterTransition = {
                    slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(300))
                },
                exitTransition = {
                    slideOutVertically(tween(250)) { -it / 6 } + fadeOut(tween(200))
                },
                popEnterTransition = {
                    slideInVertically(tween(250)) { -it / 6 } + fadeIn(tween(250))
                },
                popExitTransition = {
                    slideOutVertically(tween(300)) { it / 6 } + fadeOut(tween(200))
                }
            ) {
                val vm: SearchViewModel = koinViewModel()
                SearchScreen(
                    viewModel = vm,
                    onBookClick = { book ->
                        BookCache.put(book)
                        navController.navigate(Routes.bookDetails(book.id))
                    },
                    onSeriesClick = { seriesId, seriesTitle, authorId ->
                        navController.navigate(Routes.seriesDetails(seriesId, seriesTitle, authorId))
                    },
                    onAuthorClick = { authorId, authorName ->
                        navController.navigate(Routes.authorBooks(authorId, authorName))
                    }
                )
            }

            composable(
                route = Routes.LIBRARY,
                enterTransition = {
                    slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))
                },
                exitTransition = {
                    slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(200))
                },
                popEnterTransition = {
                    slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(200))
                }
            ) {
                val vm: LibraryViewModel = koinViewModel()
                val context = LocalContext.current
                LibraryScreen(
                    viewModel = vm,
                    onBookClick = { filePath, bookId, format ->
                        val bookFormat = BookFormat.fromExtension(format)
                        if (bookFormat != null && bookFormat.isNativelySupported()) {
                            navController.navigate(Routes.reader(bookId, format, filePath))
                        } else {
                            openBookExternally(context, filePath, bookFormat?.mimeType ?: "application/octet-stream")
                        }
                    }
                )
            }

            composable(
                route = Routes.SETTINGS,
                enterTransition = {
                    slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(300))
                },
                exitTransition = {
                    slideOutVertically(tween(250)) { -it / 6 } + fadeOut(tween(200))
                },
                popEnterTransition = {
                    slideInVertically(tween(250)) { -it / 6 } + fadeIn(tween(250))
                },
                popExitTransition = {
                    slideOutVertically(tween(300)) { it / 6 } + fadeOut(tween(200))
                }
            ) {
                val vm: SettingsViewModel = koinViewModel()
                SettingsScreen(viewModel = vm)
            }

            composable(
                route = Routes.BOOK_DETAILS,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.IntType }
                ),
                enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
                exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200)) },
                popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)) }
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId")
                if (bookId == null) {
                    androidx.compose.material3.Text("Ошибка: книга не найдена")
                    return@composable
                }
                val initialBook = com.foxybook.app.core.models.BookCache.get(bookId)

                val vm: BookDetailsViewModel = koinViewModel()
                val context = LocalContext.current
                LaunchedEffect(bookId) {
                    vm.onEvent(BookDetailsEvent.LoadBook(bookId, initialBook))
                }

                BookDetailsScreen(
                    bookId = bookId,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onReadBook = { filePath, format ->
                        val bookFormat = BookFormat.fromExtension(format)
                        if (bookFormat != null && !bookFormat.isNativelySupported()) {
                            openBookExternally(context, filePath, bookFormat.mimeType)
                        } else {
                            navController.navigate(Routes.reader(bookId, format, filePath))
                        }
                    },
                    onGoToSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(
                route = Routes.SERIES_DETAILS,
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("seriesTitle") { type = NavType.StringType },
                    navArgument("authorId") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val seriesId = backStackEntry.arguments?.getString("seriesId") ?: return@composable
                val rawTitle = backStackEntry.arguments?.getString("seriesTitle") ?: return@composable
                val seriesTitle = try { URLDecoder.decode(rawTitle, "UTF-8") } catch (_: Exception) { rawTitle.replace("+", " ") }
                val authorId = backStackEntry.arguments?.getString("authorId") ?: ""

                val vm: SeriesDetailsViewModel = koinViewModel()
                SeriesDetailsScreen(
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    authorId = authorId,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onBookClick = { book ->
                        BookCache.put(book)
                        navController.navigate(Routes.bookDetails(book.id))
                    }
                )
            }

            composable(
                route = Routes.AUTHOR_BOOKS,
                arguments = listOf(
                    navArgument("authorId") { type = NavType.StringType },
                    navArgument("authorName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val authorId = backStackEntry.arguments?.getString("authorId") ?: return@composable
                val rawName = backStackEntry.arguments?.getString("authorName") ?: return@composable
                val authorName = try { URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName.replace("+", " ") }

                val vm: AuthorBooksViewModel = koinViewModel()
                AuthorBooksScreen(
                    authorId = authorId,
                    authorName = authorName,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onBookClick = { book ->
                        BookCache.put(book)
                        navController.navigate(Routes.bookDetails(book.id))
                    }
                )
            }

            composable(
                route = Routes.READER,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.IntType },
                    navArgument("bookFormat") { type = NavType.StringType },
                    navArgument("filePath") { type = NavType.StringType }
                ),
                enterTransition = { slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)) },
                exitTransition = { fadeOut(tween(200)) },
                popExitTransition = { slideOutHorizontally(tween(350)) { it } + fadeOut(tween(250)) }
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
}
