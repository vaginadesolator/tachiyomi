package eu.kanade.tachiyomi.ui.manga.chapter

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.ChaptersItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.chapter.base.BaseChapterHolder
import java.util.Date

class ChapterHolder(
    view: View,
    private val adapter: ChaptersAdapter
) : BaseChapterHolder(view, adapter) {

    private val binding = ChaptersItemBinding.bind(view)

    init {
        binding.download.setOnClickListener { onDownloadClick(it) }
    }

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter

        binding.chapterTitle.text = when (manga.displayMode) {
            Manga.DISPLAY_NUMBER -> {
                val number = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
                itemView.context.getString(R.string.display_mode_chapter, number)
            }
            else -> chapter.name
        }

        // Set correct text color
        val chapterTitleColor = when {
            chapter.read -> adapter.readColor
            chapter.bookmark -> adapter.bookmarkedColor
            else -> adapter.unreadColor
        }
        binding.chapterTitle.setTextColor(chapterTitleColor)

        val chapterDescriptionColor = when {
            chapter.read -> adapter.readColor
            chapter.bookmark -> adapter.bookmarkedColor
            else -> adapter.unreadColorSecondary
        }
        binding.chapterDescription.setTextColor(chapterDescriptionColor)

        binding.bookmarkIcon.isVisible = chapter.bookmark

        val descriptions = mutableListOf<CharSequence>()

        if (chapter.date_upload > 0) {
            descriptions.add(adapter.dateFormat.format(Date(chapter.date_upload)))
        }

        if (!chapter.read && chapter.last_page_read > 0) {
            val currentPage = chapter.last_page_read + 1
            val text = if (chapter.page_count > 0) itemView.context.getString(R.string.chapter_progress_with_page_count, currentPage, chapter.page_count)
            else itemView.context.getString(R.string.chapter_progress, currentPage)

            val lastPageRead = SpannableString(text).apply {
                setSpan(ForegroundColorSpan(adapter.readColor), 0, length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            descriptions.add(lastPageRead)
        } else if (chapter.page_count > 0) {
            descriptions.add(itemView.context.getString(R.string.chapter_page_count, chapter.page_count))
        }

        if (!chapter.scanlator.isNullOrBlank()) {
            descriptions.add(chapter.scanlator!!)
        }

        if (descriptions.isNotEmpty()) {
            binding.chapterDescription.text = descriptions.joinTo(SpannableStringBuilder(), " • ")
        } else {
            binding.chapterDescription.text = ""
        }

        binding.download.isVisible = item.manga.source != LocalSource.ID
        binding.download.setState(item.status, item.progress)
    }
}
