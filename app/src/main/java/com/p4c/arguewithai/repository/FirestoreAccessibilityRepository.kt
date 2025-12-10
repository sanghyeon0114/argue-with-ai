package com.p4c.arguewithai.repository

import android.os.Build
import com.p4c.arguewithai.utils.SystemTimeProvider
import com.p4c.arguewithai.utils.TimeProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Date

class FirestoreAccessibilityRepository (
    private val time: TimeProvider = SystemTimeProvider()
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")
    fun setAccessibilityEnabled(enabled: Boolean, onResult: (Boolean) -> Unit = {}) {

        val docRef = db.collection(FirebaseConfig.ROOT_COLLECTION)
            .document(uid())
            .collection(FirebaseConfig.User.CLIENT)
            .document(FirebaseConfig.User.Client.ACCESSIBILITY)

        val data = hashMapOf(
            "accessibilityEnabled" to enabled,
            "updatedAt" to Timestamp(Date(time.nowMs())),
            "device" to mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "sdkInt" to Build.VERSION.SDK_INT
            )
        )

        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}