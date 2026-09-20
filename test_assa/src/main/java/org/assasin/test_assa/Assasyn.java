package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Assasyn implements Listener {

    private final Main plugin;
    private final HashMap<UUID, ItemStack[]> armorStorage = new HashMap<>();

    private final Set<UUID> invisibleActive = new HashSet<>();
    private final Set<UUID> interactLock = new HashSet<>();
    private final Map<UUID, BukkitTask> invisibilityTasks = new HashMap<>();

    public Assasyn(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(24.0);
        player.setHealth(24.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(6.0);

        AttributeInstance attackDmg = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackDmg != null) {
            attackDmg.setBaseValue(1.0);
        }

        player.getInventory().setItem(0, createSpamSword());
        player.getInventory().setItem(1, createShurikens(1));
        player.getInventory().setItem(2, createTeleportItem(1));
        player.getInventory().setItem(3, createInvisibilityItem(1));
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));

        ItemStack shopItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta shopMeta = shopItem.getItemMeta();
        if (shopMeta != null) {
            shopMeta.setDisplayName("§6§lCLASS SHOP §7(Click)");
            shopItem.setItemMeta(shopMeta);
        }

        player.getInventory().setItem(8, shopItem);
        player.getInventory().setHelmet(createUnbreakableHead(Material.WITHER_SKELETON_SKULL));
        player.getInventory().setChestplate(createZeroArmorPiece(Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST));
        player.getInventory().setLeggings(createZeroArmorPiece(Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS));
        player.getInventory().setBoots(createZeroArmorPiece(Material.LEATHER_BOOTS, EquipmentSlot.FEET));
    }

    private void damageFromAbility(LivingEntity victim, Player attacker, double damage) {
        victim.setMetadata("ability_damage", new FixedMetadataValue(plugin, true));
        victim.damage(damage, attacker);
        victim.removeMetadata("ability_damage", plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player p = (Player) event.getDamager();

        if (event.getEntity().hasMetadata("ability_damage")) {
            return;
        }

        ItemStack slotZero = p.getInventory().getItem(0);
        boolean isAssassinClass = slotZero != null
                && slotZero.hasItemMeta()
                && "§8Assassin's Dagger".equals(slotZero.getItemMeta().getDisplayName());

        if (isAssassinClass) {
            ItemStack mainHand = p.getInventory().getItemInMainHand();
            boolean hasAssassinDagger = mainHand != null
                    && mainHand.getType() == Material.WOODEN_SWORD
                    && mainHand.hasItemMeta()
                    && "§8Assassin's Dagger".equals(mainHand.getItemMeta().getDisplayName());

            if (!hasAssassinDagger) {
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
                AttributeInstance attackDmg = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                if (attackDmg != null) {
                    attackDmg.setBaseValue(1.0);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onInvisibilityUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.INK_SAC) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1 || p.hasCooldown(Material.INK_SAC)) {
            p.sendMessage("§cShadow Veil will be ready in: " + item.getAmount() + "s");
            return;
        }

        if (invisibleActive.contains(p.getUniqueId())) {
            return;
        }
        invisibleActive.add(p.getUniqueId());

        int slot = p.getInventory().getHeldItemSlot();

        if (plugin.getPaladyn() != null) {
            plugin.getPaladyn().removeMark(p.getUniqueId());
        }

        p.setMetadata("invisible_clean", new FixedMetadataValue(plugin, true));

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));

        if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "assasyn_invis_speed")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false));
        }

        p.setArrowsInBody(0);

        armorStorage.put(p.getUniqueId(), p.getInventory().getArmorContents());
        p.getInventory().setArmorContents(new ItemStack[4]);

        p.sendMessage("§7*You become one with the shadows*");
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.5f);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                finishInvisibility(p);
            }
        }.runTaskLater(plugin, 100L);
        invisibilityTasks.put(p.getUniqueId(), task);

        startCooldown(p, slot, Material.INK_SAC, 25);
    }

    private void finishInvisibility(Player p) {
        if (p.isOnline()) {
            if (armorStorage.containsKey(p.getUniqueId())) {
                p.getInventory().setArmorContents(armorStorage.get(p.getUniqueId()));
                armorStorage.remove(p.getUniqueId());
            }

            if (p.hasMetadata("invisible_clean")) {
                if (plugin.shopManager != null && plugin.shopManager.hasShadowStrike(p.getUniqueId())) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20, 0));
                    p.sendMessage("§c§lFOCUS! §7Your next hit will be powerful.");
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
                }
                p.removeMetadata("invisible_clean", plugin);
            }

            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "assasyn_invis_speed")) {
                p.removePotionEffect(PotionEffectType.SPEED);
            }

            int currentHealth = (int) p.getHealth() / 2;
            for (Player online : Bukkit.getOnlinePlayers()) {
                Scoreboard sb = online.getScoreboard();
                Objective hpObj = sb.getObjective("hp_name");
                if (hpObj != null) {
                    hpObj.getScore(p.getName()).setScore(currentHealth);
                }
            }
            p.sendMessage("§7*Your veil fades*");
        }

        invisibleActive.remove(p.getUniqueId());
        invisibilityTasks.remove(p.getUniqueId());
    }

    private void endInvisibilityEarly(Player p) {
        BukkitTask task = invisibilityTasks.get(p.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        p.sendMessage("§c§lEXPOSED! §7You took damage and fell out of the shadows!");
        finishInvisibility(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAssasynDamaged(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player p = (Player) event.getEntity();
        if (!invisibleActive.contains(p.getUniqueId())) return;

        if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "assasyn_invis_no_break")) {
            return;
        }

        endInvisibilityEarly(p);
    }

    @EventHandler
    public void onInvisAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            if (attacker.hasMetadata("invisible_clean")) {
                attacker.removeMetadata("invisible_clean", plugin);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        p.removeMetadata("invisible_clean", plugin);
        invisibleActive.remove(p.getUniqueId());
        interactLock.remove(p.getUniqueId());
        invisibilityTasks.remove(p.getUniqueId());
        if (armorStorage.containsKey(p.getUniqueId())) {
            p.getInventory().setArmorContents(armorStorage.get(p.getUniqueId()));
            armorStorage.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onTeleportUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FEATHER) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1 || p.hasCooldown(Material.FEATHER)) return;

        int slot = p.getInventory().getHeldItemSlot();

        Player target = null;
        double closestDistance = 5.0;
        for (Entity e : p.getNearbyEntities(5, 5, 5)) {
            if (e instanceof Player && e != p) {
                if (plugin.isAlly(p, e)) continue;

                double dist = p.getLocation().distance(e.getLocation());
                if (dist < closestDistance) { closestDistance = dist; target = (Player) e; }
            }
        }
        if (target == null) { p.sendMessage("§cNo player in range!"); return; }

        Vector direction = target.getLocation().getDirection().normalize();
        Location targetLoc = target.getLocation().subtract(direction.multiply(1.2));
        targetLoc.setDirection(target.getLocation().getDirection());

        if (!targetLoc.getBlock().isPassable() || !targetLoc.clone().add(0, 1, 0).getBlock().isPassable()) {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§cCan't teleport into blocks"));
            startCooldown(p, slot, Material.FEATHER, 3);
            return;
        }

        p.teleport(targetLoc);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        startCooldown(p, slot, Material.FEATHER, 10);
    }

    @EventHandler
    public void onShurikenUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SNOWBALL) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1 || p.hasCooldown(Material.SNOWBALL)) return;

        int slot = p.getInventory().getHeldItemSlot();

        for (int i = -1; i <= 1; i++) {
            Snowball s = p.launchProjectile(Snowball.class);
            s.setMetadata("shuriken", new FixedMetadataValue(plugin, true));
            Vector velocity = p.getLocation().getDirection().clone();
            if (i != 0) velocity.rotateAroundY(Math.toRadians(i * 10));
            s.setVelocity(velocity.multiply(1.5));
            Bukkit.getScheduler().runTaskLater(plugin, s::remove, 10L);
        }

        startCooldown(p, slot, Material.SNOWBALL, 6);
    }

    @EventHandler
    public void onShurikenHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Snowball)) return;
        Snowball s = (Snowball) event.getDamager();

        if (!s.hasMetadata("shuriken") || !(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = (LivingEntity) event.getEntity();
        event.setCancelled(true);

        if (s.getShooter() instanceof Player) {
            Player shooter = (Player) s.getShooter();

            if (plugin.isAlly(shooter, victim)) {
                return;
            }

            victim.setNoDamageTicks(0);
            victim.setLastDamage(0.0);
            damageFromAbility(victim, shooter, 1.0);

            victim.setNoDamageTicks(0);
            victim.setLastDamage(0.0);
        }

        int addedDuration = 20;
        PotionEffect existing = victim.getPotionEffect(PotionEffectType.POISON);
        int newDuration = addedDuration + (existing != null ? existing.getDuration() : 0);

        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, newDuration, 1), true);
    }

    private ItemStack createSpamSword() {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8Assassin's Dagger");
            meta.setUnbreakable(true);

            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "fast_attack", 100.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "base_damage", 3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7When in Main Hand:");
            lore.add(" §24 Attack Damage");
            lore.add(" §2Spam Click");
            meta.setLore(lore);

            sword.setItemMeta(meta);
        }
        return sword;
    }

    private ItemStack createInvisibilityItem(int amount) {
        ItemStack item = new ItemStack(Material.INK_SAC, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fShadow Veil §8(Right Click)");
            List<String> lore = new ArrayList<>();
            lore.add("§7Makes you invisible for §f5 seconds§7.");
            lore.add("§cTaking damage breaks the invisibility!");
            lore.add("§8Cooldown: §f25s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createUnbreakableHead(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setUnbreakable(true); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack createZeroArmorPiece(Material material, EquipmentSlot slot) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.BLACK);
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(UUID.randomUUID(), "no_armor", 0, AttributeModifier.Operation.ADD_NUMBER, slot));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShurikens(int amount) {
        ItemStack item = new ItemStack(Material.SNOWBALL, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Shuriken §8(Right Click)");
            List<String> lore = new ArrayList<>();
            lore.add("§7Throw a spread of §f3 shurikens§7.");
            lore.add("§7Each hit deals §c1 damage §7and applies");
            lore.add("§7Poison II for §a1 second §7(stackable).");
            lore.add("§8Cooldown: §f6s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTeleportItem(int amount) {
        ItemStack item = new ItemStack(Material.FEATHER, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bShadow Step §8(Right Click)");
            List<String> lore = new ArrayList<>();
            lore.add("§7Teleport behind the nearest enemy ");
            lore.add("§7within a §f5-block §7range.");
            lore.add("§8Cooldown: §f10s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private void startCooldown(Player p, int slot, Material material, int seconds) {
        p.setCooldown(material, seconds * 20);
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
        if (current != null && current.getType() == material) {
            current.setAmount(amount);
            p.getInventory().setItem(slot, current);
            return;
        }

        // Przeszukanie całego hotbaru w razie przesunięcia przedmiotu lub stuna
        for (int i = 0; i < 9; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                item.setAmount(amount);
                p.getInventory().setItem(i, item);
                return;
            }
        }
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