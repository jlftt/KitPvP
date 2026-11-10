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

		arena.getStats().createPlayer(p);

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

		// Arena.KitMenuOnJoin: the kit menu opens by itself, so a new player sees what to do first. Two seconds
		// later: a Bedrock client is still loading the world at join and would drop the form.
		if (config.getBoolean("Arena.KitMenuOnJoin")) {
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
				if (p.isOnline() && Toolkit.inArena(p) && !arena.getKits().playerHasKit(p.getName())) {
					arena.getMenus().getKitMenu().open(p);
				}
			}, KIT_MENU_DELAY_TICKS);
		}
	}

	private static final long KIT_MENU_DELAY_TICKS = 40;

}
