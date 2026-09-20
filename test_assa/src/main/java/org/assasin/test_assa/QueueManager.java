package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class QueueManager implements Listener {

    private final Main plugin;
    private final List<UUID> queue = new ArrayList<>();

    public final Set<UUID> playersInGame = new HashSet<>();
    public UUID playerBlue;
    public UUID playerRed;

    private boolean isCountingDown = false;

    public QueueManager(Main plugin) {
        this.plugin = plugin;
    }

    public void joinQueue(Player p) {
        if (queue.contains(p.getUniqueId()) || playersInGame.contains(p.getUniqueId())) {
            p.sendMessage("§c§l(!) §7You are already in the queue or in a match!");
            return;
        }
        queue.add(p.getUniqueId());
        p.sendMessage("§b§l1V1 §8» §7You have joined the queue! (§e" + queue.size() + "§7/2)");

        if (queue.size() >= 2 && !isCountingDown) {
            startCountdown();
        }
    }

    public void leaveQueue(Player p) {
        if (p == null) return;
        queue.remove(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (playersInGame.contains(uuid)) {
            endGame(p);
        }

        leaveQueue(p);
    }

    private void startCountdown() {
        isCountingDown = true;
        new BukkitRunnable() {
            int timer = 5;
            @Override
            public void run() {
                if (queue.size() < 2) {
                    isCountingDown = false;
                    this.cancel();
                    return;
                }

                if (timer > 0) {
                    for (UUID uuid : queue) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage("§b§l1V1 §8» §7Starting in: §e" + timer + "s");
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        }
                    }
                } else {
                    startGame();
                    this.cancel();
                }
                timer--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startGame() {
        if (queue.size() < 2) return;

        playerBlue = queue.get(0);
        playerRed = queue.get(1);

        playersInGame.add(playerBlue);
        playersInGame.add(playerRed);
        queue.clear();

        setupPlayer(Bukkit.getPlayer(playerBlue), new Location(Bukkit.getWorld("world"), 295.54, 113.0, -282.450, 0f, 0f));
        setupPlayer(Bukkit.getPlayer(playerRed), new Location(Bukkit.getWorld("world"), 295.48, 113.0, -261.60, -180.13f, 2.68f));

        isCountingDown = false;

        new BukkitRunnable() {
            @Override
            public void run() {
                checkKitsAfterStart();
            }
        }.runTaskLater(plugin, 100L);
    }

    private void setupPlayer(Player p, Location loc) {
        if (p == null) return;
        p.teleport(loc);
        p.getInventory().clear();
        plugin.openClassMenu(p);
        p.sendMessage("§6§l1V1 §8» §eYou have 5 seconds to choose your class!");
    }

    private void checkKitsAfterStart() {
        Set<UUID> currentPlayers = new HashSet<>(playersInGame);

        for (UUID uuid : currentPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (plugin.getPlayerClass(uuid).contains("Brak")) {
                    String[] kits = {"minotaur", "paladyn", "assasyn", "hunter", "berserker"};
                    String randomKit = kits[new Random().nextInt(kits.length)];

                    p.performCommand("kit " + randomKit);
                    p.sendMessage("§c§l(!) §7Time's up! Randomly selected class: §e" + randomKit);
                    p.closeInventory();
                }
            }
        }
    }

    public void endGame(Player loser) {
        Set<UUID> gamePlayers = new HashSet<>(playersInGame);

        for (UUID uuid : gamePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§c§lGAME OVER! §7Returning to lobby...");
                plugin.giveLobbyItems(p);
                plugin.teleportToMainSpawn(p);
            }
        }

        playersInGame.clear();
        playerBlue = null;
        playerRed = null;
    }
}