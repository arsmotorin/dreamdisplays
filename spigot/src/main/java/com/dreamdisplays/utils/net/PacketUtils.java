package com.dreamdisplays.utils.net;

import com.dreamdisplays.DreamDisplaysPlugin;
import com.dreamdisplays.datatypes.SyncPacket;
import me.inotsleep.utils.logging.LoggingManager;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class PacketUtils {
    public static void sendDisplayInfoPacket(List<Player> players, UUID id, UUID ownerId, Vector pos, int width, int height, String url, String lang, BlockFace face, boolean isSync) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);

            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());

            out.writeLong(ownerId.getMostSignificantBits());
            out.writeLong(ownerId.getLeastSignificantBits());

            writeVarInt(out, (int) pos.getX());
            writeVarInt(out, (int) pos.getY());
            writeVarInt(out, (int) pos.getZ());

            writeVarInt(out, width);
            writeVarInt(out, height);

            writeString(out, url);

            out.writeByte(toFacingPacketByte(face));
            out.writeBoolean(isSync);

            writeString(out, lang);
            byte[] arr = byteStream.toByteArray();

            players.forEach(player -> {
                player.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:display_info", arr);
            });

        } catch (IOException exception) {
            LoggingManager.warn("Unable to send packet", exception);
        }
    }

    public static void sendSyncPacket(List<Player> players, SyncPacket packet) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);

            out.writeLong(packet.id().getMostSignificantBits());
            out.writeLong(packet.id().getLeastSignificantBits());

            out.writeBoolean(packet.isSync());
            out.writeBoolean(packet.currentState());

            writeVarLong(out, packet.currentTime());
            writeVarLong(out, packet.limitTime());

            byte[] arr = byteStream.toByteArray();

            players.forEach(player -> {
                player.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:sync", arr);
            });
        } catch (IOException exception) {
            LoggingManager.warn("Unable to send packet", exception);
        }
    }

    public static void sendDeletePacket(List<Player> players, UUID id) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);

            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());

            byte[] arr = byteStream.toByteArray();

            players.forEach(player -> {
                player.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:delete", arr);
            });
        } catch (IOException exception) {
            LoggingManager.warn( "Unable to send packet", exception);
        }
    }

    public static void sendPremiumPacket(Player player, boolean premium) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);

            out.writeBoolean(premium);

            byte[] arr = byteStream.toByteArray();

            player.sendPluginMessage(DreamDisplaysPlugin.getInstance(), "dreamdisplays:premium", arr);
        } catch (IOException exception) {
            LoggingManager.warn( "Unable to send packet", exception);
        }
    }

    public static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    public static void writeString(DataOutputStream out, String str) throws IOException {
        byte[] utf8 = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, utf8.length);
        out.write(utf8);
    }

    public static byte toFacingPacketByte(BlockFace face) {
        return switch (face) {
            case NORTH -> 0;
            case EAST  -> 1;
            case SOUTH -> 2;
            case WEST  -> 3;
            default -> 0; // Fallback to NORTH
        };
    }

    public static long readVarLong(DataInputStream buf) throws IOException {
        long value = 0L;
        int  position = 0;
        byte currentByte;
        do {
            if (position >= 10) {
                throw new RuntimeException("VarLong too big");
            }
            currentByte = buf.readByte();
            value |= (long)(currentByte & 0x7F) << (position * 7);
            position++;
        } while ((currentByte & 0x80) != 0);
        return value;
    }

    public static void writeVarLong(DataOutputStream buf, long value) throws IOException {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                buf.writeByte((int) value);
                return;
            } else {
                buf.writeByte(((int) value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    public static UUID readUUID(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    public static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            // Read the byte as unsigned
            read = in.readUnsignedByte();
            // 7 bytes for other information
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new IOException("VarInt too big");
            }
        } while ((read & 0x80) != 0);

        return result;
    }
}