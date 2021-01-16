package eu.kanade.tachiyomi.extension.api

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.injectLazy
import java.util.Date

internal class ExtensionGithubApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    private fun buildBaseUrl(githubUser: String?, githubRepository: String?): String {
        return "$BASE_URL/${githubUser ?: DEFAULT_GITHUB_USER}/${githubRepository ?: DEFAULT_GITHUB_REPOSITORY}"
    }

    suspend fun findExtensions(vararg githubUsers: String, githubRepository: String? = null): List<Extension.Available> {
        return findExtensions() + githubUsers.map { findExtensions(it, githubRepository) }.let {
            if (it.isNotEmpty()) it.reduce { acc, list -> acc + list }
            else emptyList()
        }
    }

    private suspend fun findExtensions(githubUser: String? = null, githubRepository: String? = null): List<Extension.Available> {
        return withIOContext {
            networkService.client
                .newCall(GET("${buildBaseUrl(githubUser, githubRepository)}/repo/index.min.json"))
                .await()
                .parseAs<JsonArray>()
                .let { parseResponse(it, githubUser, githubRepository) }
        }
    }

    suspend fun checkForUpdates(context: Context, githubUser: String? = null, githubRepository: String? = null): List<Extension.Installed> {
        val extensions = findExtensions(githubUser, githubRepository)

        preferences.lastExtCheck().set(Date().time)

        val installedExtensions = ExtensionLoader.loadExtensions(context)
            .filterIsInstance<LoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<Extension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdate = availableExt.versionCode > installedExt.versionCode
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        return extensionsWithUpdate
    }

    private fun parseResponse(json: JsonArray, githubUser: String?, githubRepository: String?): List<Extension.Available> {
        return json
            .filter { element ->
                val versionName = element.jsonObject["version"]!!.jsonPrimitive.content
                val libVersion = versionName.substringBeforeLast('.').toDouble()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map { element ->
                val name = Extension.buildExtName(element.jsonObject["name"]!!.jsonPrimitive.content)
                val pkgName = element.jsonObject["pkg"]!!.jsonPrimitive.content
                val apkName = element.jsonObject["apk"]!!.jsonPrimitive.content
                val versionName = element.jsonObject["version"]!!.jsonPrimitive.content
                val versionCode = element.jsonObject["code"]!!.jsonPrimitive.int
                val lang = element.jsonObject["lang"]!!.jsonPrimitive.content
                val nsfw = element.jsonObject["nsfw"]!!.jsonPrimitive.int == 1
                val vendor = element.jsonObject["vendor"]?.jsonPrimitive?.content ?: DEFAULT_GITHUB_USER

                with("${buildBaseUrl(githubUser, githubRepository)}/repo") {
                    val iconUrl = "$this/icon/${apkName.replace(".apk", ".png")}"
                    val apkUrl = "$this/apk/$apkName"
                    Extension.Available(name, pkgName, versionName, versionCode, lang, nsfw, vendor, apkName, iconUrl, apkUrl)
                }
            }
    }

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com"
        const val DEFAULT_GITHUB_USER = "tachiyomiorg"
        const val DEFAULT_GITHUB_REPOSITORY = "tachiyomi-extensions"
    }
}
