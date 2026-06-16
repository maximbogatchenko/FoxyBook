package com.foxybook.app.di

import com.foxybook.app.core.database.AppDatabase
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.data.api.FlibustaApi
import com.foxybook.app.data.api.FlibustaApiImpl
import com.foxybook.app.data.repository.BookRepositoryImpl
import com.foxybook.app.data.storage.FileDownloader
import com.foxybook.app.domain.repository.BookRepository
import com.foxybook.app.domain.usecases.DownloadBookUseCase
import com.foxybook.app.domain.usecases.GetBookInfoUseCase
import com.foxybook.app.domain.usecases.GetLibraryBooksUseCase
import com.foxybook.app.domain.usecases.GetSeriesBooksUseCase
import com.foxybook.app.domain.usecases.RemoveBookUseCase
import com.foxybook.app.domain.usecases.SearchBooksUseCase
import com.foxybook.app.domain.usecases.SearchByAuthorUseCase
import com.foxybook.app.domain.usecases.SearchBySeriesUseCase
import com.foxybook.app.features.details.BookDetailsViewModel
import com.foxybook.app.features.library.LibraryViewModel
import com.foxybook.app.features.reader.ReaderViewModel
import com.foxybook.app.features.search.SearchViewModel
import com.foxybook.app.features.series.SeriesDetailsViewModel
import com.foxybook.app.core.updater.UpdateChecker
import com.foxybook.app.features.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // ─── Core ───
    single { DataStoreManager(androidContext()) }
    single { AppDatabase.getInstance(androidContext()) }
    single { BookParser(androidContext()) }
    single { UpdateChecker(androidContext()) }

    // ─── API ───
    single<FlibustaApi> { FlibustaApiImpl(androidContext()) }

    // ─── Storage ───
    single { FileDownloader(androidContext()) }

    // ─── Data ───
    single { BookDataRepository(get()) }
    single<BookRepository> { BookRepositoryImpl(get(), get(), get(), get()) }

    // ─── Use Cases ───
    factory { SearchBooksUseCase(get()) }
    factory { SearchByAuthorUseCase(get()) }
    factory { SearchBySeriesUseCase(get()) }
    factory { GetSeriesBooksUseCase(get()) }
    factory { GetBookInfoUseCase(get()) }
    factory { DownloadBookUseCase(get()) }
    factory { GetLibraryBooksUseCase(get()) }
    factory { RemoveBookUseCase(get()) }

    // ─── ViewModels ───
    viewModel { SearchViewModel(get(), get(), get(), get()) }
    viewModel { LibraryViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { BookDetailsViewModel(get(), get(), get(), get()) }
    viewModel { ReaderViewModel(get(), get(), get(), get()) }
    viewModel { SeriesDetailsViewModel(get(), get()) }
}
