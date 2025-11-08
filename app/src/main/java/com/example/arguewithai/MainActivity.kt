package com.example.arguewithai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.arguewithai.ui.theme.ArgueWithAiTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArgueWithAiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        FirebaseApp.initializeApp(this)

        val ctx = applicationContext
        Log.d("MyService", "pkg = ${ctx.packageName}")

        try {
            val opts = FirebaseApp.getInstance().options
            Log.d("MyService", "projectId = ${opts.projectId}")
            Log.d("MyService", "appId     = ${opts.applicationId}")
            Log.d("MyService", "apiKey    = ${opts.apiKey}")
        } catch (e: Exception) {
            Log.e("MyService", "FirebaseApp.getInstance() FAILED", e)
        }

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener { Log.d("MyService", "✅[Firebase] Logged in: ${it.user?.uid}") }
            .addOnFailureListener { Log.e("MyService", "❌[Firebase] Login failed", it) }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}