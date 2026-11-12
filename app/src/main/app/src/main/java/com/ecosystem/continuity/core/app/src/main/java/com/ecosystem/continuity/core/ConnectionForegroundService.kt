package com.ecosystem.continuity.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

class ConnectionForegroundService : Service() {

    companion object {
        private const val SERVICE_ID = "com.ecosystem.continuity.P2P_BRIDGE"
        private const val CHANNEL_ID = "continuity_channel"
        
        val connectionStatus = MutableStateFlow("Відключено")
        val lastReceivedMessage = MutableStateFlow("Немає вхідних пакетів")
        private var instance: ConnectionForegroundService? = null

        fun sendPacket(packet: EcosystemPacket) {
            instance?.sendInternal(packet)
        }
    }

    private lateinit var connectionsClient: ConnectionsClient
    private var connectedEndpointId: String? = null
    private val localDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun onCreate() {
        super.onCreate()
        instance = this
        connectionsClient = Nearby.getConnectionsClient(this)
        createNotificationChannel()

        val notification = buildNotification("Пошук пристрою екосистеми...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }

        startP2PMesh()
    }

    private fun startP2PMesh() {
        connectionStatus.value = "Пошук поруч ($localDeviceName)..."
        val options = Strategy.P2P_POINT_TO_POINT

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(options).build()
        connectionsClient.startAdvertising(
            localDeviceName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        )

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(options).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
        )
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionStatus.value = "Знайдено: ${info.endpointName}. З'єднання..."
            connectionsClient.requestConnection(
                localDeviceName, endpointId, connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                connectionStatus.value = "Підключено (Активний міст)"
                updateNotification("Зв'язок встановлено")
                connectionsClient.stopDiscovery()
                connectionsClient.stopAdvertising()
            } else {
                connectionStatus.value = "Помилка з'єднання, повторний пошук..."
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            connectionStatus.value = "Зв'язок втрачено. Перепідключення..."
            updateNotification("Пошук пристрою...")
            startP2PMesh()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val jsonStr = String(bytes, StandardCharsets.UTF_8)
            runCatching {
                val packet = Json.decodeFromString<EcosystemPacket>(jsonStr)
                lastReceivedMessage.value = "[${packet.type}] від ${packet.senderDevice}: ${packet.body}"
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun sendInternal(packet: EcosystemPacket) {
        val targetId = connectedEndpointId ?: return
        val jsonStr = Json.encodeToString(packet)
        val payload = Payload.fromBytes(jsonStr.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(targetId, payload)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Continuity Background Link", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Continuity Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
    }

    override fun onDestroy() {
        connectionsClient.stopAllEndpoints()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
