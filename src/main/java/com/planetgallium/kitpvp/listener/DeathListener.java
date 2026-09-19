package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.messages.Titles;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;

import java.util.List;

public class DeathListener implements Listener {

	private final Game plugin;
	private final Arena arena;
	private final Resources resources;
	private final Resource config;
	
	public DeathListener(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.resources = plugin.getResources();
		this.config = resources.getConfig();
	}

	/**
	 * With Arena.InstantRespawn the player never actually dies: the lethal hit is cancelled and a death event is
	 * fired for them instead, so there is no death screen, no death animation and no waiting, while the rest of
	 * the server (death messages, statistics, combat tag) still sees the kill. Everything else, including
	 * sending the player back to spawn, is done by onDeath below.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onLethalDamage(EntityDamageEvent e) {
		if (!config.getBoolean("Arena.InstantRespawn") || !(e.getEntity() instanceof Player)) {
			return;
		}

		Player victim = (Player) e.getEntity();

		if (!Toolkit.inArena(victim) || victim.getGameMode() == GameMode.SPECTATOR || victim.isDead()) {
			return;
		}

		if (victim.getHealth() - e.getFinalDamage() > 0) {
			return; // survivable
		}

		if (!DeathEvents.isSupported()) {
			return; // let the player die for real, onDeath then respawns them immediately
		}

		Player killer = resolveKiller(victim, e);

		e.setCancelled(true);

		// The lethal hit was cancelled, so it never reached the hit cache that onDeath falls back on
		if (killer != null && !killer.getName().equals(victim.getName())) {
			arena.getHitCache().put(victim.getName(), killer.getName());
		}

		if (!DeathEvents.callDeathEvent(victim, e, killer)) {
			e.setCancelled(false); // could not tell anyone, so let it be a normal death after all
		}
	}

	// The player that gets the kill: whoever dealt the lethal hit, or the last player who hit the victim
	private Player resolveKiller(Player victim, EntityDamageEvent lethalDamage) {
		if (lethalDamage instanceof EntityDamageByEntityEvent) {
			Entity damager = ((EntityDamageByEntityEvent) lethalDamage).getDamager();

			if (damager instanceof Player) {
				return (Player) damager;
			}

			if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
				return (Player) ((Projectile) damager).getShooter();
			}

			if (damager.getType() == EntityType.PRIMED_TNT && damager.getCustomName() != null) {
				return Toolkit.getPlayer(victim.getWorld(), damager.getCustomName());
			}
		}

		String lastHitterName = arena.getHitCache().get(victim.getName());
		return lastHitterName != null ? Toolkit.getPlayer(victim.getWorld(), lastHitterName) : null;
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		// investigate possible memory leak when FancyDeath is enabled
		if (Toolkit.inArena(e.getEntity())) {

			Player victim = e.getEntity();
			e.setDeathMessage("");

			if (config.getBoolean("Arena.PreventDeathDrops")) {
				e.getDrops().clear();
			}

			CacheManager.getPotionSwitcherUsers().remove(victim.getName());

			// Credit the kill and record the death before respawnPlayer, which clears the victim's hit cache
			// and pushes their stats to the database
			setDeathMessage(victim);

			arena.getStats().addToStat("deaths", victim.getName(), 1);
			arena.getStats().removeExperience(victim.getName(),
					resources.getLevels().getInt("Levels.Options.Experience-Taken-On-Death"));

			respawnPlayer(victim);

			if (config.getBoolean("Arena.DeathParticles")) {
				victim.getWorld().playEffect(victim.getLocation().add(0.0D, 1.0D, 0.0D), Effect.STEP_SOUND, 152);
			}

			Toolkit.runCommands(victim, config.getStringList("Death.Commands"), "%victim%", victim.getName());

			broadcast(victim.getWorld(),
					config.fetchString("Death.Sound.Sound"),
					config.getInt("Death.Sound.Pitch"));
		}

	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent e) {
		if (Toolkit.inArena(e.getPlayer())) {
			if (config.getBoolean("Arena.InstantRespawn") || !config.getBoolean("Arena.FancyDeath")) {
				Player p = e.getPlayer();

				// Respawning straight at the arena spawn avoids the teleport that would otherwise be seen
				Location spawnLocation = arena.getRandomSpawnLocation(p.getWorld().getName());
				if (spawnLocation != null) {
					e.setRespawnLocation(spawnLocation);
					return;
				}

				new BukkitRunnable() {
					@Override
					public void run() {
						arena.toSpawn(p, p.getWorld().getName());
					}
				}.runTaskLater(plugin, 1L);
			}
		}
	}

	private void respawnPlayer(Player victim) {
		if (!victim.isOnline()) {
			return;
		}

		if (config.getBoolean("Arena.InstantRespawn")) {
			instantlyRespawnPlayer(victim);
			return;
		}

		if (config.getBoolean("Arena.FancyDeath")) {
			Location deathLocation = victim.getLocation();

			new BukkitRunnable() {
				@Override
				public void run() {
					victim.spigot().respawn();
					victim.teleport(deathLocation);
					victim.setGameMode(GameMode.SPECTATOR);
				}
			}.runTaskLater(plugin, 1L);

			arena.removePlayer(victim);
			
			new BukkitRunnable() {
				int time = config.getInt("Death.Title.Time");

				@Override
				public void run() {
					if (time != 0) {
						Titles.sendTitle(victim, 0, 21, 0,
								config.fetchString("Death.Title.Title"),
								config.fetchString("Death.Title.Subtitle")
										.replace("%seconds%", String.valueOf(time)));
						Toolkit.playSoundToPlayer(victim, "UI_BUTTON_CLICK", 1);
						time--;
					} else {
						doClearInventoryOnRespawnIfEnabled(victim);

						arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));

						victim.sendMessage(config.fetchString("Death.Title.Message"));
						victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
						Toolkit.playSoundToPlayer(victim, "ENTITY_EXPERIENCE_ORB_PICKUP", 1);

						Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"), "none", "none");

						cancel();
					}
				}
			}.runTaskTimer(plugin, 0L, 20L);

		} else {
			arena.removePlayer(victim);
			doClearInventoryOnRespawnIfEnabled(victim);

			new BukkitRunnable() {
				@Override
				public void run() {
					arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));
					Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"),
							"none", "none");
				}
			}.runTaskLater(plugin, 1L);
		}
	}

	/**
	 * Sends the player straight back into the fight: no death screen, no spectator mode and no countdown.
	 * The respawn happens on the next tick because a player cannot be respawned from inside the death event, and
	 * onRespawn already respawns them at an arena spawn.
	 */
	private void instantlyRespawnPlayer(Player victim) {
		arena.removePlayer(victim);
		doClearInventoryOnRespawnIfEnabled(victim);

		if (!victim.isDead()) {
			// The hit that would have killed them was cancelled, so they can go back to spawn right now
			victim.setFireTicks(0);
			victim.setVelocity(new Vector(0, 0, 0));
			victim.setNoDamageTicks(20); // a second of protection so they are not killed on arrival

			arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));
			victim.setHealth(Toolkit.getMaxHealth(victim));

			Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"), "none", "none");
			return;
		}

		new BukkitRunnable() {
			@Override
			public void run() {
				if (!victim.isOnline()) {
					return;
				}

				if (victim.isDead()) {
					victim.spigot().respawn();
				}

				arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));
				Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"), "none", "none");
			}
		}.runTaskLater(plugin, 1L);
	}

	private void doClearInventoryOnRespawnIfEnabled(Player victim) {
		if (config.getBoolean("Arena.ClearInventoryOnRespawn")) {
			victim.getInventory().clear();
			victim.getInventory().setArmorContents(null);
			victim.setItemOnCursor(null);
			victim.getOpenInventory().getTopInventory().clear();
		}
	}

	private void setDeathMessage(Player victim) {
		if (victim.getLastDamageCause() == null) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Unknown"));
			return;
		}

		DamageCause cause = victim.getLastDamageCause().getCause();

		if (cause == DamageCause.PROJECTILE && getShooter(victim.getLastDamageCause()) instanceof Player) {
			Player killer = (Player) getShooter(victim.getLastDamageCause());

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Shot"));
			creditWithKill(victim, killer);

//		} else if (cause == DamageCause.ENTITY_ATTACK) {
//
//			broadcast(victim.getWorld(), config.fetchString("Death.Messages.Player").replace("%victim%", victim.getName()).replace("%killer%", victim.getKiller().getName()));
//			creditWithKill(victim, victim.getKiller());

		} else if (victim.getKiller() != null) {
			Player killer = victim.getKiller();

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));
			creditWithKill(victim, killer);

		} else if (arena.getHitCache().get(victim.getName()) != null) {
			String killerName = arena.getHitCache().get(victim.getName());
			Player killer = Toolkit.getPlayer(victim.getWorld(), killerName);

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));
			creditWithKill(victim, killer);

		} else if ((cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) &&
				getExplodedEntity(victim.getLastDamageCause()) != null &&
				getExplodedEntity(victim.getLastDamageCause()).getType() == EntityType.PRIMED_TNT) {
			String bomberName = getExplodedEntity(victim.getLastDamageCause()).getCustomName();
			Player killer = Toolkit.getPlayer(victim.getWorld(), bomberName);

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));
			creditWithKill(victim, killer);

		} else if (cause == DamageCause.VOID) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Void"));

		} else if (cause == DamageCause.FALL) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Fall"));

		} else if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK || cause == DamageCause.LAVA) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Fire"));

		} else if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Explosion"));

		} else {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Unknown"));

		}
	}

	// null when the projectile was fired by a dispenser or the damage did not come from a projectile
	private Entity getShooter(EntityDamageEvent e) {
		if (e instanceof EntityDamageByEntityEvent) {
			Entity damager = ((EntityDamageByEntityEvent) e).getDamager();
			if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Entity) {
				return (Entity) ((Projectile) damager).getShooter();
			}
		}
		return null;
	}

	// null for block explosions (beds, respawn anchors), which are not caused by an entity
	private Entity getExplodedEntity(EntityDamageEvent e) {
		if (e instanceof EntityDamageByEntityEvent) {
			return ((EntityDamageByEntityEvent) e).getDamager();
		}
		return null;
	}

	private void creditWithKill(Player victim, Player killer) {
		if (victim != null && killer != null) {
			if (!victim.getName().equals(killer.getName())) {
				arena.getStats().addToStat("kills", killer.getName(), 1);
				arena.getStats().addExperience(killer, resources.getLevels().getInt("Levels.Options.Experience-Given-On-Kill"));

				List<String> killCommands = config.getStringList("Kill.Commands");
				killCommands = Toolkit.replaceInList(killCommands, "%victim%", victim.getName());
				Toolkit.runCommands(killer, killCommands, "%killer%", killer.getName());

				if (resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")) {
					new BukkitRunnable() {
						@Override
						public void run() {
							arena.updateScoreboards(killer, false);
						}
					}.runTaskLater(plugin, 20L);
				}
			}
		}
	}

	private String getDeathMessage(Player victim, Player killer, String type) {
		String deathMessage = config.fetchString("Death.Messages." + type);

		if (victim != null && killer != null) {
			if (victim.getName().equals(killer.getName())) {
				deathMessage = config.fetchString("Death.Messages.Suicide");
			}
		}

		if (killer != null) {
			deathMessage = deathMessage.replace("%killer%", killer.getName())
					.replace("%killer_health%", String.valueOf(Toolkit.round(killer.getHealth(), 2)));
		} else {
			deathMessage = config.fetchString("Death.Messages.Unknown"); // if killer is null (left the server, or some other unknown reason)
		}

		if (victim != null) {
			deathMessage = deathMessage.replace("%victim%", victim.getName());
		}

		return deathMessage;
	}

	private void broadcast(World world, String message) {
		if (config.getBoolean("Death.Messages.Enabled")) {
			for (Player all : world.getPlayers()) {
				all.sendMessage(Toolkit.translate(message));
			}
		}
	}

	private void broadcast(World world, String soundName, int pitch) {
		if (config.getBoolean("Death.Sound.Enabled")) {
			for (Player all : world.getPlayers()) {
				Toolkit.playSoundToPlayer(all, soundName, pitch);
			}
		}
	}

}
