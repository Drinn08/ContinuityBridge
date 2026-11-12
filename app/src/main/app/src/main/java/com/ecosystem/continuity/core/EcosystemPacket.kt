package com.ecosystem.continuity.core

import kotlinx.serialization.Serializable

@Serializable
data class EcosystemPacket(
    val type: String,
    val senderDevice: String,
    val title: String = "",
    val body: String = "",
    val extraData: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
