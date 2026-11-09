package cz.klubpos

import android.app.Application
import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.createDatabase
import kotlinx.coroutines.launch

/** Android entry: platform init + shared services. */
class KlubPosApp : Application() {
    val services by lazy { AppServices({ createDatabase(this) }, { ImageStore(this) }) }
    val settings get() = services.settings
    val repository get() = services.repository
    val lan get() = services.lan
    val eet get() = services.eet
    val ownerLock get() = services.ownerLock
    val memberPrefs get() = services.memberPrefs

    override fun onCreate() {
        super.onCreate()
        cz.klubpos.platform.Platform.app = this
        // EET: retry anything that could not be sent (no internet / server down)
        if (BuildConfig.FLAVOR == "pos") kotlinx.coroutines.MainScope().launch { repository; eet.retryLoop() }
    }
}
