package com.p4c.arguewithai.repository.profiles

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
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

    /**
     * users/{uid} 문서가 서버에 없으면 기본 문서를 먼저 만든다.
     * (문서가 없는 상태에서 하위 필드만 쓰는 경우를 대비)
     */
    suspend fun ensureUserDoc() {
        val doc = userDoc()
        val snap = doc.get(Source.SERVER).await()
        if (!snap.exists()) {
            doc.set(
                mapOf("uid" to uid(), "createdAt" to Timestamp(Date())),
                SetOptions.merge()
            ).await()
        }
    }

    /** 서버에 이름을 저장하고(서버 반영까지 대기), 캐시가 아닌 서버에서 다시 읽어 일치하는지 확인한다. */
    suspend fun setUserNameAndVerify(name: String): Boolean {
        userDoc().set(
            mapOf("userInfo" to mapOf("name" to name, "updatedAt" to Timestamp(Date()))),
            SetOptions.merge()
        ).await()
        val snap = userDoc().get(Source.SERVER).await()
        @Suppress("UNCHECKED_CAST")
        val userInfo = snap.get("userInfo") as? Map<String, Any>
        return userInfo?.get("name") as? String == name
    }
}
