package com.foxybook.app.core.reader

import com.foxybook.app.core.models.ParsedChapter
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory LRU cache for pre-built chapter HTML.
 * 
 * - Never re-parses FB2/EPUB/MOBI
 * - Never re-builds HTML on chapter change
 * - Preloads 3 chapters ahead/behind on background thread
 * - Page positions cached per chapter for instant flipping
 */
class ChapterHtmlCache(
    private val preloadRange: Int = 3,
    private val maxCacheSize: Int = 20
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Pre-built HTML keyed by chapter index
    private val htmlCache = ConcurrentHashMap<Int, String>()

    // Page counts per chapter (calculated once)
    private val pageCountCache = ConcurrentHashMap<Int, Int>()

    // Page scroll positions per chapter (for restoring position)
    private val pagePositionsCache = ConcurrentHashMap<Int, Int>()

    // Track which chapters are currently being built
    private val building = ConcurrentHashMap<Int, Boolean>()

    // Order tracking for LRU eviction
    private val accessOrder = java.util.LinkedList<Int>()

    val cachedChapterIndices: Set<Int> get() = htmlCache.keys.toSet()

    /**
     * Get pre-built HTML for a chapter. Returns null if not cached.
     */
    fun get(chapterIndex: Int): String? = htmlCache[chapterIndex]

    /**
     * Get page count for a chapter. Returns 0 if not calculated.
     */
    fun getPageCount(chapterIndex: Int): Int = pageCountCache[chapterIndex] ?: 0

    /**
     * Store page count for a chapter.
     */
    fun putPageCount(chapterIndex: Int, count: Int) {
        pageCountCache[chapterIndex] = count
    }

    /**
     * Store scroll position for a chapter.
     */
    fun putScrollPosition(chapterIndex: Int, position: Int) {
        pagePositionsCache[chapterIndex] = position
    }

    /**
     * Get scroll position for a chapter.
     */
    fun getScrollPosition(chapterIndex: Int): Int = pagePositionsCache[chapterIndex] ?: 0

    /**
     * Synchronous build + cache for immediate use.
     * Called on Main thread when cache miss occurs.
     */
    fun getOrBuild(
        chapterIndex: Int,
        chapter: ParsedChapter,
        isPageMode: Boolean,
        settings: ReaderSettings,
        isDarkTheme: Boolean
    ): String {
        return htmlCache.getOrPut(chapterIndex) {
            buildChapterHtml(chapter.htmlContent, isPageMode, settings, isDarkTheme)
        }
    }

    /**
     * Preload HTML for chapters in range [center - preloadRange, center + preloadRange].
     * Runs on background thread. Does not re-build already cached chapters.
     */
    fun preloadRange(
        chapters: List<ParsedChapter>,
        centerIndex: Int,
        isPageMode: Boolean,
        settings: ReaderSettings,
        isDarkTheme: Boolean
    ) {
        val start = maxOf(0, centerIndex - preloadRange)
        val end = minOf(chapters.size - 1, centerIndex + preloadRange)

        for (i in start..end) {
            if (htmlCache.containsKey(i)) continue
            if (building.putIfAbsent(i, true) != null) continue

            val chapter = chapters.getOrNull(i) ?: continue
            scope.launch {
                try {
                    val html = buildChapterHtml(chapter.htmlContent, isPageMode, settings, isDarkTheme)
                    htmlCache[i] = html
                    trimIfNeeded()
                } finally {
                    building.remove(i)
                }
            }
        }
    }

    /**
     * Invalidate all cached HTML (e.g. when settings change).
     */
    fun invalidateAll() {
        htmlCache.clear()
        pageCountCache.clear()
        building.clear()
    }

    /**
     * Invalidate page counts only (e.g. when font size changes).
     */
    fun invalidatePageCounts() {
        pageCountCache.clear()
    }

    /**
     * Clear everything.
     */
    fun clear() {
        htmlCache.clear()
        pageCountCache.clear()
        pagePositionsCache.clear()
        building.clear()
        accessOrder.clear()
    }

    private fun trimIfNeeded() {
        while (htmlCache.size > maxCacheSize && accessOrder.isNotEmpty()) {
            val oldest = accessOrder.removeFirst()
            htmlCache.remove(oldest)
            pageCountCache.remove(oldest)
        }
    }

    companion object {
        /**
         * Build full HTML document for a chapter.
         * This is the same logic as ReaderScreen.buildChapterHtml but callable from any thread.
         */
        fun buildChapterHtml(
            bodyContent: String,
            isPageMode: Boolean,
            settings: ReaderSettings,
            isDarkTheme: Boolean
        ): String {
            val bg = if (isDarkTheme) "#1A1A1A" else "#FFFFFF"
            val fg = if (isDarkTheme) "#E0E0E0" else "#1A1A1A"
            val link = if (isDarkTheme) "#FF8A65" else "#D84315"
            val bqBg = if (isDarkTheme) "#2A2A2A" else "#F5F5F5"
            val bqBd = if (isDarkTheme) "#555" else "#CCC"
            val sel = if (isDarkTheme) "#FF8A65" else "#FFB74D"
            val fs = settings.fontSize
            val lh = settings.lineHeight
            val mg = settings.margins
            val overflow = if (isPageMode) "hidden" else "auto"

            val css = """html,body{margin:0;padding:0;height:100%}
                body{font-family:serif;font-size:${fs}px;line-height:$lh;color:$fg;background:$bg;
                overflow:$overflow;-webkit-overflow-scrolling:touch;padding:${mg}px ${mg}px 0;
                -webkit-text-size-adjust:100%}
                h1,h2,h3,h4,h5,h6{line-height:1.3;margin:0 0 .5em;color:$fg}
                p{margin:0 0 1em;text-align:justify}
                img{max-width:100%;height:auto;display:block;margin:1em auto}
                a{color:$link;text-decoration:none}
                blockquote,.epigraph{margin:1em ${mg}px;padding:.8em 1em;border-left:3px solid $bqBd;
                background:$bqBg;border-radius:0 8px 8px 0;font-style:italic}
                .epigraph-author{text-align:right;font-style:normal;margin-top:.5em}
                .poem{margin:1em ${mg}px;padding:1em;background:$bqBg;border-radius:8px}
                .stanza{margin-bottom:.8em}
                .verse-line{margin:0;padding:0;text-align:left}
                .poem-author{text-align:right;margin-top:.5em}
                .image{text-align:center;margin:1em 0}
                .page-break{page-break-after:always;margin:2em 0}
                ::selection{background:${sel}40}""".trimIndent()

            val js = if (isPageMode) PAGE_MODE_JS else SCROLL_MODE_JS

            return """<!DOCTYPE html><html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <style>$css</style></head>
                <body>$bodyContent</body>
                <script>$js</script></html>"""
        }

        private const val SCROLL_MODE_JS = """
(function(){
    function report(){
        var sy=window.scrollY;
        var mx=document.documentElement.scrollHeight-window.innerHeight;
        var pct=mx>0?Math.round(sy/mx*100):0;
        Android.onScrollProgress(pct,sy);
    }
    window.addEventListener('scroll',report,{passive:true});
    setTimeout(report,300);
    document.addEventListener('click',function(e){
        var x=e.clientX,w=window.innerWidth;
        if(x>w*0.3&&x<w*0.7) Android.onCenterTap();
    });
})();"""

        private const val PAGE_MODE_JS = """
(function(){
    var _currentPage=0, _totalPages=1, _pageHeight=0;
    function calc(){
        _pageHeight=window.innerHeight;
        var totalH=document.documentElement.scrollHeight;
        _totalPages=Math.max(1,Math.ceil(totalH/_pageHeight));
        report();
    }
    function report(){
        Android.onPageInfo(_currentPage,_totalPages);
    }
    window.goToPage=function(p){
        if(p<0){Android.onPrevChapter();return}
        if(p>=_totalPages){Android.onNextChapter();return}
        _currentPage=p;
        window.scrollTo(0,_currentPage*_pageHeight);
        report();
    };
    window.nextPage=function(){goToPage(_currentPage+1)};
    window.prevPage=function(){goToPage(_currentPage-1)};
    window.getPageInfo=function(){return JSON.stringify({current:_currentPage,total:_totalPages})};
    var sx=0,sy=0,st=0,tracking=false;
    document.addEventListener('touchstart',function(e){
        sx=e.touches[0].clientX;sy=e.touches[0].clientY;st=Date.now();tracking=true;
    },{passive:true});
    document.addEventListener('touchmove',function(e){
        if(!tracking)return;
        var dx=Math.abs(e.touches[0].clientX-sx);
        var dy=Math.abs(e.touches[0].clientY-sy);
        if(dy>dx*1.5)tracking=false;
    },{passive:true});
    document.addEventListener('touchend',function(e){
        if(!tracking)return;tracking=false;
        var dx=e.changedTouches[0].clientX-sx;
        var dt=Date.now()-st;
        if(dt<500&&Math.abs(dx)>60){
            if(dx<0)nextPage();else prevPage();
        }
    },{passive:true});
    document.addEventListener('click',function(e){
        var x=e.clientX,w=window.innerWidth;
        if(x>w*0.3&&x<w*0.7) Android.onCenterTap();
    });
    window.addEventListener('load',function(){setTimeout(calc,200)});
    setTimeout(calc,400);
})();"""
    }
}
