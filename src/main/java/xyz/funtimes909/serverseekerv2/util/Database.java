package xyz.funtimes909.serverseekerv2.util;

import org.apache.commons.dbcp2.BasicDataSource;
import xyz.funtimes909.serverseekerv2.Main;
import xyz.funtimes909.serverseekerv2.builders.Mod;
import xyz.funtimes909.serverseekerv2.builders.Player;
import xyz.funtimes909.serverseekerv2.builders.Server;

import java.sql.*;
import java.util.List;

public class Database{
    private static final BasicDataSource dataSource = new BasicDataSource();

    public static void init() {
        dataSource.setUrl("jdbc:postgresql://" + Main.postgresUrl);
        dataSource.setPassword(Main.postgresPassword);
        dataSource.setUsername(Main.postgresUser);

        createIfNotExist();
    }

    public static Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database!", e);
        }
    }

    public static void createIfNotExist() {
        Main.logger.info("Attempting to create database tables...");
        try (Connection conn = getConnection()) {
            Statement tables = conn.createStatement();
            // Servers
            tables.addBatch("CREATE TABLE IF NOT EXISTS Servers (" +
                    "Address TEXT," +
                    "Port NUMERIC," +
                    "FirstSeen INT," +
                    "LastSeen INT," +
                    "Country TEXT," +
                    "Asn TEXT," +
                    "ReverseDNS TEXT," +
                    "Organization TEXT," +
                    "Version TEXT," +
                    "Protocol INT," +
                    "FmlNetworkVersion INT," +
                    "Motd TEXT," +
                    "Icon TEXT," +
                    "TimesSeen INT," +
                    "PreventsReports BOOLEAN DEFAULT NULL," +
                    "EnforceSecure BOOLEAN DEFAULT NULL," +
                    "Whitelist BOOLEAN DEFAULT NULL," +
                    "Cracked BOOLEAN DEFAULT NULL ," +
                    "MaxPlayers INT," +
                    "OnlinePlayers INT," +
                    "PRIMARY KEY (Address, Port))");

            // Player History
            tables.addBatch("CREATE TABLE IF NOT EXISTS PlayerHistory (" +
                    "Address TEXT," +
                    "Port INT," +
                    "PlayerUUID TEXT," +
                    "PlayerName TEXT," +
                    "FirstSeen INT," +
                    "LastSeen INT," +
                    "PRIMARY KEY (Address, Port, PlayerUUID)," +
                    "FOREIGN KEY (Address, Port) REFERENCES Servers(Address, Port))");

            // Mods
            tables.addBatch("CREATE TABLE IF NOT EXISTS Mods (" +
                    "Address TEXT," +
                    "Port INT," +
                    "ModID TEXT," +
                    "ModMarker TEXT," +
                    "PRIMARY KEY (Address, Port, ModId)," +
                    "FOREIGN KEY (Address, Port) REFERENCES Servers(Address, Port))");

            // Indexes
            tables.addBatch("CREATE INDEX IF NOT EXISTS ServersIndex ON Servers (Motd, version, onlineplayers, country, cracked, whitelist, lastseen)");
            tables.addBatch("CREATE INDEX IF NOT EXISTS PlayersIndex ON PlayerHistory (playername)");
            tables.addBatch("CREATE INDEX IF NOT EXISTS ModsIndex ON Mods (modid)");

            tables.executeBatch();
            tables.close();
        } catch (SQLException e) {
            Main.logger.error("Failed to create database tables!", e);
        }
    }

    public static void updateServer(Server server) {
        try (Connection conn = getConnection()) {
            String address = server.getAddress();
            short port = server.getPort();

            // Attempt to insert new server, if address and port already exist, update relevant information
            PreparedStatement insertServer = conn.prepareStatement("INSERT INTO Servers " +
                    "(Address," +
                    "Port," +
                    "FirstSeen," +
                    "LastSeen," +
                    "Country," +
                    "Asn," +
                    "ReverseDNS," +
                    "Organization," +
                    "Version," +
                    "Protocol," +
                    "FmlNetworkVersion," +
                    "Motd," +
                    "Icon," +
                    "TimesSeen," +
                    "PreventsReports," +
                    "EnforceSecure," +
                    "Whitelist," +
                    "Cracked," +
                    "MaxPlayers," +
                    "OnlinePlayers)" +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                    "ON CONFLICT (Address, Port) DO UPDATE SET " +
                    "LastSeen = EXCLUDED.LastSeen," +
                    "Country = EXCLUDED.Country," +
                    "Asn = EXCLUDED.Asn," +
                    "ReverseDNS = EXCLUDED.ReverseDNS," +
                    "Organization = EXCLUDED.Organization," +
                    "Version = EXCLUDED.Version," +
                    "Protocol = EXCLUDED.Protocol," +
                    "FmlNetworkVersion = EXCLUDED.FmlNetworkVersion," +
                    "Motd = EXCLUDED.Motd," +
                    "Icon = EXCLUDED.Icon," +
                    "TimesSeen = Servers.TimesSeen + 1," +
                    "PreventsReports = EXCLUDED.PreventsReports," +
                    "EnforceSecure = EXCLUDED.EnforceSecure," +
                    "Whitelist = EXCLUDED.Whitelist," +
                    "Cracked = EXCLUDED.Cracked," +
                    "MaxPlayers = EXCLUDED.MaxPlayers," +
                    "OnlinePlayers = EXCLUDED.OnlinePlayers");

            // Set most values as objects to insert a null if value doesn't exist
            insertServer.setString(1, server.getAddress());
            insertServer.setInt(2, server.getPort());
            insertServer.setLong(3, server.getTimestamp());
            insertServer.setLong(4, server.getTimestamp());
            insertServer.setString(5, server.getCountry());
            insertServer.setString(6, server.getAsn());
            insertServer.setString(7, server.getReverseDns());
            insertServer.setString(8, server.getOrganization());
            insertServer.setString(9, server.getVersion());
            insertServer.setObject(10, server.getProtocol(), Types.INTEGER);
            insertServer.setObject(11, server.getFmlNetworkVersion(), Types.INTEGER);
            insertServer.setString(12, server.getMotd());
            insertServer.setString(13, server.getIcon());
            insertServer.setInt(14, server.getTimesSeen());
            insertServer.setObject(15, server.getPreventsReports(), Types.BOOLEAN);
            insertServer.setObject(16, server.getEnforceSecure(), Types.BOOLEAN);
            insertServer.setObject(17, server.getWhitelist(), Types.BOOLEAN);
            insertServer.setObject(18, server.getCracked(), Types.BOOLEAN);
            insertServer.setObject(19, server.getMaxPlayers(), Types.INTEGER);
            insertServer.setObject(20, server.getOnlinePlayers(), Types.INTEGER);
            insertServer.executeUpdate();
            insertServer.close();

            // Add players, update LastSeen and Name (Potential name change) if duplicate
            if (!server.getPlayers().isEmpty()) {
                PreparedStatement updatePlayers = conn.prepareStatement("INSERT INTO PlayerHistory (Address, Port, PlayerUUID, PlayerName, FirstSeen, LastSeen) VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (Address, Port, PlayerUUID) DO UPDATE SET " +
                        "LastSeen = EXCLUDED.LastSeen," +
                        "PlayerName = EXCLUDED.PlayerName");

                // Constants
                updatePlayers.setString(1, address);
                updatePlayers.setShort(2, port);

                for (Player player : server.getPlayers()) {
                    updatePlayers.setString(3, player.uuid());
                    updatePlayers.setString(4, player.name());
                    updatePlayers.setLong(5, player.timestamp());
                    updatePlayers.setLong(6, player.timestamp());
                    updatePlayers.addBatch();
                }

                // Execute and close
                updatePlayers.executeBatch();
                updatePlayers.close();
            }

            // Add mods, do nothing if duplicate
            if (!server.getMods().isEmpty()) {
                PreparedStatement updateMods = conn.prepareStatement("INSERT INTO Mods (Address, Port, ModId, ModMarker) " +
                        "VALUES (?, ?, ?, ?)" +
                        "ON CONFLICT (Address, Port, ModId) DO NOTHING");

                // Constants
                updateMods.setString(1, address);
                updateMods.setShort(2, port);

                for (Mod mod : server.getMods()) {
                    updateMods.setString(3, mod.modId());
                    updateMods.setString(4, mod.modMarker());
                    updateMods.addBatch();
                }

                // Execute and close
                updateMods.executeBatch();
                updateMods.close();
            }
        } catch (SQLException e) {
            Main.logger.error("There was an error during a database transaction!", e);
        }
    }
}
