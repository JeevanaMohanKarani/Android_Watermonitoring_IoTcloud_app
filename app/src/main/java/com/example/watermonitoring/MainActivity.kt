package com.example.watermonitoring

import com.example.watermonitoring.HistoryActivity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.watermonitoring.ui.theme.WatermonitoringTheme
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext


class MainActivity : ComponentActivity() {

    private lateinit var dbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbRef = FirebaseDatabase.getInstance().reference

        // Launch coroutine to save history every 5 minutes
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5 * 60 * 1000) // 5 minutes

                try {
                    val snapshot = dbRef.get().addOnSuccessListener { snapshot ->
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        val historyData = mapOf(
                            "timestamp" to timestamp,
                            "chlorineStatus" to snapshot.child("chlorineLevel/status").value.toString(),
                            "gasValue" to snapshot.child("contamination/gasValue").value.toString(),
                            "risk" to snapshot.child("contamination/risk").value.toString(),
                            "contaminationMessage" to snapshot.child("contamination/message").value.toString(),
                            "humidity" to snapshot.child("environment/humidity").value.toString(),
                            "temperature" to snapshot.child("environment/temperature").value.toString(),
                            "pipeStatus" to snapshot.child("pipe/status").value.toString(),
                            "tankDistance" to snapshot.child("tankStatus/distanceCm").value.toString(),
                            "testStatus" to snapshot.child("test").value.toString()
                        )
                        dbRef.child("history").push().setValue(historyData)
                            .addOnSuccessListener { Log.d("History", "Saved at $timestamp") }
                            .addOnFailureListener { Log.e("History", "Save failed: ${it.message}") }
                    }
                } catch (e: Exception) {
                    Log.e("History", "Exception during history save: ${e.message}")
                }
            }
        }

        setContent {
            WatermonitoringTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = this // or use LocalContext inside DashboardScreen
                    DashboardScreen(
                        onHistoryClick = {
                            context.startActivity(Intent(context, HistoryActivity::class.java))
                        },
                        dbRef = dbRef
                    )
                }
            }
        }

    }
}

@Composable
fun DashboardScreen(onHistoryClick: () -> Unit, dbRef: DatabaseReference) {
    var chlorineStatus by remember { mutableStateOf("Loading...") }
    var gasValue by remember { mutableStateOf(0) }
    var risk by remember { mutableStateOf("Loading...") }
    var contaminationMessage by remember { mutableStateOf("Loading...") }
    var humidity by remember { mutableStateOf(0.0) }
    var temperature by remember { mutableStateOf(0.0) }
    var pipeStatus by remember { mutableStateOf("Loading...") }
    var tankDistance by remember { mutableStateOf(0.0) }
    var testStatus by remember { mutableStateOf("Loading...") }

    // Load live data from Firebase
    LaunchedEffect(true) {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chlorineStatus = snapshot.child("chlorineLevel/status").value.toString()
                gasValue = snapshot.child("contamination/gasValue").value.toString().toIntOrNull() ?: 0
                contaminationMessage = snapshot.child("contamination/message").value.toString()
                risk = snapshot.child("contamination/risk").value.toString()
                humidity = snapshot.child("environment/humidity").value.toString().toDoubleOrNull() ?: 0.0
                temperature = snapshot.child("environment/temperature").value.toString().toDoubleOrNull() ?: 0.0
                pipeStatus = snapshot.child("pipe/status").value.toString()
                tankDistance = snapshot.child("tankStatus/distanceCm").value.toString().toDoubleOrNull() ?: 0.0
                testStatus = snapshot.child("test").value.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error: ${error.message}")
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("💧 Water Monitoring Dashboard", style = MaterialTheme.typography.headlineSmall)
        Divider()

        DataCard("✅ Test Status", testStatus)
        DataCard("🧪 Chlorination", chlorineStatus)
        DataCard("💨 Gas Value", gasValue.toString())
        DataCard("⚠️ Contamination", "$contaminationMessage (Risk: $risk)")
        DataCard("🌡️ Temperature", "$temperature °C")
        DataCard("💧 Humidity", "$humidity %")
        DataCard("🛠️ Pipe Status", pipeStatus)
        DataCard("🛢️ Tank Distance", "${String.format("%.2f", tankDistance)} cm")

        Spacer(modifier = Modifier.height(16.dp))

        val context = LocalContext.current

        Button(
            onClick = {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val historyData = mapOf(
                    "timestamp" to timestamp,
                    "chlorineStatus" to chlorineStatus,
                    "gasValue" to gasValue.toString(),
                    "risk" to risk,
                    "contaminationMessage" to contaminationMessage,
                    "humidity" to humidity.toString(),
                    "temperature" to temperature.toString(),
                    "pipeStatus" to pipeStatus,
                    "tankDistance" to tankDistance.toString(),
                    "testStatus" to testStatus
                )

                dbRef.child("history").push().setValue(historyData)
                    .addOnSuccessListener {
                        Toast.makeText(context, "History updated successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to update history", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update History 💾")
        }


        Button(onClick = onHistoryClick, modifier = Modifier.fillMaxWidth()) {
            Text("View History 📜")
        }

    }
}

@Composable
fun DataCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}


