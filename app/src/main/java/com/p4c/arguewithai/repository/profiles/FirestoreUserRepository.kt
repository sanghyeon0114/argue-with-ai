package com.p4c.arguewithai.repository.profiles

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.p4c.arguewithai.repository.FirebaseConfig
import java.util.Date

class FirestoreUserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")

    private fun userDoc() =
        db.collection(FirebaseConfig.ROOT_COLLECTION).document(uid())

    fun setUserName(name: String, onResult: (Boolean) -> Unit = {}) {
        val data = mapOf(
            "userInfo" to mapOf(
                "name" to name,
                "updatedAt" to Timestamp(Date())
            )
        )

        userDoc().set(data, SetOptions.merge())
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun getUserName(onResult: (String?) -> Unit) {
        userDoc().get()
            .addOnSuccessListener { snapshot ->
                @Suppress("UNCHECKED_CAST")
                val userInfo = snapshot.get("userInfo") as? Map<String, Any>
                onResult(userInfo?.get("name") as? String)
            }
            .addOnFailureListener { onResult(null) }
    }
}