# ReliefMesh 🛜🆘

**Extreme-Range Offline Emergency Mesh Network for Disaster Zones**

ReliefMesh is a decentralized Android application engineered for environments with zero cellular network, Wi-Fi, or internet infrastructure. Designed for disaster response and off-grid scenarios, it transforms standard Android smartphones into a self-healing mesh network. Victims can broadcast SOS signals, and rescue teams can pinpoint their exact locations on offline maps while coordinating triage—all without a single cell tower.

---

## 🌟 Key Features

* **Zero-Internet Communication:** Operates entirely offline via peer-to-peer radio protocols.
* **Dual-Radio Architecture:**
* **Fringe Range (Up to 1.5km):** Utilizes **Bluetooth 5.0 LE Coded PHY** (Extended Advertising) for long-range, low-power epidemic gossip of 35-byte emergency packets.
* **High-Speed Bulk Sync:** Automatically spins up ephemeral **Wi-Fi Hotspots** (`LocalOnlyHotspot` + TCP Sockets) when devices are in close proximity to burst-sync the entire disaster database in milliseconds.


* **Live Offline Mapping:** Integrates `osmdroid` for entirely offline map rendering. Emergency pins drop onto the map dynamically as distress packets bounce through the mesh.
* **Triage System & Inbox:** Victims can specify needs (First Aid, Food, People Count, and Custom 20-character messages). Responders get a dedicated inbox to review distress signals and broadcast targeted "Help En Route" acknowledgments.
* **Resilient Data Pipeline:**
* **RaptorQ (Erasure Coding):** Data is chunked into strict 234-byte Fountain Symbols for rateless, resumable transmission over unstable radio links.
* **Zstandard Compression:** Maximizes payload efficiency.
* **Room WAL & Bloom Filters:** High-speed SQLite batch ingestion uses a 64-bit Bloom Filter to reject duplicate packets instantly, preventing database flooding.



---

## 🛠 Tech Stack

* **Platform:** Native Android (Java)
* **Local Storage:** Android Room Database (SQLite with WAL mode)
* **Mapping:** `osmdroid` (OpenStreetMap offline tiles)
* **Networking:** Android `BluetoothLeAdvertiser` / `BluetoothLeScanner`, `WifiManager.LocalOnlyHotspot`, Java Sockets.
* **Background Processing:** Android 14+ strict Foreground Services, WakeLocks, and `ScheduledExecutorService` for 24-hour Time-to-Live (TTL) packet pruning.

---

## 🧠 How It Works (The Pipeline)

1. **Broadcast:** A user requests help. The app packages their GPS coordinates, needs, and message into a tightly packed **35-byte** byte array.
2. **Epidemic Gossip:** The app uses BLE Coded PHY to broadcast this packet into the air. Any nearby phone running ReliefMesh catches it, saves it to its local Room DB, and immediately rebroadcasts it to extend the range.
3. **Proximity Burst:** When two nodes detect each other with a strong signal (e.g., -82 dBm), one becomes a temporary AP. They connect via TCP, compress their entire local databases, chunk them into RaptorQ symbols, and burst the missing data to each other.
4. **UI Observation:** The map UI strictly observes the Room Database via `LiveData`. As packets arrive via radio and hit the database, map markers instantly appear without blocking the main thread.

---

## 🚀 Installation & Setup

### Prerequisites

* Android Studio (Ladybug or newer recommended)
* Min SDK: API 26 (Android 8.0)
* Target SDK: API 34 (Android 14)
* **Physical Android Devices:** *Android Emulators cannot simulate Bluetooth LE Extended Advertising or Wi-Fi Direct. You must test on real hardware.*

### Building the Project

1. Clone this repository.
2. Open the project in Android Studio.
3. Sync Gradle files.
4. Connect your physical Android device via USB (ensure USB Debugging is enabled).
5. Click **Run** (`Shift + F10`).

---

## 📱 Usage Guide

### Single Device Testing (Stress Test)

If you only have one device and want to test the rendering engine and database:

1. Open the app and grant **Location** and **Nearby Devices** permissions.
2. Wait for the GPS to lock (or tap the "GPS: Locating..." text to inject a mock location if you are indoors).
3. Tap **SIMULATE 500 EMERGENCY NODES**.
4. The app will generate 500 fake SOS packets, slam them into the Room database, and instantly render them on the map.

### Multi-Device Mesh Testing

1. Install the app on **Phone A** and **Phone B**.
2. Ensure both phones display `● MESH READY` and have a green GPS lock.
3. On **Phone A**, fill out a triage request (e.g., check "First Aid", type "Trapped") and tap **Broadcast Emergency SOS**.
4. Watch **Phone B**. Within seconds, the BLE scanner will catch the packet, plot it on Phone B's map, and store it.
5. On **Phone B**, tap **View Incoming SOS Requests**, locate Phone A's signal, and tap **Accept Request**.
6. Phone A will receive the ACK packet, and the UI will update to show that help is on the way.

---

## ⚠️ Permissions Explained

ReliefMesh requires invasive permissions to operate a background mesh network. All data stays strictly on the device and the local offline network.

* `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: To embed your location in distress packets and render the map.
* `BLUETOOTH_ADVERTISE` / `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`: To power the BLE fringe-range mesh.
* `NEARBY_WIFI_DEVICES`: To spin up high-speed localized Wi-Fi hotspots for database syncing.
* `FOREGROUND_SERVICE`: To keep the radios alive in the background while the phone is locked in a user's pocket.

---

## 📄 License

This project is open-source and available under the [MIT License](https://www.google.com/search?q=LICENSE).
