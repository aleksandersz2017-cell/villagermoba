package org.assasin.test_assa;

import org.bukkit.Bukkit;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
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

    private final Map<UUID, String> selectedClass = new HashMap<>();

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

        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(this.queueManager, this);

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

        getLogger().info("Plugin test_assa - Zaladowano pomyslnie!");
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
        Bukkit.getScheduler().runTaskLater(this, () -> updateHealthScoreboard(p), 1L);
        giveLobbyItems(p);
        teleportToMainSpawn(p);
    }

    @EventHandler public void onRegen(EntityRegainHealthEvent e) { if (e.getEntity() instanceof Player) updateHealthScoreboard((Player) e.getEntity()); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player) updateHealthScoreboard((Player) e.getEntity()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            updateHealthScoreboard(p);
            if (selectedClass.containsKey(p.getUniqueId())) {
                teleportToRandomFFA(p);
            } else {
                giveLobbyItems(p);
                teleportToMainSpawn(p);
            }
        }, 2L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        this.shopManager.resetKillstreak(victim.getUniqueId());
        this.shopManager.addDeath(victim);
        e.getDrops().removeIf(item -> isAbilityItem(item.getType()));

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
        sbManager.updateAll();
        tabManager.updateAll();
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (queueManager.playersInGame.contains(p.getUniqueId())) {
            queueManager.endGame(p);
        }
        queueManager.leaveQueue(p);
        selectedClass.remove(p.getUniqueId());
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

        if (command.getName().equalsIgnoreCase("leave")) {
            if (!selectedClass.containsKey(p.getUniqueId()) && !queueManager.playersInGame.contains(p.getUniqueId())) {
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
            giveLobbyItems(p);
            teleportToMainSpawn(p);
            p.sendMessage("§e§l(!) §7Teleportowano na spawn!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("kit")) {
            // Jeśli gracz nie jest w 1v1 ani nie wybrał jeszcze klasy (FFA)
            if (selectedClass.containsKey(p.getUniqueId()) && !queueManager.playersInGame.contains(p.getUniqueId())) {
                p.sendMessage("§c§l(!) §7Masz już wybraną klasę!");
                return true;
            }

            if (args.length == 0) {
                p.sendMessage("§c§l(!) §7Użycie: /kit <klasa>");
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

            // Teleportacja tylko jeśli to wejście do FFA (nie w trakcie 1v1)
            if (!queueManager.playersInGame.contains(p.getUniqueId())) {
                teleportToRandomFFA(p);
                p.sendMessage("§a§lKLASA §8» §fWybrano: " + selectedClass.get(p.getUniqueId()));
            }

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

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("kit") && args.length == 1) {
            return Arrays.asList("minotaur", "paladyn", "assasyn", "hunter", "berserker").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }

    public String getPlayerClass(UUID uuid) { return selectedClass.getOrDefault(uuid, "§7Brak"); }
    public ShopManager getShopManager() { return this.shopManager; }
    public Paladyn getPaladyn() { return this.paladyn; }
    public Berserker getBerserker() { return this.berserker; }
}