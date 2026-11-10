package com.planetgallium.kitpvp.item;

import com.cryptomorin.xseries.XPotion;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.Map;

/**
 * Writes a kit's items and effects into its file. Nothing here saves the file: Kit.toResource saves it once, after
 * every item is written (it used to be saved after each line, a hundred disk writes for a full kit).
 */
public class AttributeWriter {

    public static void potionEffectToResource(Resource resource, String path, PotionEffect effect) {
        if (effect == null) return;

        writeEffect(resource, path, effect);
    }

    public static void itemStackToResource(Resource resource, String path, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            resource.set(path + ".Material", item.getType().toString());
            return;
        }

        resource.set(path + ".Name", meta.hasDisplayName() ? Toolkit.toNormalColorCodes(meta.getDisplayName()) : null);
        resource.set(path + ".Lore", Toolkit.toNormalColorCodes(meta.getLore()));
        resource.set(path + ".Material", item.getType().toString());
        resource.set(path + ".Amount", item.getAmount() == 1 ? null : item.getAmount());

        if (meta instanceof LeatherArmorMeta) {
            Color color = ((LeatherArmorMeta) meta).getColor();
            resource.set(path + ".Dye.Red", color.getRed());
            resource.set(path + ".Dye.Green", color.getGreen());
            resource.set(path + ".Dye.Blue", color.getBlue());
        }

        if (meta instanceof SkullMeta && ((SkullMeta) meta).hasOwner()) {
            resource.set(path + ".Skull", ((SkullMeta) meta).getOwner());
        }

        // potions, splash and lingering potions, tipped arrows
        if (meta instanceof PotionMeta) {
            serializeEffects(resource, (PotionMeta) meta, path + ".Effects");
        }

        for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
            resource.set(path + ".Enchantments." + enchantment.getKey().getKey().getKey().toUpperCase(),
                    enchantment.getValue());
        }

        if (meta instanceof Damageable && ((Damageable) meta).hasDamage()) {
            resource.set(path + ".Durability", ((Damageable) meta).getDamage());
        }
    }

    private static void serializeEffects(Resource resource, PotionMeta meta, String effectPath) {
        if (meta.hasCustomEffects()) {
            for (PotionEffect effect : meta.getCustomEffects()) {
                writeEffect(resource, effectPath, effect);
            }
            return;
        }

        PotionType type = meta.getBasePotionType();
        if (type == null) return; // a plain bottle

        // STRONG_ and LONG_ are the level II and extended versions; the reader adds the prefix back
        String typeName = type.name();
        boolean upgraded = typeName.startsWith("STRONG_");
        boolean extended = typeName.startsWith("LONG_");
        String baseName = typeName.substring(upgraded ? "STRONG_".length() : extended ? "LONG_".length() : 0);

        resource.set(effectPath + "." + baseName + ".Upgraded", upgraded);
        resource.set(effectPath + "." + baseName + ".Extended", extended);
    }

    private static void writeEffect(Resource resource, String path, PotionEffect effect) {
        String effectName = XPotion.of(effect.getType()).name();
        // -1 is an infinite effect, kept as -1 (it used to become 0 seconds)
        int durationSeconds = effect.getDuration() == -1 ? -1 : effect.getDuration() / 20;

        resource.set(path + "." + effectName + ".Amplifier", effect.getAmplifier() + 1);
        resource.set(path + "." + effectName + ".Duration", durationSeconds);
    }

}
