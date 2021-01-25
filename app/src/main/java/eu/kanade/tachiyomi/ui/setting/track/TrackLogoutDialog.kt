package eu.kanade.tachiyomi.ui.setting.track

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackLogoutDialog(bundle: Bundle? = null) : DialogController(bundle) {

    private val service = Injekt.get<TrackManager>().getService(args.getInt("serviceId"))!!

    constructor(service: TrackService) : this(bundleOf("serviceId" to service.id))

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val serviceName = activity!!.getString(service.nameRes())
        return MaterialDialog(activity!!)
            .title(text = activity!!.getString(R.string.logout_title, serviceName))
            .positiveButton(R.string.logout) {
                service.logout()
                (targetController as? Listener)?.trackLogoutDialogClosed(service)
                activity?.toast(R.string.logout_success)
            }
            .negativeButton(android.R.string.cancel)
    }

    interface Listener {
        fun trackLogoutDialogClosed(service: TrackService)
    }
}
