package com.p4c.arguewithai.repository.profiles

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.p4c.arguewithai.repository.FirebaseConfig
import kotlinx.coroutines.tasks.await
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider

class FirestoreInterventionRepository(
    private val time: TimeProvider = SystemTimeProvider()
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")

    private fun userDoc() =
        db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid())

    private fun nowTimestamp(): Timestamp {
        val ms = time.nowMs()
        return Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
    }

    /**
     * 실험 주차를 저장한다. enabled 는 2주차일 때만 true.
     * Firestore: users/{uid}.intervention = { week, enabled, updatedAt }
     */
    suspend fun setWeek(week: Int) {
        require(week in 1..3) { "week must be 1..3 but was $week" }
        val data = mapOf(
            "intervention" to mapOf(
                "week" to week,
                "enabled" to (week == 2),
                "updatedAt" to nowTimestamp()
            )
        )

        userDoc().set(data, SetOptions.merge()).await()
    }

    /**
     * 저장된 주차를 반환한다. week 필드가 없는 예전 문서는 enabled 값으로 유추(true=2, false=1).
     */
    suspend fun getWeekOrNull(): Int? {
        val snap = userDoc().get().await()
        @Suppress("UNCHECKED_CAST")
        val intervention = snap.get("intervention") as? Map<String, Any> ?: return null
        val week = (intervention["week"] as? Long)?.toInt()
        if (week != null && week in 1..3) return week
        return (intervention["enabled"] as? Boolean)?.let { if (it) 2 else 1 }
    }

    suspend fun setInterventionType(type: Int) {
        val data = mapOf(
            "intervention" to mapOf(
                "type" to type,
                "updatedAt" to nowTimestamp()
            )
        )

        userDoc().set(data, SetOptions.merge()).await()
    }

    suspend fun getInterventionTypeOrNull(): Int? {
        val snap = userDoc().get().await()
        @Suppress("UNCHECKED_CAST")
        val intervention = snap.get("intervention") as? Map<String, Any>
        return (intervention?.get("type") as? Long)?.toInt()
    }

    fun Context.isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncLocalFromRemoteIfExists(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val docRef = db.collection(FirebaseConfig.ROOT_COLLECTION).document(user.uid)

        try {
            if (context.isOnline()) {
                runCatching { db.enableNetwork().await() }
                runCatching { docRef.get(Source.SERVER).await() }
            }
        } catch (_: Exception) {
        }
    }
}
