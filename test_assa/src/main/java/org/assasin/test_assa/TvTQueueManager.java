package org.assasin.test_assa;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TvTQueueManager implements Listener {

    private final Main plugin;
    private final List<Player> queue = new ArrayList<>();
    private final Set<UUID> spectators = new HashSet<>(); // Lista przechowywująca widzów meczu TvT

    public TvTQueueManager(Main plugin) {
        this.plugin = plugin;
        startActionBarTask(); // Uruchomienie ciągłego odświeżania Action Bara
    }

    public void joinQueue(Player p) {
        // 1. SPRAWDZENIE CZY GRA JUŻ TRWA
        if (isGameActive()) {
            p.sendMessage("§e§lTvT §8» §7Mecz jest w trakcie. Wchodzisz w tryb widza!");

            // Czyszczenie ekwipunku przed wejściem na spectate
            p.getInventory().clear();
            p.setGameMode(GameMode.SPECTATOR);
            spectators.add(p.getUniqueId());

            // Teleportacja na środek areny
            Location specLoc = new Location(p.getWorld(), 128.5, 118.0, 128.5);
            p.teleport(specLoc);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            return;
        }

        // 2. SPRAWDZENIE CZY JEST JUŻ W KOLEJCE
        if (queue.contains(p)) {
            p.sendMessage("§c§l(!) §7Jesteś już w kolejce TvT!");
            return;
        }

        int required = plugin.tvtManager.getRequiredPlayers();
        int newSize = queue.size() + 1;

        // Powiadomienie graczy, którzy JUŻ są w kolejce o nowym graczu
        for (Player inQueue : queue) {
            inQueue.sendMessage("§3§lTvT §8» §e" + p.getName() + " §7dołączył do kolejki! §e(" + newSize + "/" + required + ")");
            inQueue.playSound(inQueue.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }

        queue.add(p);

        p.sendMessage("§3§lTvT §8» §7Dołączono do kolejki §e(" + queue.size() + "/" + required + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        sendActionBar(p, "§aYou are in queue §8» §e" + queue.size() + "§7/§e" + required);
        checkQueue();
    }

    private boolean isGameActive() {
        if (plugin.tvtManager != null && plugin.tvtManager.isGameActive()) {
            return true;
        }
        if (plugin.tvtNexusManager != null && plugin.tvtNexusManager.isGameActive()) {
            return true;
        }
        return false;
    }

    public void leaveQueue(Player p) {
        if (queue.remove(p)) {
            p.sendMessage("§c§l(!) §7Opuściłeś kolejkę TvT.");

            // Powiadomienie pozostałych graczy w kolejce o wyjściu kogoś z kolejki
            int required = plugin.tvtManager.getRequiredPlayers();
            for (Player inQueue : queue) {
                inQueue.sendMessage("§3§lTvT §8» §c" + p.getName() + " §7opuścił kolejkę. §e(" + queue.size() + "/" + required + ")");
            }
        }
    }

    public boolean isInQueue(Player p) {
        return queue.contains(p);
    }

    // --- METODY OBSŁUGUJĄCE OBSERWATORÓW (SPECTATORS) ---
    public boolean isSpectator(Player p) {
        return p != null && spectators.contains(p.getUniqueId());
    }

    public void removeSpectator(Player p) {
        if (p != null) {
            spectators.remove(p.getUniqueId());
        }
    }

    public void clearSpectators() {
        spectators.clear();
    }

    private void checkQueue() {
        int required = plugin.tvtManager.getRequiredPlayers();

        if (queue.size() >= required) {
            int teamSize = plugin.tvtManager.getTeamSize();

            List<Player> team1 = new ArrayList<>();
            List<Player> team2 = new ArrayList<>();

            for (int i = 0; i < teamSize; i++) {
                team1.add(queue.remove(0));
            }
            for (int i = 0; i < teamSize; i++) {
                team2.add(queue.remove(0));
            }

            // TUTAJ WYWOŁUJEMY METODĘ startMatch
            plugin.tvtManager.startMatch(team1, team2);
        }
    }

    // Task w tle wysyłający Action Bar co 20 tików (1 sekunda), dzięki czemu napis nie znika
    private void startActionBarTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queue.isEmpty()) return;

            int required = plugin.tvtManager.getRequiredPlayers();
            String text = "§aYou are in queue §8» §e" + queue.size() + "§7/§e" + required;

            for (Player p : queue) {
                if (p != null && p.isOnline()) {
                    sendActionBar(p, text);
                }
            }
        }, 20L, 20L);
    }

    private void sendActionBar(Player p, String message) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        leaveQueue(p);
        removeSpectator(p);
    }
}