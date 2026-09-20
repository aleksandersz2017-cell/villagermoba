package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Paladyn implements Listener {

    private final Main plugin;
    private final Map<UUID, UUID> markedTargets = new HashMap<>();
    private final NamespacedKey paladinSwordKey;
    private final Set<UUID> interactLock = new HashSet<>();
    private static final double MINION_SWORD_DAMAGE_MULTIPLIER = 1.2;

    public Paladyn(Main plugin) {
        this.plugin = plugin;
        this.paladinSwordKey = new NamespacedKey(plugin, "paladin_sword");
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(30.0);
        player.setHealth(30.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(16.0);
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);

        // Zostawiamy standardową bazę gry (1.0), by nie psuć przeliczania dmg w silniku Minecrafta
        AttributeInstance attackDmg = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDmg != null) {
            attackDmg.setBaseValue(1.0);
        }

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

    // Pomocnicza metoda do zadawania obrażeń z umiejętności (omija blokadę braku miecza)
    private void damageFromAbility(LivingEntity victim, Player attacker, double damage) {
        victim.setMetadata("ability_damage", new FixedMetadataValue(plugin, true));
        victim.damage(damage, attacker);
        victim.removeMetadata("ability_damage", plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player p = (Player) event.getDamager();

        // Jeśli obrażenia pochodzą z umiejętności (np. Święty Gniew), przepuszczamy je!
        if (event.getEntity().hasMetadata("ability_damage")) {
            return;
        }

        // Sprawdzamy czy gracz gra klasą Paladyn (ma miecz w slocie 0)
        ItemStack slotZero = p.getInventory().getItem(0);
        boolean isPaladinClass = slotZero != null
                && slotZero.hasItemMeta()
                && "§bSword of Light".equals(slotZero.getItemMeta().getDisplayName());

        if (isPaladinClass) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            boolean hasPaladinSword = mainHand != null
                    && mainHand.getType() == Material.DIAMOND_SWORD
                    && mainHand.hasItemMeta()
                    && "§bSword of Light".equals(mainHand.getItemMeta().getDisplayName());

            // Jeśli bije czymś innym niż Sword of Light - anulujemy!
            if (!hasPaladinSword) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                // Po odrodzeniu upewniamy się, że bazowy atak to domyślne 1.0
                AttributeInstance attackDmg = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                if (attackDmg != null) {
                    attackDmg.setBaseValue(1.0);
                }
            }
        }.runTaskLater(plugin, 1L);
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

    // --- UMIEJĘTNOŚĆ 1: NAZNACZENIE ---
    @EventHandler
    public void onPaladinSeal(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOLD_NUGGET) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) return;

        int slot = p.getInventory().getHeldItemSlot();

        // Blokujemy celowanie w sojuszników oraz własne miniony
        RayTraceResult result = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getLocation().getDirection(), 15,
                entity -> entity instanceof LivingEntity && entity != p && !plugin.isAlly(p, entity));

        if (result == null || result.getHitEntity() == null) {
            result = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getLocation().getDirection(), 15, 1.0,
                    entity -> entity instanceof LivingEntity && entity != p && !plugin.isAlly(p, entity));
        }

        if (result != null && result.getHitEntity() != null) {
            Entity hit = result.getHitEntity();

            if (hit instanceof Villager) {
                p.sendMessage("§cYou cannot target a Nexus with this ability!");
                return;
            }

            LivingEntity target = (LivingEntity) hit;
            UUID targetUUID = target.getUniqueId();
            markedTargets.put(p.getUniqueId(), targetUUID);

            p.sendMessage("§e§lYOU MARKED THE TARGET: §f" + target.getName());
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 2.0f);

            if (target instanceof Player) {
                Player targetPlayer = (Player) target;
                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.3f, 1.8f);
                targetPlayer.sendMessage("§c§lYOU HAVE BEEN MARKED BY THE PALADIN! §7(" + p.getName() + ")");
            }

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

            startCooldown(p, slot, Material.GOLD_NUGGET, 8);
        }
    }

    // --- UMIEJĘTNOŚĆ 2: ŚWIĘTY GNIEW ---
    @EventHandler
    public void onHolyWrath(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SWEET_BERRIES) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) return;

        int slot = p.getInventory().getHeldItemSlot();

        p.sendMessage("§e§lHOLY AURA ACTIVATED!");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 120 || !p.isOnline()) {
                    p.sendMessage("§7*Your aura has faded*");
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
                            if (plugin.isAlly(p, entity)) continue;

                            LivingEntity target = (LivingEntity) entity;

                            target.setNoDamageTicks(0);
                            damageFromAbility(target, p, 2.0);
                            target.setNoDamageTicks(0);

                            Vector push = target.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.3).setY(0.1);
                            target.setVelocity(push);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        startCooldown(p, slot, Material.SWEET_BERRIES, 14);
    }

    // --- UMIEJĘTNOŚĆ 3: BOSKIE WYNIESIENIE (ASCENSION) ---
    @EventHandler
    public void onAscensionUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.PHANTOM_MEMBRANE) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) return;

        int slot = p.getInventory().getHeldItemSlot();

        p.sendMessage("§e§lRISE AND ATONE!");
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 0.8f);

        for (Entity entity : p.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof LivingEntity && entity != p) {
                if (plugin.isAlly(p, entity)) continue;

                LivingEntity target = (LivingEntity) entity;
                target.removePotionEffect(PotionEffectType.LEVITATION);

                // 1. Wyrzucenie w górę (wysokość zostaje taka sama)
                target.setVelocity(new Vector(0, 0.7, 0));

                new BukkitRunnable() {
                    int ticks = 0;
                    boolean isFallingDown = false;

                    @Override
                    public void run() {
                        if (!target.isValid() || ticks >= 100) {
                            this.cancel();
                            return;
                        }

                        // 2. Po pół sekundy (10 ticków) zatrzymujemy wroga w powietrzu
                        if (ticks == 10) {
                            // Dałem czas trwania efektu na 15 ticków, dla bezpieczeństwa, by wygasł sam gdyby coś przerwało taska
                            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 15, 0, false, false));
                        }

                        // 3. SKRÓCONY CZAS: Po 25 tickach (nieco ponad 1 sekunda od rzucenia spella) uderza o ziemię
                        if (ticks == 25) {
                            target.removePotionEffect(PotionEffectType.LEVITATION);
                            target.setVelocity(new Vector(0, -0.8, 0));
                            isFallingDown = true;
                        }

                        // 4. Detekcja uderzenia w ziemię
                        if (isFallingDown && ticks > 27) {
                            if (target.isOnGround()) {
                                plugin.applyStun(target, 20);

                                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
                                target.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);

                                if (target instanceof Player) {
                                    target.sendMessage("§c§lYou were stunned after falling!");
                                }

                                this.cancel();
                                return;
                            }
                        }

                        ticks++;
                    }
                }.runTaskTimer(plugin, 1L, 1L);
            }
        }

        startCooldown(p, slot, Material.PHANTOM_MEMBRANE, 30, "§aYour §eAscension §aability is ready!");
    }

    // --- POMOCNICZE ---
    @EventHandler
    public void onPaladinAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        if (markedTargets.containsKey(attacker.getUniqueId())) {
            if (event.getEntity().getUniqueId().equals(markedTargets.get(attacker.getUniqueId()))) {
                if (attacker.getAttackCooldown() >= 1.0) {
                    boolean betterSeal = plugin.shopManager.hasPaladinHeal(attacker.getUniqueId())
                            || (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(attacker, "paladyn_better_seal"));
                    double heal = betterSeal ? 4.0 : 3.0;
                    attacker.setHealth(Math.min(attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue(), attacker.getHealth() + heal));
                    attacker.sendMessage("§a+§l❤");

                    if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(attacker, "paladyn_seal_heals_team")) {
                        for (Entity nearby : attacker.getNearbyEntities(8, 8, 8)) {
                            if (nearby instanceof Player && nearby != attacker && plugin.isAlly(attacker, (Player) nearby)) {
                                Player ally = (Player) nearby;
                                double allyMaxHealth = ally.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                                ally.setHealth(Math.min(allyMaxHealth, ally.getHealth() + 2.0));
                                ally.sendMessage("§a+§l❤ §7(Paladin's Mark)");
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onPaladinSwordMinionBonusDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();

        if (plugin.minionManager == null || !plugin.minionManager.isMinion(event.getEntity())) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        ItemMeta meta = mainHand.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(paladinSwordKey, PersistentDataType.BYTE)) return;

        event.setDamage(event.getDamage() * MINION_SWORD_DAMAGE_MULTIPLIER);
    }

    private void startCooldown(Player p, int slot, Material material, int seconds) {
        startCooldown(p, slot, material, seconds, null);
    }

    private void startCooldown(Player p, int slot, Material material, int seconds, String readyMessage) {
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
                    if (readyMessage != null) p.sendMessage(readyMessage);
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

    private ItemStack createPaladinSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bSword of Light");
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "paladin_dmg", 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "paladin_speed", -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            meta.getPersistentDataContainer().set(paladinSwordKey, PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private ItemStack createMarkItem(int amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lSinner's Mark §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Mark an enemy target up to §f15 blocks §7away.");
            lore.add("§7Full charged melee hits on the target heal you");
            lore.add("§7for §a1.5 hearts, Mark lasts §7for §f5 seconds§7.");
            lore.add("§8Cooldown: §f8s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHolyWrathItem(int amount) {
        ItemStack item = new ItemStack(Material.SWEET_BERRIES, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lHoly Wrath §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Surround yourself with a holy aura for §f6s§7.");
            lore.add("§7Continuously deals §c2 damage §7and repels enemies.");
            lore.add("§8Cooldown: §f14s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAscensionItem(int amount) {
        ItemStack item = new ItemStack(Material.PHANTOM_MEMBRANE, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lDivine Ascension §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Lifts all nearby enemies into the air.");
            lore.add("§7After a brief suspension, slams them down,");
            lore.add("§7applying a §eStun §7upon impact.");
            lore.add("§8Cooldown: §f30s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
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

    public void removeMark(@NotNull UUID targetUUID) {
        markedTargets.values().removeIf(v -> v.equals(targetUUID));
    }
}