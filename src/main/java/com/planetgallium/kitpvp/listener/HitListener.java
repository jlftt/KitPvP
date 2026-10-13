package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XSound;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import com.planetgallium.kitpvp.util.Toolkit;

public class HitListener implements Listener {

	private final Arena arena;
	private final Resource config;
	private final XSound.Record hitSound;

	public HitListener(Game plugin) {
		this.arena = plugin.getArena();
		this.config = plugin.getResources().getConfig();

		String soundString = config.fetchString("Combat.HitSound.Sound") + ", 1, " + config.getInt("Combat.HitSound.Pitch");
		this.hitSound = XSound.parse(soundString);
	}

	// Only hits that actually landed count towards kill credit (not ones blocked by a no-PvP region, for example)
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent e) {
		if (!(e.getEntity() instanceof Player)) {
			return;
		}

		Player damagedPlayer = (Player) e.getEntity();
		Player damager = getAttackingPlayer(e.getDamager());

		if (damager == null || damager.equals(damagedPlayer) || !Toolkit.inArena(damagedPlayer)) {
			return;
		}

		arena.recordHit(damagedPlayer.getName(), damager.getName());

		// The sound is for melee hits, as before; a projectile has its own
		if (e.getDamager() instanceof Player && config.getBoolean("Combat.HitSound.Enabled")) {
			hitSound.soundPlayer().forPlayers(damagedPlayer, damager);
		}
	}

	/** The player behind a hit: the attacker, the shooter of a projectile, or whoever threw the TNT. */
	private Player getAttackingPlayer(Entity damager) {
		if (damager instanceof Player) {
			return (Player) damager;
		}
		if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
			return (Player) ((Projectile) damager).getShooter();
		}
		if (damager instanceof TNTPrimed && ((TNTPrimed) damager).getSource() instanceof Player) {
			return (Player) ((TNTPrimed) damager).getSource();
		}
		return null;
	}
	
}
