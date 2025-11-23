package com.dreamdisplays.managers

import com.dreamdisplays.datatypes.State
import com.dreamdisplays.datatypes.Sync
import com.dreamdisplays.managers.Display.getDisplayData
import com.dreamdisplays.utils.net.Utils
import me.inotsleep.utils.logging.LoggingManager
import org.bukkit.entity.Player
import java.util.*

object State {
    private val playStates: MutableMap<UUID?, State> = HashMap<UUID?, State>()

    @JvmStatic
    fun processSyncPacket(packet: Sync, player: Player) {
        val data = getDisplayData(packet.id)
        if (data != null) data.isSync = packet.isSync

        if (!packet.isSync) {
            playStates.remove(packet.id)
            return
        }

        if (data == null) return

        if ((data.ownerId.toString() + "") != player.uniqueId.toString() + "") {
            LoggingManager.warn("Player " + player.name + " sent sync packet while he not owner! ")
            return
        }

        val state = playStates.computeIfAbsent(packet.id) { id: UUID? -> State(id) }
        state.update(packet)
        data.duration = packet.limitTime
        val receivers = data.receivers

        Utils.sendSyncPacket(receivers.filter { it.uniqueId != player.uniqueId }.toMutableList(), packet)
    }

    @JvmStatic
    fun sendSyncPacket(id: UUID?, player: Player?) {
        val state = playStates[id] ?: return

        val packet = state.createPacket()
        Utils.sendSyncPacket(mutableListOf(player), packet)
    }
}
