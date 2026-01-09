package com.dreamdisplays.managers

import com.dreamdisplays.datatypes.StateData
import com.dreamdisplays.datatypes.SyncData
import com.dreamdisplays.managers.DisplayManager.getDisplayData
import com.dreamdisplays.managers.DisplayManager.getReceivers
import com.dreamdisplays.utils.net.Utils
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Manages the state of displays being played by players.
 */
@NullMarked
object StateManager {
    private val playStates: MutableMap<UUID?, StateData> = HashMap()
    fun processSyncPacket(packet: SyncData, player: Player) {
        val data = getDisplayData(packet.id)
        if (data != null) data.isSync = packet.isSync

        if (!packet.isSync) {
            playStates.remove(packet.id)
            return
        }

        if (data == null) return

        if (data.ownerId != player.uniqueId) {
            return
        }

        val state = playStates.computeIfAbsent(packet.id) { id: UUID? -> StateData(id) }
        state.update(packet)
        data.duration = packet.limitTime

        val receivers = getReceivers(data)

        Utils.sendSync(receivers.filter { it.uniqueId != player.uniqueId }.toMutableList(), packet)
    }

    fun sendSyncPacket(id: UUID?, player: Player?) {
        val state = playStates[id] ?: return

        val packet = state.createPacket()
        Utils.sendSync(mutableListOf(player), packet)
    }
}
