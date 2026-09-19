package com.planetgallium.kitpvp.menu;

import com.planetgallium.kitpvp.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KitMenu {

	private final Resources resources;

	// Built once from menu.yml and copied into each opened menu; cleared by invalidate() on reload
	private String title;
	private int size;
	private ItemStack[] contents;
	private Map<Integer, String> itemPaths;

	public KitMenu(Resources resources) {
		this.resources = resources;
	}

	public void invalidate() {
		this.contents = null;
	}

	private void build() {
		Resource menuConfig = resources.getMenu();

		this.title = menuConfig.fetchString("Menu.General.Title");
		this.size = menuConfig.getInt("Menu.General.Size");
		this.contents = new ItemStack[size];
		this.itemPaths = new HashMap<>();

		ConfigurationSection section = menuConfig.getConfigurationSection("Menu.Items");
		if (section == null) {
			return;
		}

		for (String key : section.getKeys(false)) {
			int slot;
			try {
				slot = Integer.parseInt(key);
			} catch (NumberFormatException exception) {
				continue;
			}

			if (slot < 0 || slot >= size) {
				Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cMenu item " + key + " is outside the menu size, skipping.");
				continue;
			}

			String itemPath = "Menu.Items." + key;
			contents[slot] = buildItem(menuConfig.fetchString(itemPath + ".Name"),
					Toolkit.safeMaterial(menuConfig.fetchString(itemPath + ".Material")),
					menuConfig.getStringList(itemPath + ".Lore"));
			itemPaths.put(slot, itemPath);
		}
	}

	private static ItemStack buildItem(String name, Material material, List<String> lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();

		if (meta != null) {
			meta.setDisplayName(name);
			meta.setLore(lore);
			item.setItemMeta(meta);
		}
		return item;
	}

	public void open(Player p) {
		if (contents == null) {
			build();
		}
		new Instance().open(p);
	}

	private class Instance extends KitPvPMenu {

		Instance() {
			createInventory(title, size, contents);

			for (Map.Entry<Integer, String> entry : itemPaths.entrySet()) {
				String itemPath = entry.getValue();

				setButton(entry.getKey(), (player, click) -> {
					String clickType = getCommandsKey(click);
					if (clickType == null) {
						return;
					}

					List<String> commands = resources.getMenu().contains(itemPath + ".Commands." + clickType) ?
							resources.getMenu().getStringList(itemPath + ".Commands." + clickType) : new ArrayList<>();
					runCommandsThenClose(player, commands);
				});
			}
		}

	}

	// Shift-clicks count as regular clicks; middle-click, number keys and drop keys do nothing
	private static String getCommandsKey(ClickType click) {
		switch (click) {
			case LEFT:
			case SHIFT_LEFT:
				return "Left-Click";
			case RIGHT:
			case SHIFT_RIGHT:
				return "Right-Click";
			default:
				return null;
		}
	}

}
