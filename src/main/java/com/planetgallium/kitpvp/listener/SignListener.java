package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.ChatColor;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SignListener implements Listener {

	private static final List<String> REGULAR_SIGN_TYPES = Arrays.asList("clear", "menu", "stats", "refill");
	private static final List<String> CUSTOM_SIGN_TYPES = Arrays.asList("kit", "arena");

	private final Arena arena;
	private final Resource messages;
	private final Resource signs;

	public SignListener(Game plugin) {
		this.arena = plugin.getArena();
		this.messages = plugin.getResources().getMessages();
		this.signs = plugin.getResources().getSigns();
	}

	@EventHandler
	public void onSignChange(SignChangeEvent e) {
		if (!"[KitPvP]".equalsIgnoreCase(e.getLine(0)) || e.getLine(1) == null) return;

		// line 0 was already case-insensitive; "Kit" or "REFILL" on line 1 now works too
		String signType = e.getLine(1).toLowerCase(Locale.ROOT);
		String path = "Signs." + Toolkit.capitalizeFirstChar(signType);

		if (REGULAR_SIGN_TYPES.contains(signType)) {
			renameSign(e, path, null, null);
		} else if (CUSTOM_SIGN_TYPES.contains(signType)) {
			renameSign(e, path, "%" + signType + "%", e.getLine(2));
		} else {
			return;
		}
		e.getPlayer().sendMessage(messages.fetchString("Messages.Other.Sign"));
	}

	@EventHandler
	public void onSignUse(PlayerInteractEvent e) {
		// Logic behind this specific sign feature implementation: to avoid storing signs, most signs will
		// either match what is specified in the config 100%, or essentially 95%, where the other 5% may be one word
		// that is different (for instance a specified kit name, or arena name).
		// If there isn't a 100% match, a search for that one word delta will be started and subsequently used if found.

		if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		// the off hand fires a second event for the same click: the sign ran its command twice
		if (e.getHand() != EquipmentSlot.HAND) return;

		Block block = e.getClickedBlock();
		// every right-clicked block used to be snapshotted (getState) before knowing it was a sign
		if (block == null || !Tag.ALL_SIGNS.isTagged(block.getType())) return;
		if (!(block.getState() instanceof Sign)) return;

		ConfigurationSection signsSection = signs.getConfigurationSection("Signs");
		if (signsSection == null) return;

		Sign sign = (Sign) block.getState();
		Player p = e.getPlayer();

		for (String signType : signsSection.getKeys(false)) {
			List<String[]> mismatchedLines = new ArrayList<>();

			for (int i = 0; i < 3; i++) {
				String signLine = sign.getLine(i);
				String configSignLine = signs.fetchString("Signs." + signType + ".Line-" + (i + 1));

				if (!signLine.equals(configSignLine)) {
					mismatchedLines.add(new String[]{configSignLine, signLine});
				}
			}

			String placeholder = null;
			if (mismatchedLines.size() == 1) { // one line (line with kit name or arena name) doesn't match
				placeholder = getWordDelta(mismatchedLines.get(0));
				if (placeholder == null) {
					continue; // not built from this sign type
				}
			} else if (!mismatchedLines.isEmpty()) {
				continue;
			}

			executeSign(p, signType, placeholder);
			e.setCancelled(true); // prevents sign editing on 1.20+
			return;
		}
	}

	private void renameSign(SignChangeEvent e, String path, String placeholder, String placeholderValue) {
		for (int i = 0; i < 3; i++) {
			String line = signs.fetchString(path + ".Line-" + (i + 1));

			if (placeholder != null && placeholderValue != null) {
				line = line.replace(placeholder, placeholderValue);
			}
			e.setLine(i, line);
		}
	}

	private String getWordDelta(String[] mismatchedLine) {
		String[] rawConfigSignLineWords = ChatColor.stripColor(mismatchedLine[0]).split(" ");
		String[] rawSignLineWords = ChatColor.stripColor(mismatchedLine[1]).split(" ");

		// a different word count means the line was not built from this template
		if (rawSignLineWords.length != rawConfigSignLineWords.length) {
			return null;
		}

		String wordDelta = null;
		for (int i = 0; i < rawConfigSignLineWords.length; i++) {
			if (!rawConfigSignLineWords[i].equals(rawSignLineWords[i])) {
				if (wordDelta != null) {
					return null; // more than one differing word: not a match
				}
				wordDelta = rawSignLineWords[i];
			}
		}

		// exactly one differing word is the kit or arena name
		return wordDelta;
	}

	private void executeSign(Player p, String type, String placeholder) {
		switch (type.toLowerCase(Locale.ROOT)) {
			case "refill":
				arena.getMenus().getRefillMenu().open(p);
				break;
			case "clear": case "menu": case "stats":
				p.performCommand("kp " + type.toLowerCase(Locale.ROOT));
				break;
			case "kit":
			case "arena":
				if (placeholder == null) break; // a sign still showing %kit% used to run "kp kit null"
				p.performCommand("kp " + type.toLowerCase(Locale.ROOT) + " " + placeholder);
				break;
		}
	}

}
