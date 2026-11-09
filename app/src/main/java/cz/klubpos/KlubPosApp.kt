package cz.klubpos

import android.app.Application
import kotlinx.coroutines.launch
import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.AppDatabase
import cz.klubpos.data.repo.ClubRepository

/** Manual DI – small app, no need for Hilt yet. */
class KlubPosApp : Application() {
    val settings: cz.klubpos.core.AppSettings by lazy { cz.klubpos.core.AppSettings(this) }
    val memberPrefs: cz.klubpos.core.MemberPrefsStore by lazy { cz.klubpos.core.MemberPrefsStore(this) }
    val ownerLock: cz.klubpos.core.owner.OwnerLock by lazy { cz.klubpos.core.owner.OwnerLock(this) }
    val repository: ClubRepository by lazy {
        ClubRepository(AppDatabase.create(this), ImageStore(this)).also { r ->
            r.onPayment = { id, member, amount, at -> eet.recordPayment(id, member, amount, at) }
        }
    }
    val eet: cz.klubpos.eet.EetService by lazy { cz.klubpos.eet.EetService(this) }

    override fun onCreate() {
        super.onCreate()
        // EET: retry anything that could not be sent (no internet / server down)
        kotlinx.coroutines.MainScope().launch { repository; eet.retryLoop() }
    }
}
