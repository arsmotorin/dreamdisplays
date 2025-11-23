package com.dreamdisplays.utils.net

import com.dreamdisplays.DreamDisplaysPlugin
import com.dreamdisplays.datatypes.SyncPacket
import me.inotsleep.utils.logging.LoggingManager
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.function.Consumer

object PacketUtils {
    fun sendDisplayInfoPacket(
        players: MutableList<Player?>,
        id: UUID,
        ownerId: UUID,
        pos: Vector,
        width: Int,
        height: Int,
        url: String,
        lang: String,
        face: BlockFace,
        isSync: Boolean
    ) {
        try {
            val byteStream = ByteArrayOutputStream()
            val out = DataOutputStream(byteStream)

            out.writeLong(id.mostSignificantBits)
            out.writeLong(id.leastSignificantBits)

            out.writeLong(ownerId.mostSignificantBits)
            out.writeLong(ownerId.leastSignificantBits)

            writeVarInt(out, pos.getX().toInt())
            writeVarInt(out, pos.getY().toInt())
            writeVarInt(out, pos.getZ().toInt())

            writeVarInt(out, width)
            writeVarInt(out, height)

            writeString(out, url)

            out.writeByte(toFacingPacketByte(face).toInt())
            out.writeBoolean(isSync)

            writeString(out, lang)
            val arr = byteStream.toByteArray()

            players.forEach(Consumer { player: Player? ->
                player!!.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:display_info", arr)
            })
        } catch (exception: IOException) {
            LoggingManager.warn("Unable to send packet", exception)
        }
    }

    fun sendSyncPacket(players: MutableList<Player?>, packet: SyncPacket) {
        try {
            val byteStream = ByteArrayOutputStream()
            val out = DataOutputStream(byteStream)

            out.writeLong(packet.id!!.mostSignificantBits)
            out.writeLong(packet.id.leastSignificantBits)

            out.writeBoolean(packet.isSync)
            out.writeBoolean(packet.currentState)

            writeVarLong(out, packet.currentTime)
            writeVarLong(out, packet.limitTime)

            val arr = byteStream.toByteArray()

            players.forEach(Consumer { player: Player? ->
                player!!.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:sync", arr)
            })
        } catch (exception: IOException) {
            LoggingManager.warn("Unable to send packet", exception)
        }
    }

    fun sendDeletePacket(players: MutableList<Player?>, id: UUID) {
        try {
            val byteStream = ByteArrayOutputStream()
            val out = DataOutputStream(byteStream)

            out.writeLong(id.mostSignificantBits)
            out.writeLong(id.leastSignificantBits)

            val arr = byteStream.toByteArray()

            players.forEach(Consumer { player: Player? ->
                player!!.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:delete", arr)
            })
        } catch (exception: IOException) {
            LoggingManager.warn("Unable to send packet", exception)
        }
    }

    fun sendPremiumPacket(player: Player, premium: Boolean) {
        try {
            val byteStream = ByteArrayOutputStream()
            val out = DataOutputStream(byteStream)

            out.writeBoolean(premium)

            val arr = byteStream.toByteArray()

            player.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:premium", arr)
        } catch (exception: IOException) {
            LoggingManager.warn("Unable to send packet", exception)
        }
    }

    @Throws(IOException::class)
    fun writeVarInt(out: DataOutputStream, value: Int) {
        var value = value
        while ((value and -0x80).toLong() != 0L) {
            out.writeByte((value and 0x7F) or 0x80)
            value = value ushr 7
        }
        out.writeByte(value and 0x7F)
    }

    @Throws(IOException::class)
    fun writeString(out: DataOutputStream, str: String) {
        val utf8 = str.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(out, utf8.size)
        out.write(utf8)
    }

    fun toFacingPacketByte(face: BlockFace): Byte {
        return when (face) {
            BlockFace.NORTH -> 0
            BlockFace.EAST -> 1
            BlockFace.SOUTH -> 2
            BlockFace.WEST -> 3
            else -> 0
        }
    }

    @Throws(IOException::class)
    fun readVarLong(buf: DataInputStream): Long {
        var value = 0L
        var position = 0
        var currentByte: Byte
        do {
            if (position >= 10) {
                throw RuntimeException("VarLong too big")
            }
            currentByte = buf.readByte()
            value = value or ((currentByte.toInt() and 0x7F).toLong() shl (position * 7))
            position++
        } while ((currentByte.toInt() and 0x80) != 0)
        return value
    }

    @Throws(IOException::class)
    fun writeVarLong(buf: DataOutputStream, value: Long) {
        var value = value
        while (true) {
            if ((value and 0x7FL.inv()) == 0L) {
                buf.writeByte(value.toInt())
                return
            } else {
                buf.writeByte((value.toInt() and 0x7F) or 0x80)
                value = value ushr 7
            }
        }
    }

    @Throws(IOException::class)
    fun readUUID(`in`: DataInputStream): UUID {
        return UUID(`in`.readLong(), `in`.readLong())
    }

    @Throws(IOException::class)
    fun readVarInt(`in`: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Int
        do {
            // Read the byte as unsigned
            read = `in`.readUnsignedByte()
            // 7 bytes for other information
            val value = (read and 0x7F)
            result = result or (value shl (7 * numRead))

            numRead++
            if (numRead > 5) {
                throw IOException("VarInt too big")
            }
        } while ((read and 0x80) != 0)

        return result
    }
}
