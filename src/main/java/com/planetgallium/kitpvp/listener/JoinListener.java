package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.util.Resource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Toolkit;

public class JoinListener implements Listener {

	private final Game plugin;
	private final Arena arena;
	private final Resource config;
	
	public JoinListener(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.config = plugin.getResources().getConfig();
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();

		// Update checker
		if (plugin.needsUpdate()) {
			if (p.isOp()) {
				p.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aAn update was found: v" +
						plugin.getUpdateVersion() + " https://www.spigotmc.org/resources/27107/"));
			}
		}

		arena.getStats().createPlayer(p);

		if (p.getName().equals("cervinakuy")) {
			e.setJoinMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &7The Developer of &bKitPvP" +
					" &7has joined the server."));
		}

		enterArena(p);
	}
	
	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent e) {
		Player p = e.getPlayer();

		enterArena(p);
	}

	// A player with kp.arena.bypass (operators by default) keeps their inventory, effects and game mode
	// on entering the arena, so staff can come over from another server or world without losing anything
	private void enterArena(Player p) {
		if (!Toolkit.inArena(p) || p.hasPermission("kp.arena.bypass")) {
			return;
		}

		if (config.getBoolean("Arena.ClearInventoryOnJoin")) {
			p.getInventory().clear();
			p.getInventory().setArmorContents(null);
		}

		arena.addPlayer(p, config.getBoolean("Arena.ToSpawnOnJoin"),
				config.getBoolean("Arena.GiveItemsOnJoin"));
	}

}
