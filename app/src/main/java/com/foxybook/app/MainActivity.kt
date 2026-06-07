package com.foxybook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.data.api.FlibustaApiImpl
import com.foxybook.app.data.repository.BookRepositoryImpl
import com.foxybook.app.domain.usecases.DownloadBookUseCase
import com.foxybook.app.domain.usecases.GetBookInfoUseCase
import com.foxybook.app.domain.usecases.GetLibraryBooksUseCase
import com.foxybook.app.domain.usecases.GetSeriesBooksUseCase
import com.foxybook.app.domain.usecases.RemoveBookUseCase
import com.foxybook.app.domain.usecases.SearchBooksUseCase
import com.foxybook.app.domain.usecases.SearchByAuthorUseCase
import com.foxybook.app.domain.usecases.SearchBySeriesUseCase
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
import com.foxybook.app.navigation.Routes
import com.foxybook.app.ui.theme.AgonAppTheme
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dataStoreManager = remember { DataStoreManager(this) }
            val themeMode by dataStoreManager.themeMode.collectAsState(initial = "system")
            AgonAppTheme(themeMode = themeMode) {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val api = remember { FlibustaApiImpl(context) }
    val dataStoreManager = remember { DataStoreManager(context) }
    val repository = remember { BookRepositoryImpl(api, context, dataStoreManager) }
    val bookParser = remember { BookParser() }

    val searchBooksUseCase = remember { SearchBooksUseCase(repository) }
    val searchByAuthorUseCase = remember { SearchByAuthorUseCase(repository) }
    val searchBySeriesUseCase = remember { SearchBySeriesUseCase(repository) }
    val getSeriesBooksUseCase = remember { GetSeriesBooksUseCase(repository) }
    val getBookInfoUseCase = remember { GetBookInfoUseCase(repository) }
    val downloadBookUseCase = remember { DownloadBookUseCase(repository) }
    val getLibraryBooksUseCase = remember { GetLibraryBooksUseCase(repository) }
    val removeBookUseCase = remember { RemoveBookUseCase(repository) }

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
                val vm: SearchViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return SearchViewModel(searchBooksUseCase, searchByAuthorUseCase, searchBySeriesUseCase) as T
                    }
                })
                SearchScreen(
                    viewModel = vm,
                    onBookClick = { bookId -> navController.navigate(Routes.bookDetails(bookId)) },
                    onSeriesClick = { seriesId, seriesTitle ->
                        navController.navigate(Routes.seriesDetails(seriesId, seriesTitle))
                    }
                )
            }

            composable(Routes.LIBRARY) {
                val vm: LibraryViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return LibraryViewModel(dataStoreManager) as T
                    }
                })
                LibraryScreen(viewModel = vm, onBookClick = { filePath, bookId, format ->
                    navController.navigate(Routes.reader(bookId, format, filePath))
                })
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return SettingsViewModel(dataStoreManager) as T
                    }
                })
                SettingsScreen(viewModel = vm)
            }

            composable(
                route = Routes.BOOK_DETAILS,
                arguments = listOf(navArgument("bookId") { type = NavType.IntType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getInt("bookId") ?: return@composable
                val vm: BookDetailsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return BookDetailsViewModel(getBookInfoUseCase, downloadBookUseCase) as T
                    }
                })
                BookDetailsScreen(
                    bookId = bookId,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onReadBook = { filePath, format ->
                        navController.navigate(Routes.reader(bookId, format, filePath))
                    }
                )
            }

            composable(
                route = Routes.SERIES_DETAILS,
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("seriesTitle") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedId = backStackEntry.arguments?.getString("seriesId") ?: return@composable
                val encodedTitle = backStackEntry.arguments?.getString("seriesTitle") ?: return@composable
                val seriesId = URLDecoder.decode(encodedId, "UTF-8")
                val seriesTitle = URLDecoder.decode(encodedTitle, "UTF-8")
                val vm: SeriesDetailsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return SeriesDetailsViewModel(getSeriesBooksUseCase) as T
                    }
                })
                SeriesDetailsScreen(
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() },
                    onBookClick = { bookId -> navController.navigate(Routes.bookDetails(bookId)) }
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
                val encodedPath = backStackEntry.arguments?.getString("filePath") ?: return@composable
                val filePath = URLDecoder.decode(encodedPath, "UTF-8")
                val vm: ReaderViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return ReaderViewModel(bookParser, dataStoreManager) as T
                    }
                })
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

@Composable
private fun BottomNav(navController: androidx.navigation.NavHostController) {
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
