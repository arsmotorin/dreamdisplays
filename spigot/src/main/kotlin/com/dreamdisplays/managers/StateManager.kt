package com.dreamdisplays.managers

import com.dreamdisplays.datatypes.StateData
import com.dreamdisplays.datatypes.SyncData
import com.dreamdisplays.managers.DisplayManager.getDisplayData
import com.dreamdisplays.managers.DisplayManager.getReceivers
import com.dreamdisplays.utils.net.PacketUtils
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Manages the state of displays being played by players.
 */
@NullMarked
object StateManager {
    private val playStates: MutableMap<UUID, StateData> = HashMap()

    @JvmStatic
    fun processSyncPacket(packet: SyncData, player: Player) {
        val displayId = packet.id ?: return
        val data = getDisplayData(displayId)
        if (data != null) data.isSync = packet.isSync

        if (!packet.isSync) {
            playStates.remove(displayId)
            return
        }

        if (data == null) {
            playStates.remove(displayId)
            return
        }

        if (data.ownerId != player.uniqueId) {
            return
        }

        val state = playStates.computeIfAbsent(displayId) { id -> StateData(id) }
        state.update(packet)
        data.duration = packet.limitTime

        val receivers = getReceivers(data)

        PacketUtils.sendSync(
            receivers.filter { it.uniqueId != player.uniqueId }.toMutableList(),
            packet.copy(id = displayId)
        )
    }

    @JvmStatic
    fun sendSyncPacket(id: UUID?, player: Player?) {
        val displayId = id ?: return
        val state = playStates[displayId] ?: return

        val packet = state.createPacket()
        PacketUtils.sendSync(mutableListOf(player), packet)
    }
}
