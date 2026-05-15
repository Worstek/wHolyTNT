package com.worstek;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class ExplosionListener implements Listener {
    private final wHolyTNT plugin;
    private final Random random = new Random();

    public ExplosionListener(wHolyTNT plugin) {
        this.plugin = plugin;
    }

    private boolean canBuild(Location loc) {
        List<String> blockedWorlds = plugin.getConfig().getStringList("blocked-worlds");
        if (blockedWorlds.contains(loc.getWorld().getName())) return false;

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
                com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(loc);
                ApplicableRegionSet set = query.getApplicableRegions(weLoc);
                if (set.size() == 0) return true;
                List<String> blockedRegions = plugin.getConfig().getStringList("blocked-regions");
                for (ProtectedRegion region : set) {
                    if (blockedRegions.contains(region.getId())) return false;
                }
                StateFlag.State state = set.queryValue(null, Flags.BLOCK_BREAK);
                if (state == StateFlag.State.DENY) return false;
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIceBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.ICE && event.getBlock().hasMetadata("temporary_ice")) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {

            Location loc = event.getEntity().getLocation();
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
                if (entity instanceof TNTPrimed && entity.hasMetadata("tntType")) {
                    String type = entity.getMetadata("tntType").get(0).asString();
                    if (type.toLowerCase().contains("stealer") || type.equalsIgnoreCase("b2") || type.equalsIgnoreCase("ice")) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTNTExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        Location loc = event.getLocation();
        if (entity == null || !entity.hasMetadata("tntType")) return;

        String type = entity.getMetadata("tntType").get(0).asString().toLowerCase();

        if (!canBuild(loc)) {
            event.setCancelled(true);
            return;
        }

        if (type.contains("stealer")) {
            event.setCancelled(true);
            handleStealerExplosion(loc, type);
            entity.remove();
            return;
        }

        if (type.equalsIgnoreCase("c4")) {
            handleSpecialBlocks(event);
            applyC4Damage(loc, getRadiusFromConfig("tnt_c4", 5));
        } else if (type.equalsIgnoreCase("wave")) {
            event.setCancelled(true);
            int radius = getRadiusFromConfig("tnt_wave", 3);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            loc.getWorld().createExplosion(loc, 4.0f, false, false);
            applyWaveDamage(loc, radius);
        } else if (type.equalsIgnoreCase("ice")) {
            event.setCancelled(true);
            applyIceExplosion(loc);
        } else if (type.equalsIgnoreCase("a")) {
            applyCustomExplosion(event, 15, 70.0, 4.6, 12.0f);
        } else if (type.equalsIgnoreCase("b")) {
            applyCustomExplosion(event, 30, 150.0, 5.0, 40.0f);
        } else if (type.equalsIgnoreCase("b2")) {
            event.setCancelled(true);
            applyB2Explosion(loc, 12);
        }
    }

    private void handleStealerExplosion(Location loc, String type) {
        int radius = 2;
        double chance = type.equalsIgnoreCase("luckstealer") ? 0.75 : 0.50;
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        Block closestSpawner = null;
        double minDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.SPAWNER) {
                        double dist = b.getLocation().distance(loc);
                        if (dist < minDistance) {
                            minDistance = dist;
                            closestSpawner = b;
                        }
                    }
                }
            }
        }
        if (closestSpawner != null && canBuild(closestSpawner.getLocation())) {
            boolean keepMob = random.nextDouble() <= chance;
            dropSpawnerWithData(closestSpawner, keepMob);
            closestSpawner.setType(Material.AIR);
        }
    }

    private void dropSpawnerWithData(Block b, boolean keepMob) {
        if (b.getType() != Material.SPAWNER) return;
        CreatureSpawner spawner = (CreatureSpawner) b.getState();
        EntityType spawnedType = spawner.getSpawnedType();

        if (keepMob) playSpawnerDeathSound(spawnedType, b.getLocation());
        else b.getWorld().playSound(b.getLocation(), Sound.ENTITY_PIG_DEATH, 1.0f, 1.0f);

        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (meta != null) {
            CreatureSpawner itemSpawner = (CreatureSpawner) meta.getBlockState();
            EntityType finalType = keepMob ? spawnedType : EntityType.PIG;
            itemSpawner.setSpawnedType(finalType);
            meta.setBlockState(itemSpawner);
            String entityname = finalType.name();
            for (ItemFlag flag : ItemFlag.values()) meta.addItemFlags(flag);

            String rawName = plugin.getConfig().getString("spawners.name", "&fРассадник &e%type%");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rawName.replace("%type%",entityname)));

            List<String> lore = plugin.getConfig().getStringList("spawners.lore");
            if (lore != null) {
                meta.setLore(lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        b.getWorld().dropItemNaturally(b.getLocation(), item);
    }

    private void applyB2Explosion(Location loc, int radius) {
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.SPAWNER) {
                        if (canBuild(b.getLocation())) {
                            dropSpawnerWithData(b, true);
                            b.setType(Material.AIR);
                        }
                    } else if (b.getType() != Material.AIR && b.getType() != Material.BEDROCK) {
                        if (canBuild(b.getLocation())) b.setType(Material.AIR);
                    }
                }
            }
        }
    }

    private void applyIceExplosion(Location loc) {
        int radius = plugin.getConfig().getInt("tnt_ice.sphere-radius", 2);
        int duration = plugin.getConfig().getInt("tnt_ice.duration", 5);
        Map<Block, BlockData> savedStates = new HashMap<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.sqrt(x * x + y * y + z * z) <= radius) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        Material mType = b.getType();
                        if (mType == Material.WATER || mType == Material.BUBBLE_COLUMN || mType == Material.KELP ||
                                mType == Material.SEAGRASS || mType == Material.TALL_SEAGRASS || mType == Material.SEA_PICKLE) {
                            if (canBuild(b.getLocation())) {
                                savedStates.put(b, b.getBlockData().clone());
                                b.setType(Material.ICE, false);
                                b.setMetadata("temporary_ice", new FixedMetadataValue(plugin, true));
                            }
                        }
                    }
                }
            }
        }
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        if (!savedStates.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                savedStates.forEach((block, data) -> {
                    if (block.getType() == Material.ICE && block.hasMetadata("temporary_ice")) {
                        block.setBlockData(data);
                        block.removeMetadata("temporary_ice", plugin);
                    }
                });
            }, duration * 20L);
        }
    }

    private void applyC4Damage(Location loc, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (b.isLiquid() || !canBuild(b.getLocation())) continue;
                    processC4Destruction(b);
                }
            }
        }
    }

    private void processC4Destruction(Block b) {
        Material m = b.getType();
        if (m == Material.AIR || m == Material.BEDROCK || m == Material.BARRIER) return;

        if (m == Material.ANCIENT_DEBRIS) b.setType(Material.CRYING_OBSIDIAN);
        else if (m == Material.CRYING_OBSIDIAN) b.setType(Material.OBSIDIAN);
        else b.breakNaturally();
    }

    private void applyWaveDamage(Location loc, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (!canBuild(b.getLocation())) continue;
                    processWaveDestruction(b);
                }
            }
        }
    }

    private void processWaveDestruction(Block b) {
        Material m = b.getType();
        if (m == Material.AIR || m == Material.BEDROCK || m == Material.BARRIER) return;
        if (m == Material.ANCIENT_DEBRIS) b.setType(Material.OBSIDIAN);
        else b.breakNaturally();
    }

    private void applyCustomExplosion(EntityExplodeEvent event, int radius, double maxDamage, double damageDrop, float blockBreakForce) {
        event.blockList().clear();
        Location loc = event.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        loc.getWorld().getNearbyEntities(loc, radius, radius, radius).forEach(entity -> {
            if (entity instanceof LivingEntity) {
                LivingEntity victim = (LivingEntity) entity;
                double distance = loc.distance(victim.getLocation());
                if (distance <= radius) {
                    double baseDamage = maxDamage - (distance * damageDrop);
                    if (baseDamage > 0) {
                        int blocksBetween = countBlocksBetween(loc, victim.getEyeLocation());
                        double finalDamage = baseDamage * (1.0 - (blocksBetween * 0.02));
                        if (finalDamage > 0) victim.damage(finalDamage);
                    }
                }
            }
        });
        if (canBuild(loc)) {
            loc.getWorld().createExplosion(loc, blockBreakForce, false, true);
        }
    }

    private void handleSpecialBlocks(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        String type = "";
        if (entity != null && entity.hasMetadata("tntType")) {
            type = entity.getMetadata("tntType").get(0).asString().toLowerCase();
        }

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (!canBuild(b.getLocation())) {
                it.remove();
                continue;
            }

            Material m = b.getType();
            if (!type.equals("c4") && !type.equals("wave")) {
                if (m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || m == Material.ANCIENT_DEBRIS) {
                    it.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.TNT || item.getItemMeta() == null) return;
        String type = item.getItemMeta().getPersistentDataContainer().get(Items.TNT_TYPE_KEY, PersistentDataType.STRING);
        if (type != null) {
            event.getBlockPlaced().setMetadata("tntType", new FixedMetadataValue(plugin, type));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntityType() == EntityType.DROPPED_ITEM || !(event.getEntity() instanceof LivingEntity)) {
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                    event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                for (Entity entity : event.getEntity().getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof TNTPrimed && entity.hasMetadata("tntType")) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed)) return;
        TNTPrimed tnt = (TNTPrimed) event.getEntity();

        if (tnt.hasMetadata("tntType")) {
            setupTntProperties(tnt, tnt.getMetadata("tntType").get(0).asString());
            return;
        }

        Location loc = tnt.getLocation();
        Block block = loc.getBlock();
        if (block.hasMetadata("tntType")) {
            String type = block.getMetadata("tntType").get(0).asString();
            tnt.setMetadata("tntType", new FixedMetadataValue(plugin, type));
            setupTntProperties(tnt, type);
            block.removeMetadata("tntType", plugin);
        }
    }

    private void setupTntProperties(TNTPrimed tnt, String type) {
        String configPath = "tnt_" + type.toLowerCase() + ".display_name";
        String displayName = plugin.getConfig().getString(configPath);
        if (displayName != null && !displayName.isEmpty()) {
            tnt.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
            tnt.setCustomNameVisible(true);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (tnt.isValid()) {
                if (tnt.getFuseTicks() > 1) {
                    int seconds = plugin.getConfig().getInt("tnt_" + type.toLowerCase() + ".explosion-cooldown", 4);
                    tnt.setFuseTicks(seconds * 20);
                }
            }
        }, 1L);
    }

    private int countBlocksBetween(Location start, Location end) {
        int count = 0;
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = start.distance(end);
        direction.normalize();
        for (double i = 0.5; i < distance; i += 0.5) {
            Location check = start.clone().add(direction.clone().multiply(i));
            if (check.getBlock().getType().isSolid()) {
                count++;
                i += 0.5;
            }
        }
        return count;
    }

    private int getRadiusFromConfig(String path, int def) {
        int configSize = plugin.getConfig().getInt(path + ".explosion-radius", def);
        return Math.max((configSize - 1) / 2, 0);
    }

    private void playSpawnerDeathSound(EntityType type, Location loc) {
        try {
            Sound deathSound = Sound.valueOf("ENTITY_" + type.name() + "_DEATH");
            loc.getWorld().playSound(loc, deathSound, 1.0f, 1.0f);
        } catch (Exception e) {
            loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_DEATH, 1.0f, 1.0f);
        }
    }
}