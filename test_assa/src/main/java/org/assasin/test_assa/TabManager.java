package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class TabManager {

    private final Main plugin;

    public TabManager(Main plugin) {
        this.plugin = plugin;
    }

    public void updateTab(Player p) {
        if (p == null || !p.isOnline()) return;

        // Pobieranie danych z Twoich managerów
        String klasa = plugin.getPlayerClass(p.getUniqueId());
        int kills = plugin.shopManager.getTotalKills(p.getUniqueId());
        int deaths = plugin.shopManager.getDeaths(p.getUniqueId());
        int killstreak = plugin.shopManager.getKillstreak(p.getUniqueId());
        int wallet = plugin.shopManager.getPoints(p.getUniqueId());

        // HEADER - Symulacja kolumn pionowych za pomocą spacji i znaków
        // Używamy \n dla nowych linii
        String header = "\n " +
                "§6§l» §e§lTWOJE STATYSTYKI §6§l«\n" +
                "§7--------------------------------------------\n" +
                " §fNick: §a" + p.getName() + "    §8|    §fKlasa: §d" + klasa + "\n" +
                " \n" +
                " §fZabójstwa: §a" + kills + "      §fZgony: §c" + deaths + "\n" +
                " §fSeria (KS): §e" + killstreak + " 🔥     §fPortfel: §6" + wallet + "⛁\n" +
                "§7--------------------------------------------\n";

        // FOOTER - Informacje o serwerze
        String footer = "\n" +
                "§7Graczy online: §a" + Bukkit.getOnlinePlayers().size() + " §8/ §2100\n" +
                "§e§lWWW.TWOJSERWER.PL\n" +
                " ";

        // Wysłanie Tablisty do gracza
        p.setPlayerListHeaderFooter(header, footer);
    }

    // Metoda do odświeżania taba wszystkim na serwerze
    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTab(p);
        }
    }
}