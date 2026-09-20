package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TvTManager implements Listener {

    private final Main plugin;
    private int teamSize = 1;
    private long gameStartTime = 0;

    public long getGameStartTime() {
        return gameStartTime;
    }

    private final Set<UUID> playersSelectingClass = new HashSet<>();

    // NOWE: system talentów drużynowych - poziom, na jakim jest dana drużyna.
    // Klucz to nazwa drużyny (przechowywana w UPPERCASE, żeby uniknąć problemów
    // z wielkością liter przy porównaniach w innych miejscach kodu, np. "BLUE"/"RED").
    // Uzupełniane w rewardTeamLevelUp() - stamtąd przychodzi jedyne miejsce,
    // w którym poziom drużyny się realnie zmienia.
    private final Map<String, Integer> teamLevels = new HashMap<>();

    // Poziom drużyny wymagany do odblokowania wyboru talentu - patrz TvTShopManager.TALENT_REQUIRED_LEVEL
    // (te dwie stałe muszą być zsynchronizowane, jeśli zmienisz jedną, zmień drugą).
    private static final int TALENT_UNLOCK_LEVEL = 5;

    private Location spawnTeam1;
    private Location spawnTeam2;

    public TvTManager(Main plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public int getTeamSize() { return teamSize; }
    public void setTeamSize(int teamSize) { this.teamSize = teamSize; }
    public int getRequiredPlayers() { return teamSize * 2; }

    public long getElapsedMinutes() {
        if (gameStartTime == 0) return 0;
        long elapsedMillis = System.currentTimeMillis() - gameStartTime;
        return elapsedMillis / (1000 * 60);
    }

    public int getRespawnDelaySeconds() {
        return 5 + (int) getElapsedMinutes();
    }

    // NOWE: aktualny poziom danej drużyny (0, jeśli drużyna jeszcze nie awansowała ani razu).
    public int getTeamLevel(String team) {
        if (team == null) return 0;
        return teamLevels.getOrDefault(team.toUpperCase(), 0);
    }

    // NOWE: ustawia poziom drużyny bezpośrednio (np. z komendy /tvtadmin) - bez wywoływania
    // nagrody (+15 Emeraldów) ani komunikatów, które towarzyszą normalnemu rewardTeamLevelUp().
    public void setTeamLevel(String team, int level) {
        if (team == null) return;
        teamLevels.put(team.toUpperCase(), level);
    }

    public void startMatch(List<Player> team1, List<Player> team2) {
        playersSelectingClass.clear();
        teamLevels.clear();
        this.gameStartTime = System.currentTimeMillis();

        spawnTeam1 = new Location(Bukkit.getWorlds().get(0), 185.5, 116.0, 185.5, 45.0f, 0.0f);
        spawnTeam2 = new Location(Bukkit.getWorlds().get(0), 71.5, 116.0, 71.5, -135.0f, 0.0f);

        Location nexusTeam1 = new Location(Bukkit.getWorlds().get(0), 172.5, 112.0, 172.5, 45.0f, 0.0f);
        Location nexusTeam2 = new Location(Bukkit.getWorlds().get(0), 86.5, 112.0, 86.5, -135.0f, 0.0f);

        // Ujednolicenie na TAB (§9 dla BLUE, §c dla RED)
        for (Player p : team1) {
            p.setPlayerListName("§9" + p.getName());
            p.setDisplayName("§9" + p.getName() + "§r");
        }
        for (Player p : team2) {
            p.setPlayerListName("§c" + p.getName());
            p.setDisplayName("§c" + p.getName() + "§r");
        }

        if (plugin.tvtNexusManager != null) {
            plugin.tvtNexusManager.spawnNexuses(nexusTeam1, nexusTeam2, team1, team2);
        }

        if (plugin.jungleManager != null) {
            plugin.jungleManager.startJungleSystem(nexusTeam1, nexusTeam2);
        }

        for (Player p : team1) {
            p.teleport(spawnTeam1);
            p.sendMessage("§3§lTvT §8» §aYou are playing for the §9BLUE§a team! Protect your Villager.");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);

            // NATYCHMIASTOWE WYMUSZENIE KOLORU BLUE I SCOREBOARDU HP OD RAZU NA START
            plugin.updateHealthScoreboard(p);

            playersSelectingClass.add(p.getUniqueId());
            plugin.openClassMenu(p);
        }

        for (Player p : team2) {
            p.teleport(spawnTeam2);
            p.sendMessage("§3§lTvT §8» §aYou are playing for the §cRED§a team! Protect your Villager.");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);

            // NATYCHMIASTOWE WYMUSZENIE KOLORU RED I SCOREBOARDU HP OD RAZU NA START
            plugin.updateHealthScoreboard(p);

            playersSelectingClass.add(p.getUniqueId());
            plugin.openClassMenu(p);
        }

        if (plugin.minionManager != null) {
            plugin.minionManager.startMinionWaves(spawnTeam1, spawnTeam2);
        }
    }

    // Dodano obsługę auto-odświeżania koloru po respawnie
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.updateHealthScoreboard(p);
            }
        }, 2L);
    }

    public void onClassSelected(Player p) {
        if (p == null) return;

        playersSelectingClass.remove(p.getUniqueId());

        if (plugin.tvtShopManager != null) {
            plugin.tvtShopManager.giveShopItem(p);
        }

        giveRecallItem(p);
        p.closeInventory();
    }
    public void clearSelectingClass(Player p) {
        playersSelectingClass.remove(p.getUniqueId());
    }
    private void giveRecallItem(Player p) {
        ItemStack recallStar = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = recallStar.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§9§lReturn to Base §7(Right-Click)");
            recallStar.setItemMeta(meta);
        }
        p.getInventory().setItem(8, recallStar);
    }

    @EventHandler
    public void onRecallUse(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player p = e.getPlayer();
            ItemStack item = e.getItem();

            if (item != null && item.getType() == Material.NETHER_STAR) {
                if (p.getInventory().getHeldItemSlot() == 8) {
                    e.setCancelled(true);
                    plugin.startRecall(p);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();

        if (playersSelectingClass.contains(p.getUniqueId())) {
            p.sendMessage("§c§l(!) §7You must select a class to start playing!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && playersSelectingClass.contains(p.getUniqueId())) {
                    plugin.openClassMenu(p);
                }
            }, 1L);
        }
    }

    public Location getSpawnTeam1() { return spawnTeam1; }
    public Location getSpawnTeam2() { return spawnTeam2; }

    public boolean isGameActive() {
        // Gra jest aktywna, gdy czas startu jest większy od 0
        // oraz przynajmniej jeden gracz jest zarejestrowany w meczu w TvTNexusManager
        if (gameStartTime == 0 || plugin.tvtNexusManager == null) {
            return false;
        }

        // Sprawdzamy online graczy, czy którykolwiek znajduje się w grze TvT
        return Bukkit.getOnlinePlayers().stream()
                .anyMatch(plugin.tvtNexusManager::isPlayerInTvT);
    }
    @EventHandler
    public void onFriendlyFire(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();

        Player attacker = null;
        if (e.getDamager() instanceof Player) {
            attacker = (Player) e.getDamager();
        } else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) e.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker != null && plugin.tvtNexusManager != null) {
            String attackerTeam = plugin.tvtNexusManager.getPlayerTeam(attacker);
            String victimTeam = plugin.tvtNexusManager.getPlayerTeam(victim);

            if (attackerTeam != null && attackerTeam.equals(victimTeam)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        e.setDeathMessage(null);

        if (plugin.tvtNexusManager != null && plugin.tvtNexusManager.isPlayerInTvT(victim)) {
            String victimTeam = plugin.tvtNexusManager.getPlayerTeam(victim);
            String victimColor = "BLUE".equalsIgnoreCase(victimTeam) ? "§9" : "§c";

            if (killer != null && plugin.tvtNexusManager.isPlayerInTvT(killer)) {
                String killerTeam = plugin.tvtNexusManager.getPlayerTeam(killer);
                String killerColor = "BLUE".equalsIgnoreCase(killerTeam) ? "§9" : "§c";

                Bukkit.broadcastMessage("§3§lTvT §8» " + killerColor + killer.getName() + " §7killed " + victimColor + victim.getName() + "§7!");
            } else {
                Bukkit.broadcastMessage("§3§lTvT §8» " + victimColor + victim.getName() + " §7died.");
            }
        }
    }

    public void rewardTeamLevelUp(String team, int newLevel) {
        if (team != null) {
            teamLevels.put(team.toUpperCase(), newLevel);
        }

        if (plugin.tvtShopManager == null) return;

        for (Player p : plugin.getTeamPlayers(team)) {
            plugin.tvtShopManager.addEmeralds(p, 15);
            p.sendMessage("§a§lLEVEL UP §8» §7Your team received §a+15 Emeralds §7for reaching §eLevel " + newLevel + "§7!");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.3f);

            // NOWE: informacja o odblokowaniu talentów klasowych na 5 poziomie.
            if (newLevel == TALENT_UNLOCK_LEVEL) {
                p.sendMessage("§d§lTALENTS §8» §7Your team has unlocked §dclass talents§7! Open the shop to choose your talent.");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }
}