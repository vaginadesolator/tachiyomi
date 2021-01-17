package eu.kanade.tachiyomi.ui.browse.source

import android.view.View
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceMainControllerCardItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setVectorCompat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceHolder(private val view: View, val adapter: SourceAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = SourceMainControllerCardItemBinding.bind(view)

    private val shouldLabelNsfw by lazy {
        Injekt.get<PreferencesHelper>().labelNsfwExtension()
    }

    init {
        binding.sourceLatest.setOnClickListener {
            adapter.clickListener.onLatestClick(bindingAdapterPosition)
        }

        binding.pin.setOnClickListener {
            adapter.clickListener.onPinClick(bindingAdapterPosition)
        }
    }

    fun bind(item: SourceItem) {
        val source = item.source
        val extension = item.extension

        binding.title.text = source.name

        if (source !is LocalSource) {
            binding.sourceLanguage.isVisible = true
            binding.sourceLanguage.text = LocaleHelper.getDisplayName(source.lang)

            binding.sourceVendor.isVisible = true
            binding.sourceVendor.text = extension?.vendor ?: "local"

            if (extension?.isNsfw == true && shouldLabelNsfw) {
                binding.sourceWarning.isVisible = true
                binding.sourceWarning.text = itemView.context.getString(R.string.ext_nsfw_short).toUpperCase()
            }
        }

        // Set source icon
        itemView.post {
            val icon = source.icon()
            when {
                icon != null -> binding.image.setImageDrawable(icon)
                item.source.id == LocalSource.ID -> binding.image.setImageResource(R.mipmap.ic_local_source)
            }
        }

        binding.sourceLatest.isVisible = source.supportsLatest

        binding.pin.isVisible = true
        if (item.isPinned) {
            binding.pin.setVectorCompat(R.drawable.ic_push_pin_filled_24dp, view.context.getResourceColor(R.attr.colorAccent))
        } else {
            binding.pin.setVectorCompat(R.drawable.ic_push_pin_24dp, view.context.getResourceColor(android.R.attr.textColorHint))
        }
    }
}
