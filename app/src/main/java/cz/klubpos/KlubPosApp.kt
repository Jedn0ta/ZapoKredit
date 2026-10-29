package cz.klubpos

import android.app.Application
import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.AppDatabase
import cz.klubpos.data.repo.ClubRepository

/** Manual DI – small app, no need for Hilt yet. */
class KlubPosApp : Application() {
    val settings: cz.klubpos.core.AppSettings by lazy { cz.klubpos.core.AppSettings(this) }
    val ownerLock: cz.klubpos.core.owner.OwnerLock by lazy { cz.klubpos.core.owner.OwnerLock(this) }
    val repository: ClubRepository by lazy {
        ClubRepository(AppDatabase.create(this), ImageStore(this))
    }
}
