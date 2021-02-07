package eu.kanade.tachiyomi.ui.manga.chapter

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.ChapterDownloadViewBinding

class ChapterDownloadView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val binding: ChapterDownloadViewBinding

    private var state = Download.State.NOT_DOWNLOADED
    private var progress = 0

    private var downloadIconAnimator: ObjectAnimator? = null
    private var isAnimating = false

    init {
        binding = ChapterDownloadViewBinding.inflate(LayoutInflater.from(context), this, false)
        addView(binding.root)
    }

    fun setState(state: Download.State, progress: Int = 0) {
        val isDirty = this.state.value != state.value || this.progress != progress

        this.state = state
        this.progress = progress

        if (isDirty) {
            updateLayout()
        }
    }

    private fun updateLayout() {
        binding.downloadIconBorder.isVisible = state == Download.State.NOT_DOWNLOADED

        binding.downloadIcon.isVisible = state == Download.State.NOT_DOWNLOADED || state == Download.State.DOWNLOADING
        if (state == Download.State.DOWNLOADING) {
            if (!isAnimating) {
                downloadIconAnimator =
                    ObjectAnimator.ofFloat(binding.downloadIcon, "alpha", 1f, 0f).apply {
                        duration = 1000
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                    }
                downloadIconAnimator?.start()
                isAnimating = true
            }
        } else {
            downloadIconAnimator?.cancel()
            binding.downloadIcon.alpha = 1f
            isAnimating = false
        }

        binding.downloadQueued.isVisible = state == Download.State.QUEUE

        binding.downloadProgress.isVisible = state == Download.State.DOWNLOADING ||
            (state == Download.State.QUEUE && progress > 0)
        binding.downloadProgress.progress = progress

        binding.downloadedIcon.isVisible = state == Download.State.DOWNLOADED

        binding.errorIcon.isVisible = state == Download.State.ERROR
    }
}
