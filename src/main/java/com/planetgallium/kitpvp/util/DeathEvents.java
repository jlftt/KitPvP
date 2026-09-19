package com.planetgallium.kitpvp.util;

import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Fires a PlayerDeathEvent for a player who did not actually die, so that other plugins (death messages,
 * statistics, combat tag, Discord) still see the kill when Arena.InstantRespawn handles the death itself.
 *
 * The event constructor and the killer setter differ between server versions, so both are looked up once and
 * the feature reports itself unsupported when they are missing, letting the caller fall back to a real death.
 */
public final class DeathEvents {

	private static boolean resolved;
	private static Constructor<PlayerDeathEvent> deathEventConstructor;
	private static boolean constructorTakesDamageSource;
	private static Method setKillerMethod;

	private DeathEvents() {}

	public static boolean isSupported() {
		resolve();
		return deathEventConstructor != null;
	}

	private static void resolve() {
		if (resolved) {
			return;
		}
		resolved = true;

		try { // 1.20.5+
			deathEventConstructor = PlayerDeathEvent.class.getConstructor(Player.class, DamageSource.class,
					List.class, int.class, String.class);
			constructorTakesDamageSource = true;
		} catch (NoSuchMethodException | NoClassDefFoundError exception) {
			try { // older versions
				deathEventConstructor = PlayerDeathEvent.class.getConstructor(Player.class, List.class,
						int.class, String.class);
			} catch (NoSuchMethodException ignored) {
				Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cThis server version does not support notifying other " +
						"plugins of a death without a real death; InstantRespawn will let players die normally.");
			}
		}

		try { // Paper only; without it other plugins see a death with no killer
			setKillerMethod = Player.class.getMethod("setKiller", Player.class);
		} catch (NoSuchMethodException ignored) {}
	}

	/**
	 * Tells the rest of the server that the victim was killed. Returns false when the event could not be
	 * created, in which case nothing was fired.
	 */
	public static boolean callDeathEvent(Player victim, EntityDamageEvent lethalDamage, Player killer) {
		if (!isSupported()) {
			return false;
		}

		// So plugins that look at how the player died (death messages in particular) see the real cause
		victim.setLastDamageCause(lethalDamage);
		setKiller(victim, killer);

		List<ItemStack> drops = new ArrayList<>();

		try {
			PlayerDeathEvent deathEvent = constructorTakesDamageSource ?
					deathEventConstructor.newInstance(victim, lethalDamage.getDamageSource(), drops, 0, null) :
					deathEventConstructor.newInstance(victim, drops, 0, null);

			Bukkit.getPluginManager().callEvent(deathEvent);
			return true;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cFailed to notify other plugins of the death of " +
					victim.getName() + ": " + exception);
			return false;
		}
	}

	private static void setKiller(Player victim, Player killer) {
		if (setKillerMethod == null || killer == null) {
			return;
		}

		try {
			setKillerMethod.invoke(victim, killer);
		} catch (ReflectiveOperationException ignored) {}
	}

}
