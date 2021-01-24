package eu.kanade.tachiyomi.data.library

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.library.LibraryUpdateRanker.rankingScheme
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Companion.start
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.NoChaptersException
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.acquireWakeLock
import eu.kanade.tachiyomi.util.system.isServiceRunning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class will take care of updating the chapters of the manga from the library. It can be
 * started calling the [start] method. If it's already running, it won't do anything.
 * While the library is updating, a [PowerManager.WakeLock] will be held until the update is
 * completed, preventing the device from going to sleep mode. A notification will display the
 * progress of the update, and if case of an unexpected error, this service will be silently
 * destroyed.
 */
class LibraryUpdateService(
    val db: DatabaseHelper = Injekt.get(),
    val sourceManager: SourceManager = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    val downloadManager: DownloadManager = Injekt.get(),
    val trackManager: TrackManager = Injekt.get(),
    val coverCache: CoverCache = Injekt.get()
) : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var notifier: LibraryUpdateNotifier
    private lateinit var scope: CoroutineScope

    private var updateJob: Job? = null

    /**
     * Defines what should be updated within a service execution.
     */
    enum class Target {
        CHAPTERS, // Manga chapters

        COVERS, // Manga covers

        TRACKING // Tracking metadata
    }

    companion object {

        /**
         * Key for category to update.
         */
        const val KEY_CATEGORY = "category"

        /**
         * Key that defines what should be updated.
         */
        const val KEY_TARGET = "target"

        /**
         * Returns the status of the service.
         *
         * @param context the application context.
         * @return true if the service is running, false otherwise.
         */
        fun isRunning(context: Context): Boolean {
            return context.isServiceRunning(LibraryUpdateService::class.java)
        }

        /**
         * Starts the service. It will be started only if there isn't another instance already
         * running.
         *
         * @param context the application context.
         * @param category a specific category to update, or null for global update.
         * @param target defines what should be updated.
         * @return true if service newly started, false otherwise
         */
        fun start(context: Context, category: Category? = null, target: Target = Target.CHAPTERS): Boolean {
            if (!isRunning(context)) {
                val intent = Intent(context, LibraryUpdateService::class.java).apply {
                    putExtra(KEY_TARGET, target)
                    category?.let { putExtra(KEY_CATEGORY, it.id) }
                }
                ContextCompat.startForegroundService(context, intent)

                return true
            }

            return false
        }

        /**
         * Stops the service.
         *
         * @param context the application context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, LibraryUpdateService::class.java))
        }
    }

    /**
     * Method called when the service is created. It injects dagger dependencies and acquire
     * the wake lock.
     */
    override fun onCreate() {
        super.onCreate()

        scope = MainScope()
        notifier = LibraryUpdateNotifier(this)
        wakeLock = acquireWakeLock(javaClass.name)

        startForeground(Notifications.ID_LIBRARY_PROGRESS, notifier.progressNotificationBuilder.build())
    }

    /**
     * Method called when the service is destroyed. It destroys subscriptions and releases the wake
     * lock.
     */
    override fun onDestroy() {
        scope?.cancel()
        updateJob?.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    /**
     * This method needs to be implemented, but it's not used/needed.
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * Method called when the service receives an intent.
     *
     * @param intent the start intent from.
     * @param flags the flags of the command.
     * @param startId the start id of this command.
     * @return the start value of the command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val target = intent.getSerializableExtra(KEY_TARGET) as? Target
            ?: return START_NOT_STICKY

        // Unsubscribe from any previous subscription if needed.
        updateJob?.cancel()

        // Update favorite manga. Destroy service when completed or in case of an error.
        val selectedScheme = preferences.libraryUpdatePrioritization().get()
        val mangaList = getMangaToUpdate(intent, target)
            .sortedWith(rankingScheme[selectedScheme])

        updateJob = scope.launchIO {
            try {
                when (target) {
                    Target.CHAPTERS -> updateChapterList(mangaList)
                    Target.COVERS -> updateCovers(mangaList)
                    Target.TRACKING -> updateTrackings(mangaList)
                }
            } catch (e: Throwable) {
                Timber.e(e)
                stopSelf(startId)
            } finally {
                stopSelf(startId)
            }
        }

        return START_REDELIVER_INTENT
    }

    /**
     * Returns the list of manga to be updated.
     *
     * @param intent the update intent.
     * @param target the target to update.
     * @return a list of manga to update
     */
    fun getMangaToUpdate(intent: Intent, target: Target): List<LibraryManga> {
        val categoryId = intent.getIntExtra(KEY_CATEGORY, -1)

        var listToUpdate = if (categoryId != -1) {
            db.getLibraryMangas().executeAsBlocking().filter { it.category == categoryId }
        } else {
            val categoriesToUpdate = preferences.libraryUpdateCategories().get().map(String::toInt)
            if (categoriesToUpdate.isNotEmpty()) {
                db.getLibraryMangas().executeAsBlocking()
                    .filter { it.category in categoriesToUpdate }
                    .distinctBy { it.id }
            } else {
                db.getLibraryMangas().executeAsBlocking().distinctBy { it.id }
            }
        }
        if (target == Target.CHAPTERS && preferences.updateOnlyNonCompleted()) {
            listToUpdate = listToUpdate.filter { it.status != SManga.COMPLETED }
        }

        return listToUpdate
    }

    /**
     * Method that updates the given list of manga. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each manga it calls [updateManga] and updates the notification showing the current
     * progress.
     *
     * @param mangaToUpdate the list to update
     * @return an observable delivering the progress of each update.
     */
    suspend fun updateChapterList(mangaToUpdate: List<LibraryManga>) {
        // Initialize the variables holding the progress of the updates.
        val progressCount = AtomicInteger(0)
        // List containing new updates
        val newUpdates = mutableListOf<Pair<LibraryManga, Array<Chapter>>>()
        // List containing failed updates
        val failedUpdates = mutableListOf<Pair<Manga, String?>>()
        // Boolean to determine if DownloadManager has downloads
        var hasDownloads = false

        mangaToUpdate
            .map { manga ->
                // Notify manga that will update.
                notifier.showProgressNotification(manga, progressCount.andIncrement, mangaToUpdate.size)

                // Update the chapters of the manga
                try {
                    val newChapters = updateManga(manga).first
                    Pair(manga, newChapters)
                } catch (e: Throwable) {
                    // If there's any error, return empty update and continue.
                    val errorMessage = if (e is NoChaptersException) {
                        getString(R.string.no_chapters_error)
                    } else {
                        e.message
                    }
                    failedUpdates.add(Pair(manga, errorMessage))
                    Pair(manga, emptyList())
                }
            }
            // Filter out mangas without new chapters (or failed).
            .filter { (_, newChapters) -> newChapters.isNotEmpty() }
            .forEach { (manga, newChapters) ->
                if (manga.shouldDownloadNewChapters(db, preferences)) {
                    downloadChapters(manga, newChapters)
                    hasDownloads = true
                }

                // Convert to the manga that contains new chapters.
                newUpdates.add(
                    Pair(
                        manga,
                        newChapters.sortedByDescending { ch -> ch.source_order }.toTypedArray()
                    )
                )
            }

        // Notify result of the overall update.
        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads) {
                DownloadService.start(this)
            }
        }

        if (preferences.showLibraryUpdateErrors() && failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.map { it.first.title },
                errorFile.getUriCompat(this)
            )
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters, false)
    }

    /**
     * Updates the chapters for the given manga and adds them to the database.
     *
     * @param manga the manga to update.
     * @return a pair of the inserted and removed chapters.
     */
    suspend fun updateManga(manga: Manga): Pair<List<Chapter>, List<Chapter>> {
        val source = sourceManager.getOrStub(manga.source)

        // Update manga details metadata in the background
        if (preferences.autoUpdateMetadata()) {
            val networkManga = source.getMangaDetails(manga)
            // Avoid "losing" existing cover
            if (!networkManga.thumbnail_url.isNullOrEmpty()) {
                manga.prepUpdateCover(coverCache, networkManga, false)
            } else {
                networkManga.thumbnail_url = manga.thumbnail_url
            }

            manga.copyFrom(networkManga)
            db.insertManga(manga).executeAsBlocking()
        }

        val chapters = source.getChapterList(manga)

        return syncChaptersWithSource(db, chapters, manga, source)
    }

    private suspend fun updateCovers(mangaToUpdate: List<LibraryManga>) {
        var progressCount = 0

        mangaToUpdate.forEach { manga ->
            notifier.showProgressNotification(manga, progressCount++, mangaToUpdate.size)

            sourceManager.get(manga.source)?.let { source ->
                try {
                    val networkManga = source.getMangaDetails(manga)
                    manga.prepUpdateCover(coverCache, networkManga, true)
                    networkManga.thumbnail_url?.let {
                        manga.thumbnail_url = it
                        db.insertManga(manga).executeAsBlocking()
                    }
                } catch (e: Throwable) {
                    // Ignore errors and continue
                    Timber.e(e)
                }
            }
        }

        notifier.cancelProgressNotification()
    }

    /**
     * Method that updates the metadata of the connected tracking services. It's called in a
     * background thread, so it's safe to do heavy operations or network calls here.
     */
    private suspend fun updateTrackings(mangaToUpdate: List<LibraryManga>) {
        var progressCount = 0
        val loggedServices = trackManager.services.filter { it.isLogged }

        mangaToUpdate.forEach { manga ->
            // Notify manga that will update.
            notifier.showProgressNotification(manga, progressCount++, mangaToUpdate.size)

            // Update the tracking details.
            db.getTracks(manga).executeAsBlocking()
                .map { track ->
                    supervisorScope {
                        async {
                            val service = trackManager.getService(track.sync_id)
                            if (service != null && service in loggedServices) {
                                try {
                                    val updatedTrack = service.refresh(track)
                                    db.insertTrack(updatedTrack).executeAsBlocking()
                                } catch (e: Throwable) {
                                    // Ignore errors and continue
                                    Timber.e(e)
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Manga, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val destFile = File(externalCacheDir, "tachiyomi_update_errors.txt")

                destFile.bufferedWriter().use { out ->
                    errors.forEach { (manga, error) ->
                        val source = sourceManager.getOrStub(manga.source)
                        out.write("${manga.title} ($source): $error\n")
                    }
                }
                return destFile
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }
}
