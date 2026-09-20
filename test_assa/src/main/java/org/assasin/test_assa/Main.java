package org.assasin.test_assa;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.stream.Collectors;

public class Main extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private Minotaur minotaur;
    private Paladyn paladyn;
    private Assasyn assasyn;
    private Hunter hunter;
    private Berserker berserker;

    public ShopManager shopManager;
    public ScoreboardManager sbManager;
    public TabManager tabManager;
    public QueueManager queueManager;
    private MenuManager menuManager;
    public TvTManager tvtManager;
    public TvTQueueManager tvtQueueManager;
    public TvTNexusManager tvtNexusManager;
    public JungleManager jungleManager;
    public TvTShopManager tvtShopManager;
    public MinionManager minionManager;

    private final Set<Player> playersInTvT = new HashSet<>();
    private final Map<UUID, String> selectedClass = new HashMap<>();
    private final Map<UUID, ItemStack[]> stunnedPlayersHotbar = new HashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastSatiatedRegen = new java.util.HashMap<>();
    private final Map<UUID, Integer> respawningPlayers = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Set<UUID> stunnedPlayers = new HashSet<>();
    // SYSTEM RECALLU (TYLKO TvT)
    private final Map<UUID, BukkitTask> activeRecalls = new HashMap<>();
    private final int RECALL_DELAY_SECONDS = 5;

    // SYSTEM LEVELI DRUŻYNOWYCH (TvT)
    private final Map<String, Integer> teamLevels = new HashMap<>();
    private final Map<String, Float> teamExpProgress = new HashMap<>();

    private static class PlayerGameState {
        final Location location;
        final double health;

        PlayerGameState(Location location, double health) {
            this.location = location;
            this.health = health;
        }
    }

    private final Map<UUID, PlayerGameState> savedGameStates = new HashMap<>();

    public Paladyn getPaladyn() {
        return this.paladyn;
    }

    public void openClassMenu(Player p) {
        if (menuManager != null) {
            menuManager.openClassMenu(p);
        }
    }

    public void giveLobbyItems(Player p) {
        p.getInventory().clear();
        if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        }
        p.setHealth(20.0);
        if (p.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
            p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
        }

        p.setLevel(0);
        p.setExp(0.0f);

        selectedClass.remove(p.getUniqueId());

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lMODE MENU §7(Right Click)");
            meta.setLore(Arrays.asList("§7Click to open the mode selection!"));
            compass.setItemMeta(meta);
        }

        p.getInventory().setItem(4, compass);
    }

    public void teleportToRandomFFA(Player p) {
        List<org.bukkit.Location> spawns = Arrays.asList(
                new org.bukkit.Location(p.getWorld(), -84.59, 66.00, -105.35, -479.01f, 10.48f),
                new org.bukkit.Location(p.getWorld(), -67.93, 65.50, -115.18, -448.30f, 6.62f),
                new org.bukkit.Location(p.getWorld(), -48.42, 65.00, -110.50, -611.75f, 10.26f),
                new org.bukkit.Location(p.getWorld(), -60.39, 64.00, -121.58, -718.41f, 3.44f)
        );

        Random random = new Random();
        org.bukkit.Location randomSpawn = spawns.get(random.nextInt(spawns.size()));

        p.teleport(randomSpawn);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    public void teleportToMainSpawn(Player p) {
        org.bukkit.Location mainSpawn = new org.bukkit.Location(p.getWorld(), 295.48, 113.00, -261.60, -180.13f, 2.68f);

        if (tvtManager != null) {
            tvtManager.clearSelectingClass(p);
        }
        p.closeInventory(); // teraz bezpiecznie zamknie okno wyboru klasy, jeśli było otwarte

        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(mainSpawn);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    @Override
    public void onEnable() {
        this.shopManager = new ShopManager(this);
        this.sbManager = new ScoreboardManager(this);
        this.tabManager = new TabManager(this);
        this.queueManager = new QueueManager(this);
        this.menuManager = new MenuManager(this);
        this.tvtManager = new TvTManager(this);
        this.tvtQueueManager = new TvTQueueManager(this);
        this.tvtNexusManager = new TvTNexusManager(this);
        this.jungleManager = new JungleManager(this);
        this.tvtShopManager = new TvTShopManager(this);
        this.minionManager = new MinionManager(this);
        TvTAdminCommand tvtAdminCommand = new TvTAdminCommand(this);
        CustomRegenManager regenManager = new CustomRegenManager(this);
        regenManager.startRegenTask();
        getServer().getPluginManager().registerEvents(tvtShopManager, this);
        getServer().getPluginManager().registerEvents(tvtNexusManager, this);
        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(this.queueManager, this);
        getServer().getPluginManager().registerEvents(tvtQueueManager, this);
        getServer().getPluginManager().registerEvents(jungleManager, this);

        this.minotaur = new Minotaur(this);
        this.paladyn = new Paladyn(this);
        this.assasyn = new Assasyn(this);
        this.hunter = new Hunter(this);
        this.berserker = new Berserker(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(shopManager, this);
        getServer().getPluginManager().registerEvents(minotaur, this);
        getServer().getPluginManager().registerEvents(paladyn, this);
        getServer().getPluginManager().registerEvents(assasyn, this);
        getServer().getPluginManager().registerEvents(hunter, this);
        getServer().getPluginManager().registerEvents(berserker, this);

        getCommand("kit").setExecutor(this);
        getCommand("statsadmin").setExecutor(this);
        getCommand("leave").setExecutor(this);
        getCommand("spawn").setExecutor(this);
        getCommand("tvtadmin").setExecutor(tvtAdminCommand);
        getCommand("tvtadmin").setTabCompleter(tvtAdminCommand);
        if (getCommand("test_assa") != null) {
            getCommand("test_assa").setExecutor(this);
            getCommand("test_assa").setTabCompleter(this);
        }
        if (getCommand("spawnbank") != null) {
            getCommand("spawnbank").setExecutor(this);
        }

        if (getCommand("recall") != null) {
            getCommand("recall").setExecutor(this);
        }

        if (getCommand("tvt") != null) {
            getCommand("tvt").setExecutor(this);
            getCommand("tvt").setTabCompleter(this);
        }
        if (getCommand("spawnzombiehorse") != null) {
            getCommand("spawnzombiehorse").setExecutor(this);
        }
        if (getCommand("spawngiant") != null) {
            getCommand("spawngiant").setExecutor(this);
        }
        // Umieść ten kod w metodzie onEnable() w klasie Main.java
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                // Aktualizacja Tablisty (zegar MM:SS, nexuse, emeraldy)
                if (tabManager != null) {
                    tabManager.updateAll();
                }
            }
        }, 0L, 20L); // 0L = start od razu, 20L = powtarzaj co 20 ticków (1 sekunda)
        // Zamień dwa zduplikowane bloki runTaskTimer na ten jeden:
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (tabManager != null) {
                    tabManager.updateAll();
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHealthScoreboard(p);
                }
            }
        }, 0L, 20L);
        getLogger().info("Plugin test_assa - Loaded successfully!");
    }
    public void updateHealthScoreboard(Player p) {
        if (p == null || !p.isOnline()) return;

        int hearts = (int) Math.ceil(p.getHealth() / 2.0);
        String hpFormatted = String.format("%.1f", p.getHealth() / 2.0);

        String playerTeam = tvtNexusManager != null ? tvtNexusManager.getPlayerTeam(p) : null;

        ChatColor color;
        String colorCode;

        if ("BLUE".equalsIgnoreCase(playerTeam)) {
            color = ChatColor.BLUE;
            colorCode = "§9";
        } else if ("RED".equalsIgnoreCase(playerTeam)) {
            color = ChatColor.RED;
            colorCode = "§c";
        } else {
            color = ChatColor.WHITE;
            colorCode = "§f";
        }

        // Nazwa unikalnej drużyny per-gracz
        String playerTeamKey = "hp_" + p.getName();
        if (playerTeamKey.length() > 16) {
            playerTeamKey = playerTeamKey.substring(0, 16);
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();

            // 1. Licznik HP pod nickiem
            Objective obj = board.getObjective("hp_name");
            if (obj != null) {
                obj.getScore(p.getName()).setScore(hearts);
            }

            // 2. Usuwamy gracza z globalnych drużyn TvT (żeby Minecraft nie przywracał z nich ciemnego niebieskiego)
            Team globalBlue = board.getTeam("TvT_BLUE");
            if (globalBlue != null && globalBlue.hasEntry(p.getName())) {
                globalBlue.removeEntry(p.getName());
            }
            Team globalRed = board.getTeam("TvT_RED");
            if (globalRed != null && globalRed.hasEntry(p.getName())) {
                globalRed.removeEntry(p.getName());
            }

            // 3. Pobieramy lub rejestrujemy unikalną drużynę dla gracza
            Team team = board.getTeam(playerTeamKey);
            if (team == null) {
                team = board.registerNewTeam(playerTeamKey);
            }

            if (!team.hasEntry(p.getName())) {
                team.addEntry(p.getName());
            }

            // ZAWSZE ustawiamy ten sam kolor, prefiks i suffix (niezależnie od tego czy ma full HP)
            team.setColor(color);
            team.setPrefix(colorCode);
            team.setSuffix(" §r§c[" + hpFormatted + "❤]");
        }
    }
    /**
     * Uniwersalna metoda do ogłuszania graczy lub mobów.
     * @param entity Cel do ogłuszenia (Player lub LivingEntity)
     * @param durationTicks Czas trwania stuna w tickach (20 ticków = 1 sekunda)
     */
    public void applyStun(LivingEntity entity, int durationTicks) {
        if (entity == null || !entity.isValid()) return;

        entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0, false, false, true));

        // SLOW na poziomie 3 (lub 4) pozwala na BARDZO wolne chodzenie bez całkowitego zamrożenia
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, durationTicks, 3, false, false, true));

        if (entity instanceof Player) {
            Player targetPlayer = (Player) entity;
            UUID uuid = targetPlayer.getUniqueId();

            // Rejestrujemy gracza w zbiorze blokującym skakanie
            stunnedPlayers.add(uuid);

            if (!stunnedPlayersHotbar.containsKey(uuid)) {
                ItemStack[] originalHotbar = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    originalHotbar[i] = targetPlayer.getInventory().getItem(i);

                    ItemStack barrier = new ItemStack(Material.BARRIER);
                    ItemMeta meta = barrier.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§c§lSTUNNED!");
                        barrier.setItemMeta(meta);
                    }
                    targetPlayer.getInventory().setItem(i, barrier);
                }

                stunnedPlayersHotbar.put(uuid, originalHotbar);
                targetPlayer.updateInventory();
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    restoreStunnedPlayer(targetPlayer);
                }
            }.runTaskLater(this, durationTicks);
        }
    }
    @EventHandler
    public void onPlayerMoveStun(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (!stunnedPlayers.contains(p.getUniqueId())) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        // Sprawdzamy czy gracz faktycznie się przemieścił (nie tylko obrócił kamerą)
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location blocked = from.clone();
            blocked.setYaw(to.getYaw());
            blocked.setPitch(to.getPitch());
            e.setTo(blocked);
        }
    }
    public void restoreStunnedPlayer(Player p) {
        if (p == null) return;
        UUID uuid = p.getUniqueId();

        // Usuwamy gracza z blokady skakania
        stunnedPlayers.remove(uuid);

        if (stunnedPlayersHotbar.containsKey(uuid)) {
            ItemStack[] original = stunnedPlayersHotbar.remove(uuid);
            if (p.isOnline()) {
                for (int i = 0; i < 9; i++) {
                    p.getInventory().setItem(i, original[i]);
                }
            }
        }
    }
    // --- SYSTEM POZIOMÓW DRUŻYNOWYCH (SPÓJNY I JEDNOLITY) ---

    public void resetTeamLevels() {
        teamLevels.clear();
        teamExpProgress.clear();
        teamLevels.put("RED", 1);
        teamLevels.put("BLUE", 1);
        teamExpProgress.put("RED", 0.0f);
        teamExpProgress.put("BLUE", 0.0f);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isPlayerInTvT(p)) {
                p.setLevel(1);
                p.setExp(0.0f);
            }
        }
    }

    public int getTeamLevel(String teamName) {
        return teamLevels.getOrDefault(teamName, 1);
    }

    public void addTeamExp(String team, float amount) {
        if (team == null) return;

        float currentExp = teamExpProgress.getOrDefault(team, 0.0f) + amount;
        int currentLvl = teamLevels.getOrDefault(team, 1);

        while (currentExp >= 1.0f) {
            currentExp -= 1.0f;
            currentLvl++;

            for (Player p : getTeamPlayers(team)) {
                p.sendMessage("§a§lLEVEL UP! §7Your team has reached level §e" + currentLvl + "§7!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            // NOWE: nagroda emeraldowa za awans
            if (tvtManager != null) {
                tvtManager.rewardTeamLevelUp(team, currentLvl);
            }
        }

        teamExpProgress.put(team, currentExp);
        teamLevels.put(team, currentLvl);

        updateAllTeamPlayersExpBars(team);
    }

    public List<Player> getTeamPlayers(String team) {
        List<Player> players = new ArrayList<>();
        if (team == null || tvtNexusManager == null) return players;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isPlayerInTvT(p)) {
                String pTeam = tvtNexusManager.getPlayerTeam(p);
                if (team.equalsIgnoreCase(pTeam)) {
                    players.add(p);
                }
            }
        }
        return players;
    }

    public void updatePlayerExpBar(Player p) {
        if (!isPlayerInTvT(p)) return;
        String team = tvtNexusManager.getPlayerTeam(p);
        if (team != null) {
            p.setLevel(teamLevels.getOrDefault(team, 1));
            p.setExp(teamExpProgress.getOrDefault(team, 0.0f));
        }
    }

    private void updateAllTeamPlayersExpBars(String teamName) {
        for (Player p : getTeamPlayers(teamName)) {
            updatePlayerExpBar(p);
        }
    }

    public boolean isPlayerInTvT(Player player) {
        return playersInTvT.contains(player) || (tvtNexusManager != null && tvtNexusManager.isPlayerInTvT(player));
    }

    /**
     * Sprawdza, czy target to gracz z tej samej drużyny TvT co atakujący.
     * Używane przez umiejętności klasowe, żeby całkowicie pomijać sojuszników
     * (zero obrażeń, zero efektów, zero CC) — nie tylko blokować event damage.
     */
    public boolean isAlly(Player attacker, Entity target) {
        if (attacker == null || target == null) return false;
        if (tvtNexusManager == null) return false;
        if (!isPlayerInTvT(attacker)) return false;

        String attackerTeam = tvtNexusManager.getPlayerTeam(attacker);
        if (attackerTeam == null) return false;

        // 1. Gdy celem jest inny Gracz
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (!isPlayerInTvT(targetPlayer)) return false;

            String targetTeam = tvtNexusManager.getPlayerTeam(targetPlayer);
            return attackerTeam.equalsIgnoreCase(targetTeam);
        }

        // 2. Gdy celem jest Minion
        if (minionManager != null && minionManager.isMinion(target)) {
            NamespacedKey key = new NamespacedKey(this, "minion_team");
            Integer minionTeamId = target.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);

            if (minionTeamId != null) {
                if (minionTeamId == 1 && "BLUE".equalsIgnoreCase(attackerTeam)) return true;
                if (minionTeamId == 2 && "RED".equalsIgnoreCase(attackerTeam)) return true;
            }
        }

        return false;
    }
    public void addPlayerToTvT(Player player) {
        playersInTvT.add(player);
    }

    public void removePlayerFromTvT(Player player) {
        playersInTvT.remove(player);
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        sbManager.createScoreboard(p);
        tabManager.updateTab(p);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            updateHealthScoreboard(p);

            // 1. Sprawdzenie, czy gracz wylogował się w trakcie odliczania do odrodzenia
            if (respawningPlayers.containsKey(p.getUniqueId())) {

                if (isPlayerInTvT(p)) {
                    savedGameStates.remove(p.getUniqueId());
                    p.sendMessage("§c§l(!) §7Zginąłeś przed wyjściem! Oczekiwanie na odrodzenie...");

                    // Wznawiamy timer od momentu, w którym gracz wyszedł
                    int remainingTime = respawningPlayers.get(p.getUniqueId());
                    startRespawnTimer(p, remainingTime);
                    return;
                } else {
                    respawningPlayers.remove(p.getUniqueId());
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }

            // 2. Normalne przywracanie do TvT (gdy gracz ŻYŁ podczas wylogowania)
            if (isPlayerInTvT(p)) {
                p.sendMessage("§e§l(!) §7Active TvT match detected! Restoring to the match...");
                p.setGameMode(GameMode.SURVIVAL);

                if (selectedClass.containsKey(p.getUniqueId())) {
                    givePlayerClass(p, selectedClass.get(p.getUniqueId()));
                } else {
                    givePlayerClass(p, "Minotaur");
                }

                PlayerGameState state = savedGameStates.remove(p.getUniqueId());
                if (state != null) {
                    p.teleport(state.location);
                    double maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    p.setHealth(Math.min(state.health, maxHp));
                } else {
                    Location teamSpawn = this.tvtNexusManager.getPlayerTeamSpawn(p);
                    if (teamSpawn != null) p.teleport(teamSpawn);
                }
                return;
            }

            // 3. Gracz nie jest w TvT (lobby / główny spawn)
            p.setGameMode(GameMode.SURVIVAL);
            giveLobbyItems(p);
            teleportToMainSpawn(p);

        }, 1L);
    }
    @EventHandler
    public void onRegen(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player) updateHealthScoreboard((Player) e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            cancelRecall(p, "§c§lRECALL §8» §7Cancelled! You took damage.");

            // Aktualizacja HP w następnym ticku, aby Bukkit zdążył przeliczyć nowe HP po ciosie
            Bukkit.getScheduler().runTaskLater(this, () -> updateHealthScoreboard(p), 1L);
        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (activeRecalls.containsKey(p.getUniqueId())) {
            if (e.getFrom().getBlockX() != e.getTo().getBlockX() ||
                    e.getFrom().getBlockY() != e.getTo().getBlockY() ||
                    e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
                cancelRecall(p, "§c§lRECALL §8» §7Cancelled! You moved.");
            }
        }
    }
    @EventHandler
    public void onPlayerHeal(org.bukkit.event.entity.EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            // Po uleczeniu (nawet do full HP) odświeżamy natychmiast scoreboard
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline()) {
                    updateHealthScoreboard(p);
                }
            }, 1L);
        }
    }
    public class CustomRegenManager {

        private final JavaPlugin plugin;
        private BukkitTask regenTask;

        public CustomRegenManager(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        public void startRegenTask() {
            // Co 3 sekundy (60 ticków)
            regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.isDead() || !p.isOnline()) continue;

                    // Sprawdzamy, czy gracz ma włączone PvP / gra w trybie, w którym regena ma działać (opcjonalnie można dodać check `plugin.isPlayerInTvT(p)`)

                    double health = p.getHealth();
                    AttributeInstance maxHealthAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (maxHealthAttr == null) continue;
                    double maxHealth = maxHealthAttr.getValue();

                    // Jeśli gracz ma pełne HP, nie ruszamy nic
                    if (health >= maxHealth) continue;

                    float saturation = p.getSaturation();
                    int foodLevel = p.getFoodLevel();

                    // Mechanika jak w Vanilla: regena zużywa saturację (lub poziom głodu, jeśli saturacja wynosi 0)
                    // Zabieramy pół mięska saturacji (0.5) lub głodu, jeśli saturacja jest zerowa.
                    if (saturation >= 0.5f) {
                        p.setSaturation(saturation - 0.5f);
                    } else if (foodLevel > 0) {
                        p.setFoodLevel(foodLevel - 1);
                    } else {
                        // Brak jedzenia / saturacji – brak regeneracji
                        continue;
                    }

                    // Leczymy o pół serduszka (1.0 HP, ponieważ 2.0 HP = 1 całe serce)
                    double newHealth = Math.min(maxHealth, health + 1.0);
                    p.setHealth(newHealth);
                }
            }, 0L, 100L); // 60 ticków = 3 sekundy
        }

        public void stopRegenTask() {
            if (regenTask != null) {
                regenTask.cancel();
                regenTask = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        boolean isInGame = isPlayerInTvT(p) || (queueManager != null && queueManager.playersInGame.contains(p.getUniqueId()));

        if (isInGame) {
            if (p.isOp()) return;

            String message = event.getMessage().toLowerCase().trim();

            if (!message.startsWith("/msg ") && !message.equals("/msg") &&
                    !message.startsWith("/tell ") && !message.equals("/tell") &&
                    !message.startsWith("/w ") && !message.equals("/w") &&
                    !message.startsWith("/recall") && !message.equals("/recall") &&
                    !message.startsWith("/ff") && !message.equals("/ff") &&
                    !message.startsWith("/forfeit") && !message.equals("/forfeit") &&
                    !message.startsWith("/surrender") && !message.equals("/surrender")) {

                event.setCancelled(true);
                p.sendMessage("§c§l(!) §7You cannot use commands during the game! Only §e/msg§7, §e/recall §7and §e/ff §7are allowed.");
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();

        if (!isPlayerInTvT(p)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                updateHealthScoreboard(p);

                if (selectedClass.containsKey(p.getUniqueId())) {
                    givePlayerClass(p, selectedClass.get(p.getUniqueId()));

                    if (!queueManager.playersInGame.contains(p.getUniqueId())) {
                        teleportToRandomFFA(p);
                    }
                } else {
                    giveLobbyItems(p);
                    teleportToMainSpawn(p);
                }
            }, 2L);
        }
    }

    public void givePlayerClass(Player p, String className) {
        if (className == null) className = "Minotaur";

        if (className.contains("Minotaur")) minotaur.giveKit(p);
        else if (className.contains("Paladyn")) paladyn.giveKit(p);
        else if (className.contains("Assasyn")) assasyn.giveKit(p);
        else if (className.contains("Hunter")) hunter.giveKit(p);
        else if (className.contains("Berserker")) berserker.giveKit(p);
        else minotaur.giveKit(p);

        if (isPlayerInTvT(p)) {
            // 1. Recall Item (slot index 8 = slot 9 w grze)
            ItemStack recallItem = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = recallItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b§lRECALL TO BASE §7(Right Click)");
                meta.setLore(Arrays.asList("§7Use to start teleportation", "§7to team base (5s)."));
                recallItem.setItemMeta(meta);
            }
            p.getInventory().setItem(8, recallItem);

            updatePlayerExpBar(p);

            // 2. POWIADOMIENIE TVT MANAGER, ŻE KLASA ZOSTAŁA WYBRANA
            // Odblokowuje zamykanie ekwipunku i wręcza przedmiot sklepu
            if (tvtManager != null) {
                tvtManager.onClassSelected(p);
            }
        }

        double maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        p.setHealth(maxHp);
    }

    @EventHandler
    public void onRecallItemClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getItem();
            if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
                if (item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("POWRÓT DO BAZY")) {
                    e.setCancelled(true);
                    if (!isPlayerInTvT(p)) {
                        p.sendMessage("§c§l(!) §7You can only use this during a TvT match!");
                        return;
                    }
                    if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                        p.sendMessage("§c§l(!) §7You cannot use recall while dead!");
                        return;
                    }
                    startRecall(p);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        cancelRecall(victim, null);

        this.shopManager.resetKillstreak(victim.getUniqueId());
        this.shopManager.addDeath(victim);

        e.getDrops().clear();
        e.setDroppedExp(0);

        if (this.paladyn != null) this.paladyn.removeMark(victim.getUniqueId());

        if (queueManager.playersInGame.contains(victim.getUniqueId())) {
            queueManager.endGame(victim);
            e.setDeathMessage("§b§l1V1 §8» §e" + victim.getName() + " §7has been defeated!");
            return;
        }

        if (killer != null && killer != victim) {
            this.shopManager.addKillPoint(killer);
            this.shopManager.addKillstreak(killer);
            updateHealthScoreboard(killer);
            killer.playSound(killer.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);

            // Dodaje +20% do paska XP drużyny za killa
            if (isPlayerInTvT(killer)) {
                String killerTeam = tvtNexusManager.getPlayerTeam(killer);
                if (killerTeam != null) {
                    addTeamExp(killerTeam, 0.20f);
                }
            }
        }

        // --- SYSTEM RESPAWNU W TVT ---
        if (isPlayerInTvT(victim)) {
            int delaySeconds = tvtManager.getRespawnDelaySeconds();

            Bukkit.getScheduler().runTaskLater(this, () -> {
                victim.spigot().respawn();
                startRespawnTimer(victim, delaySeconds);
            }, 1L);
        }

        sbManager.updateAll();
        tabManager.updateAll();
    }

    public void startRecall(Player p) {
        if (activeRecalls.containsKey(p.getUniqueId())) {
            p.sendMessage("§c§l(!) §7You are already recalling!");
            return;
        }

        p.sendMessage("§b§lRECALL §8» §7Returning to base has started... Don't move for §e" + RECALL_DELAY_SECONDS + "s§7!");

        // Zamiast co sekundę (20L), task uruchamia się co 2 ticki (10 razy na sekundę), żeby animacja była płynna
        BukkitTask task = new BukkitRunnable() {
            int totalTicks = RECALL_DELAY_SECONDS * 20; // Łączna liczba tików (np. 5 sekund = 100 tików)
            int currentTick = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancelRecall(p, null);
                    return;
                }

                if (currentTick < totalTicks) {
                    // Obliczanie aktualnego poziomu Y (od 0.1 bloku nad ziemią do 1.8 bloku - wysokość głowy)
                    // W miarę upływu czasu (currentTick rośnie), wysokość Y proporcjonalnie rośnie
                    double progress = (double) currentTick / totalTicks;
                    double currentY = 0.1 + (progress * 1.7); // od 0.1 do 1.8

                    Location loc = p.getLocation().clone().add(0, currentY, 0);

                    // Rysowanie okręgu z białych iskier (FIREWORKS_SPARK)
                    for (int i = 0; i < 6; i++) {
                        double angle = i * (Math.PI / 3); // 6 punktów w okręgu
                        double x = Math.cos(angle) * 0.8; // promień 0.8 bloku
                        double z = Math.sin(angle) * 0.8;

                        Location particleLoc = loc.clone().add(x, 0, z);
                        p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, particleLoc, 1, 0, 0, 0, 0);
                    }

                    // Dźwięk i tytuł odtwarzamy co pełną sekundę (co 20 tików)
                    if (currentTick % 20 == 0) {
                        int secondsLeft = RECALL_DELAY_SECONDS - (currentTick / 20);
                        if (secondsLeft > 0) {
                            p.sendTitle("§b§lRECALL...", "§7Returning in: §e" + secondsLeft + "s", 0, 25, 0);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                        }
                    }

                    currentTick += 2; // Zwiększamy licznik o 2 ticki
                } else {
                    activeRecalls.remove(p.getUniqueId());

                    Location teamSpawn = tvtNexusManager.getPlayerTeamSpawn(p);
                    if (teamSpawn != null) {
                        p.teleport(teamSpawn);
                        p.sendTitle("§a§lRETURNED!", "§7Teleported to base.", 5, 20, 5);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } else {
                        p.sendMessage("§c§lRECALL ERROR §8» §7Could not find your team's spawn!");
                    }
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L); // Uruchomienie co 2 ticki dla pełnej płynności animacji

        activeRecalls.put(p.getUniqueId(), task);
    }

    private void cancelRecall(Player p, String reasonMessage) {
        if (activeRecalls.containsKey(p.getUniqueId())) {
            activeRecalls.get(p.getUniqueId()).cancel();
            activeRecalls.remove(p.getUniqueId());
            p.sendTitle("§c§lCANCELLED", "§7Recall was interrupted!", 5, 20, 5);
            if (reasonMessage != null) {
                p.sendMessage(reasonMessage);
            }
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();

        cancelRecall(p, null);

        // Jeśli gracz wyszedł podczas oczekiwania na respawn, anulujemy jego task,
        // a mapa respawningPlayers zachowa OSTATNIĄ zapisaną wartość `timeLeft` z Runnable!
        if (respawnTasks.containsKey(p.getUniqueId())) {
            respawnTasks.get(p.getUniqueId()).cancel();
            respawnTasks.remove(p.getUniqueId());
        }

        if (isPlayerInTvT(p)) {
            savedGameStates.put(p.getUniqueId(), new PlayerGameState(p.getLocation(), p.getHealth()));
        }

        if (queueManager.playersInGame.contains(p.getUniqueId())) {
            queueManager.endGame(p);
        }
        queueManager.leaveQueue(p);
        if (tvtQueueManager != null) {
            tvtQueueManager.leaveQueue(p);
        }

        if (!isPlayerInTvT(p)) {
            selectedClass.remove(p.getUniqueId());
            savedGameStates.remove(p.getUniqueId());
        }
    }
    private void startRespawnTimer(Player victim, int seconds) {
        respawningPlayers.put(victim.getUniqueId(), seconds);
        victim.setGameMode(GameMode.SPECTATOR);

        Location teamSpawn = tvtNexusManager.getPlayerTeamSpawn(victim);
        if (teamSpawn != null) {
            victim.teleport(teamSpawn);
        }

        BukkitTask task = new BukkitRunnable() {
            int timeLeft = seconds;

            @Override
            public void run() {
                if (!victim.isOnline() || !isPlayerInTvT(victim)) {
                    cancel();
                    return;
                }

                // NA BIEŻĄCO AKTUALIZUJEMY CZAS W MAPIE W KAŻDYM TICKU/SEKUNDZIE
                respawningPlayers.put(victim.getUniqueId(), timeLeft);

                if (timeLeft > 0) {
                    victim.sendTitle("§c§lYOU DIED!", "§7Respawning in: §e" + timeLeft + "s", 0, 21, 0);
                    victim.playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    timeLeft--;
                } else {
                    respawningPlayers.remove(victim.getUniqueId());
                    respawnTasks.remove(victim.getUniqueId());

                    victim.setGameMode(GameMode.SURVIVAL);
                    victim.sendTitle("§a§lRESPAWNED!", "§7Back into the fight!", 5, 20, 5);
                    victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    Location respawnLoc = tvtNexusManager.getPlayerTeamSpawn(victim);
                    if (respawnLoc != null) {
                        victim.teleport(respawnLoc);
                    }

                    if (selectedClass.containsKey(victim.getUniqueId())) {
                        givePlayerClass(victim, selectedClass.get(victim.getUniqueId()));
                    } else {
                        givePlayerClass(victim, "Minotaur");
                    }

                    updateHealthScoreboard(victim);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        respawnTasks.put(victim.getUniqueId(), task);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAbilityDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isAbilityItem(item.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAbilityPlace(BlockPlaceEvent e) {
        if (isAbilityItem(e.getItemInHand().getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAbilityInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item != null && isAbilityItem(item.getType())) {
            if (event.getClick() == ClickType.NUMBER_KEY || event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isAbilityItem(Material type) {
        return Arrays.asList(
                Material.COMPASS, Material.WHITE_CARPET, Material.BOW, Material.ARROW,
                Material.CHAIN, Material.COBWEB, Material.RABBIT_FOOT, Material.GOLDEN_AXE,
                Material.INK_SAC, Material.SNOWBALL, Material.PHANTOM_MEMBRANE,
                Material.IRON_AXE, Material.DIAMOND_SWORD, Material.BRICK, Material.IRON_BLOCK, Material.BONE,
                Material.GOLD_NUGGET, Material.SWEET_BERRIES, Material.NETHER_STAR,
                Material.GHAST_TEAR, Material.IRON_BARS, Material.BLAZE_POWDER, Material.FIRE_CHARGE,
                Material.AMETHYST_SHARD, Material.ENCHANTED_BOOK, Material.FEATHER, Material.PRISMARINE_SHARD
        ).contains(type);
    }
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        // Sprawdzamy czy gracz jest w grze (dostosuj do swojej metody, np. isPlayerInTvT lub w grze)
        // Jeśli ma to dotyczyć tylko graczy w grze/TvT, odkomentuj odpowiedni warunek:
        // if (!isPlayerInTvT(p)) return;

        org.bukkit.inventory.ItemStack current = e.getCurrentItem();
        org.bukkit.inventory.ItemStack cursor = e.getCursor();

        // Blokada dzielenia, jeśli gracz próbuje upuścić część stacka lub przekładać go w taki sposób,
        // że ilość itemu jest większa niż 1, a typ to item umiejętności.
        if ((current != null && isAbilityItem(current.getType()) && current.getAmount() > 1) ||
                (cursor != null && isAbilityItem(cursor.getType()) && cursor.getAmount() > 1)) {

            // Pozwalamy na kliknięcia, które nie zmieniają ilości (np. zwykłe podniesienie całego stacka),
            // ale blokujemy akcje takie jak podział PPM (RIGHT), przesuwanie z shiftem (SHIFT_*) itp.,
            // które mogłyby rozdzielić stack.

            org.bukkit.event.inventory.ClickType click = e.getClick();
            if (click == org.bukkit.event.inventory.ClickType.RIGHT ||
                    click.name().contains("SHIFT") ||
                    click == org.bukkit.event.inventory.ClickType.NUMBER_KEY ||
                    click == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {

                // Jeśli item ma ilość > 1 i jest to item umiejętności, blokujemy kombinacje rozdzielające
                if ((current != null && isAbilityItem(current.getType()) && current.getAmount() > 1) ||
                        (cursor != null && isAbilityItem(cursor.getType()) && cursor.getAmount() > 1)) {
                    e.setCancelled(true);
                    p.sendMessage("§c§l(!) §7You cannot split ability items!");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        org.bukkit.inventory.ItemStack item = e.getOldCursor();
        if (item != null && isAbilityItem(item.getType()) && item.getAmount() > 1) {
            // Przeciąganie i rozdzielanie stacka na kilka slotów
            if (e.getRawSlots().size() > 1) {
                e.setCancelled(true);
                ((Player) e.getWhoClicked()).sendMessage("§c§l(!) §7You cannot split ability items!");
            }
        }
    }
// --- BLOKADA: przedmioty umiejętności nie mogą trafić do offhandu ---
// Podejście "samonaprawcze": zamiast zgadywać wszystkie możliwe akcje
// (klik, drag, swap klawiszem F, swap numerkiem 1-9, kombinacje...),
// pozwalamy eventowi się wykonać, a tick później sprawdzamy offhand gracza.
// Jeśli wylądował tam przedmiot umiejętności - usuwamy go i oddajemy do ekwipunku.

    @EventHandler
    public void onSwapHandItemsBlockAbility(PlayerSwapHandItemsEvent event) {
        // UWAGA: getOffHandItem() to przedmiot, który WYLĄDUJE w offhandzie po zamianie
        // (czyli to, co obecnie jest w głównej ręce i próbuje się tam przenieść).
        // getMainHandItem() to odwrotność - to co wyląduje w głównej ręce (czyli
        // obecna zawartość offhandu). Sprawdzanie tego drugiego blokowało niewłaściwy
        // kierunek zamiany.
        ItemStack itemGoingToOffhand = event.getOffHandItem();
        if (itemGoingToOffhand != null && isAbilityItem(itemGoingToOffhand.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClickCheckOffhand(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        Bukkit.getScheduler().runTask(this, () -> fixOffhandIfAbilityItem(p));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDragCheckOffhand(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        Bukkit.getScheduler().runTask(this, () -> fixOffhandIfAbilityItem(p));
    }

    private void fixOffhandIfAbilityItem(Player p) {
        if (!p.isOnline()) return;
        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (offhand != null && isAbilityItem(offhand.getType())) {
            p.getInventory().setItemInOffHand(null);
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(offhand);
            for (ItemStack left : leftover.values()) {
                p.getWorld().dropItem(p.getLocation(), left);
            }
            p.sendMessage("§c§l(!) §7Nie możesz trzymać przedmiotu umiejętności w offhandzie!");
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // --- OBSŁUGA KONSOLI DLA RELOAD ---
        if (!(sender instanceof Player)) {
            if (command.getName().equalsIgnoreCase("test_assa") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                performReload(sender);
                return true;
            }
            sender.sendMessage("§cThis command (except /test_assa reload) is only available to players.");
            return true;
        }

        Player p = (Player) sender;

        // --- KOMENDA GŁÓWNA /test_assa ---
        if (command.getName().equalsIgnoreCase("test_assa")) {
            if (!p.isOp()) {
                p.sendMessage("§c§l(!) §7No permission!");
                return true;
            }

            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    performReload(p);
                    return true;
                }

                if (args[0].equalsIgnoreCase("tvt")) {
                    if (args.length >= 2 && (args[1].equalsIgnoreCase("end") || args[1].equalsIgnoreCase("forceend"))) {
                        if (tvtNexusManager != null) {
                            tvtNexusManager.forceEndGame();
                            p.sendMessage("§a§lTvT §8» §7The TvT match was successfully reset and ended!");
                        } else {
                            p.sendMessage("§c§l(!) §7TvTNexusManager is unavailable.");
                        }
                        return true;
                    }

                    if (args.length >= 3 && args[1].equalsIgnoreCase("setsize")) {
                        try {
                            int size = Integer.parseInt(args[2]);
                            if (size < 1) {
                                p.sendMessage("§c Team size must be at least 1!");
                                return true;
                            }
                            tvtManager.setTeamSize(size);
                            p.sendMessage("§a§lTvT §8» §7Team size changed to: §e" + size + "v" + size);
                        } catch (NumberFormatException e) {
                            p.sendMessage("§c Please enter a valid number!");
                        }
                        return true;
                    }
                }
            }

            p.sendMessage("§e§l--- TEST_ASSA HELP ---");
            p.sendMessage("§e/test_assa reload §7- Reloads the plugin's configuration and states");
            p.sendMessage("§e/test_assa tvt setsize <amount> §7- Sets the TvT team size");
            p.sendMessage("§e/test_assa tvt end §7- Forces the TvT match to end");
            return true;
        }

        // --- POZOSTAŁE KOMENDY ---
        if (command.getName().equalsIgnoreCase("spawnzombiehorse")) {
            if (!p.isOp()) {
                p.sendMessage("§c§l(!) §7No permission!");
                return true;
            }
            jungleManager.spawnLlama(p.getLocation());
            p.sendMessage("§a§lJUNGLE §8» §7Summoned a §2Zombie Horse §7at your position!");
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_HORSE_AMBIENT, 1.0f, 1.0f);
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawnbank")) {
            if (!p.isOp()) {
                p.sendMessage("§c§l(!) §7No permission!");
                return true;
            }
            jungleManager.spawnBankPig(p.getLocation());
            p.sendMessage("§a§lJUNGLE §8» §7Summoned a §aBank §7at your position!");
            p.playSound(p.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1.0f, 1.0f);
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawngiant")) {
            if (!p.isOp()) {
                p.sendMessage("§c§l(!) §7No permission!");
                return true;
            }
            jungleManager.spawnGiant(p.getLocation());
            p.sendMessage("§a§lJUNGLE §8» §7Summoned a §4Giant §7at your position!");
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.5f);
            return true;
        }

        if (command.getName().equalsIgnoreCase("tvt")) {
            if (!p.isOp()) {
                p.sendMessage("§c§l(!) §7No permission!");
                return true;
            }

            if (args.length >= 1 && (args[0].equalsIgnoreCase("end") || args[0].equalsIgnoreCase("forceend"))) {
                if (tvtNexusManager != null) {
                    tvtNexusManager.forceEndGame();
                    p.sendMessage("§a§lTvT §8» §7The TvT match was successfully reset and ended!");
                } else {
                    p.sendMessage("§c§l(!) §7TvTNexusManager is unavailable.");
                }
                return true;
            }

            if (args.length >= 2 && args[0].equalsIgnoreCase("setsize")) {
                try {
                    int size = Integer.parseInt(args[1]);
                    if (size < 1) {
                        p.sendMessage("§c Team size must be at least 1!");
                        return true;
                    }
                    tvtManager.setTeamSize(size);
                    p.sendMessage("§a§lTvT §8» §7Team size changed to: §e" + size + "v" + size);
                } catch (NumberFormatException e) {
                    p.sendMessage("§c Please enter a valid number!");
                }
                return true;
            }

            p.sendMessage("§c Usage: /tvt setsize <amount> or /tvt end");
            return true;
        }

        if (command.getName().equalsIgnoreCase("recall")) {
            if (!isPlayerInTvT(p)) {
                p.sendMessage("§c§l(!) §7The recall command is only available during a TvT match!");
                return true;
            }
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                p.sendMessage("§c§l(!) §7You cannot use recall while dead!");
                return true;
            }
            startRecall(p);
            return true;
        }

        if (command.getName().equalsIgnoreCase("leave")) {
            if (tvtQueueManager.isInQueue(p)) {
                tvtQueueManager.leaveQueue(p);
                return true;
            }

            // Dodana obsługa widza: wyjście ze spectate do lobby + tryb SURVIVAL
            if (tvtQueueManager.isSpectator(p)) {
                tvtQueueManager.removeSpectator(p); // usuwa go z listy widzów
                p.setGameMode(GameMode.SURVIVAL);
                giveLobbyItems(p);
                teleportToMainSpawn(p);
                sbManager.createScoreboard(p);
                tabManager.updateTab(p);
                p.sendMessage("§e§l(!) §7You have left spectator mode.");
                return true;
            }

            // Blokada dla graczy biernie biorących udział w aktywnym meczu TvT
            if (tvtNexusManager != null && tvtNexusManager.isGameActive() && tvtNexusManager.isPlayerInTvT(p)) {
                p.sendMessage("§c§l(!) §7You cannot leave the game while it's in progress!");
                return true;
            }

            if (isPlayerInTvT(p) || queueManager.playersInGame.contains(p.getUniqueId())) {
                p.sendMessage("§c§l(!) §7You cannot leave the game while it's in progress!");
                return true;
            }

            if (!selectedClass.containsKey(p.getUniqueId())) {
                p.sendMessage("§c§l(!) §7You are not in any mode!");
                return true;
            }

            p.sendMessage("§e§l(!) §7Leaving the mode...");
            giveLobbyItems(p);
            teleportToMainSpawn(p);
            sbManager.createScoreboard(p);
            tabManager.updateTab(p);
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawn")) {
            if (isPlayerInTvT(p) || queueManager.playersInGame.contains(p.getUniqueId())) {
                p.sendMessage("§c§l(!) §7You cannot teleport to spawn during the game!");
                return true;
            }

            if (tvtQueueManager.isInQueue(p)) {
                tvtQueueManager.leaveQueue(p);
            }
            giveLobbyItems(p);
            teleportToMainSpawn(p);
            p.sendMessage("§e§l(!) §7Teleported to spawn!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("kit")) {
            if (args.length == 0) {
                openClassMenu(p);
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                return true;
            }

            String kitName = args[0].toLowerCase();
            switch (kitName) {
                case "minotaur": givePlayerClass(p, "Minotaur"); selectedClass.put(p.getUniqueId(), "§6Minotaur"); break;
                case "paladyn": givePlayerClass(p, "Paladyn"); selectedClass.put(p.getUniqueId(), "§ePaladyn"); break;
                case "assasyn": givePlayerClass(p, "Assasyn"); selectedClass.put(p.getUniqueId(), "§8Assasyn"); break;
                case "hunter": givePlayerClass(p, "Hunter"); selectedClass.put(p.getUniqueId(), "§2Hunter"); break;
                case "berserker": givePlayerClass(p, "Berserker"); selectedClass.put(p.getUniqueId(), "§4Berserker"); break;
                case "mag": givePlayerClass(p, "Mag"); selectedClass.put(p.getUniqueId(), "§5Mag"); break;
                default: p.sendMessage("§c§l(!) §7Class not found."); return true;
            }

            p.sendMessage("§a§lCLASS §8» §fSelected: " + selectedClass.get(p.getUniqueId()));

            sbManager.updateAll();
            tabManager.updateAll();
            return true;
        }

        if (command.getName().equalsIgnoreCase("statsadmin")) {
            if (!p.isOp()) return true;
            if (args.length < 3) return true;
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) return true;
            try {
                int amount = Integer.parseInt(args[2]);
                String stat = args[1].toLowerCase();
                if (stat.equals("points")) shopManager.setPoints(target.getUniqueId(), amount);
                else if (stat.equals("kills")) shopManager.setKills(target.getUniqueId(), amount);
                else if (stat.equals("deaths")) shopManager.setDeaths(target.getUniqueId(), amount);
                sbManager.updateAll();
                p.sendMessage("§a§lSUCCESS!");
            } catch (Exception e) {}
            return true;
        }
        if (command.getName().equalsIgnoreCase("ff") || command.getName().equalsIgnoreCase("forfeit") || command.getName().equalsIgnoreCase("surrender")) {
            if (sender instanceof Player) {
                if (tvtNexusManager != null) {
                    tvtNexusManager.handleSurrenderCommand((Player) sender);
                }
            }
            return true;
        }

        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kit") && args.length == 1) {
            return Arrays.asList("minotaur", "paladyn", "assasyn", "hunter", "berserker", "mag").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (command.getName().equalsIgnoreCase("tvt") && args.length == 1) {
            return Arrays.asList("setsize", "end");
        }
        return null;
    }

    public String getPlayerClass(UUID uuid) { return selectedClass.getOrDefault(uuid, "§7None"); }
    public ShopManager getShopManager() { return this.shopManager; }
    public Berserker getBerserker() { return this.berserker; }

    private void performReload(CommandSender sender) {
        // 1. Zatrzymanie aktywnego meczu i czyszczenie stanu gry TvT
        if (tvtNexusManager != null) {
            tvtNexusManager.forceEndGame();
        }

        // 3. Reset ulepszeń/statystyk ze sklepu TvT u graczy
        if (tvtShopManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                tvtShopManager.clearEmeralds(p);
            }
        }

        // 4. Przeładowanie pliku config.yml
        reloadConfig();

        sender.sendMessage("§a§lTEST_ASSA §8» §7Configuration and game states have been successfully reloaded!");
        if (sender instanceof Player) {
            Player p = (Player) sender;
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }
}