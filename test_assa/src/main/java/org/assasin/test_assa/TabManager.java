package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class TabManager {

    private final Main plugin;
    private final Scoreboard scoreboard;
    private final Team blueTeam;
    private final Team redTeam;
    private final Team lobbyTeam;

    public TabManager(Main plugin) {
        this.plugin = plugin;

        // Pobieramy główny Scoreboard serwera
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Rejestrujemy drużyny ze sztywną kolejnością sortowania (01_ -> 02_ -> 03_)
        this.blueTeam = getOrCreateTeam("01_BLUE", "§9");
        this.redTeam = getOrCreateTeam("02_RED", "§c");
        this.lobbyTeam = getOrCreateTeam("03_LOBBY", "§f");
    }

    private Team getOrCreateTeam(String teamName, String colorPrefix) {
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.setPrefix(colorPrefix);
        return team;
    }

    public void updateTab(Player p) {
        if (p == null || !p.isOnline()) return;

        if (plugin.isPlayerInTvT(p)) {
            updateTvTTab(p);
        } else {
            updateLobbyTab(p);
        }
    }

    private void updateTvTTab(Player p) {
        String klasa = plugin.getPlayerClass(p.getUniqueId());

        int myNexusHp = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getTeamNexusHp(p) : 0;
        int enemyNexusHp = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getEnemyNexusHp(p) : 0;

        int matchKills = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getMatchKills(p) : 0;
        int matchDeaths = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getMatchDeaths(p) : 0;

        int emeralds = plugin.tvtShopManager != null ? plugin.tvtShopManager.getEmeralds(p) : 0;

        String formattedGameTime = getFormattedGameTime();

        String teamColor = "§a";
        String teamName = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getPlayerTeam(p) : null;

        // Przypisanie do Scoreboard Team gwarantuje 100% brak skakania na liście TAB
        if ("BLUE".equalsIgnoreCase(teamName)) {
            teamColor = "§9";
            if (!blueTeam.hasEntry(p.getName())) blueTeam.addEntry(p.getName());
        } else if ("RED".equalsIgnoreCase(teamName)) {
            teamColor = "§c";
            if (!redTeam.hasEntry(p.getName())) redTeam.addEntry(p.getName());
        } else {
            if (!lobbyTeam.hasEntry(p.getName())) lobbyTeam.addEntry(p.getName());
        }

        int cs = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getMatchCS(p) : 0;

        // Ustawienie wyświetlanej nazwy na liście TAB
        String tabName = teamColor + p.getName() + " §7- §e" + cs + " CS";
        if (!tabName.equals(p.getPlayerListName())) {
            p.setPlayerListName(tabName);
        }

        String targetDisplayName = teamColor + p.getName() + "§r";
        if (!targetDisplayName.equals(p.getDisplayName())) {
            p.setDisplayName(targetDisplayName);
        }

        String header = "\n " +
                "§c§l» §e§lTvT MATCH - IN PROGRESS §c§l«\n" +
                "§7--------------------------------------------\n" +
                " §fName: " + teamColor + p.getName() + "    §8|    §fClass: §d" + klasa + "\n" +
                " §fGame Time: §e" + formattedGameTime + "\n" +
                " \n" +
                " §aYour Nexus: §2" + myNexusHp + " HP      §cEnemy Nexus: §4" + enemyNexusHp + " HP\n" +
                " §fKills (Match): §a" + matchKills + "      §fDeaths (Match): §c" + matchDeaths + "\n" +
                " §fEmeralds (TvT): §a" + emeralds + " ❇\n" +
                "§7--------------------------------------------\n";

        String footer = "\n" +
                "§eDestroy the enemy Nexus to win the match!\n" +
                "§7Use §9/recall §7or the Sunflower to open the shop.\n" +
                "§7--------------------------------------------\n" +
                "§e§lYOURSERVER.COM\n" +
                " ";

        p.setPlayerListHeaderFooter(header, footer);
    }

    private String getFormattedGameTime() {
        if (plugin.tvtManager == null || plugin.tvtManager.getGameStartTime() == 0) return "00:00";

        long totalSeconds = (System.currentTimeMillis() - plugin.tvtManager.getGameStartTime()) / 1000;
        if (totalSeconds < 0) totalSeconds = 0;

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }

    private void updateLobbyTab(Player p) {
        if (!lobbyTeam.hasEntry(p.getName())) {
            lobbyTeam.addEntry(p.getName());
        }

        String targetTabName = "§f" + p.getName();
        if (!targetTabName.equals(p.getPlayerListName())) {
            p.setPlayerListName(targetTabName);
            p.setDisplayName("§f" + p.getName() + "§r");
        }

        String klasa = plugin.getPlayerClass(p.getUniqueId());
        int kills = plugin.getShopManager() != null ? plugin.getShopManager().getTotalKills(p.getUniqueId()) : 0;
        int deaths = plugin.getShopManager() != null ? plugin.getShopManager().getDeaths(p.getUniqueId()) : 0;
        int killstreak = plugin.getShopManager() != null ? plugin.getShopManager().getKillstreak(p.getUniqueId()) : 0;
        int wallet = plugin.getShopManager() != null ? plugin.getShopManager().getPoints(p.getUniqueId()) : 0;

        String header = "\n " +
                "§6§l» §e§lYOUR STATISTICS §6§l«\n" +
                "§7--------------------------------------------\n" +
                " §fName: §f" + p.getName() + "    §8|    §fClass: §d" + klasa + "\n" +
                " \n" +
                " §fKills: §a" + kills + "      §fDeaths: §c" + deaths + "\n" +
                " §fKillstreak (KS): §e" + killstreak + " 🔥     §fBalance: §6" + wallet + "⛁\n" +
                "§7--------------------------------------------\n";

        String footer = "\n" +
                "§7Online players: §a" + Bukkit.getOnlinePlayers().size() + " §8/ §2100\n" +
                "§e§lYOURSERVER.COM\n" +
                " ";

        p.setPlayerListHeaderFooter(header, footer);
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTab(p);
        }
    }
}