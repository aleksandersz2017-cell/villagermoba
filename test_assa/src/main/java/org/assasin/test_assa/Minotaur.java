package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public class Minotaur implements Listener {

    private final Main plugin;

    public Minotaur(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        // Statystyki (15 serc i 5 tarczy)
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(30.0);
        player.setHealth(30.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(10.0);
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
        // Przedmioty
        player.getInventory().setItem(0, createMinotaurAxe());
        player.getInventory().setItem(1, createAbilityItem(1)); // Cegła (Wstrząs)
        player.getInventory().setItem(2, createRageItem(1));    // Róg (Szał)
        player.getInventory().setItem(3, createDashItem(1));
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));

        // Sklep
        player.getInventory().setItem(8, createShopItem());

        // Zbroja
        player.getInventory().setHelmet(new ItemStack(Material.COW_SPAWN_EGG));
        player.getInventory().setChestplate(createWhiteArmor(Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST));
        player.getInventory().setLeggings(createWhiteArmor(Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS));
        player.getInventory().setBoots(createWhiteArmor(Material.LEATHER_BOOTS, EquipmentSlot.FEET));

        // --- OBSŁUGA ULEPSZENIA ZE SKLEPU ---
        // Sprawdzamy, czy gracz wykupił "Lekkie Kopyta" w ShopManager
        if (plugin.shopManager.hasMinotaurSpeed(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
    }

    private ItemStack createShopItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lSKLEP KLASOWY §7(Kliknij)");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMinotaurAxe() {
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fTopór Minotaura");
            meta.setUnbreakable(true);
            // 5 DMG fizycznego
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "minotaur_dmg", 5.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            // Prędkość ataku 0.9 (4.0 - 3.1)
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "minotaur_speed", -2.9, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            axe.setItemMeta(meta);
        }
        return axe;
    }

    private ItemStack createAbilityItem(int amount) {
        ItemStack item = new ItemStack(Material.BRICK, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lWstrząs Ziemi §8(Prawy Klik)");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRageItem(int amount) {
        ItemStack item = new ItemStack(Material.GOAT_HORN, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lSzał Minotaura §8(Prawy Klik)");
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onMinotaurAbility(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BRICK) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        if (item.getAmount() > 1) {
            p.sendMessage("§cWstrząs Ziemi się odnawia...");
            return;
        }

        p.sendMessage("§f§lWSTRZĄS ZIEMI!");
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.5f);
        drawShockwaveParticles(p);

        for (Entity entity : p.getNearbyEntities(6, 4, 6)) {
            if (!(entity instanceof LivingEntity) || entity == p) continue;

            Vector toEntity = entity.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
            if (p.getLocation().getDirection().normalize().dot(toEntity) > 0.7) {
                LivingEntity victim = (LivingEntity) entity;
                victim.damage(3.0, p); // 3 DMG fizycznego

                Vector pullBack = p.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.5);
                pullBack.setY(0.8); // Podrzucenie
                entity.setVelocity(pullBack);
            }
        }
        startCooldown(p, item, 8);
    }

    @EventHandler
    public void onMinotaurRage(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOAT_HORN) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        if (item.getAmount() > 1) {
            p.sendMessage("§cSzał Minotaura się odnawia...");
            return;
        }

        p.sendMessage("§e§lPRZYGOTOWANIE DO SZAŁU... NIE RUSZAJ SIĘ!");
        p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0f, 0.8f);
        Location startLoc = p.getLocation().clone();

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (p.getLocation().distanceSquared(startLoc) > 0.05) {
                    p.sendMessage("§c§lPRZERWANO! Poruszyłeś się!");
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, p.getLocation().add(0, 2, 0), 1);
                count++;
                if (count >= 3) {
                    applyRageEffects(p);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        startCooldown(p, item, 20);
    }

    private void applyRageEffects(Player p) {
        p.sendMessage("§4§lWPADASZ W SZAŁ!!!");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 120, 0));

        new BukkitRunnable() {
            int timer = 0;
            @Override
            public void run() {
                if (!p.isOnline() || timer >= 6) {
                    if (p.isOnline()) p.sendMessage("§7*Szał Minotaura opada...*");
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.REDSTONE, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.RED, 2));
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.02);
                timer++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startCooldown(Player p, ItemStack item, int seconds) {
        item.setAmount(seconds);
        new BukkitRunnable() {
            int time = seconds;
            @Override
            public void run() {
                if (!p.isOnline() || time <= 1) {
                    if (p.isOnline()) item.setAmount(1);
                    this.cancel();
                } else {
                    time--;
                    item.setAmount(time);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void drawShockwaveParticles(Player p) {
        Location loc = p.getLocation();
        Vector dir = loc.getDirection().setY(0).normalize();
        for (double d = 1.0; d <= 6.0; d += 0.5) {
            for (double angle = -45; angle <= 45; angle += 15) {
                Vector v = dir.clone();
                double rad = Math.toRadians(angle);
                double x = v.getX() * Math.cos(rad) - v.getZ() * Math.sin(rad);
                double z = v.getX() * Math.sin(rad) + v.getZ() * Math.cos(rad);
                Location pLoc = loc.clone().add(x * d, 0.2, z * d);
                p.getWorld().spawnParticle(Particle.CLOUD, pLoc, 1, 0.1, 0.1, 0.1, 0.05);
                p.getWorld().spawnParticle(Particle.BLOCK_DUST, pLoc, 2, 0.1, 0.1, 0.1, 0.1, Material.DIRT.createBlockData());
            }
        }
    }

    private ItemStack createDashItem(int amount) {
        ItemStack item = new ItemStack(Material.BONE, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lSzarża Minotaura §8(Prawy Klik)");
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onMinotaurDash(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.BONE) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        if (item.getAmount() > 1) {
            p.sendMessage("§cSzarża się odnawia...");
            return;
        }

        p.sendMessage("§b§lSZARŻA!!!");
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1.0f, 1.0f);

        Vector direction = p.getLocation().getDirection().normalize().multiply(1.8).setY(0.2);
        p.setVelocity(direction);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!p.isOnline() || ticks > 15 || p.isOnGround() && ticks > 5) {
                    this.cancel();
                    return;
                }

                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.2, 0.1, 0.2, 0.01);

                for (Entity entity : p.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (entity instanceof LivingEntity && entity != p) {
                        LivingEntity victim = (LivingEntity) entity;
                        victim.damage(3.0, p);
                        Vector bounce = victim.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.4);
                        victim.setVelocity(bounce);
                        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.2f);
                        victim.sendMessage("§c§lZOSTAŁEŚ STRATOWANY!");
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        startCooldown(p, item, 24);
    }

    private ItemStack createWhiteArmor(Material material, EquipmentSlot slot) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.WHITE);
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(UUID.randomUUID(), "no_stats", 0, AttributeModifier.Operation.ADD_NUMBER, slot));
            item.setItemMeta(meta);
        }
        return item;
    }
    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Używamy BukkitRunnable, aby nadać efekt 1 tick po respie
        // (Minecraft czasem czyści efekty dokładnie w momencie odrodzenia)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.shopManager.hasMinotaurSpeed(player.getUniqueId())) {
                    // Sprawdzamy czy gracz nadal jest Minotaurem (np. sprawdzając przedmiot w ręku)
                    ItemStack item = player.getInventory().getItem(0);
                    if (item != null && item.getType() == Material.IRON_AXE) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    @EventHandler
    public void onArmorClick(InventoryClickEvent event) {
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }
}