package com.planetgallium.kitpvp.util;

import com.planetgallium.database.TopEntry;
import com.planetgallium.kitpvp.Game;
import org.bukkit.entity.Player;
import com.planetgallium.kitpvp.game.Arena;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Placeholders extends PlaceholderExpansion {

	private final Arena arena;
	private final Map<String, String> placeholderAPItoBuiltIn;
	// placeholders are asked for on every scoreboard/tab refresh: a bad one is reported once, not each time
	private final Set<String> reportedIdentifiers = ConcurrentHashMap.newKeySet();
	
	public Placeholders(Game plugin) {
		this.arena = plugin.getArena();
		this.placeholderAPItoBuiltIn = new HashMap<>();

		placeholderAPItoBuiltIn.put("stats_kills", "%kills%");
		placeholderAPItoBuiltIn.put("stats_deaths", "%deaths%");
		placeholderAPItoBuiltIn.put("stats_kd", "%kd%");
		placeholderAPItoBuiltIn.put("stats_experience", "%xp%");
		placeholderAPItoBuiltIn.put("stats_level", "%level%");
		placeholderAPItoBuiltIn.put("player_killstreak", "%streak%");
		placeholderAPItoBuiltIn.put("player_kit", "%kit%");
		placeholderAPItoBuiltIn.put("max_level", "%max_level%");
		placeholderAPItoBuiltIn.put("max_xp", "%max_xp%");
		placeholderAPItoBuiltIn.put("level_prefix", "%level_prefix%");
	}
	
	@Override
	public String onPlaceholderRequest(Player p, @NotNull String identifier) {
		if (identifier.startsWith("top_")) {
			return handleLeaderboardPlaceholder(identifier);
		}

		if (p != null) {
			return translatePlaceholderAPIPlaceholders(identifier, p.getName());
		}
		return null;
	}

	private String handleLeaderboardPlaceholder(String identifier) {
		String[] queries = identifier.split("_");
		if (queries.length < 4) {
			return null; // not top_<type>_<player|amount>_<rank>
		}
		String topType = queries[1]; // ex: kills
		String topIdentifier = queries[2]; // ex: amount, player
		String possibleTopValue = queries[3]; // 1, 15
		int topValue = Toolkit.parseNonNegativeInt(possibleTopValue);
		if (topValue < 1) {
			reportOnce(identifier, "expected a rank number but received: " + possibleTopValue);
			topValue = 1; // use the top player
		}

		TopEntry entry = arena.getLeaderboards().getTopN(topType, topValue);
		boolean isUsernamePlaceholder = topIdentifier.equals("player");

		return isUsernamePlaceholder ? entry.getIdentifier() : String.valueOf(entry.getValue());
	}

	public String translatePlaceholderAPIPlaceholders(String placeholderAPIIdentifier, String username) {
		if (placeholderAPItoBuiltIn.containsKey(placeholderAPIIdentifier)) {
			String toBuiltInPlaceholder = placeholderAPItoBuiltIn.get(placeholderAPIIdentifier);
			return arena.getUtilities().replaceBuiltInPlaceholdersIfPresent(toBuiltInPlaceholder, username);
		} else {
			reportOnce(placeholderAPIIdentifier, "unknown placeholder identifier");
			return "invalid-placeholder";
		}
	}
	
	private void reportOnce(String identifier, String problem) {
		if (reportedIdentifiers.add(identifier)) {
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &c%kitpvp_" + identifier + "%: " + problem + ".");
		}
	}

	@Override
	public boolean canRegister() { return true; }

	@Override
	public boolean persist() { return true; }

	@Override
	public @NotNull String getAuthor() { return "Cervinakuy"; }
	
	@Override
	public @NotNull String getIdentifier() { return "kitpvp"; }
	
	@Override
	public @NotNull String getVersion() { return "1.0.0"; }
	
}
