ReliefMesh 🛜🆘

Offline Emergency Mesh Network for Disaster Zones

ReliefMesh is an Android app that enables offline emergency communication without cellular networks or internet. Smartphones form a decentralized mesh to share SOS messages, locations, and rescue updates.

✨ Features

- 🆘 Offline SOS — Send emergency requests with GPS location, needs, and messages.
- 📡 Long-Range BLE Mesh — Uses Bluetooth LE for device-to-device communication.
- ⚡ Wi-Fi Bulk Sync — Uses temporary Wi-Fi connections and TCP for fast data transfer.
- 🗺️ Offline Maps — View emergency locations using "osmdroid".
- 🤝 Triage & Acknowledgments — Responders can receive requests and send "Help En Route" updates.
- 🔄 RaptorQ + Zstd — Erasure coding and compression for reliable data transfer.
- 💾 Room Database — Stores and filters emergency packets locally.

🛠️ Tech Stack

Java • Android • Bluetooth LE • Wi-Fi • TCP Sockets • Room/SQLite • RaptorQ • Zstandard • osmdroid

🚀 How It Works

SOS → BLE Broadcast → Nearby Nodes Relay → Wi-Fi Sync → Database → Offline Map

Every phone acts as both a sender and relay, allowing emergency messages to travel across multiple devices without internet infrastructure.

📱 Testing

Requires physical Android devices for Bluetooth and Wi-Fi mesh testing.

1. Install the app on two or more Android phones.
2. Grant required permissions.
3. Broadcast an SOS from one device.
4. Nearby devices receive and display the emergency location.
5. Responders can acknowledge the request.

📄 License

MIT License