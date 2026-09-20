package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MinionManager implements Listener {

    private final Main plugin;
    private final NamespacedKey minionTeamKey;
    private final NamespacedKey minionNoRewardKey;
    private BukkitTask minionSpawnTask;
    private BukkitTask minionAiTask;
    private final List<Mob> activeMinions = new ArrayList<>();

    // Flaga powstrzymująca spawnowanie po końcu gry
    private boolean isGameActive = false;

    // Nazwy druzyn scoreboardowych dla braku kolizji wewnątrz drużyny
    private static final String TEAM_BLUE_MINIONS = "TvT_Minion_1";
    private static final String TEAM_RED_MINIONS = "TvT_Minion_2";

    public MinionManager(Main plugin) {
        this.plugin = plugin;
        this.minionTeamKey = new NamespacedKey(plugin, "minion_team");
        this.minionNoRewardKey = new NamespacedKey(plugin, "minion_no_reward");
        Bukkit.getPluginManager().registerEvents(this, plugin);

        setupCollisionTeams();
    }

    // --- REJESTRACJA DRUŻYN SCOREBOARD (KOLIZJA TYLKO DLA PRZECIWNYCH DRUŻYN) ---

    private void setupCollisionTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        registerTeamCollision(board, TEAM_BLUE_MINIONS);
        registerTeamCollision(board, TEAM_RED_MINIONS);
    }

    private void registerTeamCollision(Scoreboard board, String teamName) {
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        // Członkowie TEJ SAMEJ drużyny przechodzą przez siebie.
        // Członkowie PRZECIWNYCH drużyn fizycznie się zderzają (co pozwala na zadawanie obrażeń).
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.FOR_OTHER_TEAMS);
    }

    private void addEntityToTeam(Entity entity, int teamId) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = (teamId == 1) ? TEAM_BLUE_MINIONS : TEAM_RED_MINIONS;
        Team team = board.getTeam(teamName);

        if (team != null) {
            team.addEntry(entity.getUniqueId().toString());
        }
    }

    // Zamiana teamId (1/2) na nazwę drużyny używaną przez JungleManager ("BLUE"/"RED")
    private String teamIdToName(int teamId) {
        return (teamId == 1) ? "BLUE" : "RED";
    }

    // --- START SYSTEMU MINIONÓW ---

    public void startMinionWaves(Location team1Spawn, Location team2Spawn) {
        stopMinionWaves();
        isGameActive = true;

        if (minionSpawnTask != null) {
            Bukkit.getScheduler().cancelTask(minionSpawnTask.getTaskId());
            minionSpawnTask = null;
        }

        minionSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameActive || !areBothNexusesAlive()) {
                    stopMinionWaves();
                    cancel();
                    return;
                }
                spawnMinionWave(team1Spawn, team2Spawn);
            }
        }.runTaskTimer(plugin, 100L, 600L);

        startAiTask(team1Spawn, team2Spawn);
    }

    // --- SPRAWDZANIE ISTNIENIA OBU NEXUSÓW ---

    private boolean areBothNexusesAlive() {
        if (plugin.tvtNexusManager == null) return false;

        boolean team1NexusExists = false;
        boolean team2NexusExists = false;

        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.isValid() && !villager.isDead()) {
                    String customName = villager.getCustomName();
                    if (customName != null) {
                        if (customName.contains("BLUE NEXUS")) {
                            team1NexusExists = true;
                            // Villager nie ma kolizji z Blue Minionami, ale koliduje z Red Minionami
                            addEntityToTeam(villager, 1);
                        }
                        if (customName.contains("RED NEXUS")) {
                            team2NexusExists = true;
                            // Villager nie ma kolizji z Red Minionami, ale koliduje z Blue Minionami
                            addEntityToTeam(villager, 2);
                        }
                    }
                }
            }
        }

        return team1NexusExists && team2NexusExists;
    }

    // --- ZATRZYMANIE I CZYSZCZENIE MINIONÓW ---

    public void stopMinionWaves() {
        isGameActive = false;

        if (minionSpawnTask != null) {
            try {
                minionSpawnTask.cancel();
                Bukkit.getScheduler().cancelTask(minionSpawnTask.getTaskId());
            } catch (Exception ignored) {}
            minionSpawnTask = null;
        }

        if (minionAiTask != null) {
            try {
                minionAiTask.cancel();
                Bukkit.getScheduler().cancelTask(minionAiTask.getTaskId());
            } catch (Exception ignored) {}
            minionAiTask = null;
        }

        for (Mob minion : new ArrayList<>(activeMinions)) {
            if (minion != null && minion.isValid()) {
                minion.remove();
            }
        }
        activeMinions.clear();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;
                    if (mob.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) {
                        mob.remove();
                    }
                }
            }
        }
    }

    // --- PĘTLA AI (KONTROLA RUCHU I SZUKANIE WROGA) ---

    private void startAiTask(Location team1Spawn, Location team2Spawn) {
        minionAiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameActive) {
                    cancel();
                    return;
                }

                Iterator<Mob> iterator = activeMinions.iterator();
                while (iterator.hasNext()) {
                    Mob minion = iterator.next();

                    if (minion == null || !minion.isValid() || minion.isDead()) {
                        iterator.remove();
                        continue;
                    }

                    Integer teamId = minion.getPersistentDataContainer().get(minionTeamKey, PersistentDataType.INTEGER);
                    if (teamId == null) continue;

                    Location destination = (teamId == 1) ? team2Spawn : team1Spawn;
                    LivingEntity validTarget = findEnemyTarget(minion, teamId);

                    if (validTarget != null) {
                        minion.setTarget(validTarget);
                    } else {
                        minion.setTarget(null);
                        minion.getPathfinder().moveTo(destination);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // --- SZUKANIE CELU (TYLKO WROGIE MINIONY ORAZ WROGI NEXUS) ---

    private LivingEntity findEnemyTarget(Mob minion, int minionTeam) {
        double closestDistance = 15.0;
        LivingEntity bestTarget = null;

        for (Entity nearby : minion.getNearbyEntities(closestDistance, closestDistance, closestDistance)) {
            if (nearby instanceof Player) continue;

            if (nearby instanceof Villager) {
                String customName = nearby.getCustomName();
                if (customName != null) {
                    boolean isEnemyNexus = (minionTeam == 1 && customName.contains("RED")) ||
                            (minionTeam == 2 && customName.contains("BLUE"));
                    if (isEnemyNexus) {
                        return (LivingEntity) nearby;
                    }
                }
            } else if (nearby instanceof Mob) {
                Mob otherMob = (Mob) nearby;
                if (otherMob.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) {
                    Integer otherTeam = otherMob.getPersistentDataContainer().get(minionTeamKey, PersistentDataType.INTEGER);
                    if (otherTeam != null && otherTeam != minionTeam) {
                        double dist = minion.getLocation().distance(otherMob.getLocation());
                        if (dist < closestDistance) {
                            closestDistance = dist;
                            bestTarget = otherMob;
                        }
                    }
                }
            }
        }
        return bestTarget;
    }

    /**
     * Spawnuje 2 dodatkowe straye dla drużyny, która zabiła lamę.
     * Statystyki strayów zależą od Tieru Lamy.
     */
    public void spawnBonusStraysForKillerTeam(String teamName, int tier) {
        if (!isGameActive || !areBothNexusesAlive() || teamName == null) return;

        int teamId = "BLUE".equalsIgnoreCase(teamName) ? 1 : 2;

        World world = Bukkit.getWorlds().get(0);

        Location fixedBlueSpawn = new Location(world, 167.5, 112.0, 167.5);
        Location fixedRedSpawn = new Location(world, 91.5, 112.0, 91.5);

        Location spawnLoc = (teamId == 1) ? fixedBlueSpawn : fixedRedSpawn;
        Location targetLoc = (teamId == 1) ? fixedRedSpawn : fixedBlueSpawn;

        String prefix = (teamId == 1) ? "§1§lBLUE" : "§c§lRED";

        // Tier 1 i 2: 20 HP | Tier 3: 30 HP
        double strayHp = (tier >= 3) ? 30.0 : 20.0;
        double strayDmg = 10.0;

        for (int i = 0; i < 2; i++) {
            spawnSingleMinion(EntityType.STRAY, spawnLoc, targetLoc, teamId,
                    prefix + " §6§lLlama Stray", strayHp, strayDmg, true);
        }
    }

    // Domyślne przeciążenie dla wstecznej kompatybilności
    public void spawnBonusStraysForKillerTeam(String teamName) {
        spawnBonusStraysForKillerTeam(teamName, 1);
    }

    /**
     * Spawnuje 3 strażników banku (Bank Guards) z bazy drużyny w zależności od Tieru banku.
     */
    public void spawnBankGuardsForKillerTeam(String teamName, int tier) {
        if (!isGameActive || !areBothNexusesAlive() || teamName == null) return;

        int teamId = "BLUE".equalsIgnoreCase(teamName) ? 1 : 2;

        World world = Bukkit.getWorlds().get(0);

        Location fixedBlueSpawn = new Location(world, 167.5, 112.0, 167.5);
        Location fixedRedSpawn = new Location(world, 91.5, 112.0, 91.5);

        Location spawnLoc = (teamId == 1) ? fixedBlueSpawn : fixedRedSpawn;
        Location targetLoc = (teamId == 1) ? fixedRedSpawn : fixedBlueSpawn;

        String prefix = (teamId == 1) ? "§1§lBLUE" : "§c§lRED";

        // Dobieramy typ moba, nazwe i statystyki na podstawie Tieru banku
        EntityType mobType = EntityType.HUSK;
        double hp = 30.0;
        double dmg = 2.0;
        String nameSuffix = " §e§lBank Guard Tier 1";

        if (tier == 2) {
            mobType = EntityType.ZOMBIE;
            hp = 40.0;
            dmg = 3.0;
            nameSuffix = " §6§lBank Guard Tier 2";
        } else if (tier == 3) {
            mobType = EntityType.DROWNED;
            hp = 50.0;
            dmg = 5.0;
            nameSuffix = " §c§lBank Guard Tier 3";
        }

        String fullName = prefix + nameSuffix;

        for (int i = 0; i < 3; i++) {
            spawnSingleMinion(mobType, spawnLoc, targetLoc, teamId, fullName, hp, dmg, true);
        }
    }

    private void spawnMinionWave(Location team1Spawn, Location team2Spawn) {
        if (!isGameActive || !areBothNexusesAlive()) {
            stopMinionWaves();
            return;
        }

        World world = (team1Spawn != null && team1Spawn.getWorld() != null)
                ? team1Spawn.getWorld()
                : Bukkit.getWorlds().get(0);

        // Sztywne pozycje bazowe dla spawnu
        Location fixedBlueSpawn = new Location(world, 167.5, 112.0, 167.5);
        Location fixedRedSpawn = new Location(world, 91.5, 112.0, 91.5);

        // Spawnowanie fali BLUE (kierunek marszu: w stronę RED)
        spawnTeamMinionsWithFormation(fixedBlueSpawn, fixedRedSpawn, 1, "§9§lBLUE Minion");

        // Spawnowanie fali RED (kierunek marszu: w stronę BLUE)
        spawnTeamMinionsWithFormation(fixedRedSpawn, fixedBlueSpawn, 2, "§c§lRED Minion");
    }

    private void spawnTeamMinionsWithFormation(Location spawnLoc, Location targetLoc, int teamId, String prefixName) {
        if (spawnLoc == null || targetLoc == null || spawnLoc.getWorld() == null || !isGameActive) return;

        // Wyznaczamy wektor kierunku (od bazy do celu)
        org.bukkit.util.Vector direction = targetLoc.toVector().subtract(spawnLoc.toVector()).normalize();

        // Wektor prostopadły (boczny)
        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-direction.getZ(), 0, direction.getX()).normalize();

        // --- LINIA PIERWSZA: PIECHOTA / MELEE (ZOMBIFIED PIGLIN) ---
        double frontOffset = 1.0;

        spawnSingleMinion(EntityType.ZOMBIFIED_PIGLIN, getOffsetLocation(spawnLoc, direction, side, frontOffset, -2.0), targetLoc, teamId, prefixName + " §7(Melee)", 20.0, 2.0);
        spawnSingleMinion(EntityType.ZOMBIFIED_PIGLIN, getOffsetLocation(spawnLoc, direction, side, frontOffset, -1.0), targetLoc, teamId, prefixName + " §7(Melee)", 20.0, 2.0);
        spawnSingleMinion(EntityType.ZOMBIFIED_PIGLIN, getOffsetLocation(spawnLoc, direction, side, frontOffset, 0.0), targetLoc, teamId, prefixName + " §7(Melee)", 20.0, 2.0);
        spawnSingleMinion(EntityType.ZOMBIFIED_PIGLIN, getOffsetLocation(spawnLoc, direction, side, frontOffset, 1.0), targetLoc, teamId, prefixName + " §7(Melee)", 20.0, 2.0);
        spawnSingleMinion(EntityType.ZOMBIFIED_PIGLIN, getOffsetLocation(spawnLoc, direction, side, frontOffset, 2.0), targetLoc, teamId, prefixName + " §7(Melee)", 20.0, 2.0);

        // --- LINIA DRUGA: ŁUCZNICY / RANGED (SKELETON) ---
        double backOffset = -2.0;

        spawnSingleMinion(EntityType.SKELETON, getOffsetLocation(spawnLoc, direction, side, backOffset, -1.0), targetLoc, teamId, prefixName + " §7(Ranged)", 10.0, 3.0);
        spawnSingleMinion(EntityType.SKELETON, getOffsetLocation(spawnLoc, direction, side, backOffset, 1.0), targetLoc, teamId, prefixName + " §7(Ranged)", 10.0, 3.0);
    }

    private Location getOffsetLocation(Location base, org.bukkit.util.Vector dir, org.bukkit.util.Vector side, double forwardOffset, double sideOffset) {
        Location offsetLoc = base.clone();
        offsetLoc.add(dir.clone().multiply(forwardOffset));
        offsetLoc.add(side.clone().multiply(sideOffset));
        return offsetLoc;
    }

    private void spawnSingleMinion(EntityType type, Location spawnLoc, Location targetLoc, int teamId, String name, double hp, double damage) {
        spawnSingleMinion(type, spawnLoc, targetLoc, teamId, name, hp, damage, false);
    }

    private void spawnSingleMinion(EntityType type, Location spawnLoc, Location targetLoc, int teamId, String name, double hp, double damage, boolean noReward) {
        if (!isGameActive || !areBothNexusesAlive() || spawnLoc == null || spawnLoc.getWorld() == null) return;

        if (!spawnLoc.getChunk().isLoaded()) {
            spawnLoc.getChunk().load(true);
        }

        Entity spawnedEntity = spawnLoc.getWorld().spawnEntity(spawnLoc, type);

        if (!(spawnedEntity instanceof Mob)) {
            if (spawnedEntity != null) spawnedEntity.remove();
            return;
        }

        Mob minion = (Mob) spawnedEntity;

        if (!minion.getPassengers().isEmpty()) {
            for (Entity passenger : new ArrayList<>(minion.getPassengers())) {
                minion.removePassenger(passenger);
                passenger.remove();
            }
        }
        if (minion.isInsideVehicle()) {
            Entity vehicle = minion.getVehicle();
            minion.leaveVehicle();
            if (vehicle != null) vehicle.remove();
        }

        // --- SKALOWANIE: ZMNIEJSZENIE WYSOKOŚCI O PÓŁ KRATKI (0.75x) DLA WSZYSTKICH MINIONÓW OPRÓCZ CHICKEN JOCKEY ---
        boolean isChickenJockey = (minion.getVehicle() instanceof Chicken) ||
                minion.getPassengers().stream().anyMatch(p -> p instanceof Chicken) ||
                minion.getType() == EntityType.CHICKEN;

        if (!isChickenJockey) {
            AttributeInstance scaleAttr = minion.getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(0.75); // Skala 0.75 zmniejsza wysokość z ~2 bloku do ~1.5 bloku (-0.5 bloku)
            }
        }

        minion.getPersistentDataContainer().set(minionTeamKey, PersistentDataType.INTEGER, teamId);

        if (noReward) {
            minion.getPersistentDataContainer().set(minionNoRewardKey, PersistentDataType.BYTE, (byte) 1);
        }

        // --- BUFFY GOLEMA ---
        double bonusHp = 0.0;
        Material golemChestplate = null;

        if (!noReward && plugin.jungleManager != null) {
            String teamName = teamIdToName(teamId);

            if (type == EntityType.ZOMBIFIED_PIGLIN) {
                bonusHp = plugin.jungleManager.getGolemMeleeMinionHpBonus(teamName);
            } else if (type == EntityType.SKELETON) {
                bonusHp = plugin.jungleManager.getGolemSkeletonMinionHpBonus(teamName);
            }

            golemChestplate = plugin.jungleManager.getGolemChestplateTier(teamName);
        }

        double finalHp = hp + bonusHp;

        minion.setCustomName(name + " §7[" + (int) finalHp + "❤]");
        minion.setCustomNameVisible(true);

        addEntityToTeam(minion, teamId);

        if (minion.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            minion.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(finalHp);
            minion.setHealth(finalHp);
        }

        if (minion.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            minion.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
        }

        ItemStack helm = (teamId == 1) ? new ItemStack(Material.CYAN_STAINED_GLASS) : new ItemStack(Material.RED_STAINED_GLASS);

        // Obsługa wszystkich typów Zombie (PigZombie, Husk, Zombie, Drowned)
        if (minion instanceof Zombie) {
            Zombie zombie = (Zombie) minion;
            zombie.setBaby(false);

            EntityEquipment equipment = zombie.getEquipment();
            if (equipment != null) {
                equipment.clear();

                equipment.setHelmet(helm);
                equipment.setHelmetDropChance(0.0f);

                if (minion instanceof PigZombie) {
                    equipment.setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
                } else if (type == EntityType.DROWNED) {
                    equipment.setItemInMainHand(new ItemStack(Material.TRIDENT));
                } else {
                    equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                }
                equipment.setItemInMainHandDropChance(0.0f);

                if (golemChestplate != null) {
                    equipment.setChestplate(new ItemStack(golemChestplate));
                    equipment.setChestplateDropChance(0.0f);
                }
            }
        } else if (minion instanceof AbstractSkeleton) {
            AbstractSkeleton s = (AbstractSkeleton) minion;

            EntityEquipment equipment = s.getEquipment();
            if (equipment != null) {
                equipment.clear();

                equipment.setHelmet(helm);
                equipment.setHelmetDropChance(0.0f);

                equipment.setItemInMainHand(new ItemStack(Material.BOW));
                equipment.setItemInMainHandDropChance(0.0f);

                if (golemChestplate != null) {
                    equipment.setChestplate(new ItemStack(golemChestplate));
                    equipment.setChestplateDropChance(0.0f);
                }
            }
        }

        minion.setRemoveWhenFarAway(false);
        minion.getPathfinder().moveTo(targetLoc);

        if (minion.isValid()) {
            activeMinions.add(minion);
        }
    }

    public boolean isMinion(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER);
    }

    // --- BLOKADA ZADAWANIA OBRAŻEŃ WŁASNYM MINIONOM ---

    @EventHandler
    public void onMinionFriendlyFire(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Mob)) return;
        Mob victimMinion = (Mob) e.getEntity();

        if (!victimMinion.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) return;
        Integer minionTeam = victimMinion.getPersistentDataContainer().get(minionTeamKey, PersistentDataType.INTEGER);
        if (minionTeam == null) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) e.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker != null && plugin.tvtNexusManager != null) {
            String attackerTeam = plugin.tvtNexusManager.getPlayerTeam(attacker);
            if (attackerTeam != null) {
                if ((minionTeam == 1 && "BLUE".equalsIgnoreCase(attackerTeam)) ||
                        (minionTeam == 2 && "RED".equalsIgnoreCase(attackerTeam))) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // --- BLOKADA NAKIEROWYWANIA SIĘ NA GRACZY ORAZ SOJUSZNIKÓW ---

    @EventHandler
    public void onMinionTarget(EntityTargetEvent e) {
        if (!(e.getEntity() instanceof Mob)) return;
        Mob minion = (Mob) e.getEntity();

        if (!minion.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) return;

        Integer minionTeam = minion.getPersistentDataContainer().get(minionTeamKey, PersistentDataType.INTEGER);
        if (minionTeam == null) return;

        Entity rawTarget = e.getTarget();

        if (rawTarget instanceof Player) {
            e.setCancelled(true);
            minion.setTarget(null);
            return;
        }

        if (rawTarget != null) {
            if (rawTarget instanceof Mob) {
                Mob targetMob = (Mob) rawTarget;
                Integer targetTeam = targetMob.getPersistentDataContainer().get(minionTeamKey, PersistentDataType.INTEGER);
                if (minionTeam.equals(targetTeam)) {
                    e.setCancelled(true);
                    minion.setTarget(null);
                }
            } else if (rawTarget instanceof Villager) {
                String name = rawTarget.getCustomName();
                if (name != null) {
                    if ((minionTeam == 1 && name.contains("BLUE")) || (minionTeam == 2 && name.contains("RED"))) {
                        e.setCancelled(true);
                        minion.setTarget(null);
                    }
                }
            }
        }
    }

    // --- AKTUALIZACJA HP PRZY OBRAŻENIACH ---

    @EventHandler
    public void onMinionDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Mob)) return;
        Mob minion = (Mob) e.getEntity();

        if (!activeMinions.contains(minion)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (minion.isValid() && !minion.isDead()) {
                double currentHp = Math.max(0, minion.getHealth());
                String currentName = minion.getCustomName();

                if (currentName != null && currentName.contains("[")) {
                    String baseName = currentName.substring(0, currentName.lastIndexOf("["));
                    minion.setCustomName(baseName + "§7[" + (int) Math.ceil(currentHp) + "❤]");
                }
            }
        }, 1L);
    }

    public int getMinionTeam(Entity entity) {
        if (entity == null) return 0;
        Integer team = entity.getPersistentDataContainer().get(minionTeamKey, PersistentDataType.INTEGER);
        return team != null ? team : 0;
    }

    // --- NAGRODY ZA ZABICIE MINIONA ---

    @EventHandler
    public void onMinionDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Mob)) return;
        Mob minion = (Mob) e.getEntity();

        if (activeMinions.contains(minion)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            activeMinions.remove(minion);

            if (minion.getPersistentDataContainer().has(minionNoRewardKey, PersistentDataType.BYTE)) {
                return;
            }

            Player killer = minion.getKiller();
            if (killer == null || !plugin.isPlayerInTvT(killer)) {
                return;
            }

            int emeraldReward = 5;
            float teamExpPercent = 0.02f;

            if (plugin.tvtNexusManager != null) {
                String playerTeam = plugin.tvtNexusManager.getPlayerTeam(killer);
                if (playerTeam != null) {
                    plugin.addTeamExp(playerTeam, teamExpPercent);
                }
                plugin.tvtNexusManager.addCS(killer, 1);
            }

            if (plugin.tvtShopManager != null) {
                plugin.tvtShopManager.addEmeralds(killer, emeraldReward);
            }

            killer.sendMessage("§a§lMINION §8» §7Kill reward: §a+" + emeraldReward + " Emeralds §7| §e+2% EXP");
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }
}