package com.dreamdisplays.client.core

interface ClientState {
    val isOnScreen: Boolean
    val isFocusMode: Boolean
    val displaysEnabled: Boolean
    val isPremium: Boolean
    val isAdmin: Boolean
    val isReportingEnabled: Boolean
    val connectedServerId: String?
}
