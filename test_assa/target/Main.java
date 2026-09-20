package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
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

    private final Map<UUID, String> selectedClass = new HashMap<>();

    // Rejestr serwera: UUID gracza -> Dokładny czas Unix (w ms), kiedy gracz ma wstać
    private final Map<UUID, Long> globalRespawnTimestamps = new HashMap<>();

    // SYSTEM RECALLU (TYLKO DLA TvT)
    private final Map<UUID, BukkitTask> activeRecalls = new HashMap<>();
    private final int RECALL_DELAY_SECONDS = 5;

    // Klasa oraz mapa do przechowywania stanu gracza podczas wyjścia z gry
    private static class PlayerGameState {
        final Location location;
        final double health;

        PlayerGameState(Location location, double health) {
            this.location = location;
            this.health = health;
        }
    }

    private final Map<UUID, PlayerGameState> savedGameStates = new HashMap<>();

    public void openClassMenu(Player p) {
        if (menuManager != null) {
            menuManager.openClassMenu(p);
        }
    }

    public void giveLobbyItems(Player p) {
        p.getInventory().clear();
        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        p.setHealth(20.0);
        p.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(4.0);

        selectedClass.remove(p.getUniqueId());

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lMENU TRYBÓW §7(Prawy klik)");
            meta.setLore(Arrays.asList("§7Kliknij, aby otworzyć wybór trybu!"));
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

        getServer().getPluginManager().registerEvents(tvtNexusManager, this);
        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(this.queueManager, this);
        getServer().getPluginManager().registerEvents(tvtQueueManager, this);

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
        getCommand("recall").setExecutor(this);

        if (getCommand("tvt") != null) {
            getCommand("tvt").setExecutor(this);
            getCommand("tvt").setTabCompleter(this);
        }

        startGlobalServerTimer();

        getLogger().info("Plugin test_assa - Zaladowano pomyslnie!");
    }

    private void startGlobalServerTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                Iterator<Map.Entry<UUID, Long>> iterator = globalRespawnTimestamps.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    UUID uuid = entry.getKey();
                    long respawnTime = entry.getValue();

                    Player p = Bukkit.getPlayer(uuid);

                    if (p == null || !isPlayerInTvT(p)) {
                        if (p != null && !isPlayerInTvT(p)) {
                            iterator.remove();
                        }
                        continue;
                    }

                    long remainingMs = respawnTime - now;

                    if (remainingMs <= 0) {
                        iterator.remove();
                        respawnTvTPlayer(p);
                    } else {
                        int remainingSeconds = (int) Math.ceil(remainingMs / 1000.0);
                        p.sendTitle("§c§lZGINĄŁEŚ!", "§7Odrodzenie za: §e" + remainingSeconds + "s", 0, 21, 0);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void updateHealthScoreboard(Player p) {
        if (p == null || !p.isOnline()) return;
        int hearts = (int) (p.getHealth() / 2);
        String hpFormatted = String.format("%.1f", p.getHealth() / 2.0);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            Objective obj = board.getObjective("hp_name");
            if (obj != null) obj.getScore(p.getName()).setScore(hearts);

            Team team = board.getTeam(p.getName());
            if (team == null) {
                team = board.registerNewTeam(p.getName());
                team.addEntry(p.getName());
            }
            team.setSuffix(" §c[" + hpFormatted + "❤]");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        sbManager.createScoreboard(p);
        tabManager.updateTab(p);

        if (isPlayerInTvT(p)) {
            p.sendMessage("§e§l(!) §7Wykryto aktywną grę TvT! Przywracanie do meczu...");

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (globalRespawnTimestamps.containsKey(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                    if (p.isDead()) {
                        p.spigot().respawn();
                    }

                    long now = System.currentTimeMillis();
                    long respawnTime = globalRespawnTimestamps.getOrDefault(p.getUniqueId(), 0L);

                    if (respawnTime <= now) {
                        globalRespawnTimestamps.remove(p.getUniqueId());
                        respawnTvTPlayer(p);
                    } else {
                        p.setGameMode(GameMode.SPECTATOR);
                        Location teamSpawn = tvtNexusManager.getPlayerTeamSpawn(p);
                        if (teamSpawn != null) p.teleport(teamSpawn);
                    }
                    return;
                }

                if (selectedClass.containsKey(p.getUniqueId())) {
                    givePlayerClass(p, selectedClass.get(p.getUniqueId()));
                } else {
                    minotaur.giveKit(p);
                }

                PlayerGameState state = savedGameStates.remove(p.getUniqueId());
                if (state != null && state.health > 0.0) {
                    p.teleport(state.location);
                    double maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    p.setHealth(Math.min(state.health, maxHp));
                } else {
                    Location teamSpawn = this.tvtNexusManager.getPlayerTeamSpawn(p);
                    if (teamSpawn != null) p.teleport(teamSpawn);
                }
                updateHealthScoreboard(p);
            }, 2L);
            return;
        }

        giveLobbyItems(p);
        teleportToMainSpawn(p);
        Bukkit.getScheduler().runTaskLater(this, () -> updateHealthScoreboard(p), 1L);
    }

    @EventHandler public void onRegen(EntityRegainHealthEvent e) { if (e.getEntity() instanceof Player) updateHealthScoreboard((Player) e.getEntity()); }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            updateHealthScoreboard(p);
            cancelRecall(p, "§c§lRECALL §8» §7Anulowano! Otrzymano obrażenia.");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (activeRecalls.containsKey(p.getUniqueId())) {
            if (e.getFrom().getBlockX() != e.getTo().getBlockX() ||
                    e.getFrom().getBlockY() != e.getTo().getBlockY() ||
                    e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
                cancelRecall(p, "§c§lRECALL §8» §7Anulowano! Poruszyłeś się.");
            }
        }
    }

    public boolean isPlayerInTvT(Player p) {
        return tvtNexusManager != null && tvtNexusManager.isPlayerInTvT(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        boolean isInGame = isPlayerInTvT(p) || (queueManager != null && queueManager.playersInGame.contains(p.getUniqueId()));

        if (isInGame) {
            if (p.isOp()) {
                return;
            }

            String message = event.getMessage().toLowerCase().trim();

            if (!message.startsWith("/msg ") && !message.equals("/msg") &&
                    !message.startsWith("/tell ") && !message.equals("/tell") &&
                    !message.startsWith("/w ") && !message.equals("/w") &&
                    !message.startsWith("/recall") && !message.equals("/recall")) {

                event.setCancelled(true);
                p.sendMessage("§c§l(!) §7Nie możesz używać komend w trakcie gry! Dozwolone są tylko komendy §e/msg §7oraz §e/recall§7.");
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

    private void givePlayerClass(Player p, String className) {
        if (className.contains("Minotaur")) minotaur.giveKit(p);
        else if (className.contains("Paladyn")) paladyn.giveKit(p);
        else if (className.contains("Assasyn")) assasyn.giveKit(p);
        else if (className.contains("Hunter")) hunter.giveKit(p);
        else if (className.contains("Berserker")) berserker.giveKit(p);

        double maxHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        p.setHealth(maxHp);
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
            e.setDeathMessage("§b§l1V1 §8» §e" + victim.getName() + " §7został pokonany!");
            return;
        }

        if (killer != null && killer != victim) {
            this.shopManager.addKillPoint(killer);
            this.shopManager.addKillstreak(killer);
            double maxHealth = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            killer.setHealth(maxHealth);
            updateHealthScoreboard(killer);
            killer.sendMessage("§d§l❤ Odrodzenie!");
            killer.playSound(killer.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
        }

        if (isPlayerInTvT(victim)) {
            int delaySeconds = tvtManager.getRespawnDelaySeconds();
            long respawnTimeMillis = System.currentTimeMillis() + (delaySeconds * 1000L);

            globalRespawnTimestamps.put(victim.getUniqueId(), respawnTimeMillis);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!victim.isOnline()) return;

                victim.spigot().respawn();
                victim.setGameMode(GameMode.SPECTATOR);

                Location teamSpawn = tvtNexusManager.getPlayerTeamSpawn(victim);
                if (teamSpawn != null) {
                    victim.teleport(teamSpawn);
                }
            }, 1L);
        }

        sbManager.updateAll();
        tabManager.updateAll();
    }

    private void respawnTvTPlayer(Player player) {
        globalRespawnTimestamps.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.sendTitle("§a§lODRODZENIE!", "§7Wracasz do walki!", 5, 20, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        Location respawnLoc = tvtNexusManager.getPlayerTeamSpawn(player);
        if (respawnLoc != null) {
            player.teleport(respawnLoc);
        }

        if (selectedClass.containsKey(player.getUniqueId())) {
            givePlayerClass(player, selectedClass.get(player.getUniqueId()));
        } else {
            minotaur.giveKit(player);
        }

        updateHealthScoreboard(player);
    }

    private void startRecall(Player p) {
        if (activeRecalls.containsKey(p.getUniqueId())) {
            p.sendMessage("§c§l(!) §7Już jesteś w trakcie recallu!");
            return;
        }

        p.sendMessage("§b§lRECALL §8» §7Rozpoczęto powrót do bazy... Nie ruszaj się przez §e" + RECALL_DELAY_SECONDS + "s§7!");

        BukkitTask task = new BukkitRunnable() {
            int time = RECALL_DELAY_SECONDS;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancelRecall(p, null);
                    return;
                }

                if (time > 0) {
                    p.sendTitle("§b§lRECALL...", "§7Powrót za: §e" + time + "s", 0, 21, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                    time--;
                } else {
                    activeRecalls.remove(p.getUniqueId());
                    p.sendTitle("§a§lPOWRÓT!", "§7Przeniesiono do bazy.", 5, 20, 5);

                    Location teamSpawn = tvtNexusManager.getPlayerTeamSpawn(p);
                    if (teamSpawn != null) {
                        p.teleport(teamSpawn);
                    }

                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        activeRecalls.put(p.getUniqueId(), task);
    }

    private void cancelRecall(Player p, String reasonMessage) {
        if (activeRecalls.containsKey(p.getUniqueId())) {
            activeRecalls.get(p.getUniqueId()).cancel();
            activeRecalls.remove(p.getUniqueId());
            p.sendTitle("§c§lANULOWANO", "§7Recall został przerwany!", 5, 20, 5);
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

        if (isPlayerInTvT(p)) {
            if (p.getGameMode() != GameMode.SPECTATOR && !p.isDead()) {
                savedGameStates.put(p.getUniqueId(), new PlayerGameState(p.getLocation(), p.getHealth()));
            } else {
                savedGameStates.remove(p.getUniqueId());
            }
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
            globalRespawnTimestamps.remove(p.getUniqueId());
        }
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
        if (item != null && isAbilityItem(event.getCurrentItem().getType())) {
            if (event.getClick() == ClickType.NUMBER_KEY || event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isAbilityItem(Material type) {
        return Arrays.asList(
                Material.COMPASS, Material.WHITE_CARPET, Material.BOW, Material.ARROW,
                Material.CHAIN, Material.COBWEB, Material.RABBIT_FOOT, Material.GOLDEN_AXE,
                Material.INK_SAC, Material.SNOWBALL, Material.GOLDEN_SHOVEL,
                Material.IRON_AXE, Material.DIAMOND_SWORD, Material.BRICK, Material.GOAT_HORN, Material.BONE,
                Material.GOLD_NUGGET, Material.GOLDEN_HOE, Material.NETHER_STAR,
                Material.GHAST_TEAR, Material.IRON_BARS
        ).contains(type);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (command.getName().equalsIgnoreCase("recall")) {
            if (!isPlayerInTvT(p)) {
                p.sendMessage("§c§l(!) §7Komenda recall jest dostępna tylko podczas meczu TvT!");
                return true;
            }
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                p.sendMessage("§c§l(!) §7Nie możesz użyć recallu będąc martwym!");
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

            if (isPlayerInTvT(p) || queueManager.playersInGame.contains(p.getUniqueId())) {
                p.sendMessage("§c§l(!) §7Nie możesz opuścić gry podczas jej trwania!");
                return true;
            }

            if (!selectedClass.containsKey(p.getUniqueId())) {
                p.sendMessage("§c§l(!) §7Nie jesteś w żadnym trybie!");
                return true;
            }

            p.sendMessage("§e§l(!) §7Opuszczasz tryb...");
            giveLobbyItems(p);
            teleportToMainSpawn(p);
            sbManager.createScoreboard(p);
            tabManager.updateTab(p);
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawn")) {
            if (isPlayerInTvT(p) || queueManager.playersInGame.contains(p.getUniqueId())) {
                p.sendMessage("§c§l(!) §7Nie możesz teleportować się na spawn podczas gry!");
                return true;
            }

            if (tvtQueueManager.isInQueue(p)) {
                tvtQueueManager.leaveQueue(p);
            }
            giveLobbyItems(p);
            teleportToMainSpawn(p);
            p.sendMessage("§e§l(!) §7Teleportowano na spawn!");
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
                case "minotaur": minotaur.giveKit(p); selectedClass.put(p.getUniqueId(), "§6Minotaur"); break;
                case "paladyn": paladyn.giveKit(p); selectedClass.put(p.getUniqueId(), "§ePaladyn"); break;
                case "assasyn": assasyn.giveKit(p); selectedClass.put(p.getUniqueId(), "§8Assasyn"); break;
                case "hunter": hunter.giveKit(p); selectedClass.put(p.getUniqueId(), "§2Hunter"); break;
                case "berserker": berserker.giveKit(p); selectedClass.put(p.getUniqueId(), "§4Berserker"); break;
                default: p.sendMessage("§c§l(!) §7Nie znaleziono klasy."); return true;
            }

            p.sendMessage("§a§lKLASA §8» §fWybrano: " + selectedClass.get(p.getUniqueId()));

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
                p.sendMessage("§a§lSUKCES!");
            } catch (Exception e) {}
            return true;
        }

        if (command.getName().equalsIgnoreCase("tvt")) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("setsize")) {
                if (!p.isOp()) {
                    p.sendMessage("§c§l(!) §7Brak uprawnień!");
                    return true;
                }
                try {
                    int size = Integer.parseInt(args[1]);
                    if (size < 1) {
                        p.sendMessage("§c Rozmiar drużyny musi wynosić min. 1!");
                        return true;
                    }
                    tvtManager.setTeamSize(size);
                    p.sendMessage("§a§lTvT §8» §7Zmieniono rozmiar drużyn na: §e" + size + "v" + size);
                } catch (NumberFormatException e) {
                    p.sendMessage("§c Podaj poprawną liczbę!");
                }
                return true;
            }
            p.sendMessage("§c Użycie: /tvt setsize <ilość_osób_w_drużynie>");
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kit") && args.length == 1) {
            return Arrays.asList("minotaur", "paladyn", "assasyn", "hunter", "berserker").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (command.getName().equalsIgnoreCase("tvt") && args.length == 1) {
            return Collections.singletonList("setsize");
        }
        return null;
    }

    public String getPlayerClass(UUID uuid) { return selectedClass.getOrDefault(uuid, "§7Brak"); }
    public ShopManager getShopManager() { return this.shopManager; }
    public Paladyn getPaladyn() { return this.paladyn; }
    public Berserker getBerserker() { return this.berserker; }
}