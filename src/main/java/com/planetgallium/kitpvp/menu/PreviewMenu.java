package com.planetgallium.kitpvp.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cryptomorin.xseries.XMaterial;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.util.CacheManager;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

public class PreviewMenu {

	private static final int SIZE = 54;
	private static final int SELECT_SLOT = 7;
	private static final int BACK_ARROW_SLOT = 8;

	private ItemStack[] buildContents(Kit kit, Resources resources) {
		ItemStack[] contents = new ItemStack[SIZE];

		//			ARMOR			//

		contents[0] = kit.getHelmet();
		contents[1] = kit.getChestplate();
		contents[2] = kit.getLeggings();
		contents[3] = kit.getBoots();

		//		POTION EFFECTS		//

		List<String> effectsLore = new ArrayList<>();

		for (PotionEffect effect : kit.getEffects()) {
			String type = effect.getType().getName();
			int amplifierNonZeroBased = effect.getAmplifier() + 1;
			int durationSeconds = effect.getDuration() / 20;
			boolean infinite = effect.getDuration() < 0 || durationSeconds > 10000;

			effectsLore.add("&7- " + type + " " + amplifierNonZeroBased + " (" + (infinite ? "Infinite" : (durationSeconds + "s")) + ")");
		}

		if (kit.getEffects().size() == 0) {
			effectsLore.add("&7None");
		}

		contents[4] = buildItem(resources.getMessages().fetchString("Messages.Other.PreviewMenuPotionEffectsItemName"),
				XMaterial.BREWING_STAND.parseItem(), effectsLore);

		//			HOTBAR			//

		for (int i = 0; i < 9; i++) {
			if (kit.getInventory().containsKey(i)) {
				contents[45 + i] = kit.getInventory().get(i);
			}
		}

		//			ITEMS			//

		for (int i = 9; i < 36; i++) {
			if (kit.getInventory().containsKey(i)) {
				contents[9 + i] = kit.getInventory().get(i);
			}
		}

		//			FILL			//

		if (kit.getFill() != null) {
			for (int i = 18; i < SIZE; i++) {
				if (contents[i] == null) {
					contents[i] = kit.getFill();
				}
			}
		}

		// Bedrock players cannot right-click a kit in the menu, so the kit is also selectable from here
		contents[SELECT_SLOT] = buildItem(resources.getMessages().fetchString("Messages.Other.PreviewMenuSelectItemName"),
				XMaterial.LIME_DYE.parseItem(), new ArrayList<>());

		contents[BACK_ARROW_SLOT] = buildItem(resources.getMessages().fetchString("Messages.Other.PreviewMenuBackArrowItemName"),
				XMaterial.ARROW.parseItem(), new ArrayList<>());

		return contents;
	}

	private static ItemStack buildItem(String name, ItemStack item, List<String> lore) {
		ItemMeta meta = item.getItemMeta();

		if (meta != null) {
			meta.setDisplayName(name);
			meta.setLore(Toolkit.colorizeList(lore));
			item.setItemMeta(meta);
		}
		return item;
	}

	public void open(Player p, Kit kit, Resources resources) {
		ItemStack[] contents = CacheManager.getPreviewMenuCache()
				.computeIfAbsent(kit.getName(), kitName -> buildContents(kit, resources));
		String title = resources.getMessages().fetchString("Messages.Other.PreviewMenuTitle")
				.replace("%kit%", kit.getName());

		new Instance(title, contents, resources, kit.getName()).open(p);
	}

	private static class Instance extends KitPvPMenu {

		Instance(String title, ItemStack[] contents, Resources resources, String kitName) {
			createInventory(title, SIZE, contents);

			setButton(SELECT_SLOT, (player, click) ->
					runCommandsThenClose(player, Collections.singletonList("player: kp kit " + kitName)));

			setButton(BACK_ARROW_SLOT, (player, click) ->
					runCommandsThenClose(player, resources.getConfig().getStringList("PreviewMenuBackArrowCommands")));
		}

	}

}
