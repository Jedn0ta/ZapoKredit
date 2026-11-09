package cz.klubpos

import androidx.compose.runtime.staticCompositionLocalOf
import cz.klubpos.core.crypto.ImageStore
import cz.klubpos.data.db.AppDatabase
import cz.klubpos.data.repo.ClubRepository

/**
 * All app services (manual DI), the same on the tablet and on the PC.
 * Created by KlubPosApp (Android) or by main() (PC) with the platform's database and image store.
 */
class AppServices(dbFactory: () -> AppDatabase, imagesFactory: () -> ImageStore) {
    val settings: cz.klubpos.core.AppSettings by lazy { cz.klubpos.core.AppSettings() }
    val memberPrefs: cz.klubpos.core.MemberPrefsStore by lazy { cz.klubpos.core.MemberPrefsStore() }
    val ownerLock: cz.klubpos.core.owner.OwnerLock by lazy { cz.klubpos.core.owner.OwnerLock() }
    val repository: ClubRepository by lazy {
        ClubRepository(dbFactory(), imagesFactory()).also { r ->
            r.onPayment = { id, member, amount, at -> eet.recordPayment(id, member, amount, at) }
        }
    }
    val eet: cz.klubpos.eet.EetService by lazy { cz.klubpos.eet.EetService(this) }
    val lan: cz.klubpos.lan.LanHub by lazy { cz.klubpos.lan.LanHub(this) }
}

val LocalServices = staticCompositionLocalOf<AppServices> { error("AppServices not provided") }
