package com.foxybook.app.features.reader

/**
 * Общий алгоритм восстановления позиции чтения для обоих режимов
 * (страницы и прокрутка).
 *
 * Раньше в каждом режиме был свой поиск, и оба требовали точного попадания
 * сохранённого смещения в диапазон элемента. После любой перевёрстки —
 * другой шрифт, поля, поворот экрана, другое устройство — диапазоны
 * сдвигались, точного совпадения не находилось, и книга открывалась на
 * первой странице главы.
 *
 * Теперь смещение остаётся основным якорем, но ищется «ближайший предыдущий»
 * элемент, а не «точное вхождение». Поэтому промах даёт правдоподобное
 * место, а не возврат в начало.
 */
internal data class RestoreAnchor(
    val chapterIndex: Int,
    /** Смещение элемента от начала главы. */
    val offset: Int,
    /** Номер элемента внутри главы: страницы или блока. */
    val indexInChapter: Int
)

/**
 * @return индекс элемента в общем списке, на котором стоит читатель,
 *         либо -1, если в этой главе нет ни одного элемента.
 */
internal fun findRestoreIndex(
    items: List<RestoreAnchor>,
    chapterIndex: Int,
    offset: Int,
    savedIndexInChapter: Int
): Int {
    val inChapter = items.withIndex().filter { it.value.chapterIndex == chapterIndex }
    if (inChapter.isEmpty()) return -1

    // Основной якорь — сохранённое смещение: последний элемент, начинающийся
    // не позже него. Для смещения 0 это первая страница главы, для смещения
    // за пределами главы — последняя.
    inChapter.lastOrNull { (_, a) -> a.offset <= offset }?.let { return it.index }

    // Смещение не годится (например, оказалось меньше начала главы) —
    // откатываемся на сохранённый номер и только потом на начало главы.
    inChapter.firstOrNull { (_, a) -> a.indexInChapter == savedIndexInChapter }?.let { return it.index }

    return inChapter.first().index
}
