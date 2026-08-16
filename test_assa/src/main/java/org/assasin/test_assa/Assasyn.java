package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class Assasyn implements Listener {

    private final Main plugin;
    private final HashMap<UUID, ItemStack[]> armorStorage = new HashMap<>();

    public Assasyn(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(24.0);
        player.setHealth(24.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(8.0);
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
        player.getInventory().setItem(0, createSpamSword());
        player.getInventory().setItem(1, createShurikens(1));
        player.getInventory().setItem(2, createTeleportItem(1));
        player.getInventory().setItem(3, createInvisibilityItem(1));
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));

        ItemStack shopItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta shopMeta = shopItem.getItemMeta();
        if (shopMeta != null) {
            shopMeta.setDisplayName("§6§lSKLEP KLASOWY §7(Kliknij)");
            shopItem.setItemMeta(shopMeta);
        }

        player.getInventory().setItem(8, shopItem);
        player.getInventory().setHelmet(createUnbreakableHead(Material.WITHER_SKELETON_SKULL));
        player.getInventory().setChestplate(createZeroArmorPiece(Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST));
        player.getInventory().setLeggings(createZeroArmorPiece(Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS));
        player.getInventory().setBoots(createZeroArmorPiece(Material.LEATHER_BOOTS, EquipmentSlot.FEET));
    }

    @EventHandler
    public void onInvisibilityUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.INK_SAC) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        if (item.getAmount() > 1) return;

        if (plugin.getPaladyn() != null) {
            plugin.getPaladyn().removeMark(p.getUniqueId());
        }

        // Oznaczenie gracza jako "czystego" (nie zaatakował nikogo jeszcze)
        p.setMetadata("invisible_clean", new FixedMetadataValue(plugin, true));

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
        armorStorage.put(p.getUniqueId(), p.getInventory().getArmorContents());
        p.getInventory().setArmorContents(new ItemStack[4]);

        p.sendMessage("§7*Stajesz się jednością z cieniem*");
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    if (armorStorage.containsKey(p.getUniqueId())) {
                        p.getInventory().setArmorContents(armorStorage.get(p.getUniqueId()));
                        armorStorage.remove(p.getUniqueId());
                    }

                    // --- MECHANIKA UDERZENIA Z CIENIA ---
                    if (p.hasMetadata("invisible_clean")) {
                        // Sprawdzamy w sklepie czy wykupił ulepszenie
                        if (plugin.shopManager.hasShadowStrike(p.getUniqueId())) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20, 0)); // Siła I na 1 sek
                            p.sendMessage("§c§lSKUPIENIE! §7Twoje następne uderzenie będzie potężne.");
                            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
                        }
                        p.removeMetadata("invisible_clean", plugin);
                    }

                    // NAPRAWA HP Scoreboard
                    int currentHealth = (int) p.getHealth() / 2;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        Scoreboard sb = online.getScoreboard();
                        Objective hpObj = sb.getObjective("hp_name");
                        if (hpObj != null) {
                            hpObj.getScore(p.getName()).setScore(currentHealth);
                        }
                    }
                    p.sendMessage("§7*Twoja zasłona opada*");
                }
            }
        }.runTaskLater(plugin, 100L);

        item.setAmount(15);
        new BukkitRunnable() {
            int time = 15;
            @Override
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                time--;
                if (time <= 1) {
                    item.setAmount(1);
                    this.cancel();
                } else {
                    item.setAmount(time);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // Listener usuwający bonus, jeśli gracz zaatakuje kogoś podczas niewidki
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
        if (item.getAmount() > 1) return;

        Player target = null;
        double closestDistance = 5.0;
        for (Entity e : p.getNearbyEntities(5, 5, 5)) {
            if (e instanceof Player && e != p) {
                double dist = p.getLocation().distance(e.getLocation());
                if (dist < closestDistance) { closestDistance = dist; target = (Player) e; }
            }
        }
        if (target == null) { p.sendMessage("§cBrak gracza w zasięgu!"); return; }

        Vector direction = target.getLocation().getDirection().normalize();
        p.teleport(target.getLocation().subtract(direction.multiply(1.2)));
        p.getLocation().setDirection(target.getLocation().getDirection());
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        item.setAmount(10);
        new BukkitRunnable() {
            int time = 10;
            @Override
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                time--;
                if (time <= 1) { item.setAmount(1); this.cancel(); } else { item.setAmount(time); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onShurikenUse(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SNOWBALL) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        if (item.getAmount() > 1) return;

        for (int i = -1; i <= 1; i++) {
            Snowball s = p.launchProjectile(Snowball.class);
            s.setMetadata("shuriken", new FixedMetadataValue(plugin, true));
            Vector velocity = p.getLocation().getDirection().clone();
            if (i != 0) velocity.rotateAroundY(Math.toRadians(i * 10));
            s.setVelocity(velocity.multiply(1.5));
            Bukkit.getScheduler().runTaskLater(plugin, s::remove, 10L);
        }
        item.setAmount(5);
        new BukkitRunnable() {
            int time = 5;
            @Override
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                time--;
                if (time <= 1) { item.setAmount(1); this.cancel(); } else { item.setAmount(time); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onShurikenHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Snowball) {
            Snowball s = (Snowball) event.getDamager();

            if (s.hasMetadata("shuriken") && event.getEntity() instanceof LivingEntity) {
                LivingEntity victim = (LivingEntity) event.getEntity();

                // 1. ANULUJEMY EVENT - to usuwa domyślny dźwięk "śnieżki"
                event.setCancelled(true);

                if (s.getShooter() instanceof Player) {
                    Player shooter = (Player) s.getShooter();

                    // 2. ZADAJEMY OBRAŻENIA RĘCZNIE
                    victim.damage(2.0, shooter);

                    // 3. ODGRYWAMY WŁASNY DŹWIĘK (np. metaliczne cięcie lub uderzenie strzały)
                    // Jeśli chcesz całkowitą CISZĘ - po prostu usuń poniższą linię.
                }

                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
            }
        }
    }

    private ItemStack createSpamSword() {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8Sztylet Asasyna");
            meta.setUnbreakable(true);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(UUID.randomUUID(), "fast_attack", 100.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(UUID.randomUUID(), "base_damage", 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private ItemStack createInvisibilityItem(int amount) {
        ItemStack item = new ItemStack(Material.INK_SAC, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("§0Zasłona Cienia §8(Prawy Klik)"); item.setItemMeta(meta); }
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
        if (meta != null) { meta.setDisplayName("§7Shuriken §8(Prawy Klik)"); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack createTeleportItem(int amount) {
        ItemStack item = new ItemStack(Material.FEATHER, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName("§bKrok Cienia §8(Prawy Klik)"); item.setItemMeta(meta); }
        return item;
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