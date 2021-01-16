package eu.kanade.tachiyomi.ui.browse.extension

import android.view.View
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ExtensionCardItemBinding
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.util.system.LocaleHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionHolder(view: View, val adapter: ExtensionAdapter) :
    FlexibleViewHolder(view, adapter) {

    private val binding = ExtensionCardItemBinding.bind(view)

    private val shouldLabelNsfw by lazy {
        Injekt.get<PreferencesHelper>().labelNsfwExtension()
    }

    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(bindingAdapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension

        binding.extTitle.text = extension.name
        binding.vendor.text = extension.vendor
        binding.version.text = extension.versionName
        binding.lang.text = LocaleHelper.getSourceDisplayName(extension.lang, itemView.context)
        binding.warning.text = when {
            extension is Extension.Untrusted -> itemView.context.getString(R.string.ext_untrusted)
            extension is Extension.Installed && extension.isObsolete -> itemView.context.getString(R.string.ext_obsolete)
            extension is Extension.Installed && extension.isUnofficial && extension.vendor == ExtensionLoader.UNOFFICIAL_VENDOR -> itemView.context.getString(R.string.ext_unofficial)
            extension.isNsfw && shouldLabelNsfw -> itemView.context.getString(R.string.ext_nsfw_short)
            else -> ""
        }.toUpperCase()

        GlideApp.with(itemView.context).clear(binding.image)
        if (extension is Extension.Available) {
            GlideApp.with(itemView.context)
                .load(extension.iconUrl)
                .into(binding.image)
        } else {
            extension.getApplicationIcon(itemView.context)?.let { binding.image.setImageDrawable(it) }
        }
        bindButton(item)
    }

    @Suppress("ResourceType")
    fun bindButton(item: ExtensionItem) = with(binding.extButton) {
        isEnabled = true
        isClickable = true

        val extension = item.extension

        val installStep = item.installStep
        if (installStep != null) {
            setText(
                when (installStep) {
                    InstallStep.Pending -> R.string.ext_pending
                    InstallStep.Downloading -> R.string.ext_downloading
                    InstallStep.Installing -> R.string.ext_installing
                    InstallStep.Installed -> R.string.ext_installed
                    InstallStep.Error -> R.string.action_retry
                }
            )
            if (installStep != InstallStep.Error) {
                isEnabled = false
                isClickable = false
            }
        } else if (extension is Extension.Installed) {
            when {
                extension.hasUpdate -> {
                    setText(R.string.ext_update)
                }
                else -> {
                    setText(R.string.action_settings)
                }
            }
        } else if (extension is Extension.Untrusted) {
            setText(R.string.ext_trust)
        } else {
            setText(R.string.ext_install)
        }
    }
}
