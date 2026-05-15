package com.worstek;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class wHolyTNT extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final List<String> tntKeys = Arrays.asList("tntcannon", "a", "b", "b2", "c4", "ice", "stealer", "luckstealer", "wave");

    @Override
    public void onEnable() {
        // Сохраняем и загружаем конфиг
        saveDefaultConfig();

        // Регистрация событий
        getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
        getServer().getPluginManager().registerEvents(new TNTLauncher(this), this);

        // Регистрация команды
        if (getCommand("wholytnt") != null) {
            getCommand("wholytnt").setExecutor(this);
            getCommand("wholytnt").setTabCompleter(this);
        }

        getLogger().info("§a[wHolyTNT] Плагин успешно запущен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(getMsg("usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("wholytnt.admin")) {
                sender.sendMessage("§cУ вас нет прав!");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(getMsg("usage"));
                return true;
            }

            String type = args[1].toLowerCase();
            if (!tntKeys.contains(type)) {
                sender.sendMessage(getMsg("tnt-unknown"));
                return true;
            }

            Player target;
            int amount = 1;

            if (args.length == 2) {
                if (sender instanceof Player) {
                    target = (Player) sender;
                } else {
                    sender.sendMessage("§cИз консоли нужно указывать ник!");
                    return true;
                }
            } else {
                target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage("§cИгрок не найден!");
                    return true;
                }
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        amount = 1;
                    }
                }
            }

            ItemStack tntItem = Items.createTNT(this, type);
            if (tntItem != null) {
                tntItem.setAmount(amount);
                target.getInventory().addItem(tntItem);

                target.sendMessage(getMsg("tnt-receive").replace("(тип динамита)", type.toUpperCase()));
                if (sender != target) {
                    sender.sendMessage("§aВы выдали §f" + type + " §aигроку §f" + target.getName());
                }
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("wholytnt.admin")) {
                sender.sendMessage("§cУ вас нет прав!");
                return true;
            }
            reloadConfig();
            sender.sendMessage(getMsg("reload-msg"));
            return true;
        }

        return true;
    }

    public String getMsg(String path) {
        String prefix = getConfig().getString("prefix", "&x&D&C&E&7&7&4wHolyTNT:");
        String msg = getConfig().getString(path, "");
        if (msg.isEmpty()) return "";
        // Сначала заменяем префикс, потом красим всё вместе
        return ColorUtils.translate(msg.replace("{prefix}", prefix));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String input = args[args.length - 1].toLowerCase();
        if (args.length == 1) {
            return Arrays.asList("give", "reload").stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return tntKeys.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}