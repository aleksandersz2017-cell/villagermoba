package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.UUID;

public class Berserker implements Listener {

    private final Main plugin;

    public Berserker(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player p) {
        p.getInventory().clear();
        // Dodaj to do każdej metody giveKit u każdego bohatera:
        p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(28.0);
        p.setHealth(28.0);
        p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(100.0);
        p.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(8.0);

        // SLOTY
        p.getInventory().setItem(0, createBerserkerAxe());
        p.getInventory().setItem(1, createChainItem(1));
        p.getInventory().setItem(2, createWhirlwindItem(1));
        p.getInventory().setItem(3, createLeapItem(1)); // NOWA ABILITKA
        p.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));
        p.getInventory().setItem(8, createShopItem());

        p.getInventory().setHelmet(createCosmeticArmor(Material.LEATHER_HELMET, EquipmentSlot.HEAD));
        p.getInventory().setChestplate(createCosmeticArmor(Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST));
        p.getInventory().setLeggings(createCosmeticArmor(Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS));
        p.getInventory().setBoots(createCosmeticArmor(Material.LEATHER_BOOTS, EquipmentSlot.FEET));

        p.sendMessage("§8» §4§lWYBRANO KLASĘ: BERSERKER");
    }

    // --- ABILITKA 3: SKOK SZAŁU (NOWE) ---
    @EventHandler
    public void onLeapUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.RABBIT_FOOT) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getPlayerClass(p.getUniqueId()).contains("Berserker")) return;

        e.setCancelled(true);
        if (item.getAmount() > 1) {
            p.sendMessage("§cSkok będzie gotowy za: " + item.getAmount() + "s");
            return;
        }
        activateLeap(p, item);
    }

    private void activateLeap(Player p, ItemStack item) {
        p.sendMessage("§4§lRYK: SKOK!");

        // --- NOWE: Włączamy ochronę przed upadkiem ---
        p.setMetadata("leap_fall_protection", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        p.teleport(p.getLocation().add(0, 0.1, 0));

        new BukkitRunnable() {
            @Override
            public void run() {
                Vector v = new Vector(0, 1.3, 0);
                p.setVelocity(v);
                p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 0.5f);
            }
        }.runTaskLater(plugin, 1L);

        new BukkitRunnable() {
            int ticksInAir = 0;
            boolean hadLeftGround = false;

            @Override
            public void run() {
                ticksInAir++;
                if (!p.isOnline() || ticksInAir > 100) {
                    p.removeMetadata("leap_fall_protection", plugin); // Czyścimy w razie błędu
                    this.cancel();
                    return;
                }

                if (!p.isOnGround()) hadLeftGround = true;

                if (hadLeftGround && p.isOnGround() && ticksInAir > 5) {
                    Location landLoc = p.getLocation();

                    // --- EFEKTY LĄDOWANIA (bez zmian) ---
                    p.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, landLoc, 1);
                    p.getWorld().spawnParticle(Particle.BLOCK_CRACK, landLoc, 50, 1.2, 0.2, 1.2, Material.DIRT.createBlockData());
                    p.playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);

                    // DMG I STUN (bez zmian)
                    for (Entity target : p.getNearbyEntities(2.5, 2.0, 2.5)) {
                        if (target instanceof LivingEntity && target != p) {
                            LivingEntity victim = (LivingEntity) target;
                            victim.damage(2.0, p);
                            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 20, 10, false, false));
                            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 20, 0, false, false));
                        }
                    }

                    // --- NOWE: Wyłączamy ochronę 1 tick po wylądowaniu ---
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            p.removeMetadata("leap_fall_protection", plugin);
                        }
                    }.runTaskLater(plugin, 1L);

                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);

        startCooldown(p, item, 12);
    }
    @EventHandler
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();

        // Jeśli powód to upadek i gracz ma metadata ochrony
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            if (p.hasMetadata("leap_fall_protection")) {
                e.setCancelled(true);
                // Opcjonalnie: mały efekt pyłu przy "miękkim" lądowaniu
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.2, 0.1, 0.2, 0.05);
            }
        }
    }
    // --- ABILITKA 1: ŁAŃCUCH (Bez zmian) ---
    @EventHandler
    public void onChainUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.CHAIN) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getPlayerClass(p.getUniqueId()).contains("Berserker")) return;
        event.setCancelled(true);
        if (item.getAmount() > 1) {
            p.sendMessage("§cŁańcuch będzie gotowy za: " + item.getAmount() + "s");
            return;
        }
        shootChain(p, item);
    }

    private void shootChain(Player p, ItemStack chainItem) {
        Location startLoc = p.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize();
        ArmorStand projectile = p.getWorld().spawn(startLoc, ArmorStand.class, as -> {
            as.setVisible(false); as.setMarker(true); as.setGravity(false); as.setCanTick(true);
        });
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2f);
        ItemStack ironNugget = new ItemStack(Material.IRON_NUGGET);
        new BukkitRunnable() {
            int distance = 0;
            @Override
            public void run() {
                distance++;
                Location currentLoc = projectile.getLocation().add(direction);
                projectile.teleport(currentLoc);
                p.getWorld().spawnParticle(Particle.CRIT, currentLoc.add(0, 0.5, 0), 3, 0.1, 0.1, 0.1, 0.01);
                p.getWorld().spawnParticle(Particle.ITEM_CRACK, currentLoc, 2, 0.1, 0.1, 0.1, 0.05, ironNugget);
                for (Entity entity : projectile.getNearbyEntities(0.8, 0.8, 0.8)) {
                    if (entity instanceof LivingEntity && entity != p && !(entity instanceof ArmorStand)) {
                        LivingEntity victim = (LivingEntity) entity;
                        victim.damage(2.0, p);
                        Location tpLoc = victim.getLocation().add(victim.getLocation().getDirection().multiply(-1.0));
                        p.teleport(tpLoc.setDirection(direction));
                        p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1.5f);
                        projectile.remove(); this.cancel(); return;
                    }
                }
                if (distance > 25 || projectile.getLocation().getBlock().getType().isSolid()) { projectile.remove(); this.cancel(); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        startCooldown(p, chainItem, 10);
    }

    // --- ABILITKA 2: MŁYNEK (Bez zmian) ---
    @EventHandler
    public void onWhirlwind(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COBWEB) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getPlayerClass(p.getUniqueId()).contains("Berserker")) return;
        e.setCancelled(true);
        if (item.getAmount() > 1) {
            p.sendMessage("§c§l(!) §7Młynek będzie gotowy za: §f" + item.getAmount() + "s");
            return;
        }
        activateWhirlwind(p, item);
    }

    private void activateWhirlwind(Player p, ItemStack item) {
        Pig steer = p.getWorld().spawn(p.getLocation(), Pig.class, pig -> {
            pig.setInvisible(true); pig.setSaddle(true); pig.setInvulnerable(true); pig.setSilent(true);
            pig.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 70, 1, false, false));
        });
        steer.addPassenger(p);
        p.sendMessage("§4§lSZAŁ BOJOWY: MŁYNEK!");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60 || !p.isOnline() || steer.isDead() || p.getVehicle() == null) { steer.remove(); this.cancel(); return; }
                if (p.getVehicle() == null || !p.getVehicle().equals(steer)) steer.addPassenger(p);
                Location loc = p.getLocation();
                if (ticks % 2 == 0) {
                    double angle = ticks * 0.8;
                    double x = Math.cos(angle) * 2.0; double z = Math.sin(angle) * 2.0;
                    p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(x, 1.2, z), 1);
                    p.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0.1);
                }
                if (ticks % 10 == 0) {
                    p.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
                    for (Entity target : p.getNearbyEntities(2.8, 2.0, 2.8)) {
                        if (target instanceof LivingEntity && target != p && target != steer) {
                            LivingEntity victim = (LivingEntity) target; victim.damage(1.5, p);
                            Vector push = victim.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.5).setY(0.2);
                            victim.setVelocity(push);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        startCooldown(p, item, 15);
    }

    // --- PRZEDMIOTY ---
    private ItemStack createLeapItem(int amount) {
        ItemStack item = new ItemStack(Material.RABBIT_FOOT, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§lSkok Szału §8(Prawy Klik)");
        meta.setLore(Arrays.asList("§7Wyskakujesz w powietrze,", "§7przy lądowaniu §fOgłuszasz §7wrogów."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBerserkerAxe() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.setDisplayName("§4§lTopór Szału");
        meta.setUnbreakable(true);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "generic.attackDamage", 3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "generic.attackSpeed", 100.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        axe.setItemMeta(meta);
        return axe;
    }

    private ItemStack createChainItem(int amount) {
        ItemStack item = new ItemStack(Material.CHAIN, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7§lŻelazny Łańcuch §8(Prawy Klik)");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWhirlwindItem(int amount) {
        ItemStack item = new ItemStack(Material.COBWEB, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lMłynek Szału §8(Prawy Klik)");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createShopItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lSKLEP KLASOWY §7(Kliknij)");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCosmeticArmor(Material mat, EquipmentSlot slot) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(UUID.randomUUID(), "cosmetic_only", 0.0, AttributeModifier.Operation.ADD_NUMBER, slot));
        item.setItemMeta(meta);
        return item;
    }

    private void startCooldown(Player p, ItemStack item, int seconds) {
        item.setAmount(seconds);
        new BukkitRunnable() {
            int time = seconds;
            @Override
            public void run() {
                if (!p.isOnline() || time <= 1) {
                    if (p.isOnline()) { item.setAmount(1); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f); }
                    this.cancel();
                } else {
                    time--; item.setAmount(time);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent e) {
        // 1. Sprawdzamy, czy atakujący to gracz
        if (!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();

        // 2. Sprawdzamy, czy gracz ma klasę Berserker
        if (plugin.getPlayerClass(p.getUniqueId()).contains("Berserker")) {

            // 3. KLUCZOWY MOMENT: Sprawdzamy, czy ulepszenie zostało kupione w ShopManager
            if (plugin.shopManager.hasBerserkerPassive(p.getUniqueId())) {

                double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                double currentHealth = p.getHealth();

                // Obliczamy brakujące HP (ratio od 0.0 do 1.0)
                double ratio = (maxHealth - currentHealth) / maxHealth;

                // Wzór: bazowy dmg * (1 + bonus).
                // Jeśli masz 1 HP, ratio wynosi prawie 1.0, więc bijesz o 50% mocniej.
                double bonus = ratio * 0.5;
                e.setDamage(e.getDamage() * (1.0 + bonus));

                // Opcjonalny efekt wizualny, żebyś widział, że działa (czerwone cząsteczki)
                if (ratio > 0.1) {
                    e.getEntity().getWorld().spawnParticle(org.bukkit.Particle.REDSTONE,
                            e.getEntity().getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2,
                            new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1));
                }
            }
        }
    }

    @EventHandler
    public void onShopOpen(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.GOLD_INGOT) {
            if (e.getAction().name().contains("RIGHT")) plugin.shopManager.openShop(e.getPlayer());
        }
    }

    @EventHandler public void onArmorClick(InventoryClickEvent e) { if (e.getSlotType() == InventoryType.SlotType.ARMOR) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { e.setCancelled(true); }
}