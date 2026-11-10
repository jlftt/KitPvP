package com.planetgallium.kitpvp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.planetgallium.kitpvp.Game;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.potion.PotionEffectType;

public class Toolkit {

	private static final Material FALLBACK_MATERIAL = Material.BEDROCK;

	// Material names from the configs, resolved once: XMaterial's lookup runs on every click otherwise
	private static final Map<String, Material> materialCache = new ConcurrentHashMap<>();

	// The missing-spawn warning used to print on every move and click until a spawn was set
	private static boolean warnedNoArena;

	public static boolean inArena(World world) {
		Resource config = Game.getInstance().getResources().getConfig();
		if (config.contains("Arenas")) {
			return config.contains("Arenas." + world.getName());
		}
		if (!warnedNoArena) {
			warnedNoArena = true;
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cThere was no spawn found, please set it using /kp addspawn.");
		}
		return false;
	}
	
	public static boolean inArena(Entity entity) {
		return inArena(entity.getWorld());
	}
	
	/**
	 * The closest other player below maxY that this player can see, as {name, distance}; null if none.
	 */
	public static String[] getNearestPlayer(Player player, int maxY) {
		Location location = player.getLocation();
		Player nearest = null;
		double nearestDistanceSquared = Double.MAX_VALUE;

		for (Player other : player.getWorld().getPlayers()) {
			if (other == player || other.getGameMode() == GameMode.SPECTATOR || !player.canSee(other)) {
				continue;
			}
			Location otherLocation = other.getLocation();
			if (otherLocation.getBlockY() >= maxY) {
				continue;
			}
			double distanceSquared = location.distanceSquared(otherLocation);
			if (distanceSquared < nearestDistanceSquared) {
				nearest = other;
				nearestDistanceSquared = distanceSquared;
			}
		}

		if (nearest == null) {
			return null;
		}
		return new String[] {nearest.getName(), String.valueOf(Math.sqrt(nearestDistanceSquared))};
	}
 	
 	public static double round(double value, int precision) {
 	    int scale = (int) Math.pow(10, precision);
 	    return (double) Math.round(value * scale) / scale;
 	}
 	
 	public static Color getColorFromConfig(FileConfiguration config, String path) {
 		return Color.fromRGB(config.getInt(path + ".Red"),
				config.getInt(path + ".Green"),
				config.getInt(path + ".Blue"));
 	}

 	public static void runCommands(Player p, List<String> commands, String replaceFrom, String replaceTo) {
		if (commands == null) return;

		for (String commandString : commands) {
			String[] commandPhrase = commandString.split(":", 2);

			if (commandPhrase.length == 1) {
				Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cIncorrect command format, skipped: &f" + commandString +
						" &c(see &fhttps://bit.ly/kp-command-format&c)");
				continue;
			}

			commandPhrase[1] = commandPhrase[1].trim();

			String sender = commandPhrase[0];
			String command = commandPhrase[1];

			if (!sender.equals("console") && !sender.equals("player")) {
				Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cIncorrect command format, skipped: &f" + commandString +
						" &c(see &fhttps://bit.ly/kp-command-format&c)");
				continue;
			}

			// A command that throws used to end the caller there: an ability went without its cooldown,
			// and a death without the respawn that follows the kill commands
			try {
				String commandToRun = replaceCommandPlaceholders(command, p, replaceFrom, replaceTo);
				if (sender.equals("console")) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
				} else {
					p.performCommand(commandToRun);
				}
			} catch (RuntimeException exception) {
				Throwable cause = exception;
				while (cause.getCause() != null) {
					cause = cause.getCause();
				}
				Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cCommand failed, skipped: &f" + command +
						" &c(" + cause + ")");
			}
		}
	}

	private static String replaceCommandPlaceholders(String s, Player p, String replaceFrom, String replaceTo) {
		String withPotentialPapiPlaceholders = addPlaceholdersIfPossible(p, s);
		return withPotentialPapiPlaceholders
				.replace("%player%", p.getName())
				.replace("%uuid%", p.getUniqueId().toString())
				.replace(replaceFrom, replaceTo);
	}
 	
 	
 	public static List<String> colorizeList(List<String> list) {
 		List<String> newList = new ArrayList<>();

 		if (list != null) {
			for (String string : list) {
				newList.add(ChatColor.translateAlternateColorCodes('&', string));
			}
			return newList;
		}
 		return null;
 	}

 	public static List<String> replaceInList(List<String> list, String find, String replace) {
		List<String> newList = new ArrayList<>();

		for (String string : list) {
			newList.add(string.replace(find, replace));
		}
		return newList;
	}

 	public static String toNormalColorCodes(String string) {
		if (string != null) {
			return string.replace("§", "&");
		}
		return null;
	}

	public static List<String> toNormalColorCodes(List<String> list) {
		List<String> newList = new ArrayList<>();

		if (list != null) {
			for (String line : list) {
				newList.add(toNormalColorCodes(line));
			}
			return newList;
		}
		return null;
	}

 	public static Player getPlayer(World world, String name) {
		for (Player player : world.getPlayers()) {
			if (player.getName().equals(name)) {
				return player;
			}
		}
		return null;
	}

	public static void saveLocationToResource(Resource resource, String path, Location location) {
		resource.set(path + ".World", location.getWorld().getName());
		resource.set(path + ".X", location.getBlockX());
		resource.set(path + ".Y", location.getBlockY());
		resource.set(path + ".Z", location.getBlockZ());
		resource.set(path + ".Yaw", location.getYaw());
		resource.set(path + ".Pitch", location.getPitch());
		resource.save();
	}

	public static Location getLocationFromResource(Resource resource, String path) {
		return new Location(Bukkit.getWorld(resource.fetchString(path + ".World")),
				(float) resource.getInt(path + ".X") + 0.5,
				(float) resource.getInt(path + ".Y"),
				(float) resource.getInt(path + ".Z") + 0.5,
				(float) resource.getDouble(path + ".Yaw"),
				(float) resource.getDouble(path + ".Pitch"));
	}

	public static String addPlaceholdersIfPossible(Player player, String text) {
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			text = PlaceholderAPI.setPlaceholders(player, text);
		}
		return Toolkit.translate(text);
	}

	// Highest numeric value among granted "prefix.<number>" permissions; ignores negated ones and non-numbers
	// such as "prefix.*", which used to throw NumberFormatException
	public static double getPermissionAmountDouble(Player player, String permissionPrefix, double defaultValue) {
		double highestPermissionValue = 0.0;
		for (PermissionAttachmentInfo attachmentInfo : player.getEffectivePermissions()) {
			String permission = attachmentInfo.getPermission();
			if (attachmentInfo.getValue() && permission.startsWith(permissionPrefix)) {
				try {
					// strips from some.permission.49 to just 49
					double permissionValue = Double.parseDouble(permission.substring(permissionPrefix.length()));
					if (permissionValue > highestPermissionValue) {
						highestPermissionValue = permissionValue;
					}
				} catch (NumberFormatException ignored) {}
			}
		}

		return highestPermissionValue == 0.0 ? defaultValue : highestPermissionValue;

	}

	// Bedrock players connected through Floodgate/Geyser get a UUID whose most significant bits are zero.
	// Their client cannot tell left and right clicks apart in a Java menu, so menus treat their clicks differently.
	public static boolean isBedrockPlayer(Player player) {
		return player.getUniqueId().getMostSignificantBits() == 0;
	}

	// Kit and ability permissions (kp.kit.*, kp.ability.*) are skipped when Other.RequireKitPermissions is false
	public static boolean hasKitPermission(Player player, String permission) {
		Resource config = Game.getInstance().getResources().getConfig();
		if (config.contains("Other.RequireKitPermissions") && !config.getBoolean("Other.RequireKitPermissions")) {
			return true;
		}
		return player.hasPermission(permission);
	}

	public static int getPermissionAmount(Player player, String permissionPrefix, int defaultValue) {
//		String permissionPrefix = "some.permission.here.";
		if (player.isOp()) {
			return Integer.MAX_VALUE;
		}

		double highestPermissionValue = getPermissionAmountDouble(player, permissionPrefix, defaultValue);
		return (int) highestPermissionValue;
	}

	public static String translate(String s) {
		return ChatColor.translateAlternateColorCodes('&', s.replace("%prefix%", Game.getPrefix()));
	}

	public static int getNextAvailable(FileConfiguration yamlConfig, String path, int limit, boolean zeroBased,
									   int fallbackAmount) {
		for (int i = zeroBased ? 0 : 1; i < limit; i++) {
			if (!yamlConfig.contains(path + "." + i)) {
				return i;
			}
		}
		return fallbackAmount;
	}

	public static boolean containsAnyThatStartWith(List<String> list, String valueToTest) {
		for (String string : list) {
			if (valueToTest.startsWith(string)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasMatchingDisplayName(ItemStack item, String targetDisplayName) {
		ItemMeta meta = item.getItemMeta();

		if (item.hasItemMeta() && meta != null) {
			return meta.hasDisplayName() && Toolkit.translate(meta.getDisplayName()).equals(targetDisplayName);
		}
		return false;
	}

	public static boolean hasMatchingMaterial(ItemStack item, String targetMaterial) {
		return item.getType() == Toolkit.safeMaterial(targetMaterial);
	}

	public static boolean matchesConfigItem(ItemStack item, Resource resource, String path) {
		ItemMeta itemMeta = item.getItemMeta();
		String configItemName = resource.fetchString(path + ".Name");
		String configItemMaterial = resource.fetchString(path + ".Material");

		if (Toolkit.hasMatchingMaterial(item, configItemMaterial)) {
			if (itemMeta != null && itemMeta.hasDisplayName()) {
				return Toolkit.translate(itemMeta.getDisplayName()).equals(configItemName);
			}
		}
		return false;
	}

	public static void printToConsole(String message) {
		Bukkit.getConsoleSender().sendMessage(Toolkit.translate(message));
	}

	public static String capitalizeFirstChar(String word) {
		return word.substring(0, 1).toUpperCase() + word.substring(1);
	}

	public static void setMaxHealth(Player p, int amount) {
		AttributeInstance healthAttribute = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
		if (healthAttribute != null) {
			healthAttribute.setBaseValue(amount);
		}
	}

	public static int getMaxHealth(Player p) {
		AttributeInstance healthAttribute = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
		return healthAttribute != null ? (int) healthAttribute.getValue() : 20;
	}

	public static Material safeMaterial(String materialName) {
		if (materialName == null) {
			printToConsole(String.format("&7[&b&lKIT-PVP&7] &cMissing material, defaulting to [%s].", FALLBACK_MATERIAL));
			return FALLBACK_MATERIAL;
		}
		// An unknown name is cached too, so its warning prints once
		return materialCache.computeIfAbsent(materialName, Toolkit::resolveMaterial);
	}

	private static Material resolveMaterial(String materialName) {
		// 1. Use XMaterial first
		Optional<XMaterial> materialOptional = XMaterial.matchXMaterial(materialName);
		if (materialOptional.isPresent()) {
			Material material = materialOptional.get().parseMaterial();
			if (material != null) {
				return material;
			}
		}

		// 2. XMaterial fails, try native check before returning fallbackMaterial (forward-compatible)
		Material potentialNativeMaterial = Material.getMaterial(materialName);
		if (potentialNativeMaterial != null) {
			return potentialNativeMaterial;
		}

		// 3. Native check fails; return fallbackMaterial
		printToConsole(String.format("&7[&b&lKIT-PVP&7] &cUnknown material [%s], defaulting to [%s].",
				materialName, FALLBACK_MATERIAL));
		return FALLBACK_MATERIAL;
	}

	public static ItemStack safeItemStack(String materialName) {
		return new ItemStack(Toolkit.safeMaterial(materialName));
	}

	public static Sound safeSound(String soundName) {
		Optional<XSound> soundOptional = XSound.matchXSound(soundName);
		if (soundOptional.isPresent()) {
			Sound sound = soundOptional.get().parseSound();
			if (sound != null) {
				return sound;
			}
		}
		printToConsole("&7[&b&lKIT-PVP&7] &cInvalid sound: " + soundName);
		return XSound.ENTITY_ENDER_DRAGON_HURT.parseSound();
	}

	public static PotionEffectType safePotionEffectType(String potionEffectTypeName) {
		Optional<XPotion> potionOptional = XPotion.matchXPotion(potionEffectTypeName);
		if (potionOptional.isPresent()) {
			PotionEffectType potionEffectType = potionOptional.get().getPotionEffectType();
			if (potionEffectType != null) {
				return potionEffectType;
			}
		}
		printToConsole("&7[&b&lKIT-PVP&7] &cInvalid potion effect type: " + potionEffectTypeName);
		return XPotion.BLINDNESS.getPotionEffectType();
	}

	public static ItemStack getHandItemForInteraction(Event e) {
		if (e instanceof PlayerInteractEvent) {
			PlayerInteractEvent interactEvent = (PlayerInteractEvent) e;
			return interactEvent.getPlayer().getInventory().getItem(interactEvent.getHand() != null ?
					interactEvent.getHand() : EquipmentSlot.HAND);
		} else if (e instanceof PlayerInteractEntityEvent) {
			PlayerInteractEntityEvent interactEntityEvent = (PlayerInteractEntityEvent) e;
			// no null check needed, there is always a hand with this event
			return interactEntityEvent.getPlayer().getInventory().getItem(interactEntityEvent.getHand());
		}
		return new ItemStack(Material.AIR);
	}

	public static void setHandItemForInteraction(Event e, ItemStack item) {
		if (e instanceof PlayerInteractEvent) {
			PlayerInteractEvent interactEvent = (PlayerInteractEvent) e;
			interactEvent.getPlayer().getInventory().setItem(interactEvent.getHand() != null ?
					interactEvent.getHand() : EquipmentSlot.HAND, item);
		} else if (e instanceof PlayerInteractEntityEvent) {
			PlayerInteractEntityEvent interactEntityEvent = (PlayerInteractEntityEvent) e;
			interactEntityEvent.getPlayer().getInventory().setItem(interactEntityEvent.getHand(), item);
		}
	}

	public static boolean eitherHandHasMaterial(Player p, String materialString) {
		Material materialToFind = safeMaterial(materialString);
		return p.getInventory().getItemInMainHand().getType() == materialToFind ||
				p.getInventory().getItemInOffHand().getType() == materialToFind;
	}

	public static void playSoundToPlayer(Player p, String soundName, int pitch) {
		p.playSound(p.getLocation(), Toolkit.safeSound(soundName), 1, pitch);
	}

	public static SlotWrapper getSlotUsedForInteraction(PlayerInteractEvent e) {
		if (e.getHand() == EquipmentSlot.OFF_HAND) {
			return new SlotWrapper(e.getHand());
		}
		return new SlotWrapper(e.getPlayer().getInventory().getHeldItemSlot());
	}

	public static void appendToLore(ItemStack item, String lineToAppend) {
		ItemMeta itemMeta = item.getItemMeta();
		if (itemMeta != null) {
			List<String> newLore = itemMeta.hasLore() ? itemMeta.getLore() : new ArrayList<>();
			newLore.add(lineToAppend);

			itemMeta.setLore(newLore);

			item.setItemMeta(itemMeta);
		}
	}

	public static boolean singleLineLoreMatches(ItemStack item, String expectedLore) {
		ItemMeta itemMeta = item.getItemMeta();
		if (itemMeta != null) {
			return itemMeta.hasLore() && itemMeta.getLore() != null && itemMeta.getLore().get(0).equals(expectedLore);
		}
		return false;
	}

	public static boolean isPluginEnabled(String pluginName) {
		return Bukkit.getPluginManager().isPluginEnabled(pluginName);
	}

	public static class SlotWrapper {

		private boolean usesEquipmentSlot = false;
		private int heldItemSlot = 0;
		private EquipmentSlot equipmentSlot;

		public SlotWrapper(int heldItemSlot) {
			this.heldItemSlot = heldItemSlot;
		}

		public SlotWrapper(EquipmentSlot equipmentSlot) {
			this.usesEquipmentSlot = true;
			this.equipmentSlot = equipmentSlot;
		}

		// Falls back to adding the item to the inventory if the slot was filled in the meantime
		public void placeItemInSlot(Player p, ItemStack item) {
			ItemStack current = usesEquipmentSlot ? p.getInventory().getItem(equipmentSlot) :
					p.getInventory().getItem(heldItemSlot);
			if (current != null && current.getType() != Material.AIR) {
				p.getInventory().addItem(item);
				return;
			}

			if (usesEquipmentSlot) {
				p.getInventory().setItem(equipmentSlot, item);
			} else {
				p.getInventory().setItem(heldItemSlot, item);
			}
		}

	}

	/** Seconds from a config to ticks; -1 stays -1, an infinite effect. */
	public static int parsePotionEffectDuration(int duration) {
		return duration == -1 ? -1 : duration * 20;
	}

	/** A whole number of at least 0, or -1 when the text is not one (letters, sign, too big). */
	public static int parseNonNegativeInt(String text) {
		if (text == null || text.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c < '0' || c > '9') {
				return -1;
			}
		}
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException tooBig) {
			return -1;
		}
	}

}
