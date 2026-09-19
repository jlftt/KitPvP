package com.planetgallium.kitpvp.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

@FunctionalInterface
public interface MenuButton {

	void onClick(Player player, ClickType click);

}
