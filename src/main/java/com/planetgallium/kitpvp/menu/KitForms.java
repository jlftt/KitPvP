package com.planetgallium.kitpvp.menu;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.floodgate.util.InputMode;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The kit menu as native Bedrock forms, for players on a touch screen.
 * <p>
 * A phone or tablet squeezes a large chest menu until its items no longer sit where they are drawn, and a Bedrock
 * client has a single kind of click, so the chest menu's right click (preview) cannot be reached at all. These
 * players get a list of the kits instead; picking one shows its lore with Select, Preview and Back. The buttons run
 * the same Left-Click and Right-Click commands from menu.yml as the chest menu.
 * <p>
 * Only loaded once Floodgate is known to be enabled: KitMenu checks before touching this class.
 */
final class KitForms {

	// The network's gray-and-white look; a form button is light gray, so its text stays dark. Titles and kit
	// names in bold, in the button's own color; the ability under a name plain dark gray
	private static final String BOLD = "§l";
	private static final String BUTTON_DETAIL = "§8";

	/** A spyglass for Preview: a Bedrock game texture, as BedrockIcon in menu.yml. */
	private static final String PREVIEW_ICON = "textures/items/spyglass";

	private KitForms() {
	}

	/**
	 * Opens the kit list as a form when the player is on Bedrock with a touch screen.
	 *
	 * @return whether a form was sent; false leaves the player to the chest menu
	 */
	static boolean openIfTouch(Player player) {
		FloodgatePlayer bedrock = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
		if (bedrock == null || !isPhoneOrTablet(bedrock)) {
			return false;
		}

		Resource menu = Game.getInstance().getResources().getMenu();
		ConfigurationSection section = menu.getConfigurationSection("Menu.Items");
		if (section == null) {
			return false;
		}

		// The kits in the chest's slot order; items without a Left-Click command (decoration, close) are left out
		TreeMap<Integer, String> kits = new TreeMap<>();
		for (String key : section.getKeys(false)) {
			try {
				String path = "Menu.Items." + key;
				if (menu.contains(path + ".Commands.Left-Click")) {
					kits.put(Integer.parseInt(key), path);
				}
			} catch (NumberFormatException ignored) {
			}
		}
		if (kits.isEmpty()) {
			return false;
		}

		List<String> paths = new ArrayList<>(kits.values());
		SimpleForm.Builder form = SimpleForm.builder().title(BOLD + plain(menu.fetchString("Menu.General.Title")));
		for (String path : paths) {
			String summary = firstLine(menu.getStringList(path + ".Lore"));
			button(form, BOLD + plain(menu.fetchString(path + ".Name"))
					+ (summary.isEmpty() ? "" : "\n§r" + BUTTON_DETAIL + summary), icon(menu, path));
		}
		form.validResultHandler(response -> openKit(player, paths.get(response.clickedButtonId())));

		bedrock.sendForm(form.build());
		return true;
	}

	/**
	 * A touch screen, or a phone or tablet whatever its input: the input mode can be missing or unknown, as it
	 * seemed for an iPhone joining through Geyser's NetherNet, and a phone's screen stays small with a controller too.
	 */
	private static boolean isPhoneOrTablet(FloodgatePlayer bedrock) {
		if (bedrock.getInputMode() == InputMode.TOUCH) {
			return true;
		}
		DeviceOs device = bedrock.getDeviceOs();
		return device == DeviceOs.GOOGLE || device == DeviceOs.IOS || device == DeviceOs.AMAZON
				|| device == DeviceOs.WINDOWS_PHONE;
	}

	/** One kit: its lore, then Select, Preview when the kit has one, and Back to the list. */
	private static void openKit(Player player, String path) {
		Resource menu = Game.getInstance().getResources().getMenu();
		List<String> select = menu.getStringList(path + ".Commands.Left-Click");
		List<String> preview = menu.contains(path + ".Commands.Right-Click") ?
				menu.getStringList(path + ".Commands.Right-Click") : new ArrayList<>();

		List<String> lore = new ArrayList<>();
		for (String line : menu.getStringList(path + ".Lore")) {
			lore.add(Toolkit.translate(line));
		}

		SimpleForm.Builder form = SimpleForm.builder()
				.title(BOLD + plain(menu.fetchString(path + ".Name")))
				.content(String.join("\n", lore).trim());
		button(form, "Select", icon(menu, path));
		if (!preview.isEmpty()) {
			button(form, "Preview", PREVIEW_ICON);
		}
		button(form, "Back", null);

		form.validResultHandler(response -> {
			int clicked = response.clickedButtonId();
			if (clicked == 0) {
				run(player, select);
			} else if (clicked == 1 && !preview.isEmpty()) {
				run(player, preview);
			} else {
				// Back: on the main thread, where the menu reads its config
				Bukkit.getScheduler().runTask(Game.getInstance(), () -> {
					if (player.isOnline()) {
						openIfTouch(player);
					}
				});
			}
		});

		FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
	}

	/**
	 * A button with the icon a menu item names in BedrockIcon: a texture path of the Bedrock game, such as
	 * textures/items/bow_standby; without one, the button has no icon.
	 */
	private static void button(SimpleForm.Builder form, String text, String icon) {
		if (icon == null || icon.isBlank()) {
			form.button(text);
		} else {
			form.button(text, FormImage.Type.PATH, icon.trim());
		}
	}

	/** The item's BedrockIcon, or null when it has none. */
	private static String icon(Resource menu, String path) {
		return menu.contains(path + ".BedrockIcon") ? menu.fetchString(path + ".BedrockIcon") : null;
	}

	/** Form answers arrive off the main thread; the menu's commands run on it, as from the chest menu. */
	private static void run(Player player, List<String> commands) {
		Bukkit.getScheduler().runTask(Game.getInstance(), () -> {
			if (player.isOnline()) {
				Toolkit.runCommands(player, commands, "none", "none");
			}
		});
	}

	/** Without color codes: a form button is light gray, on which the menu's white names would not show. */
	private static String plain(String text) {
		String stripped = ChatColor.stripColor(Toolkit.translate(text == null ? "" : text));
		return stripped == null ? "" : stripped.trim();
	}

	/** The first lore line with text in it, such as the kit's ability, for the button's second line. */
	private static String firstLine(List<String> lore) {
		for (String line : lore) {
			String text = plain(line);
			if (!text.isEmpty()) {
				return text;
			}
		}
		return "";
	}

}
