package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Hunter implements Listener {

    private final Main plugin;
    private final Set<UUID> slidingPlayers = new HashSet<>();

    public Hunter(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        // --- STATYSTYKI ---
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(16.0);
        player.setHealth(16.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(2.0);
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
        // --- EKWIPUNEK ---
        player.getInventory().setItem(0, createHunterBow());
        player.getInventory().setItem(1, createSlideItem(1)); // Teraz Biały Dywan

        player.getInventory().setItem(8, createShopItem());
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));

        player.getInventory().setChestplate(createUnbreakableArmor(Material.LEATHER_CHESTPLATE));
    }

    private ItemStack createShopItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lSKLEP KLASOWY §7(Kliknij)");
        item.setItemMeta(meta);
        return item;
    }

    // --- UMIEJĘTNOŚĆ 1: SLAJD ---
    @EventHandler
    public void onSlide(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        // ZMIENIONO NA WHITE_CARPET
        if (item == null || item.getType() != Material.WHITE_CARPET) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (item.getAmount() > 1) {
            p.sendMessage("§cSlajd będzie gotowy za: " + item.getAmount() + "s");
            return;
        }

        UUID uuid = p.getUniqueId();
        slidingPlayers.add(uuid);

        Vector direction = p.getLocation().getDirection().setY(0).normalize();
        Vector slide = direction.multiply(1.8).setY(0.01);
        p.setVelocity(slide);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.5f, 1.2f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.1, 0), 15, 0.3, 0, 0.3, 0.05);
        p.sendMessage("§2§lSLAJD!");

        new BukkitRunnable() {
            @Override
            public void run() {
                slidingPlayers.remove(uuid);
            }
        }.runTaskLater(plugin, 8L);

        startCooldown(p, item, 10);
    }

    @EventHandler
    public void onMoveDuringSlide(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (slidingPlayers.contains(p.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                p.setVelocity(p.getVelocity());
            }
        }
    }

    private ItemStack createHunterBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2§lŁuk Myśliwego");
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
            bow.setItemMeta(meta);
        }
        return bow;
    }

    private ItemStack createSlideItem(int amount) {
        // ZMIENIONO NA WHITE_CARPET
        ItemStack item = new ItemStack(Material.WHITE_CARPET, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2§lZwinny Slajd §8(Prawy Klik)");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createUnbreakableArmor(Material material) {
        ItemStack armor = new ItemStack(material);
        ItemMeta meta = armor.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            armor.setItemMeta(meta);
        }
        return armor;
    }

    private void startCooldown(Player p, ItemStack item, int seconds) {
        item.setAmount(seconds);
        new BukkitRunnable() {
            int time = seconds;
            @Override
            public void run() {
                if (!p.isOnline() || time <= 1) {
                    if (p.isOnline()) {
                        item.setAmount(1);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
                    }
                    this.cancel();
                } else {
                    time--;
                    item.setAmount(time);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler public void onArmorClick(InventoryClickEvent e) { if (e.getSlotType() == InventoryType.SlotType.ARMOR) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { e.setCancelled(true); }
}