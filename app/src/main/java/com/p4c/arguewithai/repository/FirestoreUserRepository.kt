package com.p4c.arguewithai.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.p4c.arguewithai.utils.Logger
import java.util.*

class FirestoreUserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("FirebaseAuth not logged in")

    fun setUserName(name: String, onResult: (Boolean) -> Unit = {}) {
        val docRef = db.collection(FirebaseConfig.ROOT_COLLECTION)
            .document(uid())
            .collection("profile")
            .document("userInfo")

        val data = mapOf(
            "name" to name,
            "updatedAt" to Timestamp(Date())
        )

        docRef.set(data)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun getUserName(onResult: (String?) -> Unit) {
        val docRef = db.collection(FirebaseConfig.ROOT_COLLECTION)
            .document(uid())
            .collection("profile")
            .document("userInfo")

        docRef.get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.getString("name"))
            }
            .addOnFailureListener { onResult(null) }
    }
}
