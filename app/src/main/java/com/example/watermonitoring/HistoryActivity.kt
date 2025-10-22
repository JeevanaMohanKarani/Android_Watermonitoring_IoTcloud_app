package com.example.watermonitoring

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.watermonitoring.ui.theme.WatermonitoringTheme
import com.google.firebase.database.*

class HistoryActivity : ComponentActivity() {

    private lateinit var dbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbRef = FirebaseDatabase.getInstance().reference.child("history")

        setContent {
            WatermonitoringTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HistoryScreen(dbRef)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(dbRef: DatabaseReference) {
    var historyList by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }

    LaunchedEffect(true) {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<Map<String, String>>()
                for (child in snapshot.children) {
                    val map = child.value as? Map<*, *>
                    map?.let {
                        val safeMap = map.mapNotNull {
                            if (it.key is String && it.value is String) {
                                it.key as String to it.value as String
                            } else null
                        }.toMap()
                        tempList.add(safeMap)
                    }
                }
                historyList = tempList.reversed() // latest first
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HistoryActivity", "Database error: ${error.message}")
            }
        })
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("📜 History Records", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(historyList) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("🕒 ${record["timestamp"] ?: "N/A"}", style = MaterialTheme.typography.titleSmall)
                        Text("✅ Test: ${record["testStatus"] ?: "N/A"}")
                        Text("🧪 Chlorine: ${record["chlorineStatus"] ?: "N/A"}")
                        Text("💨 Gas: ${record["gasValue"] ?: "N/A"}")
                        Text("⚠️ Contamination: ${record["contaminationMessage"] ?: "N/A"} (Risk: ${record["risk"] ?: "?"})")
                        Text("🌡️ Temp: ${record["temperature"] ?: "N/A"} °C")
                        Text("💧 Humidity: ${record["humidity"] ?: "N/A"} %")
                        Text("🛠️ Pipe: ${record["pipeStatus"] ?: "N/A"}")
                        Text("🛢️ Tank Distance: ${record["tankDistance"] ?: "N/A"} cm")
                    }
                }
            }
        }
    }
}


