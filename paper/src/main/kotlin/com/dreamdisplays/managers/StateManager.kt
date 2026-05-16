package com.dreamdisplays.managers

import com.dreamdisplays.datatypes.StateData
import com.dreamdisplays.datatypes.SyncData
import com.dreamdisplays.managers.DisplayManager.getDisplayData
import com.dreamdisplays.managers.DisplayManager.getReceivers
import com.dreamdisplays.utils.net.PacketUtil
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@NullMarked
object StateManager {
    private val playStates: MutableMap<UUID, StateData> = ConcurrentHashMap()
    private val lastSyncBroadcast: MutableMap<UUID, Long> = ConcurrentHashMap()

    private const val SYNC_MIN_INTERVAL_MS = 250L

    @JvmStatic
    fun processSyncPacket(packet: SyncData, player: Player) {
        val displayId = packet.id ?: return
        val data = getDisplayData(displayId)
        if (data != null) data.isSync = packet.isSync

        if (!packet.isSync) {
            playStates.remove(displayId)
            lastSyncBroadcast.remove(displayId)
            return
        }

        if (data == null) {
            playStates.remove(displayId)
            return
        }

        if (data.ownerId != player.uniqueId) {
            return
        }

        if (packet.currentTime < 0 || packet.limitTime < 0
            || packet.currentTime > 24L * 60 * 60 * 1_000_000_000L
        ) {
            return
        }

        val state = playStates.computeIfAbsent(displayId) { id -> StateData(id) }
        state.update(packet)
        data.duration = packet.limitTime

        val now = System.currentTimeMillis()
        val lastBroadcast = lastSyncBroadcast[displayId] ?: 0L
        if (now - lastBroadcast < SYNC_MIN_INTERVAL_MS) {
            return
        }
        lastSyncBroadcast[displayId] = now

        val receivers = getReceivers(data)

        PacketUtil.sendSync(
            receivers.filter { it.uniqueId != player.uniqueId }.toMutableList(),
            packet.copy(id = displayId)
        )
    }

    @JvmStatic
    fun sendSyncPacket(id: UUID?, player: Player?) {
        val displayId = id ?: return
        val state = playStates[displayId] ?: return

        val packet = state.createPacket()
        PacketUtil.sendSync(mutableListOf(player), packet)
    }
}
