package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class ScoreboardManager {

    private final Main plugin;

    public ScoreboardManager(Main plugin) {
        this.plugin = plugin;
    }

    public void createScoreboard(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        // --- OBSŁUGA DRUŻYN ORAZ WYŚWIETLANIA HP NAD GŁOWAMI/W SUFIKSIE ---
        setupTeamsAndHealth(board);

        // SPRAWDZENIE CZY GRACZ JEST W TvT
        if (plugin.isPlayerInTvT(p)) {
            createTvTScoreboard(p, board);
        } else {
            createLobbyScoreboard(p, board);
        }

        p.setScoreboard(board);
    }

    private void setupTeamsAndHealth(Scoreboard board) {
        // Tworzenie/Pobieranie drużyn wewnątrz osobnego Scoreboarda
        Team blueTeam = board.getTeam("TvT_BLUE");
        if (blueTeam == null) {
            blueTeam = board.registerNewTeam("TvT_BLUE");
            blueTeam.setColor(ChatColor.BLUE);
            blueTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            blueTeam.setAllowFriendlyFire(false);
        }

        Team redTeam = board.getTeam("TvT_RED");
        if (redTeam == null) {
            redTeam = board.registerNewTeam("TvT_RED");
            redTeam.setColor(ChatColor.RED);
            redTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            redTeam.setAllowFriendlyFire(false);
        }

        // Rejestrowanie graczy online, ich drużyn oraz sufiksów z HP
        for (Player online : Bukkit.getOnlinePlayers()) {
            String teamName = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getPlayerTeam(online) : null;

            double hearts = online.getHealth() / 2.0;
            String hpFormatted = String.format("%.1f", hearts);
            String suffixHp = " §c[" + hpFormatted + "❤]";

            if ("BLUE".equalsIgnoreCase(teamName)) {
                blueTeam.addEntry(online.getName());
                // Przypisanie sufiksu HP dla drużyny BLUE
                blueTeam.setSuffix(suffixHp);
            } else if ("RED".equalsIgnoreCase(teamName)) {
                redTeam.addEntry(online.getName());
                // Przypisanie sufiksu HP dla drużyny RED
                redTeam.setSuffix(suffixHp);
            } else {
                // Gracze poza TvT (Lobby) -> Osobne drużyny pod sufiks z HP bez zmiany koloru
                Team defaultTeam = board.getTeam(online.getName());
                if (defaultTeam == null) {
                    defaultTeam = board.registerNewTeam(online.getName());
                }
                defaultTeam.addEntry(online.getName());
                defaultTeam.setSuffix(suffixHp);
            }
        }
    }

    private void createTvTScoreboard(Player p, Scoreboard board) {
        Objective obj = board.registerNewObjective("tvt_stats", "dummy", "§c§lTvT MODE §8» §eNEXUS");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String klasa = plugin.getPlayerClass(p.getUniqueId());

        int myNexusHp = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getTeamNexusHp(p) : 0;
        int enemyNexusHp = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getEnemyNexusHp(p) : 0;

        // FETCH MATCH STATISTICS
        int kills = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getMatchKills(p) : 0;
        int deaths = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getMatchDeaths(p) : 0;
        int cs = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getMatchCS(p) : 0; // NOWE

        // FETCH EMERALDS FROM TVTSHOPMANAGER
        int emeralds = plugin.tvtShopManager != null ? plugin.tvtShopManager.getEmeralds(p) : 0;

        addScore(obj, "§7-------------------", 12);
        addScore(obj, "§aYour Nexus: §2" + myNexusHp + " HP", 11);
        addScore(obj, "§cEnemy Nexus: §4" + enemyNexusHp + " HP", 10);
        addScore(obj, "§7 ", 9);
        addScore(obj, "§fClass: " + klasa, 8);
        addScore(obj, "§7  ", 7);
        addScore(obj, "§fKills (Match): §a" + kills, 6);
        addScore(obj, "§fDeaths (Match): §c" + deaths, 5);
        addScore(obj, "§fCS: §e" + cs, 4); // NOWE
        addScore(obj, "§fEmeralds: §a" + emeralds + " ❇", 3);
        addScore(obj, "§7------------------- ", 2);
        addScore(obj, "§eYourServer.com", 1);
    }

    private void createLobbyScoreboard(Player p, Scoreboard board) {
        Objective obj = board.registerNewObjective("stats", "dummy", "§6§lSTATISTICS");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // --- Fetch data from ShopManager (FFA / Lobby) ---
        int kills = plugin.getShopManager() != null ? plugin.getShopManager().getTotalKills(p.getUniqueId()) : 0;
        int deaths = plugin.getShopManager() != null ? plugin.getShopManager().getDeaths(p.getUniqueId()) : 0;
        int wallet = plugin.getShopManager() != null ? plugin.getShopManager().getPoints(p.getUniqueId()) : 0;
        int killstreak = plugin.getShopManager() != null ? plugin.getShopManager().getKillstreak(p.getUniqueId()) : 0;

        double kd = (deaths == 0) ? kills : (double) kills / deaths;
        String kdFormatted = String.format("%.2f", kd);

        String klasa = plugin.getPlayerClass(p.getUniqueId());

        // --- Build list ---
        addScore(obj, "§7---", 12);
        addScore(obj, "§fKills: §a" + kills, 11);
        addScore(obj, "§fDeaths: §c" + deaths, 10);
        addScore(obj, "§fK/D: §e" + kdFormatted, 9);
        addScore(obj, "§fKillstreak: §d" + killstreak + " 🔥", 8);
        addScore(obj, "§7 ", 7);
        addScore(obj, "§fClass: " + klasa, 6);
        addScore(obj, "§7  ", 5);
        addScore(obj, "§fBalance: §6" + wallet + "⛁", 4);
        addScore(obj, "§7--- ", 3);
        addScore(obj, "§eYourServer.com", 2);
    }

    private void addScore(Objective obj, String text, int score) {
        Score s = obj.getScore(text);
        s.setScore(score);
    }

    public void updateScoreboard(Player p) {
        if (p != null && p.isOnline()) {
            createScoreboard(p);
        }
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            createScoreboard(p);
        }
    }
}