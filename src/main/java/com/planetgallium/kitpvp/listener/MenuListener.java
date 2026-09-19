package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.menu.KitPvPMenu;
import com.planetgallium.kitpvp.menu.MenuButton;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MenuListener implements Listener {

    private static final long CLICK_COOLDOWN_MILLIS = 200;

    private final Game plugin;
    private final Map<UUID, Long> lastClickTimes = new HashMap<>();

    public MenuListener(Game plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory topInventory = e.getInventory();
        if (!(topInventory.getHolder() instanceof KitPvPMenu) || !(e.getWhoClicked() instanceof Player)) {
            return;
        }

        // Menus are read-only: this also blocks shift-clicks, number keys and double-clicks from the player inventory
        e.setCancelled(true);

        if (e.getClickedInventory() != topInventory) {
            return;
        }

        KitPvPMenu menu = (KitPvPMenu) topInventory.getHolder();
        MenuButton button = menu.getButton(e.getSlot());
        if (button == null) {
            return;
        }

        Player p = (Player) e.getWhoClicked();
        long now = System.currentTimeMillis();
        Long lastClickTime = lastClickTimes.get(p.getUniqueId());
        if (lastClickTime != null && now - lastClickTime < CLICK_COOLDOWN_MILLIS) {
            return;
        }
        lastClickTimes.put(p.getUniqueId(), now);

        try {
            button.onClick(p, e.getClick());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Error while handling a menu click from " + p.getName(), exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof KitPvPMenu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastClickTimes.remove(e.getPlayer().getUniqueId());
    }

}
