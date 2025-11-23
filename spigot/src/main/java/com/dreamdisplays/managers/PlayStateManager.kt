package com.dreamdisplays.managers

import com.dreamdisplays.datatypes.PlayState
import com.dreamdisplays.datatypes.SyncPacket
import com.dreamdisplays.managers.DisplayManager.getDisplayData
import com.dreamdisplays.utils.net.PacketUtils
import me.inotsleep.utils.logging.LoggingManager
import org.bukkit.entity.Player
import java.util.*

object PlayStateManager {
    private val playStates: MutableMap<UUID?, PlayState> = HashMap<UUID?, PlayState>()

    @JvmStatic
    fun processSyncPacket(packet: SyncPacket, player: Player) {
        val data = getDisplayData(packet.id)
        if (data != null) data.isSync = packet.isSync

        if (!packet.isSync) {
            playStates.remove(packet.id)
            return
        }

        if (data == null) return

        if ((data.ownerId.toString() + "") != player.getUniqueId().toString() + "") {
            LoggingManager.warn("Player " + player.getName() + " sent sync packet while he not owner! ")
            return
        }

        val state = playStates.computeIfAbsent(packet.id) { id: UUID? -> PlayState(id) }
        state.update(packet)
        data.duration = packet.limitTime
        val receivers = data.receivers

        PacketUtils.sendSyncPacket(receivers.stream().filter { p: Player? -> p!!.getUniqueId() != player.getUniqueId() }
            .toList(), packet)
    }

    @JvmStatic
    fun sendSyncPacket(id: UUID?, player: Player?) {
        if (!playStates.containsKey(id)) return
        val state: PlayState = playStates.get(id)!!

        val packet = state.createPacket()
        PacketUtils.sendSyncPacket(mutableListOf<Player?>(player), packet)
    }
}
