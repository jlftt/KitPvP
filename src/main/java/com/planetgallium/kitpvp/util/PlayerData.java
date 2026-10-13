package com.planetgallium.kitpvp.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlayerData {

    private final Map<String, Integer> data;
    private final Map<String, Long> kitCooldowns;
    // Kits the database holds no cooldown for, so it is asked once per kit rather than on every check
    private final Set<String> kitsWithoutStoredCooldown = new HashSet<>();

    public PlayerData(int kills, int deaths, int experience, int level) {
        this.data = new HashMap<>();
        this.kitCooldowns = new HashMap<>();

        data.put("kills", kills);
        data.put("deaths", deaths);
        data.put("experience", experience);
        data.put("level", level);
    }

    public void setData(String identifier, int value) {
        data.put(identifier, value);
    }

    public void addKitCooldown(String kitName, long timeKitLastUsed) { kitCooldowns.put(kitName, timeKitLastUsed); }

    public int getData(String identifier) {
        return data.get(identifier);
    }

    public long getTimeKitLastUsed(String kitName) { return kitCooldowns.get(kitName); }

    public Map<String, Long> getKitCooldowns() { return kitCooldowns; }

    public Map<String, Integer> getData() { return data; }

    public boolean hasNoStoredCooldown(String kitName) { return kitsWithoutStoredCooldown.contains(kitName); }

    public void setNoStoredCooldown(String kitName) { kitsWithoutStoredCooldown.add(kitName); }

    /**
     * The stats and kit cooldowns as they are now, for a save running on another thread while the
     * player goes on playing.
     */
    public PlayerData copy() {
        PlayerData copy = new PlayerData(-1, -1, -1, -1);
        copy.data.putAll(data);
        copy.kitCooldowns.putAll(kitCooldowns);
        return copy;
    }

}
