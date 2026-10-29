package com.foxybook.app.core.models

/**
 * Ручная отметка чтения. Поверх неё действует автоматическое определение
 * по прогрессу и дате последнего открытия.
 */
object BookReadState {
    /** Статус определяется автоматически. */
    const val AUTO = "AUTO"

    /** Пользователь отметил книгу прочитанной. */
    const val FINISHED = "FINISHED"

    /** Пользователь вернул книгу в непрочитанные. */
    const val UNREAD = "UNREAD"

    val ALL = setOf(AUTO, FINISHED, UNREAD)

    /** Прогресс, который выставляется при ручной отметке «прочитано». */
    const val PROGRESS_FINISHED = 100

    fun normalize(value: String?): String {
        val upper = value?.trim()?.uppercase() ?: return AUTO
        return if (upper in ALL) upper else AUTO
    }
}

/** Группа books в библиотеке. */
enum class ReadStatus {
    /** Открыта, но не дочитана. */
    READING,

    /** Ещё не открывалась. */
    UNREAD,

    /** Дочитана или отмечена прочитанной. */
    FINISHED
}

/** Поле сортировки библиотеки. */
enum class LibrarySortField {
    DATE_ADDED,
    LAST_READ,
    TITLE,
    AUTHOR,
    PROGRESS
}

/**
 * Настройка сортировки библиотеки. Хранится в DataStore одной строкой,
 * поэтому сериализация и разбор собраны здесь же и покрыты тестами.
 */
data class LibrarySortSpec(
    val field: LibrarySortField = LibrarySortField.DATE_ADDED,
    val descending: Boolean = true,
    val groupByStatus: Boolean = true
) {

    fun encode(): String = buildString {
        append(field.name)
        append('|')
        append(if (descending) DESC_MARKER else ASC_MARKER)
        append('|')
        append(if (groupByStatus) FLAG_ON else FLAG_OFF)
    }

    companion object {
        const val DESC_MARKER = "desc"
        const val ASC_MARKER = "asc"
        const val FLAG_ON = "1"
        const val FLAG_OFF = "0"

        /** Порядок как до появления настройки: новые сверху. */
        val DEFAULT = LibrarySortSpec(
            field = LibrarySortField.DATE_ADDED,
            descending = true,
            groupByStatus = true
        )

        fun decode(raw: String?): LibrarySortSpec {
            if (raw.isNullOrBlank()) return DEFAULT
            val parts = raw.split('|')
            val field = parts.getOrNull(0)?.trim()
                ?.let { name -> LibrarySortField.entries.firstOrNull { it.name == name } }
                ?: return DEFAULT
            val direction = parts.getOrNull(1)?.trim()
            val groups = parts.getOrNull(2)?.trim()
            return LibrarySortSpec(
                field = field,
                descending = when (direction) {
                    ASC_MARKER -> false
                    DESC_MARKER -> true
                    else -> true
                },
                groupByStatus = groups != FLAG_OFF
            )
        }
    }
}

/** Группа книг с заголовком. [status] == null, когда группировка выключена. */
data class BookGroup(
    val status: ReadStatus?,
    val books: List<LibraryBook>
)

/**
 * Сортировка и разбиение библиотеки по статусу чтения.
 *
 * Логика чистая и не зависит от Android — её покрывают юнит-тесты.
 */
object LibrarySorter {

    /** Порядок полок в библиотеке. */
    private val GROUP_ORDER = listOf(ReadStatus.READING, ReadStatus.UNREAD, ReadStatus.FINISHED)

    /**
     * Статус книги: ручная отметка важнее автоматики, автоматика — это
     * прогресс чтения и дата последнего открытия.
     */
    fun readStatus(book: LibraryBook): ReadStatus = when (BookReadState.normalize(book.readState)) {
        BookReadState.FINISHED -> ReadStatus.FINISHED
        BookReadState.UNREAD -> ReadStatus.UNREAD
        else -> when {
            book.readingProgress >= PROGRESS_FINISHED -> ReadStatus.FINISHED
            book.readingProgress > 0 || book.lastReadDate > 0 -> ReadStatus.READING
            else -> ReadStatus.UNREAD
        }
    }

    fun sort(books: List<LibraryBook>, spec: LibrarySortSpec): List<LibraryBook> {
        if (books.size <= 1) return books
        return books.sortedWith(comparatorFor(spec))
    }

    /** Сортирует и разбивает на полки. Пустые полки не возвращаются. */
    fun group(books: List<LibraryBook>, spec: LibrarySortSpec): List<BookGroup> {
        val sorted = sort(books, spec)
        if (!spec.groupByStatus) {
            return if (sorted.isEmpty()) emptyList() else listOf(BookGroup(null, sorted))
        }
        return GROUP_ORDER.map { status ->
            BookGroup(status, sorted.filter { readStatus(it) == status })
        }.filter { it.books.isNotEmpty() }
    }

    private fun comparatorFor(spec: LibrarySortSpec): Comparator<LibraryBook> {
        val primary: Comparator<LibraryBook> = when (spec.field) {
            LibrarySortField.DATE_ADDED -> compareBy { it.downloadDate }
            LibrarySortField.LAST_READ -> compareBy { it.lastReadDate }
            LibrarySortField.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            LibrarySortField.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.author }
            LibrarySortField.PROGRESS -> compareBy { it.readingProgress }
        }
        val directed = if (spec.descending) primary.reversed() else primary
        // Детерминированный вторичный ключ: одинаковые значения не должны
        // «прыгать» между перерисовками и делают порядок предсказуемым.
        return directed
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            .thenBy { it.id }
            .thenBy { it.format }
    }

    private const val PROGRESS_FINISHED = 100
}