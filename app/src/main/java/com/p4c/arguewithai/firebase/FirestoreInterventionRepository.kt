package com.p4c.arguewithai.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
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

    private fun interventionDoc() =
        db.collection(FirebaseConfig.ROOT_COLLECTION)
            .document(uid())
            .collection(FirebaseConfig.User.CLIENT)
            .document("intervention")

    suspend fun setEnabled(enabled: Boolean) {
        val ms = time.nowMs()
        val data = hashMapOf(
            "enabled" to enabled,
            "updatedAt" to Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt()),
            "updatedAtMs" to ms
        )
        interventionDoc().set(data, SetOptions.merge()).await()
    }
    suspend fun getEnabledOrNull(): Boolean? {
        val snap = interventionDoc().get().await()
        return if (snap.exists()) snap.getBoolean("enabled") else null
    }

    fun Context.isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    suspend fun syncLocalFromRemoteIfExists(context: Context) {
        val docRef = db.collection("YOUR_COLLECTION").document("YOUR_DOC")

        try {
            if (context.isOnline()) {
                runCatching { db.enableNetwork().await() }
                runCatching {
                    docRef.get(Source.SERVER).await()
                }
            }
        } catch (_: Exception) {
        }
    }
}
