package eu.kanade.tachiyomi.ui.more

import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.aboutlibraries.LibsBuilder
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.UpdateResult
import eu.kanade.tachiyomi.data.updater.UpdaterService
import eu.kanade.tachiyomi.data.updater.github.GithubUpdateChecker
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import timber.log.Timber
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AboutController : SettingsController() {

    private val updateChecker by lazy { GithubUpdateChecker() }

    private val dateFormat: DateFormat = preferences.dateFormat()

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_about

        preference {
            key = "pref_about_version"
            titleRes = R.string.version
            summary = "Release r${BuildConfig.COMMIT_COUNT} (${BuildConfig.COMMIT_SHA})"

            onClick { copyDebugInfo() }
        }
        preference {
            key = "pref_about_build_time"
            titleRes = R.string.build_time
            summary = getFormattedBuildTime()
        }
        if (isUpdaterEnabled) {
            preference {
                key = "pref_about_check_for_updates"
                titleRes = R.string.check_for_updates

                onClick { checkVersion() }
            }
        }
        preference {
            key = "pref_about_whats_new"
            titleRes = R.string.whats_new

            onClick {
                val url = "https://github.com/vaginadesolator/tachiyomi/releases/tag/r${BuildConfig.COMMIT_COUNT}"
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            }
        }

        preferenceCategory {
            preference {
                key = "pref_about_website"
                titleRes = R.string.website
                val url = "https://tachiyomi.org"
                summary = url
                onClick {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                }
            }
            preference {
                key = "pref_about_discord"
                title = "Discord"
                val url = "https://discord.gg/tachiyomi"
                summary = url
                onClick {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                }
            }
            preference {
                key = "pref_about_github"
                title = "GitHub"
                val url = "https://github.com/vaginadesolator/tachiyomi"
                summary = url
                onClick {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                }
            }
            preference {
                key = "pref_about_label_extensions"
                titleRes = R.string.label_extensions
                val url = "https://github.com/vaginadesolator/tachiyomi-extensions"
                summary = url
                onClick {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                }
            }
            preference {
                key = "pref_about_licenses"
                titleRes = R.string.licenses

                onClick {
                    LibsBuilder()
                        .withActivityTitle(activity!!.getString(R.string.licenses))
                        .withAboutIconShown(false)
                        .withAboutVersionShown(false)
                        .withLicenseShown(true)
                        .start(activity!!)
                }
            }
        }
    }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private fun checkVersion() {
        if (activity == null) return

        activity?.toast(R.string.update_check_look_for_updates)

        launchNow {
            try {
                when (val result = updateChecker.checkForUpdate()) {
                    is UpdateResult.NewUpdate<*> -> {
                        val body = result.release.info
                        val url = result.release.downloadLink

                        // Create confirmation window
                        NewUpdateDialogController(body, url).showDialog(router)
                    }
                    is UpdateResult.NoNewUpdate -> {
                        activity?.toast(R.string.update_check_no_new_updates)
                    }
                }
            } catch (error: Exception) {
                activity?.toast(error.message)
                Timber.e(error)
            }
        }
    }

    class NewUpdateDialogController(bundle: Bundle? = null) : DialogController(bundle) {

        constructor(body: String, url: String) : this(
            bundleOf(BODY_KEY to body, URL_KEY to url)
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog(activity!!)
                .title(res = R.string.update_check_notification_update_available)
                .message(text = args.getString(BODY_KEY) ?: "")
                .positiveButton(R.string.update_check_confirm) {
                    val appContext = applicationContext
                    if (appContext != null) {
                        // Start download
                        val url = args.getString(URL_KEY) ?: ""
                        UpdaterService.start(appContext, url)
                    }
                }
                .negativeButton(R.string.update_check_ignore)
        }

        private companion object {
            const val BODY_KEY = "NewUpdateDialogController.body"
            const val URL_KEY = "NewUpdateDialogController.key"
        }
    }

    private fun copyDebugInfo() {
        val deviceInfo =
            """
            App version: r${BuildConfig.COMMIT_COUNT} (${BuildConfig.FLAVOR}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE})
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            Android build ID: ${Build.DISPLAY}
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE}
            Device model: ${Build.MODEL}
            Device product name: ${Build.PRODUCT}
            """.trimIndent()

        activity?.copyToClipboard("Debug information", deviceInfo)
    }

    private fun getFormattedBuildTime(): String {
        return try {
            val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
            inputDf.timeZone = TimeZone.getTimeZone("UTC")
            val buildTime = inputDf.parse(BuildConfig.BUILD_TIME)

            val outputDf = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            )
            outputDf.timeZone = TimeZone.getDefault()

            buildTime.toDateTimestampString(dateFormat)
        } catch (e: ParseException) {
            BuildConfig.BUILD_TIME
        }
    }
}
