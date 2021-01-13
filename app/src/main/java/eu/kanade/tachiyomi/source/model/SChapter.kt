package eu.kanade.tachiyomi.source.model

import tachiyomi.source.model.ChapterInfo
import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    var scanlator: String?

    var page_count: Int

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        page_count = other.page_count
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }
}

fun SChapter.toChapterInfo(): ChapterInfo {
    return ChapterInfo(
        dateUpload = this.date_upload,
        key = this.url,
        name = this.name,
        number = this.chapter_number,
        scanlator = this.scanlator ?: "",
        pageCount = this.page_count
    )
}

fun ChapterInfo.toSChapter(): SChapter {
    val chapter = this
    return SChapter.create().apply {
        url = chapter.key
        name = chapter.name
        date_upload = chapter.dateUpload
        chapter_number = chapter.number
        scanlator = chapter.scanlator
        page_count = chapter.pageCount
    }
}
