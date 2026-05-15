package com.worstek;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final wHolyTNT plugin;

    public ConfigManager(wHolyTNT plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        FileConfiguration config = this.plugin.getConfig();
    }
}