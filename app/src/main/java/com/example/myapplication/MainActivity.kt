package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val now = System.currentTimeMillis()
            val mockAlerts = listOf(
                FloodAlert("1", 42, Severity.HIGH, "Heavy rain expected", now, now + 86_400_000),
                FloodAlert("2", 7, Severity.SEVERE, "Tana River overflow", now, now + 86_400_000)
            )
            MaterialTheme { MapScreen(alerts = mockAlerts) }
        }
    }
}