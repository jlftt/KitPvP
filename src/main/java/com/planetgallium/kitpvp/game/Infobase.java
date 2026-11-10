package com.planetgallium.kitpvp.game;

import com.planetgallium.database.*;
import com.planetgallium.database.Record;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Infobase {

    private final Game plugin;
    private final Resources resources;

    private final Database database;
    private final Table statsTable;

    private final static int UUID_MAX_CHARACTERS = 36;
    private final static int USERNAME_MAX_CHARACTERS = 16;

    public Infobase(Game plugin) {
        this.plugin = plugin;
        this.resources = plugin.getResources();
        this.database = setupDatabase(resources.getConfig());

        // Stats
        this.statsTable = database.createTable("stats", new Record(
                new Field("uuid", DataType.FIXED_STRING, Infobase.UUID_MAX_CHARACTERS),
                new Field("username", DataType.STRING, Infobase.USERNAME_MAX_CHARACTERS),
                new Field("kills", DataType.INTEGER),
                new Field("deaths", DataType.INTEGER),
                new Field("experience", DataType.INTEGER),
                new Field("level", DataType.INTEGER)));

        // Kit Cooldowns (hidden files are already left out of the list)
        for (String kitName : resources.getPluginDirectoryFiles("kits", false)) {
            addKitCooldownTable(kitName);
        }
    }

    private Database setupDatabase(Resource config) {
        Toolkit.printToConsole("&7[&b&lKIT-PVP&7] Establishing database connection...");

        // Set database to MySQL, or in any other case, SQLite
        if (config.fetchString("Storage.Type").equalsIgnoreCase("mysql")) {
            String host = config.fetchString("Storage.MySQL.Host");
            int port = config.getInt("Storage.MySQL.Port");
            String databaseName = config.fetchString("Storage.MySQL.Database");
            String username = config.fetchString("Storage.MySQL.Username");
            String password = config.fetchString("Storage.MySQL.Password");

            return new Database(host, port, databaseName, username, password);
        } else {
            return new Database("plugins/KitPvP/storage.db");
        }
    }

    public boolean isPlayerRegistered(String username) {
        String uuid = usernameToUUID(username);
        return uuid != null && tableContainsUUID("stats", uuid);
    }

    public void registerPlayerStats(Player p) {
        if (!verifyTableExists("stats")) {
            return;
        }

        Field uuidField = uuidField("uuid", p.getUniqueId().toString());
        Record playerRecord = statsTable.getRecord(uuidField);

        if (playerRecord == null) {
            Record statsRecord = new Record(
                    uuidField,
                    new Field("username", DataType.STRING, p.getName(), Infobase.USERNAME_MAX_CHARACTERS),
                    new Field("kills", DataType.INTEGER, 0),
                    new Field("deaths", DataType.INTEGER, 0),
                    new Field("experience", DataType.INTEGER, 0),
                    new Field("level", DataType.INTEGER,
                            resources.getLevels().getInt("Levels.Options.Minimum-Level")));
            statsTable.insertRecord(statsRecord);
            playerRecord = statsRecord; // same values as the row just written, no need to read it back
        } else {
            // check if stored username needs changing if a player changed their username
            String storedUsername = (String) playerRecord.getFieldValue("username");
            String currentUsername = p.getName();

            if (!currentUsername.equals(storedUsername)) {
                Field updatedUsernameField = new Field("username", DataType.STRING, p.getName(),
                        Infobase.USERNAME_MAX_CHARACTERS);
                statsTable.updateRecord(uuidField, updatedUsernameField);
            }
        }

        // put stats into stats cache
        CacheManager.getStatsCache().put(p.getName(), recordToPlayerData(playerRecord));
    }

    public String usernameToUUID(String username) {
        if (CacheManager.getUUIDCache().containsKey(username)) {
            return CacheManager.getUUIDCache().get(username);
        }

        // The name may differ from the one cached on join (e.g. Floodgate prefixes Bedrock names)
        Player onlinePlayer = Bukkit.getPlayerExact(username);
        if (onlinePlayer != null) {
            String uuid = onlinePlayer.getUniqueId().toString();
            CacheManager.getUUIDCache().put(username, uuid);
            return uuid;
        }

        if (verifyTableExists("stats")) {
            Table stats = database.getTable("stats");

            List<Record> matchingRecords = stats.searchRecords(usernameField(username));
            if (matchingRecords.size() == 1) {
                Record matchingRecord = matchingRecords.get(0);

                String uuid = (String) matchingRecord.getFieldValue("uuid");
                if (uuid != null) {
                    CacheManager.getUUIDCache().put(username, uuid);
                }
                return uuid;
            }
        }
        return null;
    }

    private boolean tableContainsUUID(String tableName, String uuid) {
        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);
            return table.getRecord(uuidField("uuid", uuid)) != null;
        }
        return false;
    }

    public List<TopEntry> getTopNStats(String identifier, int n) {
        if (verifyTableExists("stats")) {
            Table table = database.getTable("stats");

            return table.getTopN(new Field(identifier, DataType.INTEGER),
                    new Field("username", DataType.FIXED_STRING, Infobase.USERNAME_MAX_CHARACTERS), n);
        }
        return new ArrayList<>();
    }

    public void addKitCooldownTable(String kitName) {
        database.createTable(kitName + "_cooldowns", new Record(
                        new Field("uuid", DataType.FIXED_STRING, Infobase.UUID_MAX_CHARACTERS),
                        new Field("last_used", DataType.INTEGER)));
    }

    public void deleteKitCooldownTable(String kitName) {
        String kitCooldownTableName = kitName + "_cooldowns";
        if (verifyTableExists(kitCooldownTableName)) {
            database.deleteTable(kitCooldownTableName);
        }
        cleanupUnusedKitCooldownTables();
    }

    // Drops the cooldown tables of kits whose file is gone (deleteTable takes them out of the map, hence the copy)
    private void cleanupUnusedKitCooldownTables() {
        for (String tableName : new ArrayList<>(database.getTables().keySet())) {
            if (tableName.endsWith("_cooldowns")) {
                String kitName = tableName.substring(0, tableName.length() - "_cooldowns".length());
                if (!plugin.getArena().getKits().isKit(kitName)) {
                    database.deleteTable(tableName);
                }
            }
        }
    }

    public void setStatsData(String username, PlayerData playerData) {
        if (usernameToUUID(username) == null) {
            plugin.getLogger().warning("Failed to save stats; unknown player " + username);
            return;
        }

        if (verifyTableExists("stats")) {
            Table statsTable = database.getTable("stats");
            Field uuidField = uuidField("username", username);

            List<Field> fieldsToUpdate = new ArrayList<>();
            for (String statIdentifier : playerData.getData().keySet()) {
                Field statField = new Field(statIdentifier, DataType.INTEGER, playerData.getData(statIdentifier));
                fieldsToUpdate.add(statField);
            }

            // by doing this, it updates a whole stat row with one single SQL statement
            statsTable.updateRecord(uuidField, fieldsToUpdate.toArray(new Field[0]));
        }

        // Cooldowns
        if (!playerData.getKitCooldowns().isEmpty()) {
            Field uuidField = uuidField("username", username);
            for (Map.Entry<String, Long> entry : playerData.getKitCooldowns().entrySet()) {
                String kitCooldownTableName = entry.getKey() + "_cooldowns";
                if (verifyTableExists(kitCooldownTableName)) {
                    Record cooldownRecord = new Record(uuidField,
                            new Field("last_used", DataType.INTEGER, entry.getValue()));
                    database.getTable(kitCooldownTableName).updateOrInsertRecord(cooldownRecord);
                }
            }
        }
    }

    public Object getData(String tableName, String identifier, String username) {
        if (usernameToUUID(username) == null) {
            return null;
        }

        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);
            Record record = table.getRecord(uuidField("username", username));

            // no row is normal here: a player who never took a kit has no row in its cooldown table
            if (record != null) {
                return record.getFieldValue(identifier);
            }
        }
        return null;
    }

    public PlayerData recordToPlayerData(Record playerRecord) {
        PlayerData playerData = new PlayerData(-1, -1, -1, -1);
        for (String statIdentifier : playerData.getData().keySet()) {
            playerData.setData(statIdentifier, (int) playerRecord.getFieldValue(statIdentifier));
        }
        return playerData;
    }

    public PlayerData getStatsData(String username) {
        PlayerData playerData = new PlayerData(-1, -1, -1, -1);
        if (verifyTableExists("stats")) {
            Table statsTable = database.getTable("stats");

            if (usernameToUUID(username) == null) {
                return playerData;
            }

            Record playerRecord = statsTable.getRecord(uuidField("username", username));
            if (playerRecord == null) {
                return playerData;
            }
            return recordToPlayerData(playerRecord);
        }
        return playerData;
    }

    private boolean verifyTableExists(String tableName) {
        if (database.getTable(tableName) != null) {
            return true;
        }
        plugin.getLogger().warning("Database table " + tableName + " does not exist");
        return false;
    }

    private Field uuidField(String flag, String value) {
        if (flag.equalsIgnoreCase("username")) {
            return new Field("uuid", DataType.FIXED_STRING, usernameToUUID(value),
                    Infobase.UUID_MAX_CHARACTERS);
        } else if (flag.equalsIgnoreCase("uuid")) {
            return new Field("uuid", DataType.FIXED_STRING, value,
                    Infobase.UUID_MAX_CHARACTERS);
        }
        return null;
    }

    private Field usernameField(String value) {
        return new Field("username", DataType.STRING, value, Infobase.USERNAME_MAX_CHARACTERS);
    }

}
