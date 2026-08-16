package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class ScoreboardManager {

    private final Main plugin;

    public ScoreboardManager(Main plugin) {
        this.plugin = plugin;
    }

    public void createScoreboard(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        // --- HP pod nickiem ---
        for (Player online : Bukkit.getOnlinePlayers()) {
            Team team = board.getTeam(online.getName());
            if (team == null) {
                team = board.registerNewTeam(online.getName());
            }
            team.addEntry(online.getName());

            double hearts = online.getHealth() / 2.0;
            String hpFormatted = String.format("%.1f", hearts);
            team.setSuffix(" §c[" + hpFormatted + "❤]");
        }

        Objective obj = board.registerNewObjective("stats", "dummy", "§6§lSTATYSTYKI");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // --- Pobieranie danych z ShopManager ---
        int kills = plugin.getShopManager().getTotalKills(p.getUniqueId());
        int deaths = plugin.getShopManager().getDeaths(p.getUniqueId());
        int wallet = plugin.getShopManager().getPoints(p.getUniqueId());
        int killstreak = plugin.getShopManager().getKillstreak(p.getUniqueId());

        double kd = (deaths == 0) ? kills : (double) kills / deaths;
        String kdFormatted = String.format("%.2f", kd);

        // --- KLUCZOWA ZMIANA: Pobieramy klasę identycznie jak w TabManager ---
        // Metoda getPlayerClass w Main.java zwraca sformatowaną nazwę (np. "§4Berserker")
        String klasa = plugin.getPlayerClass(p.getUniqueId());

        // --- Budowanie listy ---
        addScore(obj, "§7---", 12);
        addScore(obj, "§fKille: §a" + kills, 11);
        addScore(obj, "§fŚmierci: §c" + deaths, 10);
        addScore(obj, "§fK/D: §e" + kdFormatted, 9);
        addScore(obj, "§fKillstreak: §d" + killstreak + " 🔥", 8);
        addScore(obj, "§7 ", 7);

        // Wyświetlamy dokładnie to, co widzi Tablista
        addScore(obj, "§fKlasa: " + klasa, 6);

        addScore(obj, "§7  ", 5);
        addScore(obj, "§fPortfel: §6" + wallet + "⛁", 4);
        addScore(obj, "§7--- ", 3);
        addScore(obj, "§eTwojSerwer.pl", 2);

        p.setScoreboard(board);
    }

    private void addScore(Objective obj, String text, int score) {
        Score s = obj.getScore(text);
        s.setScore(score);
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            createScoreboard(p);
        }
    }
}