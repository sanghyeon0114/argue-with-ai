package com.p4c.arguewithai.firebase

import android.os.Build
import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import com.p4c.arguewithai.InterventionPrefs

class FirestoreInterventionRepository(
    private val time: TimeProvider = SystemTimeProvider()
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")

    /** users/{uid}/client/intervention */
    private fun interventionDoc() =
        db.collection(FirebaseConfig.ROOT_COLLECTION)
            .document(uid())
            .collection(FirebaseConfig.User.CLIENT)
            .document("intervention")

    /** 원격 저장: enabled 값과 타임스탬프/디바이스 정보 기록 */
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

    suspend fun syncLocalFromRemoteIfExists(context: Context) {
        val remote = getEnabledOrNull() ?: return
        InterventionPrefs.setEnabled(context, remote)
    }
}
