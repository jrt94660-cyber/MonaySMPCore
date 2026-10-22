package com.monaysmp.core;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

final class Database implements AutoCloseable {
    private final Connection connection;

    Database(JavaPlugin plugin) throws SQLException {
        File file = new File(plugin.getDataFolder(), "monaysmp.db");
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS homes (
                  uuid TEXT NOT NULL, name TEXT NOT NULL, world TEXT NOT NULL,
                  x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                  yaw REAL NOT NULL, pitch REAL NOT NULL,
                  PRIMARY KEY(uuid, name)
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_data (
                  uuid TEXT PRIMARY KEY, afk INTEGER NOT NULL DEFAULT 0
                )
                """);
        }
    }

    synchronized boolean setHome(UUID uuid, String name, Location loc) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO homes(uuid,name,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?)
            ON CONFLICT(uuid,name) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y,
            z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch
            """)) {
            ps.setString(1, uuid.toString()); ps.setString(2, name.toLowerCase());
            ps.setString(3, loc.getWorld().getName()); ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY()); ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw()); ps.setFloat(8, loc.getPitch());
            return ps.executeUpdate() > 0;
        }
    }

    synchronized Location getHome(UUID uuid, String name, JavaPlugin plugin) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT world,x,y,z,yaw,pitch FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                World world = plugin.getServer().getWorld(rs.getString("world"));
                if (world == null) return null;
                return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        }
    }

    synchronized boolean deleteHome(UUID uuid, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, name.toLowerCase());
            return ps.executeUpdate() > 0;
        }
    }

    synchronized Map<String, String> listHomes(UUID uuid) throws SQLException {
        Map<String, String> homes = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name,world FROM homes WHERE uuid=? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) homes.put(rs.getString("name"), rs.getString("world"));
            }
        }
        return homes;
    }

    @Override public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.close();
    }
}
