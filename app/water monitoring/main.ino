#include <WiFi.h>
#include <FirebaseESP32.h>
#include <DHT.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// ====== WiFi Credentials ======
#define WIFI_SSID "Wokwi-GUEST"
#define WIFI_PASSWORD ""

// ====== Firebase Configuration ======
#define FIREBASE_HOST "https://water-monitor-2e831-default-rtdb.asia-southeast1.firebasedatabase.app/"
#define FIREBASE_API_KEY "AIzaSyAYv8yj4gwQItOCj5jEN9AN7S-N7ycCPHw"

FirebaseData fbdo;
FirebaseConfig config;
FirebaseAuth auth;

// ====== Sensor & Button Pins ======
#define TRIG_PIN       2
#define ECHO_PIN       4
#define CHLORINE_BTN   5
#define PIPE_BTN       18
#define MQ135_AO       36  // A0 = VP
#define DHT_PIN        15
#define DHT_TYPE       DHT22

DHT dht(DHT_PIN, DHT_TYPE);

// ====== Setup WiFi ======
void connectToWiFi() {
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(300);
  }
  Serial.println(" Connected!");
}

// ====== Setup Function ======
void setup() {
  Serial.begin(115200);
  connectToWiFi();

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(CHLORINE_BTN, INPUT_PULLUP);
  pinMode(PIPE_BTN, INPUT_PULLUP);
  pinMode(MQ135_AO, INPUT);

  dht.begin(); // Initialize DHT sensor

  // Firebase Config
  config.api_key = FIREBASE_API_KEY;
  config.database_url = FIREBASE_HOST;
  config.token_status_callback = tokenStatusCallback;

  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("Firebase signup successful");
  } else {
    Serial.println("Firebase signup failed: " + fbdo.errorReason());
  }

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  Serial.println("Setup complete.");
}

// ====== Loop Function ======
void loop() {
if (Firebase.ready()) {
  if (Firebase.setString(fbdo, "/test", "ESP32 working!")) {
    Serial.println("✅ Firebase write success!");
  } else {
    Serial.println("❌ Firebase write failed: " + fbdo.errorReason());
  }
}
  // ---- Water Tank Level ----
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH);
  float distance = duration * 0.034 / 2;
  Firebase.setFloat(fbdo, "/tankStatus/distanceCm", distance);
  Serial.printf("Tank Distance: %.2f cm\n", distance);

  // ---- Chlorination Button ----
  if (digitalRead(CHLORINE_BTN) == LOW) {
    Firebase.setString(fbdo, "/chlorineLevel/status", "Chlorinated");
    Serial.println("Chlorination status updated");
  }else {
  Firebase.setString(fbdo, "/chlorineLevel/status", "Not Chlorinated");
  Serial.println("Chlorination status: Not Chlorinated");
  }

  // ---- Pipe Damage Button ----
  if (digitalRead(PIPE_BTN) == LOW) {
    Firebase.setString(fbdo, "/pipe/status", "Damaged");
    Serial.println("Pipe damage status updated");
  }else {
  Firebase.setString(fbdo, "/pipe/status", "Normal");
  Serial.println("Pipe status: Normal");
}

  // ---- MQ135 Gas Sensor ----
  int gasValue = analogRead(MQ135_AO);
  Firebase.setInt(fbdo, "/contamination/gasValue", gasValue);
  if (gasValue > 3000) {
    Firebase.setString(fbdo, "/contamination/message", "Warning: Drainage water detected near tap.");
    Firebase.setString(fbdo, "/contamination/risk", "High risk of diarrhea");
  } else {
    Firebase.setString(fbdo, "/contamination/message", "Safe");
    Firebase.setString(fbdo, "/contamination/risk", "Low");
  }
  Serial.printf("MQ135 Reading: %d\n", gasValue);

  // ---- DHT22 (Optional) ----
  float temp = dht.readTemperature();
  float humid = dht.readHumidity();
  if (!isnan(temp) && !isnan(humid)) {
    Firebase.setFloat(fbdo, "/environment/temperature", temp);
    Firebase.setFloat(fbdo, "/environment/humidity", humid);
    Serial.printf("Temp: %.2f °C, Humidity: %.2f %%\n", temp, humid);
  }

  delay(3000); // Wait before next update
}
