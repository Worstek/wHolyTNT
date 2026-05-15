package com.worstek;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Iterator;

public class TNTLauncher implements Listener {

    private final wHolyTNT plugin;
    private final NamespacedKey lockKey;

    public TNTLauncher(wHolyTNT plugin) {
        this.plugin = plugin;
        this.lockKey = new NamespacedKey(plugin, "locked");
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(Items.CANNON_KEY, PersistentDataType.BYTE)) {
            event.getBlockPlaced().setMetadata("isTNTLauncher", new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block.hasMetadata("isTNTLauncher")) {
            event.setCancelled(true);

            if (block.hasMetadata("cannon_cooldown")) {
                long lastShot = block.getMetadata("cannon_cooldown").get(0).asLong();
                if (System.currentTimeMillis() - lastShot < 1000) return;
            }
            block.setMetadata("cannon_cooldown", new FixedMetadataValue(plugin, System.currentTimeMillis()));

            if (!(block.getState() instanceof Dispenser)) return;
            Dispenser dispenser = (Dispenser) block.getState();
            Inventory inv = dispenser.getInventory();

            ItemStack tntItem = inv.getItem(4);
            if (tntItem != null && tntItem.getType() == Material.TNT) {
                launch(block, tntItem);
                if (tntItem.getAmount() > 1) {
                    tntItem.setAmount(tntItem.getAmount() - 1);
                } else {
                    inv.setItem(4, null);
                }
            }
        }
    }

    private void launch(Block block, ItemStack item) {
        if (!(block.getBlockData() instanceof Directional)) return;
        Directional data = (Directional) block.getBlockData();
        BlockFace facing = data.getFacing();

        Location spawnLoc = block.getLocation().add(0.5, 0.5, 0.5).add(facing.getDirection().multiply(1.3));
        block.getWorld().playSound(block.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);

        Snowball motor = block.getWorld().spawn(spawnLoc, Snowball.class);
        motor.setGravity(false);
        motor.setMetadata("cannon_projectile", new FixedMetadataValue(plugin, true));

        TNTPrimed tnt = (TNTPrimed) block.getWorld().spawnEntity(spawnLoc, EntityType.PRIMED_TNT);
        tnt.setFuseTicks(400);
        tnt.setGravity(false);

        if (item.hasItemMeta()) {
            String type = item.getItemMeta().getPersistentDataContainer().get(Items.TNT_TYPE_KEY, PersistentDataType.STRING);
            if (type != null) {
                tnt.setMetadata("tntType", new FixedMetadataValue(plugin, type));
                if (item.getItemMeta().hasDisplayName()) {
                    tnt.setCustomName(item.getItemMeta().getDisplayName());
                    tnt.setCustomNameVisible(true);
                }
            }
        }

        motor.addPassenger(tnt);
        Vector velocity = facing.getDirection().multiply(1.2);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 300 || motor.isDead() || tnt.isDead() || !tnt.isValid()) {
                    if (!motor.isDead()) motor.remove();
                    this.cancel();
                    return;
                }
                motor.setVelocity(velocity);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Snowball && event.getEntity().hasMetadata("cannon_projectile")) {
            Snowball motor = (Snowball) event.getEntity();
            motor.getPassengers().forEach(passenger -> {
                if (passenger instanceof TNTPrimed) {
                    ((TNTPrimed) passenger).setFuseTicks(0);
                }
            });
            motor.remove();
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.DISPENSER && block.hasMetadata("isTNTLauncher")) {
                if (block.getState() instanceof Dispenser) {
                    setupTntGunMenu((Dispenser) block.getState());
                }
            }
        }
    }

    private void setupTntGunMenu(Dispenser dispenser) {
        Inventory inv = dispenser.getInventory();
        ItemStack glass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = glass.getItemMeta();
        if (m != null) {
            m.setDisplayName("§f");
            m.getPersistentDataContainer().set(lockKey, PersistentDataType.BYTE, (byte) 1);
            glass.setItemMeta(m);
        }
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, glass);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.DISPENSER) return;

        boolean isLauncher = false;
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(lockKey, PersistentDataType.BYTE)) {
                isLauncher = true;
                break;
            }
        }
        if (!isLauncher) return;

        int slot = event.getRawSlot();
        if (slot >= 0 && slot < 9 && slot != 4) {
            event.setCancelled(true);
            return;
        }

        ItemStack draggedItem = (slot == 4) ? event.getCursor() : (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ? event.getCurrentItem() : null);
        if (draggedItem != null && draggedItem.getType() != Material.AIR && draggedItem.getType() != Material.TNT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        handleLauncherRemoval(event.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (b.hasMetadata("isTNTLauncher")) {
                handleLauncherRemoval(b, true);
                it.remove();
            }
        }
    }

    private void handleLauncherRemoval(Block block, boolean dropItems) {
        if (block.hasMetadata("isTNTLauncher")) {
            if (block.getState() instanceof Dispenser) {
                Dispenser d = (Dispenser) block.getState();
                Inventory inv = d.getInventory();
                ItemStack tntInSlot = inv.getItem(4);

                // Очищаем стекло
                for (int i = 0; i < 9; i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(lockKey, PersistentDataType.BYTE)) {
                        inv.setItem(i, null);
                    }
                }

                if (dropItems) {
                    // Используем Items.createTNT для создания предмета пушки
                    ItemStack cannon = Items.createTNT(plugin, "tntcannon");
                    block.setType(Material.AIR);
                    block.getWorld().dropItemNaturally(block.getLocation(), cannon);
                    if (tntInSlot != null && tntInSlot.getType() != Material.AIR) {
                        block.getWorld().dropItemNaturally(block.getLocation(), tntInSlot);
                    }
                }
            }
            block.removeMetadata("isTNTLauncher", plugin);
            block.removeMetadata("cannon_cooldown", plugin);
        }
    }
}