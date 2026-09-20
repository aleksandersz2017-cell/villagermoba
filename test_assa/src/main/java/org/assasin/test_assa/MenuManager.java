package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class MenuManager implements Listener {

    private final Main plugin;
    private final String menuTitle = "§8§lGAME MODE SELECTION";
    private final String classMenuTitle = "§8§lCHOOSE CLASS (FFA)";

    public MenuManager(Main plugin) {
        this.plugin = plugin;
    }

    // --- 1. OPEN MAIN MENU (COMPASS) ---
    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, menuTitle);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Slot 11: FFA
        inv.setItem(11, createItem(Material.DIAMOND_SWORD, "§6§lFFA MODE",
                "§7Fight everyone without interruption!", "§7You choose your kit once per life.", "", "§e▶ Click to open class menu"));

        // Slot 13: Arena 1v1
        inv.setItem(13, createItem(Material.IRON_SWORD, "§b§l1V1 ARENA (Queue)",
                "§7Sign up for a 1v1 duel.", "§7You can play FFA while waiting!", "", "§e▶ Click to join"));

        // Slot 15: Team vs Team (MOBA)
        int teamSize = (plugin.tvtManager != null) ? plugin.tvtManager.getTeamSize() : 1;
        inv.setItem(15, createItem(Material.BEACON, "§3§lTEAM VS TEAM §7(" + teamSize + "v" + teamSize + ")",
                "§7Team battle with Villagers (MOBA)!",
                "§7Protect your Villager and destroy the enemy.",
                "",
                "§e▶ Click to join queue"));

        // Slot 22: Spawn / Lobby
        inv.setItem(22, createItem(Material.NETHER_STAR, "§c§lSPAWN / LOBBY",
                "§7Return to the safe zone.", "", "§e▶ Click to return"));

        p.openInventory(inv);
    }

    // --- 2. OPEN CLASS SELECTION (FOR FFA) ---
    public void openClassMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, classMenuTitle);

        inv.setItem(0, createItem(Material.IRON_AXE, "§6Minotaur", "§7Great strength and roar."));
        inv.setItem(1, createItem(Material.DIAMOND_SWORD, "§ePaladin", "§7Healing and defense."));
        inv.setItem(2, createItem(Material.IRON_SWORD, "§8Assassin", "§7Speed and poison."));
        inv.setItem(3, createItem(Material.BOW, "§2Hunter", "§7Ranged and slides."));
        inv.setItem(4, createItem(Material.GOLDEN_AXE, "§4Berserker", "§7Frenzy and leaps."));

        p.openInventory(inv);
    }

    // --- 3. CLICK HANDLING ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        // Main Menu Handling
        if (title.equals(menuTitle)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;

            switch (e.getCurrentItem().getType()) {
                case DIAMOND_SWORD: // FFA
                    p.closeInventory();
                    openClassMenu(p);
                    break;

                case IRON_SWORD: // 1v1
                    p.closeInventory();
                    plugin.queueManager.joinQueue(p);
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    break;

                case BEACON: // Team vs Team (TvT)
                    p.closeInventory();
                    if (plugin.tvtQueueManager != null) {
                        plugin.tvtQueueManager.joinQueue(p);
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    } else {
                        p.sendMessage("§c§l(!) §7TvT mode is currently unavailable.");
                    }
                    break;

                case NETHER_STAR: // SPAWN
                    p.closeInventory();
                    Location spawnLoc = new Location(p.getWorld(), -70.02, 165.00, -117.97, -406.40f, 5.43f);
                    p.teleport(spawnLoc);
                    plugin.giveLobbyItems(p);
                    p.sendMessage("§e§l(!) §7You have returned to the spawn.");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    break;

                default:
                    break;
            }
        }

        // Class Selection Handling (FFA)
        if (title.equals(classMenuTitle)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

            String itemName = e.getCurrentItem().getItemMeta().getDisplayName();

            if (itemName.contains("Minotaur")) p.performCommand("kit minotaur");
            else if (itemName.contains("Paladin")) p.performCommand("kit paladyn");
            else if (itemName.contains("Assassin")) p.performCommand("kit assasyn");
            else if (itemName.contains("Hunter")) p.performCommand("kit hunter");
            else if (itemName.contains("Berserker")) p.performCommand("kit berserker");

            p.closeInventory();
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    // --- 4. COMPASS IN HAND HANDLING ---
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.COMPASS) {
            openMainMenu(e.getPlayer());
        }
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}