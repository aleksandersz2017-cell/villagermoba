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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Paladyn implements Listener {

    private final Main plugin;
    private final Map<UUID, UUID> markedTargets = new HashMap<>();

    public Paladyn(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(30.0);
        player.setHealth(30.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(14.0);
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
        player.getInventory().setItem(0, createPaladinSword());
        player.getInventory().setItem(1, createMarkItem(1));
        player.getInventory().setItem(2, createHolyWrathItem(1));
        player.getInventory().setItem(3, createAscensionItem(1));
        player.getInventory().setItem(8, createShopItem());
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));

        player.getInventory().setHelmet(new ItemStack(Material.PLAYER_HEAD));
        player.getInventory().setChestplate(createVisualArmor(Material.DIAMOND_CHESTPLATE, EquipmentSlot.CHEST));
        player.getInventory().setLeggings(createVisualArmor(Material.DIAMOND_LEGGINGS, EquipmentSlot.LEGS));
        player.getInventory().setBoots(createVisualArmor(Material.DIAMOND_BOOTS, EquipmentSlot.FEET));
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

    // --- UMIEJĘTNOŚĆ 1: NAZNACZENIE ---
    @EventHandler
    public void onPaladinSeal(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOLD_NUGGET) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        if (item.getAmount() > 1) return;

        RayTraceResult result = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getLocation().getDirection(), 15,
                entity -> entity instanceof LivingEntity && entity != p);

        if (result != null && result.getHitEntity() != null) {
            LivingEntity target = (LivingEntity) result.getHitEntity();
            UUID targetUUID = target.getUniqueId();
            markedTargets.put(p.getUniqueId(), targetUUID);

            p.sendMessage("§e§lNAZNACZYŁEŚ CEL: §f" + target.getName());
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 2.0f);

            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= 100 || !target.isValid() || !markedTargets.getOrDefault(p.getUniqueId(), UUID.randomUUID()).equals(targetUUID)) {
                        markedTargets.remove(p.getUniqueId());
                        this.cancel();
                        return;
                    }
                    Location loc = target.getLocation().add(0, 2.4, 0);
                    for (double i = 0; i < Math.PI * 2; i += Math.PI / 4) {
                        double x = Math.cos(i) * 0.5;
                        double z = Math.sin(i) * 0.5;
                        loc.add(x, 0, z);
                        target.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0, 0.1, 0, 0.01);
                        loc.subtract(x, 0, z);
                    }
                    ticks += 5;
                }
            }.runTaskTimer(plugin, 0L, 5L);

            startCooldown(p, item, 8);
        }
    }

    // --- UMIEJĘTNOŚĆ 2: ŚWIĘTY GNIEW ---
    @EventHandler
    public void onHolyWrath(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOLDEN_HOE) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        if (item.getAmount() > 1) return;

        p.sendMessage("§e§lAKTYWOWANO ŚWIĘTĄ AURĘ!");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 130, 0, false, false));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 120 || !p.isOnline()) {
                    p.sendMessage("§7*Twoja aura wygasła*");
                    this.cancel();
                    return;
                }
                double angle = ticks * 0.5;
                double x = Math.cos(angle) * 1.2;
                double z = Math.sin(angle) * 1.2;
                Location particleLoc = p.getLocation().add(x, 1.0, z);
                p.getWorld().spawnParticle(Particle.CRIT_MAGIC, particleLoc, 3, 0.05, 0.05, 0.05, 0.02);

                if (ticks % 10 == 0) {
                    for (Entity entity : p.getNearbyEntities(1.5, 1.5, 1.5)) {
                        if (entity instanceof LivingEntity && entity != p) {
                            LivingEntity target = (LivingEntity) entity;
                            target.damage(2.0, p);
                            Vector push = target.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.3).setY(0.1);
                            target.setVelocity(push);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        startCooldown(p, item, 14);
    }

    // --- UMIEJĘTNOŚĆ 3: BOSKIE WYNIESIENIE (ASCENSION) ---
    @EventHandler
    public void onAscensionUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.GOLDEN_SHOVEL) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        if (item.getAmount() > 1) return;

        p.sendMessage("§e§lPOWSTAŃCIE I ODPOKUTUJCIE!");
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 0.8f);

        for (Entity entity : p.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof LivingEntity && entity != p) {
                LivingEntity target = (LivingEntity) entity;

                // Usuwamy stare efekty
                target.removePotionEffect(PotionEffectType.LEVITATION);
                target.setMetadata("ascension_stun", new FixedMetadataValue(plugin, true));

                // 1. Początkowy impuls w górę
                target.setVelocity(new Vector(0, 0.7, 0));

                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (ticks >= 40 || !target.isValid()) {
                            target.removePotionEffect(PotionEffectType.LEVITATION);
                            target.setVelocity(new Vector(0, -0.6, 0));
                            this.cancel();
                            return;
                        }

                        // FAZA LEWITACJI:
                        // Używamy TYLKO efektu mikstury, nie ustawiamy Velocity.
                        // Dzięki temu Knockback z miecza działa w 100% normalnie.
                        if (ticks == 10) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 35, 0, false, false));
                        }

                        ticks++;
                    }
                }.runTaskTimer(plugin, 1L, 1L);
            }
        }
        startAscensionCooldown(p, item, 30);
    }

    @EventHandler
    public void onAscensionFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) event.getEntity();
                if (target.hasMetadata("ascension_stun")) {
                    target.removeMetadata("ascension_stun", plugin);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 255, false, false));
                    target.sendMessage("§c§lZostałeś ogłuszony potężnym upadkiem!");
                    target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
                    target.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                }
            }
        }
    }

    // --- POMOCNICZE ---
    @EventHandler
    public void onPaladinAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        if (markedTargets.containsKey(attacker.getUniqueId())) {
            if (event.getEntity().getUniqueId().equals(markedTargets.get(attacker.getUniqueId()))) {
                if (attacker.getAttackCooldown() >= 1.0) {
                    double heal = plugin.shopManager.hasPaladinHeal(attacker.getUniqueId()) ? 4.0 : 3.0;
                    attacker.setHealth(Math.min(attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue(), attacker.getHealth() + heal));
                    attacker.sendMessage("§a+§l❤");
                }
            }
        }
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

    private void startAscensionCooldown(Player p, ItemStack item, int seconds) {
        item.setAmount(seconds);
        new BukkitRunnable() {
            int timeLeft = seconds;
            @Override
            public void run() {
                if (!p.isOnline() || item.getAmount() <= 1) {
                    this.cancel();
                    return;
                }
                timeLeft--;
                if (timeLeft <= 0) {
                    item.setAmount(1);
                    p.sendMessage("§aTwoja umiejętność §eAscension §ajest gotowa!");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                    this.cancel();
                } else {
                    item.setAmount(timeLeft);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private ItemStack createPaladinSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bMiecz Światłości");
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "paladin_dmg", 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "paladin_speed", -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private ItemStack createMarkItem(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.setDisplayName("§e§lNaznaczenie Grzesznika");
        return item;
    }

    private ItemStack createHolyWrathItem(int amount) {
        ItemStack item = new ItemStack(Material.GOLDEN_HOE, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.setDisplayName("§e§lŚwięty Gniew");
        return item;
    }

    private ItemStack createAscensionItem(int amount) {
        ItemStack item = new ItemStack(Material.GOLDEN_SHOVEL, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.setDisplayName("§e§lBoskie Wyniesienie §8(Prawy Klik)");
        return item;
    }

    private ItemStack createVisualArmor(Material material, EquipmentSlot slot) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(UUID.randomUUID(), "visual_only", 0.0, AttributeModifier.Operation.ADD_NUMBER, slot));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler public void onArmorClick(InventoryClickEvent e) { if (e.getSlotType() == InventoryType.SlotType.ARMOR) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { e.setCancelled(true); }

    public void removeMark(@NotNull UUID uniqueId) {
        markedTargets.remove(uniqueId);
    }
}