package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class TvTNexusManager implements Listener {

    private final Main plugin;
    private final NamespacedKey nexusKey;
    private final NamespacedKey minionTeamKey;

    private Villager team1Nexus;
    private Villager team2Nexus;

    private Location team1SpawnLoc;
    private Location team2SpawnLoc;

    private double team1Hp = 200.0;
    private double team2Hp = 200.0;
    private final double maxHp = 200.0;

    private final Map<UUID, Integer> playerTeams = new HashMap<>(); // UUID -> 1 (Blue) or 2 (Red)
    private final Map<UUID, Integer> matchKills = new HashMap<>();  // Stats strictly for current match
    private final Map<UUID, Integer> matchDeaths = new HashMap<>();
    private final Map<UUID, Integer> matchCS = new HashMap<>(); // Creep Score dla obecnego meczu

    private BukkitTask fountainTask;
    private BukkitTask nexusDefenseTask;

    public TvTNexusManager(Main plugin) {
        this.plugin = plugin;
        this.nexusKey = new NamespacedKey(plugin, "tvt_nexus");
        this.minionTeamKey = new NamespacedKey(plugin, "minion_team");
        startFountainTask();
        startNexusDefenseTask();
    }

    /**
     * Sprawdza, czy gra w trybie TvT jest aktualnie aktywna.
     * Zwraca true, jeśli są gracze w drużynach oraz oba Nexusy są zespawnowane i poprawne.
     */
    public boolean isGameActive() {
        return !playerTeams.isEmpty() && team1Nexus != null && team1Nexus.isValid() && team2Nexus != null && team2Nexus.isValid();
    }

    /**
     * Ustawia lub usuwa drużynę gracza za pomocą komend admina.
     * @param target Gracz, którego drużyna ma zostać zmieniona.
     * @param teamName "BLUE", "1", "RED", "2" lub null / "" do usunięcia z meczu.
     */
    public void setPlayerTeam(Player target, String teamName) {
        if (target == null) return;
        UUID uuid = target.getUniqueId();

        if (teamName == null || teamName.isEmpty() || teamName.equalsIgnoreCase("NONE") || teamName.equalsIgnoreCase("NULL")) {
            playerTeams.remove(uuid);
            matchKills.remove(uuid);
            matchDeaths.remove(uuid);
            matchCS.remove(uuid);
            for (Set<UUID> votes : surrenderVotes.values()) {
                votes.remove(uuid);
            }
        } else if (teamName.equalsIgnoreCase("BLUE") || teamName.equalsIgnoreCase("1")) {
            playerTeams.put(uuid, 1);
            matchKills.putIfAbsent(uuid, 0);
            matchDeaths.putIfAbsent(uuid, 0);
            matchCS.putIfAbsent(uuid, 0);
        } else if (teamName.equalsIgnoreCase("RED") || teamName.equalsIgnoreCase("2")) {
            playerTeams.put(uuid, 2);
            matchKills.putIfAbsent(uuid, 0);
            matchDeaths.putIfAbsent(uuid, 0);
            matchCS.putIfAbsent(uuid, 0);
        }

        updateDisplays();
    }

    // --- SYSTEM FONTANNY NA SPAWNIE ---
    private void startFountainTask() {
        if (fountainTask != null) {
            fountainTask.cancel();
        }

        fountainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameActive()) return;

                Location blueSpawn = getSpawnLocByTeam(1);
                Location redSpawn = getSpawnLocByTeam(2);

                for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null || !p.isOnline() || p.isDead()) continue;

                    int playerTeam = entry.getValue();

                    // Sprawdzenie spawnu Blue (Team 1)
                    if (blueSpawn != null && blueSpawn.getWorld().equals(p.getWorld()) && p.getLocation().distance(blueSpawn) <= 4.0) {
                        applyFountainEffects(p, playerTeam, 1);
                    }

                    // Sprawdzenie spawnu Red (Team 2)
                    if (redSpawn != null && redSpawn.getWorld().equals(p.getWorld()) && p.getLocation().distance(redSpawn) <= 4.0) {
                        applyFountainEffects(p, playerTeam, 2);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // Sprawdzanie co 0.5s (10 tików)
    }

    private void applyFountainEffects(Player p, int playerTeam, int fountainTeam) {
        if (playerTeam == fountainTeam) {
            // Sojusznik -> Regeneracja IV na ~1.5 sekundy
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 3, true, false, true));
        } else {
            // Wróg -> Harming II (Instant Damage II)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HARM, 1, 1, true, false, true));
        }
    }

    // --- SYSTEM ATAKU / OBRONY NEXUSA ---
    private void startNexusDefenseTask() {
        if (nexusDefenseTask != null) {
            nexusDefenseTask.cancel();
        }

        // Strzelanie co 1 sekundę (20 tików)
        nexusDefenseTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameActive()) return;

                processNexusAttack(team1Nexus, 1);
                processNexusAttack(team2Nexus, 2);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void processNexusAttack(Villager nexus, int nexusTeam) {
        Location nexusLoc = nexus.getLocation().add(0, 1.5, 0); // Wysokość klatki piersiowej/głowy
        double range = 10.0;

        // 1. Priorytet: Wrogie miniony w zasięgu 10m (wykluczamy własną drużynę)
        Mob targetMinion = nexusLoc.getWorld().getNearbyEntities(nexusLoc, range, range, range).stream()
                .filter(e -> e instanceof Mob)
                .map(e -> (Mob) e)
                .filter(m -> {
                    if (!m.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) return false;
                    int minionTeam = m.getPersistentDataContainer().getOrDefault(minionTeamKey, PersistentDataType.INTEGER, 0);
                    return minionTeam != 0 && minionTeam != nexusTeam && !m.isDead();
                })
                .min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(nexusLoc)))
                .orElse(null);

        LivingEntity target = targetMinion;

        // 2. Jeśli brak wrogich minionów -> Wrogowie (Gracze przeciwnika) w zasięgu 10m
        if (target == null) {
            Player targetPlayer = nexusLoc.getWorld().getNearbyEntities(nexusLoc, range, range, range).stream()
                    .filter(e -> e instanceof Player)
                    .map(e -> (Player) e)
                    .filter(p -> {
                        int playerTeam = playerTeams.getOrDefault(p.getUniqueId(), 0);
                        return playerTeam != 0 && playerTeam != nexusTeam && !p.isDead() && p.getGameMode() == GameMode.SURVIVAL;
                    })
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(nexusLoc)))
                    .orElse(null);

            target = targetPlayer;
        }

        // Wystrzał wirtualnego pocisku bez eksplozji
        if (target != null) {
            spawnSingleTargetNexusProjectile(nexus, target, 10.0); // 10.0 obrażeń dla celu
            nexusLoc.getWorld().playSound(nexusLoc, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.2f);
        }
    }

    // --- METODA TWORZĄCA POCISK SINGLE-TARGET (ZERO EKSPLOZJI I SPLASH DMG) ---
    private void spawnSingleTargetNexusProjectile(Villager nexus, LivingEntity target, double damage) {
        Location nexusOrigin = nexus.getLocation().add(0, 1.5, 0);
        Location currentLoc = nexusOrigin.clone();

        new BukkitRunnable() {
            private int ticksLived = 0;
            private final double speed = 0.8; // Prędkość lotu pocisku na tik (co 0.05s)
            private final double maxNexusRange = 12.0; // Max zasięg lotu pocisku Nexusa (12 bloków)

            @Override
            public void run() {
                // 1. Znikaj bez obrażeń, jeśli cel/nexus nie istnieją lub cel umarł
                if (target == null || !target.isValid() || target.isDead() || nexus == null || !nexus.isValid() || ticksLived > 60) {
                    cancel();
                    return;
                }

                // 2. Jeśli pocisk wylatuje poza 12 bloków od punktu startowego Nexusa -> znika
                if (currentLoc.distanceSquared(nexusOrigin) > (maxNexusRange * maxNexusRange)) {
                    currentLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, currentLoc, 5, 0.1, 0.1, 0.1, 0.01);
                    cancel();
                    return;
                }

                // Śledzenie klatki piersiowej wyznaczonego celu
                Location targetCenter = target.getLocation().add(0, 1.0, 0);
                Vector direction = targetCenter.toVector().subtract(currentLoc.toVector());
                double distanceToTarget = direction.length();

                // 3. Trafienie – hit bezpośredni w 1 osobę/miniona (brak eksplozji)
                if (distanceToTarget <= speed) {
                    target.damage(damage, nexus); // Obrażenia przypisane bezpośrednio do celu
                    target.getWorld().playSound(targetCenter, Sound.ENTITY_ITEM_BREAK, 0.8f, 0.6f); // Dźwięk uderzenia
                    target.getWorld().spawnParticle(Particle.SQUID_INK, targetCenter, 10, 0.2, 0.2, 0.2, 0.05);
                    cancel();
                    return;
                }

                // Przesunięcie pocisku w stronę celu
                direction.normalize().multiply(speed);
                currentLoc.add(direction);

                // Efekty wizualne lecącej cząsteczki
                currentLoc.getWorld().spawnParticle(Particle.SMOKE_LARGE, currentLoc, 3, 0.05, 0.05, 0.05, 0.01);
                currentLoc.getWorld().spawnParticle(Particle.SQUID_INK, currentLoc, 2, 0.02, 0.02, 0.02, 0.01);

                ticksLived++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Location getSpawnLocByTeam(int team) {
        if (team == 1) return new Location(Bukkit.getWorlds().get(0), 185.5, 116.0, 185.5);
        if (team == 2) return new Location(Bukkit.getWorlds().get(0), 71.5, 116.0, 71.5);
        return null;
    }

    // --- GET PLAYER TEAM ---
    public String getPlayerTeam(Player p) {
        if (p == null) return null;
        int team = playerTeams.getOrDefault(p.getUniqueId(), 0);
        if (team == 1) return "BLUE";
        if (team == 2) return "RED";
        return null;
    }

    // --- GET MATCH STATS ---
    public int getMatchKills(Player p) {
        return matchKills.getOrDefault(p.getUniqueId(), 0);
    }

    public int getMatchDeaths(Player p) {
        return matchDeaths.getOrDefault(p.getUniqueId(), 0);
    }

    /**
     * Creep Score (CS) obecnego gracza w bieżącym meczu.
     * 1 CS = 5 emeraldów: minion=1, bank=4, lama=3, gigant=20, golem=10.
     */
    public int getMatchCS(Player p) {
        return matchCS.getOrDefault(p.getUniqueId(), 0);
    }

    /**
     * Dodaje CS do gracza w bieżącym meczu (np. przy zabiciu miniona/bossa dżungli).
     */
    public void addCS(Player p, int amount) {
        if (p == null) return;
        matchCS.merge(p.getUniqueId(), amount, Integer::sum);
    }

    public int getTeamNexusHp(Player p) {
        int team = playerTeams.getOrDefault(p.getUniqueId(), 0);
        if (team == 1) return (int) team1Hp;
        if (team == 2) return (int) team2Hp;
        return 0;
    }

    public int getEnemyNexusHp(Player p) {
        int team = playerTeams.getOrDefault(p.getUniqueId(), 0);
        if (team == 1) return (int) team2Hp;
        if (team == 2) return (int) team1Hp;
        return 0;
    }

    public Location getPlayerTeamSpawn(Player p) {
        int team = playerTeams.getOrDefault(p.getUniqueId(), 0);
        if (team == 1) return new Location(Bukkit.getWorlds().get(0), 185.5, 116.0, 185.5, 45.0f, 0.0f);
        if (team == 2) return new Location(Bukkit.getWorlds().get(0), 71.5, 116.0, 71.5, -135.0f, 0.0f);
        return null;
    }

    // --- CHECK IF PLAYER IS NEAR THEIR NEXUS (10-BLOCK RADIUS) ---
    public boolean isPlayerNearNexus(Player p) {
        if (p == null || !p.isOnline() || p.isDead()) return false;

        Location nexusLoc = getPlayerTeamSpawn(p);
        if (nexusLoc == null || nexusLoc.getWorld() == null || !nexusLoc.getWorld().equals(p.getWorld())) {
            return false;
        }

        return p.getLocation().distance(nexusLoc) <= 10.0;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();

        if (isPlayerInTvT(victim)) {
            matchDeaths.put(victim.getUniqueId(), getMatchDeaths(victim) + 1);

            Player killer = victim.getKiller();
            if (killer != null && isPlayerInTvT(killer)) {
                matchKills.put(killer.getUniqueId(), getMatchKills(killer) + 1);
            }

            updateDisplays();
        }
    }

    public void spawnNexuses(Location locTeam1, Location locTeam2, List<Player> team1, List<Player> team2) {
        this.team1SpawnLoc = locTeam1;
        this.team2SpawnLoc = locTeam2;

        clearNexusesAtLoc(locTeam1);
        clearNexusesAtLoc(locTeam2);
        clearNexuses();

        team1Hp = maxHp;
        team2Hp = maxHp;

        playerTeams.clear();
        matchKills.clear();
        matchDeaths.clear();
        matchCS.clear();
        plugin.resetTeamLevels();
        resetJungleData();

        for (Player p : team1) playerTeams.put(p.getUniqueId(), 1);
        for (Player p : team2) playerTeams.put(p.getUniqueId(), 2);

        team1Nexus = (Villager) locTeam1.getWorld().spawnEntity(locTeam1, EntityType.VILLAGER);
        configureNexus(team1Nexus, "§9§lBLUE NEXUS", team1Hp);

        team2Nexus = (Villager) locTeam2.getWorld().spawnEntity(locTeam2, EntityType.VILLAGER);
        configureNexus(team2Nexus, "§c§lRED NEXUS", team2Hp);

        updateDisplays();
    }

    private void configureNexus(Villager v, String title, double hp) {
        v.getPersistentDataContainer().set(nexusKey, PersistentDataType.BYTE, (byte) 1);
        v.setCustomName(title + " §7[" + (int) hp + "/" + (int) maxHp + " ❤]");
        v.setCustomNameVisible(true);
        v.setAI(false);
        v.setCollidable(true);
        v.setInvulnerable(false);
        v.setProfession(Villager.Profession.NITWIT);
        if (v.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            v.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
            v.setHealth(maxHp);
        }
    }

    public boolean isPlayerInTvT(Player p) {
        return playerTeams.containsKey(p.getUniqueId());
    }

    @EventHandler
    public void onNexusDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Villager)) return;
        Villager target = (Villager) e.getEntity();

        if (!target.getPersistentDataContainer().has(nexusKey, PersistentDataType.BYTE)) return;

        // Określenie atakującej drużyny (Gracz, Minion lub Strzała)
        Entity damager = e.getDamager();
        int attackerTeam = 0;

        if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Entity) {
                damager = (Entity) projectile.getShooter();
            }
        }

        if (damager instanceof Player) {
            attackerTeam = playerTeams.getOrDefault(damager.getUniqueId(), 0);
        } else if (damager instanceof Mob) {
            Mob mob = (Mob) damager;
            if (mob.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) {
                attackerTeam = mob.getPersistentDataContainer().getOrDefault(minionTeamKey, PersistentDataType.INTEGER, 0);
            }
        }

        // Blokujemy obrażenia od encji spoza gry
        if (attackerTeam == 0) {
            e.setCancelled(true);
            return;
        }

        // Blokada Friendly Fire (atakowanie własnego Nexusa)
        if ((target == team1Nexus && attackerTeam == 1) || (target == team2Nexus && attackerTeam == 2)) {
            e.setCancelled(true);
            if (damager instanceof Player) {
                ((Player) damager).sendMessage("§c§l(!) §7You cannot attack your own Nexus!");
            }
            return;
        }

        // --- WARUNEK: NEXUS JEST CHRONIONY, JEŚLI NIE MA WROGA W ZASIĘGU (10 BLOKÓW) ---
        int nexusTeam = (target == team1Nexus) ? 1 : 2;
        if (!isEnemyInNexusRange(target, nexusTeam)) {
            e.setCancelled(true);
            if (damager instanceof Player) {
                ((Player) damager).sendMessage("§c§l(!) §7Nexus is protected! It must have a target in range to be attacked.");
            }
            return;
        }

        // --- BLOKADA SPAMU CIOSAMI (IMMUNITY FRAMES / NO DAMAGE TICKS) ---
        if (target.getNoDamageTicks() > 0) {
            e.setCancelled(true);
            return;
        }

        double damage = e.getFinalDamage();

        // --- NOWA LOGIKA: 50% MNIEJ OBRAŻEŃ OD GRACZA, JEŚLI W ZASIĘGU NIE MA MINIONÓW ---
        if (damager instanceof Player) {
            boolean hasMinonsInRange = hasEnemyMinionsInRange(target, nexusTeam);
            if (!hasMinonsInRange) {
                damage = damage * 0.5; // Redukcja o 50%
            }
        }

        // Ustawiamy finalny damage eventu na 0, żeby kontrolować HP przez zmienne klasy
        e.setDamage(0);

        // Aktywujemy klatki nietykalności
        target.setNoDamageTicks(target.getMaximumNoDamageTicks());

        if (target == team1Nexus) {
            team1Hp = Math.max(0, team1Hp - damage);
            updateNexusName(team1Nexus, "§b§lBLUE NEXUS", team1Hp);
            broadcastSound(Sound.ENTITY_VILLAGER_HURT, 1.0f, 0.8f);

            if (team1Hp <= 0) {
                endGame(2);
                return;
            }
        } else if (target == team2Nexus) {
            team2Hp = Math.max(0, team2Hp - damage);
            updateNexusName(team2Nexus, "§c§lRED NEXUS", team2Hp);
            broadcastSound(Sound.ENTITY_VILLAGER_HURT, 1.0f, 0.8f);

            if (team2Hp <= 0) {
                endGame(1);
                return;
            }
        }

        updateDisplays();
    }

    // --- POMOCNICZA METODA SPRAWDZAJĄCA CZY W ZASIĘGU NEXUSA SĄ TYLKO MINIONY ---
    private boolean hasEnemyMinionsInRange(Villager nexus, int nexusTeam) {
        Location nexusLoc = nexus.getLocation().add(0, 1.5, 0);
        double range = 10.0;

        return nexusLoc.getWorld().getNearbyEntities(nexusLoc, range, range, range).stream()
                .filter(e -> e instanceof Mob)
                .map(e -> (Mob) e)
                .anyMatch(m -> {
                    if (!m.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) return false;
                    int minionTeam = m.getPersistentDataContainer().getOrDefault(minionTeamKey, PersistentDataType.INTEGER, 0);
                    return minionTeam != 0 && minionTeam != nexusTeam && !m.isDead();
                });
    }

    // --- POMOCNICZA METODA SPRAWDZAJĄCA CZY W ZASIĘGU NEXUSA JEST WROGI CEL ---
    private boolean isEnemyInNexusRange(Villager nexus, int nexusTeam) {
        Location nexusLoc = nexus.getLocation().add(0, 1.5, 0);
        double range = 10.0;

        // 1. Sprawdzanie wrogich minionów
        boolean hasMinion = nexusLoc.getWorld().getNearbyEntities(nexusLoc, range, range, range).stream()
                .filter(e -> e instanceof Mob)
                .map(e -> (Mob) e)
                .anyMatch(m -> {
                    if (!m.getPersistentDataContainer().has(minionTeamKey, PersistentDataType.INTEGER)) return false;
                    int minionTeam = m.getPersistentDataContainer().getOrDefault(minionTeamKey, PersistentDataType.INTEGER, 0);
                    return minionTeam != 0 && minionTeam != nexusTeam && !m.isDead();
                });

        if (hasMinion) return true;

        // 2. Sprawdzanie wrogich graczy
        return nexusLoc.getWorld().getNearbyEntities(nexusLoc, range, range, range).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .anyMatch(p -> {
                    int playerTeam = playerTeams.getOrDefault(p.getUniqueId(), 0);
                    return playerTeam != 0 && playerTeam != nexusTeam && !p.isDead() && p.getGameMode() == GameMode.SURVIVAL;
                });
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(nexusKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    private void updateNexusName(Villager v, String prefix, double currentHp) {
        v.setCustomName(prefix + " §7[" + (int) currentHp + "/" + (int) maxHp + " ❤]");
    }

    private void endGame(int winningTeam) {
        String winnerText = (winningTeam == 1) ? "§b§lBLUE TEAM" : "§c§lRED TEAM";

        for (UUID uuid : playerTeams.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("");
                p.sendMessage("§3§l=================================");
                p.sendMessage("§a§lTvT MATCH ENDED!");
                p.sendMessage("§7Winners: " + winnerText);
                p.sendMessage("§3§l=================================");
                p.sendMessage("");

                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                p.getInventory().clear();

                if (plugin.tvtShopManager != null) {
                    plugin.tvtShopManager.clearEmeralds(p);
                }
                if (plugin.minionManager != null) {
                    plugin.minionManager.stopMinionWaves();
                }
                plugin.giveLobbyItems(p);
                plugin.teleportToMainSpawn(p);
                surrenderVotes.clear();
            }
        }

        clearNexuses();

        playerTeams.clear();
        matchKills.clear();
        matchDeaths.clear();
        matchCS.clear();
        plugin.resetTeamLevels();
        resetJungleData();

        updateDisplays();
    }

    public void clearNexuses() {
        if (team1Nexus != null) {
            team1Nexus.remove();
            team1Nexus = null;
        }
        if (team2Nexus != null) {
            team2Nexus.remove();
            team2Nexus = null;
        }

        if (team1SpawnLoc != null) clearNexusesAtLoc(team1SpawnLoc);
        if (team2SpawnLoc != null) clearNexusesAtLoc(team2SpawnLoc);

        resetJungleData();
    }

    private void clearNexusesAtLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 4.0, 4.0, 4.0)) {
            if (e instanceof Villager) {
                if (e.getPersistentDataContainer().has(nexusKey, PersistentDataType.BYTE) ||
                        (e.getCustomName() != null && e.getCustomName().contains("NEXUS"))) {
                    e.remove();
                }
            }
        }
    }

    public void forceEndGame() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.isPlayerInTvT(p) || playerTeams.containsKey(p.getUniqueId())) {
                p.sendMessage("§c§lTvT §8» §7The match has been forcibly ended by an administrator!");
                p.getInventory().clear();

                if (plugin.tvtShopManager != null) {
                    plugin.tvtShopManager.clearEmeralds(p);
                }

                plugin.giveLobbyItems(p);
                plugin.teleportToMainSpawn(p);
                surrenderVotes.clear();
            }
        }

        clearNexuses();

        playerTeams.clear();
        matchKills.clear();
        matchDeaths.clear();
        matchCS.clear();
        plugin.resetTeamLevels();
        resetJungleData();
        updateDisplays();
    }

    /**
     * Pomocnicza metoda zerująca stan dżungli (tiery i moby) poprzez JungleManager.
     */
    private void resetJungleData() {
        if (plugin.jungleManager != null) {
            plugin.jungleManager.stopJungleSystem();
        }
    }

    // --- SYSTEM PODDAWANIA SIĘ (/ff) ---
    private final Map<Integer, Set<UUID>> surrenderVotes = new HashMap<>(); // Zespół -> Zbiór UUID graczy, którzy wpisali /ff

    public void handleSurrenderCommand(Player p) {
        if (!isPlayerInTvT(p)) {
            p.sendMessage("§c§l(!) §7You are not in a TvT match!");
            return;
        }

        int playerTeam = playerTeams.get(p.getUniqueId());
        UUID uuid = p.getUniqueId();

        surrenderVotes.putIfAbsent(playerTeam, new HashSet<>());
        Set<UUID> votes = surrenderVotes.get(playerTeam);

        if (votes.contains(uuid)) {
            p.sendMessage("§c§l(!) §7You have already voted to surrender! Wait for your teammates.");
            return;
        }

        votes.add(uuid);

        // Pobieramy bezpiecznie wszystkich aktywnych graczy z danej drużyny
        List<Player> teamPlayers = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
            if (entry.getValue() == playerTeam) {
                Player teammate = Bukkit.getPlayer(entry.getKey());
                if (teammate != null && teammate.isOnline()) {
                    teamPlayers.add(teammate);
                }
            }
        }

        int requiredVotes = teamPlayers.size();
        int currentVotes = 0;

        // Liczymy ile z obecnych online graczy z tej drużyny oddało głos
        for (Player teammate : teamPlayers) {
            if (votes.contains(teammate.getUniqueId())) {
                currentVotes++;
            }
        }

        // Wysyłamy wiadomość na czacie DO KAŻDEGO gracza w tej drużynie
        for (Player teammate : teamPlayers) {
            teammate.sendMessage("§e§l(!) §ePlayer §f" + p.getName() + " §ewants to surrender! Votes: §a" + currentVotes + "§7/§a" + requiredVotes);
            teammate.playSound(teammate.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        }

        // Jeśli wszyscy online gracze z drużyny zagłosowali -> Koniec gry
        if (currentVotes >= requiredVotes) {
            surrenderVotes.remove(playerTeam);
            int winningTeam = (playerTeam == 1) ? 2 : 1;

            for (Player all : Bukkit.getOnlinePlayers()) {
                if (isPlayerInTvT(all)) {
                    all.sendMessage("§c§l(!) §7The " + (playerTeam == 1 ? "Blue" : "Red") + " team has surrendered! The opposing team wins the match!");
                }
            }

            endGame(winningTeam);
        }
    }

    public void clearSurrenderVotes() {
        surrenderVotes.clear();
    }

    private void updateDisplays() {
        if (plugin.tabManager != null) {
            plugin.tabManager.updateAll();
        }
        if (plugin.sbManager != null) {
            plugin.sbManager.updateAll();
        }
    }

    private void broadcastSound(Sound sound, float volume, float pitch) {
        for (UUID uuid : playerTeams.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }
}