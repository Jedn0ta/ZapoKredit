package cz.klubpos.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import cz.klubpos.core.crypto.DatabaseKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A favourite combo ("Klasika"): a few products ordered together. */
data class Combo(val name: String, val items: List<Pair<Long, Double>>)

/** Per-member choices: own favourite list (empty = automatic from history) and combos. */
data class MemberPrefs(val favorites: List<Long> = emptyList(), val combos: List<Combo> = emptyList())

/** Encrypted store of [MemberPrefs] for all members. */
class MemberPrefsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "member_prefs", DatabaseKeyManager.masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val _flow = MutableStateFlow(readAll())
    val flow: StateFlow<Map<Long, MemberPrefs>> = _flow

    private fun readAll(): Map<Long, MemberPrefs> = prefs.all.mapNotNull { (k, v) ->
        val id = k.removePrefix("m").toLongOrNull() ?: return@mapNotNull null
        runCatching { id to parse(JSONObject(v as String)) }.getOrNull()
    }.toMap()

    private fun parse(o: JSONObject): MemberPrefs {
        val fav = o.optJSONArray("fav") ?: JSONArray()
        val cs = o.optJSONArray("combos") ?: JSONArray()
        return MemberPrefs(
            (0 until fav.length()).map { fav.getLong(it) },
            (0 until cs.length()).map { cs.getJSONObject(it) }.map { c ->
                val its = c.getJSONArray("items")
                Combo(c.optString("name", "Klasika"), (0 until its.length()).map { its.getJSONArray(it) }.map { it.getLong(0) to it.getDouble(1) })
            })
    }

    fun get(memberId: Long) = _flow.value[memberId] ?: MemberPrefs()

    fun update(memberId: Long, f: (MemberPrefs) -> MemberPrefs) {
        val p = f(get(memberId))
        val o = JSONObject()
            .put("fav", JSONArray(p.favorites))
            .put("combos", JSONArray(p.combos.map { c ->
                JSONObject().put("name", c.name).put("items", JSONArray(c.items.map { JSONArray().put(it.first).put(it.second) }))
            }))
        prefs.edit().putString("m$memberId", o.toString()).apply()
        _flow.value = _flow.value + (memberId to p)
    }
}
