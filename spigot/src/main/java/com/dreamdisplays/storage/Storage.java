package com.dreamdisplays.storage;

import com.dreamdisplays.DreamDisplaysPlugin;
import com.dreamdisplays.datatypes.DisplayData;
import com.dreamdisplays.managers.DisplayManager;
import me.inotsleep.utils.logging.LoggingManager;
import me.inotsleep.utils.storage.connection.BaseConnection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Storage {
    BaseConnection connection;
    DreamDisplaysPlugin plugin;
    String tablePrefix;

    public Storage(DreamDisplaysPlugin plugin) {
        this.plugin = plugin;

        Runnable connectTask = () -> {
            tablePrefix = DreamDisplaysPlugin.config.storage.tablePrefix;

            try {
                connection = BaseConnection.createConnection(DreamDisplaysPlugin.config.storage, plugin.getDataFolder());
                connection.connect();

                onConnect();
            } catch (SQLException e) {
                LoggingManager.error("Could not connect to database", e);
                DreamDisplaysPlugin.disablePlugin();
            }
        };

        if (DreamDisplaysPlugin.isFolia()) {
            try {
                Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                Object asyncScheduler = bukkitClass.getMethod("getAsyncScheduler").invoke(null);
                Class<?> consumerClass = Class.forName("java.util.function.Consumer");
                Object task = Proxy.newProxyInstance(consumerClass.getClassLoader(), new Class<?>[]{consumerClass}, (proxy, method, args) -> {
                    connectTask.run();
                    return null;
                });
                asyncScheduler.getClass().getMethod("runNow", Object.class, consumerClass).invoke(asyncScheduler, plugin, task);
            } catch (Exception e) {
                // Fallback to sync if reflection fails
                connectTask.run();
            }
        } else {
            new BukkitRunnable() {
                public void run() {
                    connectTask.run();
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    private void onConnect() throws SQLException {
        connection.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + tablePrefix + "displays (" +
                "id BINARY(16) PRIMARY KEY NOT NULL, " +
                "ownerId BINARY(16) NOT NULL, " +
                "videoCode CHAR(11) NULL, " +
                "world CHAR(255) NOT NULL, " +
                "pos1 BIGINT NOT NULL, " +
                "pos2 BIGINT NOT NULL, " +
                "size BIGINT NOT NULL, " +
                "facing TINYINT UNSIGNED NOT NULL, " +
                "isSync BOOLEAN NOT NULL," +
                "duration BIGINT NULL" +
                ");");

        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet cols = meta.getColumns(null, null, tablePrefix + "displays", "lang")) {
            if (!cols.next()) {
                connection.executeUpdate("ALTER TABLE " + tablePrefix + "displays " +
                        "ADD COLUMN lang VARCHAR(255) DEFAULT '' NOT NULL");
            }
        }

        DisplayManager.register(getAllDisplays());
    }

    public void onDisable() {
        if (connection == null) return;

        try {
            DisplayManager.save(this::saveDisplay);

            connection.disconnect();
        } catch (SQLException e) {
            LoggingManager.error("Unable to save data", e);
        }
    }

    private byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }

    private UUID bytesToUuid(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        long msb = buf.getLong();
        long lsb = buf.getLong();
        return new UUID(msb, lsb);
    }

    public void saveDisplay(DisplayData data) {
        String sql = "REPLACE INTO " + tablePrefix + "displays " +
                "(id, ownerId, videoCode, world, pos1, pos2, size, facing, isSync, duration, lang) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        try {
            connection.executeUpdate(sql,
                    uuidToBytes(data.getId()),
                    uuidToBytes(data.getOwnerId()),
                    data.getUrl(),
                    data.getPos1().getWorld().getName(),
                    packBlockPos(
                            data.getPos1().getBlockX(),
                            data.getPos1().getBlockY(),
                            data.getPos1().getBlockZ()
                    ),
                    packBlockPos(
                            data.getPos2().getBlockX(),
                            data.getPos2().getBlockY(),
                            data.getPos2().getBlockZ()
                    ),
                    pack(data.getWidth(), data.getHeight()),
                    (byte) data.getFacing().ordinal(),
                    data.isSync(),

                    // If duration is null, it will be saved as NULL in the database automatically.
                    data.getDuration(),
                    data.getLang()
            );
        } catch (SQLException e) {
            LoggingManager.error("Could not fetch from database", e);
            DreamDisplaysPlugin.disablePlugin();
        }
    }

   // Fetch all displays from the database
    public List<DisplayData> getAllDisplays() {
        String sql = "SELECT id, ownerId, videoCode, world, pos1, pos2, size, facing, isSync, duration, lang " +
                "FROM " + tablePrefix + "displays";
        List<DisplayData> list = new ArrayList<>();

        try (ResultSet rs = connection.executeQuery(sql)) {
            while (rs.next()) {
                UUID id = bytesToUuid(rs.getBytes("id"));
                UUID ownerId = bytesToUuid(rs.getBytes("ownerId"));
                String videoCode = rs.getString("videoCode");
                World world = Bukkit.getWorld(rs.getString("world"));

                long packed1 = rs.getLong("pos1");
                long packed2 = rs.getLong("pos2");
                int x1 = unpackX(packed1), y1 = unpackY(packed1), z1 = unpackZ(packed1);
                int x2 = unpackX(packed2), y2 = unpackY(packed2), z2 = unpackZ(packed2);

                long sizePacked = rs.getLong("size");
                int width = unpackHigh(sizePacked);
                int height = unpackLow(sizePacked);

                DisplayData data = new DisplayData(
                        id, ownerId,
                        new Location(world, x1, y1, z1),
                        new Location(world, x2, y2, z2),
                        width, height,
                        BlockFace.values()[rs.getInt("facing")]
                );
                data.setUrl(videoCode);
                data.setSync(rs.getBoolean("isSync"));
                long dur = rs.getLong("duration");
                if (!rs.wasNull()) {
                    data.setDuration(dur);
                }

                data.setLang(rs.getString("lang"));

                list.add(data);
            }
        } catch (SQLException e) {
            LoggingManager.error("Could not fetch from database", e);
            DreamDisplaysPlugin.disablePlugin();
        }

        return list;
    }

    public void deleteDisplay(DisplayData data) {
        String sql = "DELETE FROM " + tablePrefix + "displays WHERE id = ?";
        try {
            connection.executeUpdate(sql, uuidToBytes(data.getId()));
        } catch (SQLException e) {
            LoggingManager.error("Could not delete display from database", e);
            DreamDisplaysPlugin.disablePlugin();
        }
    }

    private static long packBlockPos(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int unpackX(long packed) {
        return (int)(packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int)(packed << 52 >> 52);
    }

    private static int unpackZ(long packed) {
        return (int)(packed << 26 >> 38);
    }

    public static long pack(int high, int low) {
        return (((long) high) << 32) | (low & 0xFFFFFFFFL);
    }

    public static int unpackHigh(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackLow(long packed) {
        return (int) packed;
    }
}
