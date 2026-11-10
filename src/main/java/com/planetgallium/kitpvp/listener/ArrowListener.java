package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.util.Resource;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Toolkit;

public class ArrowListener implements Listener {

	private final Game plugin;
	private final Resource config;

	public ArrowListener(Game plugin) {
		this.plugin = plugin;
		this.config = plugin.getResources().getConfig();
	}

	// MONITOR + ignoreCancelled: a hit that another check cancelled (spawn protection, no-kit protection,
	// WorldGuard) no longer gives an arrow back or a health message
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onArrowHit(EntityDamageByEntityEvent e) {
		if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Arrow)) return;
		if (!(((Arrow) e.getDamager()).getShooter() instanceof Player)) return;
		if (!Toolkit.inArena(e.getEntity())) return;

		Player damagedPlayer = (Player) e.getEntity();
		Player shooter = (Player) ((Arrow) e.getDamager()).getShooter();

		if (damagedPlayer.equals(shooter)) return; // self-hit

		doArrowHitMessageIfEnabled(shooter, damagedPlayer);
		doArrowReturnIfEnabled(shooter);
	}

	private void doArrowHitMessageIfEnabled(Player shooter, Player damagedPlayer) {
		if (!config.getBoolean("Combat.ArrowHit.Enabled") || !shooter.hasPermission("kp.arrowmessage")) return;

		// read 2 ticks later, once the damage has been applied
		plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			if (!shooter.isOnline() || !damagedPlayer.isOnline() || damagedPlayer.isDead()) return;

			double health = Math.round(damagedPlayer.getHealth() * 10.0) / 10.0;
			// compared with the kit's max health: a 40 HP kit at 20 HP was skipped, at 40 HP shown
			if (health < Toolkit.getMaxHealth(damagedPlayer)) {
				shooter.sendMessage(config.fetchString("Combat.ArrowHit.Message")
						.replace("%player%", damagedPlayer.getName())
						.replace("%health%", String.valueOf(health)));
			}
		}, 2L);
	}

	private void doArrowReturnIfEnabled(Player shooter) {
		if (!config.getBoolean("Combat.ArrowReturn.Enabled")) return;
		// the permission used to be checked only when an arrow stack had room, so everyone got arrows back
		if (!shooter.hasPermission("kp.arrowreturn")) return;

		int count = config.getInt("Combat.ArrowReturn.Count");
		if (count <= 0) return;

		// addItem tops up existing arrow stacks first, then empty slots; whatever is left had no room
		if (!shooter.getInventory().addItem(new ItemStack(Material.ARROW, count)).isEmpty()) {
			shooter.sendMessage(config.fetchString("Combat.ArrowReturn.NoSpace"));
		}
	}

}
