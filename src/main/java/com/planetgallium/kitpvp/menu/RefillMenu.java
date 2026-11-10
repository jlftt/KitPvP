package com.planetgallium.kitpvp.menu;

import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RefillMenu {

    private static final int SIZE = 54;

    private final Resources resources;

    public RefillMenu(Resources resources) {
        this.resources = resources;
    }

    public void open(Player p) {
        // One soup built per opening (it was rebuilt from the config for each of the 54 slots)
        Resource config = resources.getConfig();
        ItemStack soup = new ItemStack(Material.MUSHROOM_STEW);
        ItemMeta meta = soup.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Toolkit.translate(config.fetchString("Soups.Name")));
            meta.setLore(Toolkit.colorizeList(config.getStringList("Soups.Lore")));
            soup.setItemMeta(meta);
        }

        // Each player gets their own inventory: they take the soups out of it
        Inventory refill = Bukkit.createInventory(null, SIZE, Toolkit.translate("Refill"));
        for (int slot = 0; slot < SIZE; slot++) {
            refill.setItem(slot, soup);
        }
        p.openInventory(refill);
    }

}
