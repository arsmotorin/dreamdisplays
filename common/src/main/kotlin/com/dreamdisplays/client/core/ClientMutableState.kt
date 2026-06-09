package com.dreamdisplays.client.core

interface ClientMutableState : ClientState {
    override var isOnScreen: Boolean
    override var isFocusMode: Boolean
    override var displaysEnabled: Boolean
    override var isPremium: Boolean
    override var isAdmin: Boolean
    override var isReportingEnabled: Boolean
    override var connectedServerId: String?
}
