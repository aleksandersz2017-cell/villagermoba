package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Berserker implements Listener {

    private final Main plugin;

    // Zamek na jeden tick - blokuje podwójne odpalenie PlayerInteractEvent
    private final Set<UUID> interactLock = new HashSet<>();

    public Berserker(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player p) {
        p.getInventory().clear();

        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(28.0);
        p.setHealth(28.0);
        p.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(10.0);

        // Zostawiamy standardową bazę gry (1.0), by nie psuć przeliczania dmg w silniku Minecrafta
        p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(1.0);

        p.getInventory().setItem(0, createBerserkerAxe());
        p.getInventory().setItem(1, createChainItem(1));
        p.getInventory().setItem(2, createWhirlwindItem(1));
        p.getInventory().setItem(3, createLeapItem(1));
        p.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));
        p.getInventory().setItem(8, createShopItem());

        p.getInventory().setHelmet(createCosmeticArmor(Material.LEATHER_HELMET, EquipmentSlot.HEAD));
        p.getInventory().setChestplate(createCosmeticArmor(Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST));
        p.getInventory().setLeggings(createCosmeticArmor(Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS));
        p.getInventory().setBoots(createCosmeticArmor(Material.LEATHER_BOOTS, EquipmentSlot.FEET));

        p.sendMessage("§8» §4§lCLASS SELECTED: BERSERKER");
    }

    // Pomocnicza metoda do zadawania obrażeń z umiejętności (omija blokadę braku topora)
    private void damageFromAbility(LivingEntity victim, Player attacker, double damage) {
        victim.setMetadata("ability_damage", new FixedMetadataValue(plugin, true));
        victim.damage(damage, attacker);
        victim.removeMetadata("ability_damage", plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return;

        Player p = (Player) e.getDamager();

        // Jeśli obrażenia pochodzą z umiejętności (oznaczone wyżej), przepuszczamy je bez przeszkód!
        if (e.getEntity().hasMetadata("ability_damage")) {
            return;
        }

        // Sprawdzamy czy gracze gra klasą Berserker
        ItemStack slotZero = p.getInventory().getItem(0);
        boolean isBerserkerClass = slotZero != null
                && slotZero.hasItemMeta()
                && "§4§lRage Axe".equals(slotZero.getItemMeta().getDisplayName());

        if (isBerserkerClass) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            boolean hasBerserkerAxe = mainHand != null
                    && mainHand.getType() == Material.GOLDEN_AXE
                    && mainHand.hasItemMeta()
                    && "§4§lRage Axe".equals(mainHand.getItemMeta().getDisplayName());

            // Jeśli bije czymś innym niż Rage Axe (np. z pustej łapki lub przedmiotu abilitki) - anulujemy fizyczny atak!
            if (!hasBerserkerAxe) {
                e.setCancelled(true);
                return;
            }

            // Pasywka Berserkera (zwiększone obrażenia przy niskim zdrowiu)
            if (plugin.shopManager != null && plugin.shopManager.hasBerserkerPassive(p.getUniqueId())) {
                double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                double currentHealth = p.getHealth();
                double ratio = (maxHealth - currentHealth) / maxHealth;
                double bonus = ratio * 0.5;
                e.setDamage(e.getDamage() * (1.0 + bonus));

                if (ratio > 0.1) {
                    e.getEntity().getWorld().spawnParticle(Particle.REDSTONE,
                            e.getEntity().getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2,
                            new Particle.DustOptions(Color.RED, 1));
                }
            }
        }
    }

    // --- ABILITKA 3: SKOK SZAŁU ---
    @EventHandler
    public void onLeapUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.RABBIT_FOOT) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getPlayerClass(p.getUniqueId()).contains("Berserker")) return;

        e.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cRage Leap will be ready in: " + item.getAmount() + "s");
            return;
        }
        activateLeap(p, item);
    }

    private void activateLeap(Player p, ItemStack item) {
        p.sendMessage("§4§lROAR: LEAP!");

        int slot = p.getInventory().getHeldItemSlot();

        p.setMetadata("leap_fall_protection", new FixedMetadataValue(plugin, true));

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
                    p.removeMetadata("leap_fall_protection", plugin);
                    this.cancel();
                    return;
                }

                if (!p.isOnGround()) hadLeftGround = true;

                if (hadLeftGround && p.isOnGround() && ticksInAir > 5) {
                    Location landLoc = p.getLocation();

                    p.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, landLoc, 1);
                    p.getWorld().spawnParticle(Particle.BLOCK_CRACK, landLoc, 50, 1.2, 0.2, 1.2, Material.DIRT.createBlockData());
                    p.playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);

                    for (Entity target : landLoc.getWorld().getNearbyEntities(landLoc, 3.5, 2.5, 3.5)) {
                        if (target instanceof LivingEntity && target != p) {
                            if (plugin.isAlly(p, target)) continue;

                            LivingEntity victim = (LivingEntity) target;
                            damageFromAbility(victim, p, 2.0); // Zadanie DMG z abilitki
                            plugin.applyStun(victim, 10);
                            victim.setVelocity(new Vector(0, 0, 0));
                        }
                    }

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

        startCooldown(p, slot, Material.RABBIT_FOOT, 12);
    }

    @EventHandler
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();

        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            if (p.hasMetadata("leap_fall_protection")) {
                e.setCancelled(true);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.2, 0.1, 0.2, 0.05);
            }
        }
    }

    // --- ABILITKA 1: ŁAŃCUCH ---
    @EventHandler
    public void onChainUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.CHAIN) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getPlayerClass(p.getUniqueId()).contains("Berserker")) return;
        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cChain will be ready in: " + item.getAmount() + "s");
            return;
        }
        shootChain(p, item);
    }

    private void shootChain(Player p, ItemStack chainItem) {
        int slot = p.getInventory().getHeldItemSlot();

        int maxRange = 16;
        if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "berserker_longer_chain")) {
            maxRange = 25;
        }
        final int chainRange = maxRange;

        Location startLoc = p.getEyeLocation();
        Vector direction = startLoc.getDirection().normalize().multiply(3.0);
        ArmorStand projectile = p.getWorld().spawn(startLoc, ArmorStand.class, as -> {
            as.setVisible(false); as.setMarker(true); as.setGravity(false); as.setCanTick(true);
        });
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2f);
        ItemStack ironNugget = new ItemStack(Material.IRON_NUGGET);
        new BukkitRunnable() {
            int distance = 0;
            @Override
            public void run() {
                distance += 3;
                Location previousLoc = projectile.getLocation();
                Location currentLoc = previousLoc.clone().add(direction);
                projectile.teleport(currentLoc);

                Vector step = direction.clone().normalize().multiply(0.3);
                Location trailLoc = previousLoc.clone();
                double traveled = 0;
                double segmentLength = direction.length();
                while (traveled < segmentLength) {
                    trailLoc.add(step);
                    traveled += 0.3;
                    p.getWorld().spawnParticle(Particle.CRIT, trailLoc.clone().add(0, 0.5, 0), 2, 0.05, 0.05, 0.05, 0.01);
                    p.getWorld().spawnParticle(Particle.ITEM_CRACK, trailLoc, 1, 0.05, 0.05, 0.05, 0.02, ironNugget);
                }

                for (Entity entity : projectile.getNearbyEntities(0.8, 0.8, 0.8)) {
                    if (entity instanceof LivingEntity && entity != p && !(entity instanceof ArmorStand)) {
                        if (plugin.isAlly(p, entity)) continue;
                        LivingEntity victim = (LivingEntity) entity;
                        damageFromAbility(victim, p, 2.0); // Zadanie DMG z abilitki

                        Vector dirToVictim = victim.getLocation().toVector().subtract(p.getLocation().toVector());

                        if (dirToVictim.lengthSquared() < 0.01) {
                            dirToVictim = p.getLocation().getDirection();
                        }

                        Vector approachDir = dirToVictim.clone().normalize();

                        Location targetLoc = victim.getLocation().clone().subtract(approachDir.multiply(1.2));
                        targetLoc.setY(victim.getLocation().getY());

                        targetLoc.setDirection(approachDir);

                        p.teleport(targetLoc);
                        p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1.5f);
                        projectile.remove();
                        this.cancel();
                        return;
                    }
                }
                if (distance > chainRange || projectile.getLocation().getBlock().getType().isSolid()) { projectile.remove(); this.cancel(); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        startCooldown(p, slot, Material.CHAIN, 10);
    }

    // --- ABILITKA 2: MŁYNEK ---
    @EventHandler
    public void onWhirlwind(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COBWEB) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getPlayerClass(p.getUniqueId()).contains("Berserker")) return;
        e.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§c§l(!) §7Rage Whirlwind will be ready in: §f" + item.getAmount() + "s");
            return;
        }
        activateWhirlwind(p, item);
    }

    private void activateWhirlwind(Player p, ItemStack item) {
        int slot = p.getInventory().getHeldItemSlot();

        double slowRetain = 0.5;
        if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "berserker_whirlwind_less_slow")) {
            slowRetain = 0.75;
        }
        final double whirlwindSlowRetain = slowRetain;

        p.sendMessage("§4§lBATTLE RAGE: WHIRLWIND!");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60 || !p.isOnline() || p.isSneaking()) {
                    p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§7Whirlwind cancelled."));
                    this.cancel();
                    return;
                }

                p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§c§lPress shift to stop Whirlwind"));

                Vector vel = p.getVelocity();
                vel.multiply(whirlwindSlowRetain);
                p.setVelocity(vel);

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
                        if (target instanceof LivingEntity && target != p) {
                            if (plugin.isAlly(p, target)) continue;
                            LivingEntity victim = (LivingEntity) target;
                            damageFromAbility(victim, p, 3.0); // Zadanie DMG z abilitki
                            Vector push = victim.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.5).setY(0.2);
                            victim.setVelocity(push);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        startCooldown(p, slot, Material.COBWEB, 15);
    }

    // --- PRZEDMIOTY ---
    private ItemStack createBerserkerAxe() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4§lRage Axe");
            meta.setUnbreakable(true);

            // Modyfikatory atrybutów przydzielające +2.0 DMG (łącznie 3.0 DMG z bazą) i brak cooldownu zamachu
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "berserker_dmg", 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "berserker_speed", 100.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7When in Main Hand:");
            lore.add(" §23 Attack Damage");
            lore.add(" §2Spam Click");
            meta.setLore(lore);

            axe.setItemMeta(meta);
        }
        return axe;
    }

    private ItemStack createLeapItem(int amount) {
        ItemStack item = new ItemStack(Material.RABBIT_FOOT, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lRage Leap §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Launch into the air and slam down.");
            lore.add("§7Deals §c2 damage §7and §eStuns §7nearby");
            lore.add("§7enemies in a §f3.5-block §7radius.");
            lore.add("§7Grants fall damage immunity.");
            lore.add("§8Cooldown: §f12s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createChainItem(int amount) {
        ItemStack item = new ItemStack(Material.CHAIN, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7§lIron Chain §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Shoots a chain forwards.");
            lore.add("§7On hit, deals §c2 damage §7and teleports");
            lore.add("§7you to the targeted enemy.");
            lore.add("§8Cooldown: §f10s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWhirlwindItem(int amount) {
        ItemStack item = new ItemStack(Material.COBWEB, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lRage Whirlwind §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Slows you down for up to §f3 seconds§7.");
            lore.add("§7Repeatedly deals §6AOE §c3 damage §7and knocks");
            lore.add("§7back enemies in a §f2.8-block §7radius.");
            lore.add("§eSneak (Shift) §7to cancel early.");
            lore.add("§8Cooldown: §f15s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lCLASS SHOP §7(Click)");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCosmeticArmor(Material mat, EquipmentSlot slot) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(UUID.randomUUID(), "cosmetic_only", 0.0, AttributeModifier.Operation.ADD_NUMBER, slot));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startCooldown(Player p, int slot, Material material, int seconds) {
        updateCooldownItem(p, slot, material, seconds);

        new BukkitRunnable() {
            int time = seconds;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    this.cancel();
                    return;
                }

                if (time <= 1) {
                    updateCooldownItem(p, slot, material, 1);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
                    this.cancel();
                    return;
                }

                time--;
                updateCooldownItem(p, slot, material, time);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void updateCooldownItem(Player p, int slot, Material material, int amount) {
        ItemStack current = p.getInventory().getItem(slot);
        if (current == null || current.getType() != material) return;

        current.setAmount(amount);
        p.getInventory().setItem(slot, current);
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