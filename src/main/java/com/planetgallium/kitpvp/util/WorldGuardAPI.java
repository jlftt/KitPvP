package com.planetgallium.kitpvp.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import org.bukkit.entity.Player;

/**
 * WorldGuard 7 region checks. Only called once WorldGuard is enabled (Game.hasWorldGuard), so its classes are never
 * loaded without it. The WorldGuard 6 path (Minecraft 1.12 and older) and the 90-flag reflection enum are gone.
 */
public final class WorldGuardAPI {

    private WorldGuardAPI() {}

    public static boolean allowsPvp(Player player) {
        ApplicableRegionSet regionSet = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

        return regionSet.testState(WorldGuardPlugin.inst().wrapPlayer(player), Flags.PVP);
    }

}
