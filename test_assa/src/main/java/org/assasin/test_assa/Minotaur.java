package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Minotaur implements Listener {

    private final Main plugin;
    private final Map<UUID, ItemStack[]> stunnedPlayersHotbar = new HashMap<>();

    // Zamek na jeden tick, blokuje podwójne odpalenie PlayerInteractEvent
    private final Set<UUID> interactLock = new HashSet<>();

    public Minotaur(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(30.0);
        player.setHealth(30.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(10.0);

        // Zostawiamy standardową bazę gry (1.0), by nie psuć przeliczania dmg w silniku Minecrafta
        AttributeInstance attackDmg = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDmg != null) {
            attackDmg.setBaseValue(1.0);
        }

        player.getInventory().setItem(0, createMinotaurAxe());
        player.getInventory().setItem(1, createAbilityItem(1));
        player.getInventory().setItem(2, createRageItem(1));
        player.getInventory().setItem(3, createDashItem(1));
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));

        player.getInventory().setItem(8, createShopItem());

        player.getInventory().setHelmet(new ItemStack(Material.COW_SPAWN_EGG));
        player.getInventory().setChestplate(createWhiteArmor(Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST));
        player.getInventory().setLeggings(createWhiteArmor(Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS));
        player.getInventory().setBoots(createWhiteArmor(Material.LEATHER_BOOTS, EquipmentSlot.FEET));

        if (plugin.shopManager.hasMinotaurSpeed(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
    }

    // Pomocnicza metoda do zadawania obrażeń z umiejętności (omija blokadę braku topora)
    private void damageFromAbility(LivingEntity victim, Player attacker, double damage) {
        victim.setMetadata("ability_damage", new FixedMetadataValue(plugin, true));
        victim.damage(damage, attacker);
        victim.removeMetadata("ability_damage", plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player p = (Player) event.getDamager();

        // Jeśli obrażenia pochodzą z umiejętności (oznaczone wyżej), przepuszczamy je bez przeszkód!
        if (event.getEntity().hasMetadata("ability_damage")) {
            return;
        }

        // Sprawdzamy czy gracz gra klasą Minotaur
        ItemStack slotZero = p.getInventory().getItem(0);
        boolean isMinotaurClass = slotZero != null
                && slotZero.hasItemMeta()
                && "§fMinotaur's Axe".equals(slotZero.getItemMeta().getDisplayName());

        if (isMinotaurClass) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            boolean hasMinotaurAxe = mainHand != null
                    && mainHand.getType() == Material.IRON_AXE
                    && mainHand.hasItemMeta()
                    && "§fMinotaur's Axe".equals(mainHand.getItemMeta().getDisplayName());

            // Jeśli bije czymś innym niż Minotaur's Axe (np. z pustej łapki lub przedmiotu abilitki) - anulujemy!
            if (!hasMinotaurAxe) {
                event.setCancelled(true);
            }
        }
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

    private ItemStack createMinotaurAxe() {
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fMinotaur's Axe");
            meta.setUnbreakable(true);

            // Zmniejszone na 4.0, bo baza gracza to teraz 1.0 (razem daje to domyślne 5.0)
            meta.addAttributeModifier(
                    Attribute.GENERIC_ATTACK_DAMAGE,
                    new AttributeModifier(UUID.randomUUID(), "minotaur_dmg", 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND)
            );
            meta.addAttributeModifier(
                    Attribute.GENERIC_ATTACK_SPEED,
                    new AttributeModifier(UUID.randomUUID(), "minotaur_speed", -2.7, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND)
            );

            // Ukrywamy brzydkie, generowane automatycznie szare teksty (-2.7 Attack Speed)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            // Własne przeliczone lore
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7When in Main Hand:");
            lore.add(" §25 Attack Damage");
            lore.add(" §21.3 Attack Speed");
            meta.setLore(lore);

            axe.setItemMeta(meta);
        }
        return axe;
    }

    private ItemStack createAbilityItem(int amount) {
        ItemStack item = new ItemStack(Material.BRICK, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lEarthquake §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Slam the ground in a cone ahead.");
            lore.add("§7Deals §c3 damage §7and pulls enemies");
            lore.add("§7Towards you.");
            lore.add("§8Cooldown: §f8s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRageItem(int amount) {
        ItemStack item = new ItemStack(Material.IRON_BLOCK, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lMinotaur's Rage §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Stand completely still for §f3 seconds§7.");
            lore.add("§7Upon channeling, grants §bSpeed II §7and");
            lore.add("§c2 Extra attack dmg §7for 6 seconds.");
            lore.add("§eMoving cancels the channel.");
            lore.add("§8Cooldown: §f30s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createDashItem(int amount) {
        ItemStack item = new ItemStack(Material.BONE, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lMinotaur's Charge §8(Right Click)");

            List<String> lore = new ArrayList<>();
            lore.add("§7Charge forward with unstoppable force.");
            lore.add("§7Deals §c3 damage§7, knocks back, and §eStuns");
            lore.add("§7all enemies hit for §f1.5 seconds§7.");
            lore.add("§8Cooldown: §f24s");
            meta.setLore(lore);

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
        if (event.getHand() != EquipmentSlot.HAND) return;
        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cEarthquake is recharging...");
            return;
        }

        int slot = p.getInventory().getHeldItemSlot();

        p.sendMessage("§f§lEARTHQUAKE!");
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.5f);
        drawShockwaveParticles(p);

        for (Entity entity : p.getNearbyEntities(6, 4, 6)) {
            if (!(entity instanceof LivingEntity) || entity == p) continue;
            if (plugin.isAlly(p, entity)) continue;

            Vector toEntity = entity.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
            if (p.getLocation().getDirection().normalize().dot(toEntity) > 0.7) {
                LivingEntity victim = (LivingEntity) entity;
                damageFromAbility(victim, p, 3.0); // Zadanie DMG z abilitki

                Vector pullBack = p.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.5);
                pullBack.setY(0.8);
                entity.setVelocity(pullBack);
            }
        }
        startCooldown(p, slot, Material.BRICK, 8);
    }

    @EventHandler
    public void onMinotaurRage(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.IRON_BLOCK) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cMinotaur's Rage is recharging...");
            return;
        }

        int slot = p.getInventory().getHeldItemSlot();

        p.sendMessage("§e§lPREPARING RAGE... DON'T MOVE!");
        p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0f, 0.8f);
        Location startLoc = p.getLocation().clone();

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (p.getLocation().distanceSquared(startLoc) > 0.3) {
                    p.sendMessage("§c§lINTERRUPTED! You moved!");
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
        startCooldown(p, slot, Material.IRON_BLOCK, 30);
    }

    private void applyRageEffects(Player p) {
        p.sendMessage("§4§lYOU FLY INTO A RAGE!!!");
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        int duration = 120;
        if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "minotaur_longer_rage")) {
            duration = 150;
        }
        final int rageDuration = duration;
        final int particleLoops = rageDuration / 20;

        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, rageDuration, 1));

        AttributeInstance attackAttribute = p.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        AttributeModifier rageDmgModifier = new AttributeModifier(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "minotaur_rage_dmg",
                2.0,
                AttributeModifier.Operation.ADD_NUMBER
        );

        if (attackAttribute != null) {
            attackAttribute.removeModifier(rageDmgModifier);
            attackAttribute.addModifier(rageDmgModifier);
        }

        new BukkitRunnable() {
            int timer = 0;

            @Override
            public void run() {
                if (!p.isOnline() || timer >= particleLoops) {
                    if (p.isOnline()) {
                        p.sendMessage("§7*Minotaur's Rage is fading...*");
                        if (attackAttribute != null) {
                            attackAttribute.removeModifier(rageDmgModifier);
                        }
                    }
                    this.cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.REDSTONE, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.RED, 2));
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.02);
                timer++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
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

    @EventHandler
    public void onMinotaurDash(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.BONE) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cCharge is recharging...");
            return;
        }

        int slot = p.getInventory().getHeldItemSlot();

        p.sendMessage("§b§lTRAMPLE ACTIVE!!!");
        p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2));

        Map<UUID, Long> hitCooldowns = new HashMap<>();

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!p.isOnline() || ticks > 80) {
                    this.cancel();
                    return;
                }

                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.4, 0.1, 0.4, 0.01);
                if (ticks % 10 == 0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1.0f, 0.8f);
                }

                for (Entity entity : p.getNearbyEntities(0.5, 0.5, 0.5)) {
                    if (entity instanceof LivingEntity && entity != p) {
                        if (plugin.isAlly(p, entity)) continue;

                        LivingEntity victim = (LivingEntity) entity;
                        UUID vId = victim.getUniqueId();
                        long now = System.currentTimeMillis();

                        if (hitCooldowns.containsKey(vId) && now - hitCooldowns.get(vId) < 1000) {
                            continue;
                        }
                        hitCooldowns.put(vId, now);

                        damageFromAbility(victim, p, 3.0); // Zadanie DMG z abilitki

                        Vector bounce = victim.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.4);
                        victim.setVelocity(bounce);

                        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.2f);
                        victim.sendMessage("§c§lYOU WERE TRAMPLED!");

                        if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "minotaur_dash_cooldown_reduction")) {
                            reduceDashCooldown(p, slot);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        startCooldown(p, slot, Material.BONE, 24);
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

                if (plugin.shopManager.hasMinotaurSpeed(player.getUniqueId())) {
                    ItemStack item = player.getInventory().getItem(0);
                    if (item != null && item.getType() == Material.IRON_AXE) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
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

    private void reduceDashCooldown(Player p, int slot) {
        ItemStack item = p.getInventory().getItem(slot);
        if (item != null && item.getType() == Material.BONE && item.getAmount() > 1) {
            int newAmount = Math.max(1, item.getAmount() - 1);
            item.setAmount(newAmount);
            p.getInventory().setItem(slot, item);
        }
    }

    private void updateCooldownItem(Player p, int slot, Material material, int amount) {
        ItemStack current = p.getInventory().getItem(slot);
        if (current == null || current.getType() != material) return;

        current.setAmount(amount);
        p.getInventory().setItem(slot, current);
    }

    @EventHandler
    public void onArmorClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player p = (Player) event.getWhoClicked();
            if (stunnedPlayersHotbar.containsKey(p.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (stunnedPlayersHotbar.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }
}