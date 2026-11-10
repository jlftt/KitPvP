package com.planetgallium.kitpvp.item;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.*;

import java.util.*;

public class AttributeParser {

    // PotionEffect
    private static final PotionEffectType FALLBACK_EFFECT_TYPE = PotionEffectType.CONFUSION;
    private static final int FALLBACK_EFFECT_DURATION_SECONDS = 10;
    private static final int FALLBACK_EFFECT_AMPLIFIER_NON_ZERO_BASED = 1;

    // ItemStack
    private static final Material FALLBACK_ITEM_MATERIAL = Material.BEDROCK;
    private static final int FALLBACK_ITEM_AMOUNT = 1;
    private static final Enchantment FALLBACK_ITEM_ENCHANTMENT = Enchantment.THORNS;
    private static final int FALLBACK_ITEM_ENCHANTMENT_AMPLIFIER = 1;

    public static List<PotionEffect> getEffectsFromPath(Resource resource, String path) {

        List<PotionEffect> effects = new ArrayList<>();
        ConfigurationSection effectsSection = resource.getConfigurationSection(path);

        if (effectsSection != null) {

            for (String effectName : effectsSection.getKeys(false)) {
                PotionEffectType type = Toolkit.safePotionEffectType(effectName);
                int amplifier = resource.getInt(path + "." + effectName + ".Amplifier");
                int duration = resource.getInt(path + "." + effectName + ".Duration");

                effects.add(new PotionEffect(type != null ? type : FALLBACK_EFFECT_TYPE,
                        duration != 0 ? duration : FALLBACK_EFFECT_DURATION_SECONDS,
                        amplifier != 0 ? amplifier : FALLBACK_EFFECT_AMPLIFIER_NON_ZERO_BASED));
            }

        }

        return effects;

    }

    public static ItemStack getItemStackFromPath(Resource resource, String path) {
        if (!resource.contains(path)) return null;

        ItemStack item = XMaterial.matchXMaterial(FALLBACK_ITEM_MATERIAL).parseItem();
        ItemMeta meta = item.getItemMeta();

        //          BASIC ITEM INFORMATION          //

        if (resource.contains(path + ".Name")) {
            meta.setDisplayName(resource.fetchString(path + ".Name"));
        }

        if (resource.contains(path + ".Lore")) {
            meta.setLore(resource.getStringList(path + ".Lore"));
        }

        if (resource.contains(path + ".Material")) {
            item.setType(Toolkit.safeMaterial(resource.fetchString(path + ".Material")));
        }

        if (resource.contains(path + ".Amount")) {
            item.setAmount(resource.getInt(path + ".Amount"));
        } else {
            item.setAmount(FALLBACK_ITEM_AMOUNT);
        }

        item.setItemMeta(meta);

        //              CUSTOM ATTRIBUTES            //

        if (resource.contains(path + ".Effects")) {
            setEffectsFromPath(item, resource, path + ".Effects");
        }

        if (Game.getInstance().getResources().getConfig().getBoolean("Arena.PreventItemDurabilityDamage")) {
            setUnbreakable(item);
        }

        if (resource.contains(path + ".Dye")) {
            dyeItem(item, Toolkit.getColorFromConfig(resource, path + ".Dye"));
        }

        if (resource.contains(path + ".Skull")) {
            setSkull(item, resource.fetchString(path + ".Skull"));
        }

        if (resource.contains(path + ".Durability")) {
            setCustomDurability(item, resource.getInt(path + ".Durability"));
        }

        ConfigurationSection enchantmentsSection = resource.getConfigurationSection(path + ".Enchantments");
        if (enchantmentsSection != null) {
            Map<Enchantment, Integer> enchantments = new HashMap<>();

            for (String enchantmentName : enchantmentsSection.getKeys(false)) {
                Enchantment enchantment = null;
                Optional<XEnchantment> enchantmentFromConfig = XEnchantment.matchXEnchantment(enchantmentName.toUpperCase());
                if (enchantmentFromConfig.isPresent()) {
                    enchantment = enchantmentFromConfig.get().getEnchant();
                }
                if (enchantment == null) {
                    Toolkit.printToConsole(String.format("&7[&b&lKIT-PVP&7] &cUnknown enchantment [%s], defaulting to [THORNS].", enchantmentName));
                    enchantment = FALLBACK_ITEM_ENCHANTMENT;
                }
                int amplifier = enchantmentsSection.getInt(enchantmentName);

                enchantments.put(enchantment, amplifier != 0 ? amplifier : FALLBACK_ITEM_ENCHANTMENT_AMPLIFIER);
            }

            item.addUnsafeEnchantments(enchantments);
        }

        return item;
    }

    private static void setCustomDurability(ItemStack item, int damageAmount) {
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(damageAmount);
            item.setItemMeta(meta);
        }
    }

    private static void setSkull(ItemStack item, String skullOwner) {
        item.setType(XMaterial.PLAYER_HEAD.parseMaterial());

        SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
        skullMeta.setOwner(skullOwner);

        item.setItemMeta(skullMeta);
    }

    private static void dyeItem(ItemStack item, Color color) {
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(color);
            item.setItemMeta(meta);
        }
    }

    private static void setUnbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
    }

    /**
     * An effect with a Duration makes every effect listed a custom one; without one, the first effect names the
     * potion's base type, as a brewed potion.
     */
    private static void setEffectsFromPath(ItemStack item, Resource resource, String effectsPath) {
        ConfigurationSection effectsSection = resource.getConfigurationSection(effectsPath);
        if (effectsSection == null || effectsSection.getKeys(false).isEmpty()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta)) {
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cEffects at [" + effectsPath + "] in " + resource.getName() +
                    " need a potion or tipped arrow; ignored.");
            return;
        }
        PotionMeta potionMeta = (PotionMeta) meta;

        String firstEffectName = effectsSection.getKeys(false).iterator().next();
        boolean isCustom = effectsSection.contains(firstEffectName + ".Duration");

        if (isCustom) {
            setCustomEffects(potionMeta, effectsSection);
        } else {
            setVanillaEffect(potionMeta, effectsSection, firstEffectName);
        }

        item.setItemMeta(potionMeta);
    }

    private static void setVanillaEffect(PotionMeta potionMeta, ConfigurationSection effectsSection, String effectName) {
        // A potion type name (SWIFTNESS, TURTLE_MASTER: what /kp create writes), else an effect name (SPEED)
        PotionType baseType = potionTypeNamed(effectName);
        if (baseType == null) {
            baseType = XPotion.matchXPotion(effectName).map(XPotion::getPotionType).orElse(null);
        }
        if (baseType == null) {
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cUnknown potion type [" + effectName + "]; potion left plain.");
            return;
        }

        PotionType type = baseType;
        if (effectsSection.getBoolean(effectName + ".Upgraded")) {
            type = variantOf(baseType, "STRONG_");
        } else if (effectsSection.getBoolean(effectName + ".Extended")) {
            type = variantOf(baseType, "LONG_");
        }

        potionMeta.setBasePotionType(type);
    }

    /** The level II or extended version of a potion type, or the type itself when it has none. */
    private static PotionType variantOf(PotionType baseType, String prefix) {
        PotionType variant = potionTypeNamed(prefix + baseType.name());
        return variant != null ? variant : baseType;
    }

    private static PotionType potionTypeNamed(String name) {
        try {
            return PotionType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException noSuchType) {
            return null;
        }
    }

    private static void setCustomEffects(PotionMeta potionMeta, ConfigurationSection effectsSection) {
        for (String effectName : effectsSection.getKeys(false)) {
            int amplifierNonZeroBased = effectsSection.getInt(effectName + ".Amplifier");
            potionMeta.addCustomEffect(new PotionEffect(Toolkit.safePotionEffectType(effectName),
                    Toolkit.parsePotionEffectDuration(effectsSection.getInt(effectName + ".Duration")),
                    Math.max(0, amplifierNonZeroBased - 1)), true);
        }
    }

}
