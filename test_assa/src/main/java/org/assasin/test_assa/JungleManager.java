package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Giant;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JungleManager implements Listener {

    private final Main plugin;
    private final NamespacedKey bankKey;
    private final Map<String, Integer> bankDeathCounts = new HashMap<>();

    // ZMIANA TUTAJ: Usuń znaki równości i przypisania
    private final NamespacedKey bankTierKey;
    private final NamespacedKey bankIdKey;
    private final NamespacedKey llamaKey;
    private final NamespacedKey llamaTierKey;
    private final NamespacedKey llamaIdKey;
    private final Map<String, Integer> llamaDeathCounts = new HashMap<>();
    private final NamespacedKey giantKey;
    private final NamespacedKey golemKey;
    private final NamespacedKey chickenJockeyTeamKey;
    private final NamespacedKey minionTeamKey;

    private final Map<UUID, Long> llamaAttackCooldown = new HashMap<>();
    private final Map<UUID, Long> giantAttackCooldown = new HashMap<>();
    private final Map<UUID, Long> giantMeleeCooldown = new HashMap<>(); // NOWE: cooldown ataku melee giganta
    private final List<Entity> activeChickenJockeys = new ArrayList<>();
    // --- SYSTEM DŻUNGLI (SPAWNERY) ---
    private BukkitTask jungleTask;
    private final List<JungleSpawner> activeSpawners = new ArrayList<>();

    // --- NOWE: SYSTEM GOLEMA (BUFFY DRUŻYNOWE) ---
    // Liczba zabitych golemów przez daną drużynę (max 6 -> max buff)
    private final Map<String, Integer> teamGolemKills = new HashMap<>();
    // Trwały (do końca gry) bonus HP dla minionów melee (pigliny) danej drużyny
    private final Map<String, Double> teamMeleeMinionHpBonus = new HashMap<>();
    // Trwały (do końca gry) bonus HP dla szkieletów danej drużyny
    private final Map<String, Double> teamSkeletonMinionHpBonus = new HashMap<>();
    // Aktualny tier napierśnika (kirysa) dla zbuforowanych minionów danej drużyny
    private final Map<String, Material> teamGolemChestplateTier = new HashMap<>();

    private static final Material[] GOLEM_CHESTPLATE_TIERS = new Material[] {
            Material.LEATHER_CHESTPLATE,
            Material.GOLDEN_CHESTPLATE,
            Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE,
            Material.NETHERITE_CHESTPLATE
    };
    private static final int GOLEM_MAX_BUFFS = GOLEM_CHESTPLATE_TIERS.length;

    public JungleManager(Main plugin) {
        this.plugin = plugin;
        this.bankKey = new NamespacedKey(plugin, "jungle_bank_pig");
        this.bankTierKey = new NamespacedKey(plugin, "bank_tier");
        this.bankIdKey = new NamespacedKey(plugin, "bank_id");
        this.llamaKey = new NamespacedKey(plugin, "jungle_llama");
        this.llamaTierKey = new NamespacedKey(plugin, "llama_tier");
        this.llamaIdKey = new NamespacedKey(plugin, "llama_id");
        this.giantKey = new NamespacedKey(plugin, "jungle_giant");
        this.golemKey = new NamespacedKey(plugin, "jungle_golem");
        this.chickenJockeyTeamKey = new NamespacedKey(plugin, "jungle_chicken_jockey_team");
        this.minionTeamKey = new NamespacedKey(plugin, "minion_team");
    }

    /**
     * Startuje system spawnowania na mapa TvT na sztywno określonych koordynatach
     */
    public void startJungleSystem(Location base1, Location base2) {
        World world = null;
        if (base1 != null && base1.getWorld() != null) {
            world = base1.getWorld();
        } else if (base2 != null && base2.getWorld() != null) {
            world = base2.getWorld();
        } else {
            world = Bukkit.getWorlds().get(0);
        }

        startJungleSystem(world);
    }

    public void startJungleSystem() {
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (defaultWorld != null) {
            startJungleSystem(defaultWorld);
        }
    }

    public void startJungleSystem(World world) {
        stopJungleSystem();

        if (world == null) return;

        // Reset stanu buffów golema na start nowej gry
        teamGolemKills.clear();
        teamMeleeMinionHpBonus.clear();
        teamSkeletonMinionHpBonus.clear();
        teamGolemChestplateTier.clear();

        // -- NOWE SPAWNY JUNGLI NA KORDACH --
        // GOLEM CAMP: 73.5, 112, 183.5 (Spawn po 5 min [300s], respawn co 5 min [300s] po zabiciu)
        // (zastępuje dawną 3. dodatkową świnię, która spawniła się po 3 minutach)
        Location golemLoc = new Location(world, 73.5, 112, 183.5);
        activeSpawners.add(new JungleSpawner(MobType.GOLEM, golemLoc, 300, 300));

        // BANK 2: 128.5, 112, 165.5 (Spawn od razu [0s], respawn co 90s)
        Location bank2Loc = new Location(world, 128.5, 112, 165.5);
        activeSpawners.add(new JungleSpawner(MobType.BANK, bank2Loc, 0, 90));

        // BANK 3: 91.5, 112, 128.5 (Spawn od razu [0s], respawn co 90s)
        Location bank3Loc = new Location(world, 91.5, 112, 128.5);
        activeSpawners.add(new JungleSpawner(MobType.BANK, bank3Loc, 0, 90));

        // GIANT (BOSS): 169, 114, 97 (Spawn po 10 min [600s], respawn co 5 min [300s])
        Location giantLoc = new Location(world, 169.5, 114, 97.5);
        activeSpawners.add(new JungleSpawner(MobType.GIANT, giantLoc, 600, 300));

        // LLAMA 1: 128, 112, 86 (Spawn po 2 min [120s], respawn co 3 min [180s])
        Location llama1Loc = new Location(world, 128.5, 112, 86.5);
        activeSpawners.add(new JungleSpawner(MobType.LLAMA, llama1Loc, 120, 180));

        // LLAMA 2: 173, 112, 131 (Spawn po 2 min [120s], respawn co 3 min [180s])
        Location llama2Loc = new Location(world, 173.5, 112, 131.5);
        activeSpawners.add(new JungleSpawner(MobType.LLAMA, llama2Loc, 120, 180));

        // Pętla odliczająca czas spawnerów (co 1 sekunda)
        jungleTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (JungleSpawner spawner : activeSpawners) {
                    if (spawner.entity != null && spawner.entity.isValid() && !spawner.entity.isDead()) {
                        continue;
                    }

                    if (spawner.nextSpawnTime == 0) {
                        // Mob zginął -> zapisujemy czas na kolejny respawn
                        spawner.nextSpawnTime = now + (spawner.respawnDelaySeconds * 1000L);
                    } else if (now >= spawner.nextSpawnTime) {
                        // Czas minął -> respawn
                        spawnMobBySpawner(spawner);
                        spawner.nextSpawnTime = 0;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnMobBySpawner(JungleSpawner spawner) {
        if (spawner.type == MobType.BANK) {
            spawner.entity = spawnBankPig(spawner.location);
        } else if (spawner.type == MobType.LLAMA) {
            spawner.entity = spawnLlama(spawner.location);
        } else if (spawner.type == MobType.GIANT) {
            spawner.entity = spawnGiant(spawner.location);
            Bukkit.broadcastMessage("§4§lBOSS §8» §c§lThe Giant has respawned in the middle of the map!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.0f);
            }
        } else if (spawner.type == MobType.GOLEM) {
            spawner.entity = spawnGolem(spawner.location);
            Bukkit.broadcastMessage("§b§lGOLEM §8» §f§lThe Golem has respawned in the jungle!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 0.7f, 1.0f);
            }
        }
    }

    /**
     * Zatrzymuje system i usuwa moby (na koniec meczu)
     */
    public void stopJungleSystem() {
        if (jungleTask != null) {
            jungleTask.cancel();
            jungleTask = null;
        }

        for (JungleSpawner spawner : activeSpawners) {
            if (spawner.entity != null && !spawner.entity.isDead()) {
                spawner.entity.remove();
            }
        }
        activeSpawners.clear();

        // Usunięcie aktywnych chicken jockeyów
        for (Entity entity : new ArrayList<>(activeChickenJockeys)) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        activeChickenJockeys.clear();

        // Reset stanu buffów golema na koniec meczu
        teamGolemKills.clear();
        teamMeleeMinionHpBonus.clear();
        teamSkeletonMinionHpBonus.clear();
        teamGolemChestplateTier.clear();

        // --- ZEROWANIE TIERÓW I LICZNIKÓW DŻUNGLI NA KONIEC GRY ---
        bankDeathCounts.clear();
        llamaDeathCounts.clear();
    }
    // --- METODY SPAWNOWANIA JEDNOSTEK ---


    public Pig spawnBankPig(Location spawnLoc) {
        if (spawnLoc == null || spawnLoc.getWorld() == null) return null;

        // Tworzymy unikalne ID dla tego konkretnego banku na podstawie jego koordynatów
        String bankId = spawnLoc.getWorld().getName() + "_" + spawnLoc.getBlockX() + "_" + spawnLoc.getBlockY() + "_" + spawnLoc.getBlockZ();

        // Obliczanie tieru na podstawie historii śmierci DLA TEGO KONKRETNEGO ID
        int pastDeaths = bankDeathCounts.getOrDefault(bankId, 0);
        int spawnIndex = pastDeaths + 1;

        int tier = 1;
        if (spawnIndex >= 5 && spawnIndex <= 9) {
            tier = 2;
        } else if (spawnIndex >= 10) {
            tier = 3;
        }

        Pig pig = (Pig) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.PIG);

        double hp = 100.0;
        if (tier == 2) hp = 125.0;
        if (tier == 3) hp = 150.0;

        AttributeInstance maxHealthAttr = pig.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) maxHealthAttr.setBaseValue(hp);
        pig.setHealth(hp);

        AttributeInstance speedAttr = pig.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.0);

        // Zapisujemy w PDC świni, do którego punktu spawnu należy
        pig.getPersistentDataContainer().set(bankKey, PersistentDataType.BYTE, (byte) 1);
        pig.getPersistentDataContainer().set(bankTierKey, PersistentDataType.INTEGER, tier);
        pig.getPersistentDataContainer().set(bankIdKey, PersistentDataType.STRING, bankId);

        // Ustawienie nazwy zgodnie z tierem
        pig.setCustomName("§aBank Tier " + tier);
        pig.setCustomNameVisible(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!pig.isValid() || pig.isDead()) {
                    cancel();
                    return;
                }
                if (pig.getLocation().distance(spawnLoc) > 8.0) {
                    pig.teleport(spawnLoc);
                    pig.setVelocity(new Vector(0, 0, 0));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        return pig;
    }

    public Llama spawnLlama(Location spawnLoc) {
        if (spawnLoc == null || spawnLoc.getWorld() == null) return null;

        // Tworzymy unikalne ID dla tej konkretnej lamy na podstawie jej koordynatów
        String llamaId = spawnLoc.getWorld().getName() + "_" + spawnLoc.getBlockX() + "_" + spawnLoc.getBlockY() + "_" + spawnLoc.getBlockZ();

        // Obliczanie tieru na podstawie historii śmierci DLA TEGO KONKRETNEGO ID
        int pastDeaths = llamaDeathCounts.getOrDefault(llamaId, 0);
        int spawnIndex = pastDeaths + 1;

        int tier = 1;
        double maxHp = 75.0;

        if (spawnIndex >= 5 && spawnIndex <= 9) {
            tier = 2;
            maxHp = 100.0;
        } else if (spawnIndex >= 10) {
            tier = 3;
            maxHp = 150.0;
        }

        Llama llama = (Llama) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.LLAMA);

        // Zapisanie tagów w PDC
        llama.getPersistentDataContainer().set(llamaKey, PersistentDataType.BYTE, (byte) 1);
        llama.getPersistentDataContainer().set(llamaTierKey, PersistentDataType.INTEGER, tier);
        llama.getPersistentDataContainer().set(llamaIdKey, PersistentDataType.STRING, llamaId);

        AttributeInstance maxHealthAttr = llama.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) maxHealthAttr.setBaseValue(maxHp);
        llama.setHealth(maxHp);

        AttributeInstance speedAttr = llama.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.0);

        updateLlamaName(llama);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!llama.isValid() || llama.isDead()) {
                    cancel();
                    return;
                }

                if (llama.getLocation().distance(spawnLoc) > 10.0) {
                    llama.teleport(spawnLoc);
                    llama.setVelocity(new Vector(0, 0, 0));
                    return;
                }

                Player targetPlayer = null;
                double nearestDistance = 8.0;

                for (Entity entity : llama.getWorld().getNearbyEntities(llama.getLocation(), 8.0, 8.0, 8.0)) {
                    if (entity instanceof Player) {
                        Player p = (Player) entity;
                        if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                            double dist = p.getLocation().distance(llama.getLocation());
                            if (dist < nearestDistance) {
                                nearestDistance = dist;
                                targetPlayer = p;
                            }
                        }
                    }
                }

                if (targetPlayer != null) {
                    long lastAttack = llamaAttackCooldown.getOrDefault(llama.getUniqueId(), 0L);
                    if (System.currentTimeMillis() - lastAttack >= 2000) {
                        llamaAttackCooldown.put(llama.getUniqueId(), System.currentTimeMillis());

                        Location llamaHead = llama.getEyeLocation();
                        Location targetLoc = targetPlayer.getLocation().add(0, 1.0, 0);
                        Vector spitDirection = targetLoc.toVector().subtract(llamaHead.toVector()).normalize().multiply(1.5);

                        LlamaSpit spit = llama.launchProjectile(LlamaSpit.class, spitDirection);
                        spit.setShooter(llama);

                        llama.getWorld().playSound(llama.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.0f, 1.0f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        return llama;
    }
    public Giant spawnGiant(Location spawnLoc) {
        if (spawnLoc == null || spawnLoc.getWorld() == null) return null;

        Giant giant = (Giant) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.GIANT);
        giant.setRemoveWhenFarAway(false);
        giant.setPersistent(true);

        double maxHealth = 300.0;
        AttributeInstance maxHealthAttr = giant.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) maxHealthAttr.setBaseValue(maxHealth);
        giant.setHealth(maxHealth);
        giant.setAI(false);

        giant.getPersistentDataContainer().set(giantKey, PersistentDataType.BYTE, (byte) 1);
        updateGiantName(giant);

        // Gigant nie płonie w słońcu - co tick zerujemy ewentualny ogień
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!giant.isValid() || giant.isDead()) {
                    cancel();
                    return;
                }
                if (giant.getFireTicks() > 0) {
                    giant.setFireTicks(0);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // REGENERACJA: co 2 sekundy (40 ticków) leczy o 1 HP (pół serca)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!giant.isValid() || giant.isDead()) {
                    cancel();
                    return;
                }

                AttributeInstance maxHpAttr = giant.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double max = maxHpAttr != null ? maxHpAttr.getBaseValue() : 300.0;
                if (giant.getHealth() < max) {
                    double newHealth = Math.min(max, giant.getHealth() + 1.0); // +1 HP (pół serca)
                    giant.setHealth(newHealth);
                    updateGiantName(giant);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // 40L = 2 sekundy

        BossBar bossBar = Bukkit.createBossBar("§4§lBOSS GIANT §7[300/300 HP]", BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!giant.isValid() || giant.isDead()) {
                    bossBar.removeAll();
                    cancel();
                    return;
                }

                AttributeInstance maxHpAttr = giant.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp = maxHpAttr != null ? maxHpAttr.getBaseValue() : 300.0;
                double currentHp = giant.getHealth();

                // Prawidłowe przeliczanie postępu oraz tekstu BossBara
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, currentHp / maxHp)));
                bossBar.setTitle("§4§lBOSS GIANT §7[§c" + (int) Math.ceil(currentHp) + "§7/§c" + (int) maxHp + " HP§7]");

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().equals(giant.getWorld()) && player.getLocation().distance(giant.getLocation()) <= 30.0) {
                        if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
                    } else {
                        bossBar.removePlayer(player);
                    }
                }

                // --- ATAK AOE (fala uderzeniowa): co 30s, dmg 4.0 ---
                long lastAoeAttack = giantAttackCooldown.getOrDefault(giant.getUniqueId(), 0L);
                if (System.currentTimeMillis() - lastAoeAttack >= 30000) {
                    boolean attacked = false;
                    for (Entity entity : giant.getNearbyEntities(6.0, 6.0, 6.0)) {
                        if (entity instanceof Player) {
                            Player p = (Player) entity;
                            if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                                if (!p.isDead()) {
                                    p.damage(4.0);
                                    Vector knockback = p.getLocation().toVector().subtract(giant.getLocation().toVector()).normalize().setY(0.4).multiply(1.2);
                                    p.setVelocity(knockback);
                                    p.sendMessage("§c§l(!) §7The Giant slams the ground, causing a shockwave!");
                                    attacked = true;
                                }
                            }
                        }
                    }
                    if (attacked) {
                        giantAttackCooldown.put(giant.getUniqueId(), System.currentTimeMillis());
                        giant.getWorld().playSound(giant.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.5f);
                        try {
                            giant.getWorld().spawnParticle(Particle.valueOf("EXPLOSION_EMITTER"), giant.getLocation(), 1);
                        } catch (Exception ex) {
                            giant.getWorld().spawnParticle(Particle.valueOf("EXPLOSION_LARGE"), giant.getLocation(), 1);
                        }
                    }
                }

                // --- ATAK MELEE: zasięg 4 bloki, dmg 6 ---
                long lastMeleeAttack = giantMeleeCooldown.getOrDefault(giant.getUniqueId(), 0L);
                if (System.currentTimeMillis() - lastMeleeAttack >= 1000) {
                    Player closestTarget = null;
                    double closestDist = 4.0;

                    for (Entity entity : giant.getNearbyEntities(4.0, 4.0, 4.0)) {
                        if (entity instanceof Player) {
                            Player p = (Player) entity;
                            if ((p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) && !p.isDead()) {
                                double dist = p.getLocation().distance(giant.getLocation());
                                if (dist <= closestDist) {
                                    closestDist = dist;
                                    closestTarget = p;
                                }
                            }
                        }
                    }

                    if (closestTarget != null) {
                        giantMeleeCooldown.put(giant.getUniqueId(), System.currentTimeMillis());
                        closestTarget.damage(6.0, giant);
                        giant.getWorld().playSound(giant.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);

        return giant;
    }
    /**
     * Spawnuje Golema (Iron Golem) w dżungli.
     * 150 HP, regeneracja +2 HP co 5 sekund (100 ticków).
     * Obrażenia zniwelowane o 50% (NERF - golem zadaje połowę oryginalnego dmg).
     * Zabicie: 30% expa drużyny, 50 emeraldów dla zabójcy oraz kolejny "buff"
     * (patrz onJungleMobDeath / applyGolemKillTeamRewards).
     */
    public IronGolem spawnGolem(Location spawnLoc) {
        if (spawnLoc == null || spawnLoc.getWorld() == null) return null;

        IronGolem golem = (IronGolem) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.IRON_GOLEM);
        golem.setPlayerCreated(false);
        golem.setRemoveWhenFarAway(false);
        golem.setPersistent(true);

        AttributeInstance maxHealthAttr = golem.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) maxHealthAttr.setBaseValue(200.0); // 150 -> 200 HP
        golem.setHealth(200.0);

        AttributeInstance atkDamageAttr = golem.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atkDamageAttr != null) {
            double originalDamage = atkDamageAttr.getBaseValue();
            atkDamageAttr.setBaseValue(originalDamage * 0.5); // NERF: -50% dmg
        }

        golem.getPersistentDataContainer().set(golemKey, PersistentDataType.BYTE, (byte) 1);
        updateGolemName(golem);

        // Regeneracja: +2 HP co 5 sekund (100 ticków)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!golem.isValid() || golem.isDead()) {
                    cancel();
                    return;
                }

                AttributeInstance maxHp = golem.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double max = maxHp != null ? maxHp.getBaseValue() : 200.0;
                double newHealth = Math.min(max, golem.getHealth() + 2.0);
                golem.setHealth(newHealth);
                updateGolemName(golem);
            }
        }.runTaskTimer(plugin, 100L, 100L);

        // Trzyma golema w miejscu spawnu, tak jak przy pozostałych mobach dżungli
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!golem.isValid() || golem.isDead()) {
                    cancel();
                    return;
                }
                if (golem.getLocation().distance(spawnLoc) > 6.0) {
                    golem.teleport(spawnLoc);
                    golem.setVelocity(new Vector(0, 0, 0));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        return golem;
    }

    /**
     * Spawnuje Zombie na Kurczaku (Chicken Jockey) dla drużyny zabójcy giganta.
     * Jockey biegnie prosto z bazy tej drużyny do wrogiego nexusa, po czym ZOSTAJE
     * przy nim i regularnie go atakuje (5 dmg, tempo ataku jak zombie ~1/s) - zamiast
     * jednorazowego trafienia i zniknięcia. Ginie dopiero, gdy zabije go wrogia drużyna
     * (lub kontratak samego Nexusa), albo gdy mecz się kończy. 100 HP.
     */
    private void spawnGiantChickenJockeyForTeam(String killerTeam) {
        if (killerTeam == null) return;

        World world = Bukkit.getWorlds().get(0);

        Location blueSpawn = new Location(world, 167.5, 112.0, 167.5);
        Location redSpawn = new Location(world, 91.5, 112.0, 91.5);

        boolean isBlue = "BLUE".equalsIgnoreCase(killerTeam);
        Location spawnLoc = isBlue ? blueSpawn : redSpawn;
        final String enemyNexusTag = isBlue ? "RED NEXUS" : "BLUE NEXUS";
        final int teamId = isBlue ? 1 : 2;

        Chicken chicken = (Chicken) world.spawnEntity(spawnLoc, EntityType.CHICKEN);
        chicken.setAdult();
        chicken.getPersistentDataContainer().set(chickenJockeyTeamKey, PersistentDataType.STRING, killerTeam.toUpperCase());

        Zombie zombie = (Zombie) world.spawnEntity(spawnLoc, EntityType.ZOMBIE);
        zombie.setBaby(false);
        zombie.setAI(false); // ruchem steruje kurczak, zombie nie ma odbiegać w bok ani atakować graczy
        zombie.setCanPickupItems(false);
        zombie.getPersistentDataContainer().set(chickenJockeyTeamKey, PersistentDataType.STRING, killerTeam.toUpperCase());
        zombie.getPersistentDataContainer().set(minionTeamKey, PersistentDataType.INTEGER, teamId);

        // --- DODANIE KOLOROWEGO SZKŁA NA GŁOWĘ ZOMBIE (OCHRONA PRZED SŁOŃCEM + DESIGN) ---
        Material glassMaterial = isBlue ? Material.CYAN_STAINED_GLASS : Material.RED_STAINED_GLASS;
        if (zombie.getEquipment() != null) {
            zombie.getEquipment().setHelmet(new ItemStack(glassMaterial));
            zombie.getEquipment().setHelmetDropChance(0.0f); // zapobiega wypadaniu szkła po śmierci
        }

        AttributeInstance hpAttr = zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(200.0);
        zombie.setHealth(200.0);

        zombie.setCustomName((isBlue ? "§b§l" : "§c§l") + "Chicken Jockey §7[200❤]");
        zombie.setCustomNameVisible(true);

        chicken.addPassenger(zombie);

        activeChickenJockeys.add(chicken);
        activeChickenJockeys.add(zombie);

        final Map<UUID, Long> jockeyAttackCooldown = new HashMap<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!chicken.isValid() || chicken.isDead() || !zombie.isValid() || zombie.isDead()) {
                    if (chicken.isValid()) chicken.remove();
                    if (zombie.isValid()) zombie.remove();
                    activeChickenJockeys.remove(chicken);
                    activeChickenJockeys.remove(zombie);
                    cancel();
                    return;
                }

                Villager enemyNexus = null;
                for (Villager v : world.getEntitiesByClass(Villager.class)) {
                    if (v.isValid() && !v.isDead() && v.getCustomName() != null && v.getCustomName().contains(enemyNexusTag)) {
                        enemyNexus = v;
                        break;
                    }
                }

                if (enemyNexus == null) {
                    chicken.remove();
                    zombie.remove();
                    activeChickenJockeys.remove(chicken);
                    activeChickenJockeys.remove(zombie);
                    cancel();
                    return;
                }

                double dist = chicken.getLocation().distance(enemyNexus.getLocation());
                if (dist <= 2.5) {
                    chicken.setAI(false);

                    long lastAttack = jockeyAttackCooldown.getOrDefault(zombie.getUniqueId(), 0L);
                    if (System.currentTimeMillis() - lastAttack >= 1000) {
                        jockeyAttackCooldown.put(zombie.getUniqueId(), System.currentTimeMillis());
                        enemyNexus.damage(5.0, zombie);
                        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);
                    }
                } else {
                    chicken.getPathfinder().moveTo(enemyNexus.getLocation(), 1.3);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
    private void updateBankName(Pig pig) {
        int currentHp = (int) Math.max(0, Math.ceil(pig.getHealth()));
        pig.setCustomName("§a§lBank §7[§c" + currentHp + "§7/§c100 HP§7]");
        pig.setCustomNameVisible(true);
    }
    private void updateLlamaName(Llama llama) {
        int currentHp = (int) Math.max(0, Math.ceil(llama.getHealth()));
        double maxHp = 75.0;
        if (llama.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            maxHp = llama.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        }

        // Pobranie tieru lamy (analogicznie jak w banku)
        int tier = llama.getPersistentDataContainer().getOrDefault(llamaTierKey, PersistentDataType.INTEGER, 1);


        llama.setCustomName("§e§lLama §8[§eTier " + tier + "§8] §7[§c" + currentHp + "§7/§c" + (int) maxHp + " HP§7]");
        llama.setCustomNameVisible(true);
    }
    private void updateGiantName(Giant giant) {
        int currentHp = (int) Math.max(0, Math.ceil(giant.getHealth()));
        giant.setCustomName("§4§lGiant §7[§c" + currentHp + "§7/§c300 HP§7]");
        giant.setCustomNameVisible(true);
    }
    private void updateGolemName(IronGolem golem) {
        int currentHp = (int) Math.max(0, Math.ceil(golem.getHealth()));
        golem.setCustomName("§b§lGolem §7[§c" + currentHp + "§7/§c200 HP§7]");
        golem.setCustomNameVisible(true);
    }

    @EventHandler
    public void onGiantHitboxFix(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            Player p = (Player) e.getDamager();
            for (Entity entity : p.getNearbyEntities(4.0, 4.0, 4.0)) {
                if (entity instanceof Giant) {
                    Giant g = (Giant) entity;
                    if (g.getPersistentDataContainer().has(giantKey, PersistentDataType.BYTE)) {
                        if (e.getEntity().equals(g)) return;
                        if (p.getLocation().distance(g.getLocation()) <= 4.0) {
                            g.damage(e.getDamage(), p);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMountLlama(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Llama) {
            Llama llama = (Llama) e.getRightClicked();
            if (llama.getPersistentDataContainer().has(llamaKey, PersistentDataType.BYTE)) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * NOWE: Chicken Jockey (kurczak + zombie) może być bity WYŁĄCZNIE przez graczy z wrogiej
     * (przeciwnej) drużyny - własna drużyna (która go stworzyła, zabijając giganta) nie może
     * go zabić po drodze/w trakcie oblężenia Nexusa. Dotyczy tylko obrażeń od graczy
     * (bezpośrednio lub z pocisku) - kontratak samego Nexusa (nie jest graczem) nie jest tu
     * dotykany i działa niezależnie.
     */
    @EventHandler
    public void onChickenJockeyFriendlyFire(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!(victim instanceof Zombie) && !(victim instanceof Chicken)) return;
        if (!victim.getPersistentDataContainer().has(chickenJockeyTeamKey, PersistentDataType.STRING)) return;

        String jockeyTeam = victim.getPersistentDataContainer().get(chickenJockeyTeamKey, PersistentDataType.STRING);
        if (jockeyTeam == null) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) e.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // Ten handler dotyczy wyłącznie obrażeń od graczy - inne źródła (np. kontratak Nexusa)
        // mają własną, niezależną logikę i nie są tu blokowane.
        if (attacker == null) return;

        if (plugin.tvtNexusManager == null) {
            e.setCancelled(true);
            return;
        }

        // Biała lista: przepuszczamy TYLKO potwierdzonego gracza z wrogiej drużyny.
        // Wszystko inne (własna drużyna, gracz bez przypisanej drużyny) jest blokowane.
        String attackerTeam = plugin.tvtNexusManager.getPlayerTeam(attacker);
        if (attackerTeam == null || jockeyTeam.equalsIgnoreCase(attackerTeam)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Pig) {
            Pig pig = (Pig) e.getEntity();
            if (!pig.getPersistentDataContainer().has(bankKey, PersistentDataType.BYTE)) return;

            // Pobieramy aktualne zdrowie i Tier banku
            double newHealth = Math.max(0, pig.getHealth() - e.getFinalDamage());
            int tier = pig.getPersistentDataContainer().getOrDefault(bankTierKey, PersistentDataType.INTEGER, 1);

            // Dynamicznie pobieramy maksymalne HP świni (zależne od jej Tieru)
            double maxHp = 100.0;
            if (pig.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                maxHp = pig.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            }

            pig.setCustomName("§a§lBank Tier " + tier + " §7[§c" + (int) Math.ceil(newHealth) + "§7/§c" + (int) maxHp + " HP§7]");
        } else if (e.getEntity() instanceof Llama) {
            Llama llama = (Llama) e.getEntity();
            if (!llama.getPersistentDataContainer().has(llamaKey, PersistentDataType.BYTE)) return;
            int currentHp = (int) Math.max(0, Math.ceil(llama.getHealth()));
            double maxHp = 75.0;
            if (llama.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                maxHp = llama.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            }

            // Pobranie tieru lamy (analogicznie jak w banku)
            int tier = llama.getPersistentDataContainer().getOrDefault(llamaTierKey, PersistentDataType.INTEGER, 1);


            llama.setCustomName("§e§lLama §8[§eTier " + tier + "§8] §7[§c" + currentHp + "§7/§c" + (int) maxHp + " HP§7]");
            llama.setCustomNameVisible(true);
        } else if (e.getEntity() instanceof Giant) {
            Giant giant = (Giant) e.getEntity();
            if (!giant.getPersistentDataContainer().has(giantKey, PersistentDataType.BYTE)) return;
            double newHealth = Math.max(0, giant.getHealth() - e.getFinalDamage());
            giant.setCustomName("§4§lGiant §7[§c" + (int) Math.ceil(newHealth) + "§7/§c300 HP§7]");
        } else if (e.getEntity() instanceof IronGolem) {
            IronGolem golem = (IronGolem) e.getEntity();
            if (!golem.getPersistentDataContainer().has(golemKey, PersistentDataType.BYTE)) return;
            double newHealth = Math.max(0, golem.getHealth() - e.getFinalDamage());
            golem.setCustomName("§b§lGolem §7[§c" + (int) Math.ceil(newHealth) + "§7/§c200 HP§7]");
        } else if (e.getEntity() instanceof Zombie) {
            // NOWE: aktualizacja HP nad Chicken Jockeyem (zombie) po otrzymaniu obrażeń
            Zombie zombie = (Zombie) e.getEntity();
            if (!zombie.getPersistentDataContainer().has(chickenJockeyTeamKey, PersistentDataType.STRING)) return;
            if (e.isCancelled()) return; // np. zablokowane przez onChickenJockeyFriendlyFire - HP się nie zmieniło

            double newHealth = Math.max(0, zombie.getHealth() - e.getFinalDamage());
            String currentName = zombie.getCustomName();
            if (currentName != null && currentName.contains("[")) {
                String baseName = currentName.substring(0, currentName.lastIndexOf("["));
                zombie.setCustomName(baseName + "§7[" + (int) Math.ceil(newHealth) + "❤]");
            }
        }
    }
    /**
     * NOWE: Chicken (wierzchowiec) jest CAŁKOWICIE NIEŚMIERTELNY.
     * Jedyną jednostką, którą można realnie zabić w tym duecie, jest Zombie.
     * Dzięki temu obrażenia zawsze "trafiają" logicznie w zombie, a chicken
     * nigdy nie zniknie osobno, zostawiając zombie bez wierzchowca.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onChickenJockeyChickenInvulnerable(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Chicken)) return;
        Chicken chicken = (Chicken) e.getEntity();
        if (chicken.getPersistentDataContainer().has(chickenJockeyTeamKey, PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    /**
     * NOWE: Gdy zombie (jeżdziec) zginie, chicken pod nim natychmiast też znika.
     * Chicken i zombie są traktowane jako jedna jednostka - śmierć zombie = koniec jockeya.
     */
    @EventHandler
    public void onChickenJockeyZombieDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Zombie)) return;
        Zombie zombie = (Zombie) e.getEntity();
        if (!zombie.getPersistentDataContainer().has(chickenJockeyTeamKey, PersistentDataType.STRING)) return;

        Entity vehicle = zombie.getVehicle();
        if (vehicle instanceof Chicken) {
            vehicle.remove();
            activeChickenJockeys.remove(vehicle);
        }
        activeChickenJockeys.remove(zombie);
    }
    @EventHandler
    public void onJungleMobDeath(EntityDeathEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(chickenJockeyTeamKey, PersistentDataType.STRING)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }

        if (e.getEntity() instanceof Llama) {
            Llama llama = (Llama) e.getEntity();
            if (!llama.getPersistentDataContainer().has(llamaKey, PersistentDataType.BYTE)) return;

            e.getDrops().clear();
            e.setDroppedExp(0);

            // NOWE: Inkrementacja licznika śmierci dla konkretnego ID spawneru Lamy (identycznie jak w Banku)
            if (llama.getPersistentDataContainer().has(llamaIdKey, PersistentDataType.STRING)) {
                String llamaId = llama.getPersistentDataContainer().get(llamaIdKey, PersistentDataType.STRING);
                llamaDeathCounts.put(llamaId, llamaDeathCounts.getOrDefault(llamaId, 0) + 1);
            }

            // Odczyt Tieru z PersistentDataContainer (domyślnie 1)
            int tier = llama.getPersistentDataContainer().getOrDefault(llamaTierKey, PersistentDataType.INTEGER, 1);

            // Wyliczenie nagród w zależności od Tieru
            int emeralds = 15;
            int csAmount = 3; // 3 CS = 15 emeraldów

            if (tier == 2) {
                emeralds = 20;
                csAmount = 4; // 4 CS = 20 emeraldów
            } else if (tier >= 3) {
                emeralds = 25;
                csAmount = 5; // 5 CS = 25 emeraldów
            }

            Player killer = llama.getKiller();
            if (killer != null) {
                if (plugin.tvtShopManager != null) {
                    plugin.tvtShopManager.addEmeralds(killer, emeralds);
                }

                killer.sendMessage("§a§lJUNGLE §8» §7You killed the §eLama (Tier " + tier + ")§7! You receive §a" + emeralds + " Emeralds §7and §e20% XP§7!");
                killer.playSound(killer.getLocation(), Sound.ENTITY_LLAMA_DEATH, 1.0f, 1.0f);

                if (plugin.isPlayerInTvT(killer)) {
                    String team = plugin.tvtNexusManager.getPlayerTeam(killer);
                    if (team != null) plugin.addTeamExp(team, 0.20f);

                    if (plugin.minionManager != null) {
                        // Przekazujemy tier do spawneru strayów
                        plugin.minionManager.spawnBonusStraysForKillerTeam(team, tier);
                    }

                    plugin.tvtNexusManager.addCS(killer, csAmount);
                }
            }
        }

        if (e.getEntity() instanceof Pig) {
            Pig pig = (Pig) e.getEntity();
            if (!pig.getPersistentDataContainer().has(bankKey, PersistentDataType.BYTE)) return;

            e.getDrops().clear();
            e.setDroppedExp(0);

            if (pig.getPersistentDataContainer().has(bankIdKey, PersistentDataType.STRING)) {
                String bankId = pig.getPersistentDataContainer().get(bankIdKey, PersistentDataType.STRING);
                bankDeathCounts.put(bankId, bankDeathCounts.getOrDefault(bankId, 0) + 1);
            }

            int tier = pig.getPersistentDataContainer().getOrDefault(bankTierKey, PersistentDataType.INTEGER, 1);

            int emeraldReward = 20;
            int csReward = 4;
            if (tier == 2) { emeraldReward = 25; csReward = 5; }
            else if (tier == 3) { emeraldReward = 30; csReward = 6; }

            Player killer = pig.getKiller();
            if (killer != null) {
                if (plugin.tvtShopManager != null) plugin.tvtShopManager.addEmeralds(killer, emeraldReward);
                killer.sendMessage("§a§lJUNGLE §8» §7You killed the §aBank Tier " + tier + "§7! You receive §a" + emeraldReward + " Emeralds§7 §7and §e5% XP§7!");
                killer.playSound(killer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                if (plugin.isPlayerInTvT(killer)) {
                    String team = plugin.tvtNexusManager.getPlayerTeam(killer);
                    if (team != null) {
                        plugin.addTeamExp(team, 0.05f);

                        // WYWOŁANIE NOWEJ METODY W MINIONMANAGER:
                        if (plugin.minionManager != null) {
                            plugin.minionManager.spawnBankGuardsForKillerTeam(team, tier);
                        }

                        plugin.tvtNexusManager.addCS(killer, csReward);
                    }
                }
            }
        }

        if (e.getEntity() instanceof Giant) {
            Giant giant = (Giant) e.getEntity();
            if (!giant.getPersistentDataContainer().has(giantKey, PersistentDataType.BYTE)) return;
            e.getDrops().clear();
            e.setDroppedExp(0);
            Player killer = giant.getKiller();
            if (killer != null) {
                if (plugin.tvtShopManager != null) plugin.tvtShopManager.addEmeralds(killer, 100);
                if (plugin.isPlayerInTvT(killer)) {
                    String team = plugin.tvtNexusManager.getPlayerTeam(killer);
                    if (team != null) {
                        plugin.addTeamExp(team, 1.00f);
                        plugin.tvtNexusManager.addCS(killer, 20); // 20 CS = 100 emeraldów

                        // Spawn chicken jockeya biegnącego do wrogiego nexusa
                        spawnGiantChickenJockeyForTeam(team);

                        for (Player teammate : Bukkit.getOnlinePlayers()) {
                            if (team.equals(plugin.tvtNexusManager.getPlayerTeam(teammate))) {
                                teammate.sendMessage("§4§lBOSS §8» §e" + killer.getName() + " §7defeated the §4Giant§7! The whole team receives §a+1 Level§7!");
                                teammate.playSound(teammate.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                            }
                        }
                    }
                } else {
                    killer.giveExpLevels(1);
                    killer.sendMessage("§4§lBOSS §8» §7You killed the §4Giant§7! You receive §a100 Emeralds §7and §a+1 Level§7!");
                    killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
        }

        if (e.getEntity() instanceof IronGolem) {
            IronGolem golem = (IronGolem) e.getEntity();
            if (golem.getPersistentDataContainer().has(golemKey, PersistentDataType.BYTE)) {
                e.getDrops().clear();
                e.setDroppedExp(0);
                Player killer = golem.getKiller();
                if (killer != null) {
                    if (plugin.tvtShopManager != null) plugin.tvtShopManager.addEmeralds(killer, 50);
                    killer.playSound(killer.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 1.0f, 1.0f);

                    if (plugin.isPlayerInTvT(killer)) {
                        String team = plugin.tvtNexusManager.getPlayerTeam(killer);
                        if (team != null) {
                            plugin.addTeamExp(team, 0.30f);
                            applyGolemKillTeamRewards(team, killer);
                        }
                    } else {
                        killer.sendMessage("§b§lGOLEM §8» §7You killed the §bGolem§7! You receive §a50 Emeralds§7.");
                    }
                }
            }
        }

        if (plugin.sbManager != null) plugin.sbManager.updateAll();
        if (plugin.tabManager != null) plugin.tabManager.updateAll();
    }

    /**
     * Nagradza drużynę zabójcy golema: trwały bonus HP dla melee minionów (piglinów)
     * i szkieletów tej drużyny oraz kolejny "buff" napierśnika (klata) do momentu
     * osiągnięcia maksimum (6 buffów -> netherite). Po osiągnięciu maksimum kolejne
     * zabicia golema dają już tylko expa i emeraldy, bez dodatkowych buffów.
     */
    private void applyGolemKillTeamRewards(String team, Player killer) {
        if (team == null) return;

        if (plugin.tvtNexusManager != null) {
            plugin.tvtNexusManager.addCS(killer, 10); // NOWE: CS za Golema (10 CS = 50 emeraldów) — liczy się przy każdym zabiciu golema, nie tylko przy buffach poniżej maxa
        }

        int kills = teamGolemKills.getOrDefault(team, 0);

        if (kills >= GOLEM_MAX_BUFFS) {
            // Buffy już zmaxowane - tylko exp i emeraldy (już przyznane wyżej)
            killer.sendMessage("§b§lGOLEM §8» §7You killed the §bGolem§7! Your team already has §emax buffs§7 - you receive §a50 Emeralds §7and §e30% XP§7!");
            broadcastToTeam(team, "§b§lGOLEM §8» §e" + killer.getName() + " §7defeated the §bGolem§7! (buffs §emaxed§7, team only gets exp/emeralds)");
            return;
        }

        kills++;
        teamGolemKills.put(team, kills);

        // Trwały bonus HP dla minionów melee (pigliny) i szkieletów tej drużyny
        double newMeleeBonus = teamMeleeMinionHpBonus.getOrDefault(team, 0.0) + 2.0;
        double newSkeletonBonus = teamSkeletonMinionHpBonus.getOrDefault(team, 0.0) + 1.0;
        teamMeleeMinionHpBonus.put(team, newMeleeBonus);
        teamSkeletonMinionHpBonus.put(team, newSkeletonBonus);

        // Kolejny tier napierśnika dla zbuforowanych minionów drużyny
        Material chestplate = GOLEM_CHESTPLATE_TIERS[kills - 1];
        teamGolemChestplateTier.put(team, chestplate);

        // Zastosuj nowy tier napierśnika do minionów drużyny już obecnych na mapie
        applyChestplateToExistingTeamMinions(team, chestplate);

        String buffNameReadable = readableChestplateName(chestplate);

        killer.sendMessage("§b§lGOLEM §8» §7You killed the §bGolem§7! You receive §a50 Emeralds §7and §e30% XP§7!");
        broadcastToTeam(team, "§b§lGOLEM §8» §e" + killer.getName() + " §7defeated the §bGolem§7! Your team receives buff §f(" + kills + "/" + GOLEM_MAX_BUFFS + ")§7: " + buffNameReadable
                + " §7for minions, §f+2 HP §7(melee) / §f+1 HP §7(skeletons) until the end of the game!");

        if (kills >= GOLEM_MAX_BUFFS) {
            broadcastToTeam(team, "§b§lGOLEM §8» §7Your team has reached the §emaximum buff§7! Further Golem kills will now only grant exp and emeralds.");
        }
    }

    private String readableChestplateName(Material m) {
        switch (m) {
            case LEATHER_CHESTPLATE: return "§fleather chestplate";
            case GOLDEN_CHESTPLATE: return "§6golden chestplate";
            case CHAINMAIL_CHESTPLATE: return "§7chainmail chestplate";
            case IRON_CHESTPLATE: return "§firon chestplate";
            case DIAMOND_CHESTPLATE: return "§bdiamond chestplate";
            case NETHERITE_CHESTPLATE: return "§8netherite chestplate";
            default: return m.name();
        }
    }

    private void broadcastToTeam(String team, String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (team.equals(plugin.tvtNexusManager.getPlayerTeam(p))) {
                p.sendMessage(message);
            }
        }
    }

    /**
     * Nakłada aktualny tier napierśnika na wszystkie żyjące pigliny/szkielety danej drużyny
     * już obecne na mapie (np. minionów rozstawionych przez inne systemy).
     * Rozpoznanie "przynależności" miniona do drużyny opiera się na jego custom name
     * (jak w innych miejscach tego pluginu, np. Chicken Jockey / Nexus) - jeśli minionManager
     * nazywa/oznacza minionów danej drużyny inaczej, dopasuj warunek poniżej.
     */
    private void applyChestplateToExistingTeamMinions(String team, Material chestplate) {
        for (World world : Bukkit.getWorlds()) {
            for (PiglinAbstract piglin : world.getEntitiesByClass(PiglinAbstract.class)) {
                if (isTeamMinion(piglin, team)) {
                    equipChestplate(piglin, chestplate);
                }
            }
            for (Skeleton skeleton : world.getEntitiesByClass(Skeleton.class)) {
                if (isTeamMinion(skeleton, team)) {
                    equipChestplate(skeleton, chestplate);
                }
            }
        }
    }

    private boolean isTeamMinion(LivingEntity entity, String team) {
        String name = entity.getCustomName();
        if (name == null) return false;
        // Zakładany format nazwy miniona: zawiera nazwę drużyny (np. "[BLUE]" / "[RED]")
        return name.toUpperCase().contains(team.toUpperCase());
    }

    private void equipChestplate(LivingEntity entity, Material chestplate) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        equipment.setChestplate(new ItemStack(chestplate));
        equipment.setChestplateDropChance(0.0f);
    }

    // --- METODY POMOCNICZE DO INTEGRACJI Z minionManager ---
    // Wywołaj je przy spawnowaniu nowych minionów (pigliny / szkielety) danej drużyny,
    // żeby uwzględnić trwałe bonusy HP i aktualny tier napierśnika z buffów golema.

    public double getGolemMeleeMinionHpBonus(String team) {
        return teamMeleeMinionHpBonus.getOrDefault(team, 0.0);
    }

    public double getGolemSkeletonMinionHpBonus(String team) {
        return teamSkeletonMinionHpBonus.getOrDefault(team, 0.0);
    }

    public Material getGolemChestplateTier(String team) {
        return teamGolemChestplateTier.get(team);
    }

    public int getGolemKillCount(String team) {
        return teamGolemKills.getOrDefault(team, 0);
    }

    private enum MobType { BANK, LLAMA, GIANT, GOLEM }

    private static class JungleSpawner {
        MobType type;
        Location location;
        int respawnDelaySeconds;
        long nextSpawnTime;
        Entity entity;

        public JungleSpawner(MobType type, Location location, int initialDelaySeconds, int respawnDelaySeconds) {
            this.type = type;
            this.location = location;
            this.respawnDelaySeconds = respawnDelaySeconds;
            this.nextSpawnTime = System.currentTimeMillis() + (initialDelaySeconds * 1000L);
        }
    }
    /**
     * UNIWERSALNA METODA: sprawdza, czy dana jednostka jest mobem z systemu dżungli
     * (Bank/Pig, Llama, Giant, Golem). Może być wywoływana z dowolnej klasy przez
     * plugin.jungleManager.isJungleMob(entity) - np. do bonusowych obrażeń klas.
     * UWAGA: Chicken Jockey (kurczak+zombie) NIE jest tu liczony jako "mob jungli" -
     * on jest traktowany jako Minion (patrz MinionManager.isMinion), bo atakuje Nexus
     * tak jak zwykłe miniony.
     */
    public boolean isJungleMob(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(bankKey, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(llamaKey, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(giantKey, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(golemKey, PersistentDataType.BYTE);
    }
}