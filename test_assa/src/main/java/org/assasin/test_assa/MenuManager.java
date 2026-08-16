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
    private final String menuTitle = "§8§lWYBÓR TRYBU";
    private final String classMenuTitle = "§8§lWYBIERZ KLASĘ (FFA)";

    public MenuManager(Main plugin) {
        this.plugin = plugin;
    }

    // --- 1. OTWIERANIE GŁÓWNEGO MENU (KOMPAS) ---
    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, menuTitle);

        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(11, createItem(Material.DIAMOND_SWORD, "§6§lTRYB FFA",
                "§7Bij się z każdym bez przerwy!", "§7Wybierasz kit raz na życie.", "", "§e▶ Kliknij, aby dołączyć"));

        inv.setItem(13, createItem(Material.IRON_SWORD, "§b§lARENA 1V1 (Kolejka)",
                "§7Zapisz się do pojedynku.", "§7Możesz grać na FFA czekając!", "", "§e▶ Kliknij, aby dołączyć"));

        inv.setItem(15, createItem(Material.NETHER_STAR, "§c§lSPAWN / LOBBY",
                "§7Wróć do bezpiecznej strefy.", "", "§e▶ Kliknij, aby wrócić"));

        p.openInventory(inv);
    }

    // --- 2. OTWIERANIE WYBORU KLASY (DLA FFA) ---
    public void openClassMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, classMenuTitle);

        inv.setItem(0, createItem(Material.IRON_AXE, "§6Minotaur", "§7Wielka siła i ryk."));
        inv.setItem(1, createItem(Material.DIAMOND_SWORD, "§ePaladyn", "§7Leczenie i obrona."));
        inv.setItem(2, createItem(Material.IRON_SWORD, "§8Assasyn", "§7Szybkość i trućizna."));
        inv.setItem(3, createItem(Material.BOW, "§2Hunter", "§7Dystans i slajdy."));
        inv.setItem(4, createItem(Material.GOLDEN_AXE, "§4Berserker", "§7Szał i skoki."));

        p.openInventory(inv);
    }

    // --- 3. OBSŁUGA KLIKNIĘĆ ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        // Obsługa Głównego Menu
        if (title.equals(menuTitle)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;

            switch (e.getCurrentItem().getType()) {
                case DIAMOND_SWORD:
                    p.closeInventory();
                    p.sendMessage("§aTeleportacja na FFA...");
                    openClassMenu(p);
                    break;
                case IRON_SWORD:
                    p.closeInventory();
                    plugin.queueManager.joinQueue(p); // Wywołanie nowej kolejki
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    break;
                case NETHER_STAR:
                    p.closeInventory();
                    Location spawnLoc = new Location(p.getWorld(), -70.02, 165.00, -117.97, -406.40f, 5.43f);
                    p.teleport(spawnLoc);
                    plugin.giveLobbyItems(p);
                    p.sendMessage("§e§l(!) §7Wróciłeś na spawn.");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    break;
                default:
                    break;
            }
        }

        // Obsługa Wyboru Klasy
        if (title.equals(classMenuTitle)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

            String itemName = e.getCurrentItem().getItemMeta().getDisplayName();

            if (itemName.contains("Minotaur")) p.performCommand("kit minotaur");
            else if (itemName.contains("Paladyn")) p.performCommand("kit paladyn");
            else if (itemName.contains("Assasyn")) p.performCommand("kit assasyn");
            else if (itemName.contains("Hunter")) p.performCommand("kit hunter");
            else if (itemName.contains("Berserker")) p.performCommand("kit berserker");

            p.closeInventory();
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    // --- 4. OBSŁUGA KOMPASU W RĘCE ---
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