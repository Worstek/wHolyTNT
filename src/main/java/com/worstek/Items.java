package com.worstek;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Items {

    public static final NamespacedKey TNT_TYPE_KEY = new NamespacedKey("wholytnt", "tnt_type");
    public static final NamespacedKey CANNON_KEY = new NamespacedKey("wholytnt", "is_cannon");

    public static ItemStack createTNT(wHolyTNT plugin, String type) {
        ItemStack item;
        ItemMeta meta;

        if (type.equalsIgnoreCase("tntcannon")) {
            item = new ItemStack(Material.DISPENSER);
            meta = item.getItemMeta();
            if (meta != null) {
                String name = plugin.getConfig().getString("tnt-cannon.name");
                List<String> lore = plugin.getConfig().getStringList("tnt-cannon.lore");

                meta.setDisplayName(translateColor(name));
                meta.setLore(lore.stream().map(Items::translateColor).collect(Collectors.toList()));

                meta.getPersistentDataContainer().set(CANNON_KEY, PersistentDataType.BYTE, (byte) 1);
                item.setItemMeta(meta);
            }
            return item;
        }

        String path = "tnt_" + type.toLowerCase();
        if (!plugin.getConfig().contains(path)) return null;

        item = new ItemStack(Material.TNT);
        meta = item.getItemMeta();

        if (meta != null) {
            String name = plugin.getConfig().getString(path + ".display_name");
            if (name != null) meta.setDisplayName(translateColor(name));

            List<String> loreLines = plugin.getConfig().getStringList(path + ".lore");
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.setLore(loreLines.stream().map(Items::translateColor).collect(Collectors.toList()));
            }

            meta.getPersistentDataContainer().set(TNT_TYPE_KEY, PersistentDataType.STRING, type.toLowerCase());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String translateColor(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}