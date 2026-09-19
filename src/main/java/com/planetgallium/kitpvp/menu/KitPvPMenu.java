package com.planetgallium.kitpvp.menu;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A read-only chest GUI. Each open creates a new instance, so no inventory is shared between players, and the
 * menu is its own holder so MenuListener can recognise it and dispatch clicks to its buttons by slot.
 */
public abstract class KitPvPMenu implements InventoryHolder {

	private final Map<Integer, MenuButton> buttons = new HashMap<>();
	private Inventory inventory;

	protected void createInventory(String title, int size, ItemStack[] contents) {
		this.inventory = Bukkit.createInventory(this, size, title);
		this.inventory.setContents(contents);
	}

	protected void setButton(int slot, MenuButton button) {
		buttons.put(slot, button);
	}

	public MenuButton getButton(int slot) {
		return buttons.get(slot);
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

	/**
	 * Runs the commands on the next tick, outside the click event. If they open another menu, it replaces this one
	 * without closing the window first (no flicker, the cursor stays in place); otherwise this menu is closed.
	 */
	protected void runCommandsThenClose(Player player, List<String> commands) {
		Bukkit.getScheduler().runTask(Game.getInstance(), () -> {
			if (!player.isOnline()) {
				return;
			}

			Toolkit.runCommands(player, commands, "none", "none");

			if (inventory.getViewers().contains(player)) {
				player.closeInventory();
			}
		});
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

}
