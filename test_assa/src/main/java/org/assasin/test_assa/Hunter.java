package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Hunter implements Listener {

    private final Main plugin;

    private final Set<UUID> slidingPlayers = new HashSet<>();
    private final Set<UUID> hunterPlayers = new HashSet<>();
    private final Set<UUID> fireArrowNext = new HashSet<>();
    private final Map<UUID, Integer> instaShotsRemaining = new HashMap<>();

    // Zamek na jeden tick - blokuje podwójne odpalenie PlayerInteractEvent
    private final Set<UUID> interactLock = new HashSet<>();

    // NOWE (talent "Light Foot"): trzymamy [slot, pozostałe sekundy] aktywnego cooldownu
    // Slajdu, żeby móc go bezpiecznie SKRÓCIĆ z poziomu zadania strzały, niezależnie
    // od tego, w jakim slocie gracz aktualnie trzyma przedmiot.
    private final Map<UUID, int[]> slideCooldownState = new HashMap<>();
    private static final int SLIDE_COOLDOWN_REDUCTION_ON_HIT = 5; // sekundy, patrz talent w TvTShopManager

    // Własny system obrażeń z łuku.
    private static final double HUNTER_BASE_MAX_ARROW_DAMAGE = 5.0;
    private static final String SHOT_DAMAGE_METADATA_KEY = "hunter_shot_damage";
    private static final String CUSTOM_ARROW_TAG = "hunter_custom_arrow";

    public Hunter(Main plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        player.getInventory().clear();
        hunterPlayers.add(player.getUniqueId());

        // --- STATYSTYKI ---
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(16.0);
        player.setHealth(16.0);
        player.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(4.0);
        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);

        // --- EKWIPUNEK ---
        player.getInventory().setItem(0, createHunterBow());
        player.getInventory().setItem(1, createSlideItem(1));
        player.getInventory().setItem(2, createFireArrowItem(1));
        player.getInventory().setItem(3, createInstaShotItem(1));

        player.getInventory().setItem(8, createShopItem());
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_PORKCHOP, 64));
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));

        player.getInventory().setChestplate(createUnbreakableArmor(Material.LEATHER_CHESTPLATE));
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

    // --- SLAJD ---
    @EventHandler
    public void onSlide(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.WHITE_CARPET) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cSlide will be ready in: " + item.getAmount() + "s");
            return;
        }

        int slot = p.getInventory().getHeldItemSlot();

        UUID uuid = p.getUniqueId();
        slidingPlayers.add(uuid);

        Vector direction = p.getLocation().getDirection().setY(0).normalize();
        Vector slide = direction.multiply(1.8).setY(0.01);
        p.setVelocity(slide);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.5f, 1.2f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.1, 0), 15, 0.3, 0, 0.3, 0.05);
        p.sendMessage("§2§lSLIDE!");

        // Talent: Pursuit - Speed I for 8 seconds (160 ticks) after Sliding
        if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(p, "hunter_speed_after_slide")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, false, false));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                slidingPlayers.remove(uuid);
            }
        }.runTaskLater(plugin, 8L);

        startSlideCooldown(p, slot, 16);
    }

    // --- UMIEJĘTNOŚĆ OGNISTEJ STRZAŁY ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireArrowAbility(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.PRISMARINE_SHARD) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cFire Arrow will be ready in: " + item.getAmount() + "s");
            return;
        }

        int slot = p.getInventory().getHeldItemSlot();

        fireArrowNext.add(p.getUniqueId());

        p.sendMessage("§c§lFIRE ARROW! §7Your next shot will set the target ablaze!");
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.FLAME, p.getEyeLocation(), 15, 0.2, 0.2, 0.2, 0.05);

        startCooldown(p, slot, Material.PRISMARINE_SHARD, 10);
    }

    // --- SZYBKI STRZAŁ ---
    @EventHandler
    public void onInstaShotAbility(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.BLAZE_POWDER) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        event.setCancelled(true);

        if (interactLock.contains(p.getUniqueId())) return;
        interactLock.add(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> interactLock.remove(p.getUniqueId()));

        if (item.getAmount() > 1) {
            p.sendMessage("§cQuick Shot will be ready in: " + item.getAmount() + "s");
            return;
        }

        int slot = p.getInventory().getHeldItemSlot();

        instaShotsRemaining.put(p.getUniqueId(), 6);
        p.sendMessage("§e§lQUICK SHOT! §7The next 6 arrows will fire at full power instantly!");
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1.5f);
        p.getWorld().spawnParticle(Particle.FLAME, p.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.02);

        startCooldown(p, slot, Material.BLAZE_POWDER, 30);
    }

    // --- STRZELANIE Z ŁUKU ---
    @EventHandler
    public void onHunterBowShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player shooter = (Player) e.getEntity();

        if (!hunterPlayers.contains(shooter.getUniqueId())) return;

        Integer remaining = instaShotsRemaining.get(shooter.getUniqueId());
        boolean instaShot = remaining != null && remaining > 0;

        // BLAZE POWDER - Szybki strzał
        if (instaShot) {
            if (e.getProjectile() instanceof Arrow) {
                Arrow arrow = (Arrow) e.getProjectile();
                Vector direction = shooter.getEyeLocation().getDirection();
                arrow.setVelocity(direction.multiply(3.0));
            }

            remaining--;
            if (remaining <= 0) {
                instaShotsRemaining.remove(shooter.getUniqueId());
                shooter.sendMessage("§7Quick shots have run out.");
            } else {
                instaShotsRemaining.put(shooter.getUniqueId(), remaining);
            }
        }

        // PRISMARINE SHARD - Ognista strzała
        if (fireArrowNext.contains(shooter.getUniqueId())) {
            if (e.getProjectile() instanceof Arrow) {
                Arrow arrow = (Arrow) e.getProjectile();
                arrow.setFireTicks(80);
            }
            fireArrowNext.remove(shooter.getUniqueId());
        }

        if (e.getProjectile() instanceof Arrow) {
            Arrow arrow = (Arrow) e.getProjectile();
            arrow.setMetadata(CUSTOM_ARROW_TAG, new FixedMetadataValue(plugin, true));

            // Maksymalny poziom przebicia (Piercing) - dzięki temu silnik gry NIE zatrzymuje
            // strzały fizycznie po dotknięciu żadnej encji (sojusznik czy wróg), niezależnie
            // od tego co robimy w Bukkicie. Realne trafienie/obrażenia i usunięcie strzały
            // obsługujemy w 100% ręcznie w startArrowPiercingTask poniżej.
            arrow.setPierceLevel(127);

            ItemStack bow = e.getBow();
            int powerLevel = (bow != null) ? bow.getEnchantmentLevel(Enchantment.ARROW_DAMAGE) : 0;
            double maxDamage = HUNTER_BASE_MAX_ARROW_DAMAGE + powerLevel;

            // Szybki Strzał leci z pełną mocą niezależnie od realnego naciągu.
            double force = instaShot ? 1.0 : e.getForce();

            double shotDamage = maxDamage * force;
            arrow.setMetadata(SHOT_DAMAGE_METADATA_KEY, new FixedMetadataValue(plugin, shotDamage));
        }
    }

    /**
     * Zadanie śledzące lot strzały co tick - pozwala jej przenikać przez sojuszników i miniony
     * (plugin.isAlly), a strzała znika dopiero po realnym trafieniu wroga.
     *
     * WAŻNE (poprawka tunelowania): strzała potrafi pokonać kilka bloków na tick (jeszcze więcej
     * przy Szybkim Strzale, który ma 3x prędkość), więc sprawdzanie samej okolicy AKTUALNEJ
     * pozycji strzały (promień 1 blok) regularnie "przeskakiwało" nad celem stojącym pomiędzy
     * dwiema kolejnymi klatkami. Zamiast tego robimy rayTraceEntities na CAŁYM odcinku
     * pokonanym w tym ticku (od poprzedniej do obecnej pozycji strzały) - to wykrywa każdego,
     * kogo strzała realnie "przeleciała", niezależnie od jej prędkości.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowCollision(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) e.getEntity();
        if (!arrow.hasMetadata(CUSTOM_ARROW_TAG)) return;

        if (!(arrow.getShooter() instanceof Player)) return;
        Player shooter = (Player) arrow.getShooter();

        if (e.getHitEntity() != null && e.getHitEntity() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) e.getHitEntity();

            // 1. SOJUSZNIK - Anulujemy kolizję. Strzała przelatuje jak przez ducha!
            if (plugin.isAlly(shooter, target)) {
                e.setCancelled(true);
                return;
            }

            // 2. WRÓG - Zadajemy ręczne obrażenia i aplikujemy talenty
            double shotDamage = HUNTER_BASE_MAX_ARROW_DAMAGE;
            if (arrow.hasMetadata(SHOT_DAMAGE_METADATA_KEY)) {
                shotDamage = arrow.getMetadata(SHOT_DAMAGE_METADATA_KEY).get(0).asDouble();
            }

            // Aplikujemy obrażenia. Używamy 'shooter' jako sprawcy, więc gra uzna,
            // że to gracz zadał obrażenia, a nie sama strzała.
            target.damage(shotDamage, shooter);
            target.setNoDamageTicks(0);

            // Obsługa podpalenia
            if (arrow.getFireTicks() > 0) {
                target.setFireTicks(arrow.getFireTicks());
            }

            // Talent: Light Foot
            if (plugin.tvtShopManager != null && plugin.tvtShopManager.hasTalent(shooter, "hunter_arrow_hit_slide_cooldown")) {
                reduceSlideCooldown(shooter);
            }

            arrow.getWorld().playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT, 1f, 1f);

            // Usuwamy strzałę od razu po trafieniu we wroga
            arrow.remove();
        }
    }

    // Blokujemy domyślne obrażenia ze strzały, żeby wróg nie dostał hita 2 razy
    // (raz z Minecrafta i raz z naszego kodu wyżej)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventVanillaDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Arrow && e.getDamager().hasMetadata(CUSTOM_ARROW_TAG)) {
            e.setCancelled(true);
        }
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
            meta.setDisplayName("§2§lHunter's Bow");
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
            bow.setItemMeta(meta);
        }
        return bow;
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

    private ItemStack createSlideItem(int amount) {
        ItemStack item = new ItemStack(Material.WHITE_CARPET, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2§lAgile Slide §8(Right Click)");

            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Dash forward rapidly across the ground.");
            lore.add("§8Cooldown: §f16s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFireArrowItem(int amount) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lFire Arrow §8(Right Click)");

            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Empowers your next bow shot to ignite");
            lore.add("§7the target, setting them on fire.");
            lore.add("§8Cooldown: §f10s");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInstaShotItem(int amount) {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lQuick Shot §8(Right Click)");

            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Your next §f6 arrows §7fire at full power");
            lore.add("§7instantly without charging the bow.");
            lore.add("§8Cooldown: §f30s");
            meta.setLore(lore);

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

    private void startSlideCooldown(Player p, int slot, int seconds) {
        UUID uuid = p.getUniqueId();
        slideCooldownState.put(uuid, new int[]{slot, seconds});
        updateCooldownItem(p, slot, Material.WHITE_CARPET, seconds);

        new BukkitRunnable() {
            @Override
            public void run() {
                int[] state = slideCooldownState.get(uuid);
                if (!p.isOnline() || state == null) {
                    this.cancel();
                    return;
                }

                if (state[1] <= 1) {
                    updateCooldownItem(p, state[0], Material.WHITE_CARPET, 1);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
                    slideCooldownState.remove(uuid);
                    this.cancel();
                    return;
                }

                state[1]--;
                updateCooldownItem(p, state[0], Material.WHITE_CARPET, state[1]);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void reduceSlideCooldown(Player p) {
        int[] state = slideCooldownState.get(p.getUniqueId());
        if (state == null) return;

        state[1] = Math.max(1, state[1] - SLIDE_COOLDOWN_REDUCTION_ON_HIT);
        updateCooldownItem(p, state[0], Material.WHITE_CARPET, state[1]);
    }

    private void updateCooldownItem(Player p, int slot, Material material, int amount) {
        ItemStack current = p.getInventory().getItem(slot);
        if (current == null || current.getType() != material) return;

        current.setAmount(amount);
        p.getInventory().setItem(slot, current);
    }

    @EventHandler public void onArmorClick(InventoryClickEvent e) { if (e.getSlotType() == InventoryType.SlotType.ARMOR) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { e.setCancelled(true); }
}