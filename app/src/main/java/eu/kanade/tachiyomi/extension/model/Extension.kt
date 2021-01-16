package eu.kanade.tachiyomi.extension.model

import eu.kanade.tachiyomi.source.Source

sealed class Extension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Int
    abstract val lang: String?
    abstract val isNsfw: Boolean
    abstract val vendor: String

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Int,
        override val lang: String,
        override val isNsfw: Boolean,
        override val vendor: String,
        val sources: List<Source>,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isUnofficial: Boolean = false
    ) : Extension()

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Int,
        override val lang: String,
        override val isNsfw: Boolean,
        override val vendor: String,
        val apkName: String,
        val iconUrl: String,
        val apkUrl: String
    ) : Extension()

    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Int,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        override val vendor: String
    ) : Extension()

    companion object {
        fun buildExtName(name: String): String = name.substringAfter("Tachiyomi: ")
    }
}
