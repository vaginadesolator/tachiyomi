package eu.kanade.tachiyomi.source

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.awaitSingle
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc...
 */
interface Source {

    /**
     * Id for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Returns an observable with the updated details for a manga.
     *
     * @param manga the manga to update.
     */
    @Deprecated("Use getMangaDetails instead")
    fun fetchMangaDetails(manga: SManga): Observable<SManga>

    /**
     * Returns an observable with all the available chapters for a manga.
     *
     * @param manga the manga to update.
     */
    @Deprecated("Use getChapterList instead")
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>>

    /**
     * Returns an observable with the list of pages a chapter has.
     *
     * @param chapter the chapter.
     */
    @Deprecated("Use getPageList instead")
    fun fetchPageList(chapter: SChapter): Observable<List<Page>>

    /**
     * [1.x API] Get the updated details for a manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga {
        return fetchMangaDetails(manga).awaitSingle()
    }

    /**
     * [1.x API] Get all the available chapters for a manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fetchChapterList(manga).awaitSingle()
    }
}

fun Source.icon(): Drawable? = Injekt.get<ExtensionManager>().getAppIconForSource(this)

fun Source.getPreferenceKey(): String = "source_$id"
