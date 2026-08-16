package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class ShopManager implements Listener {

    private final Main plugin;

    // --- MAPY STATYSTYK ---
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> totalKills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Integer> currentKillstreak = new HashMap<>();

    // --- MAPY ULEPSZEŃ ---
    private final Map<UUID, Boolean> shadowStrikeEnabled = new HashMap<>();
    private final Map<UUID, Boolean> minotaurSpeedEnabled = new HashMap<>();
    private final Map<UUID, Boolean> paladinHealEnabled = new HashMap<>();
    private final Map<UUID, Boolean> berserkerPassiveEnabled = new HashMap<>(); // NOWE

    public ShopManager(Main plugin) {
        this.plugin = plugin;
    }

    // --- ZARZĄDZANIE PUNKTAMI I STATYSTYKAMI ---
    public void addKillPoint(Player killer) {
        UUID uuid = killer.getUniqueId();
        points.put(uuid, points.getOrDefault(uuid, 0) + 1);
        totalKills.put(uuid, totalKills.getOrDefault(uuid, 0) + 1);
    }

    public void addDeath(Player victim) {
        UUID uuid = victim.getUniqueId();
        deaths.put(uuid, deaths.getOrDefault(uuid, 0) + 1);
    }

    public int getKillstreak(UUID uuid) {
        return currentKillstreak.getOrDefault(uuid, 0);
    }

    public void addKillstreak(Player player) {
        UUID uuid = player.getUniqueId();
        int newStreak = getKillstreak(uuid) + 1;
        currentKillstreak.put(uuid, newStreak);

        if (newStreak % 5 == 0) {
            Bukkit.broadcastMessage("§6§lSERIA! §fGracz §e" + player.getName() + " §fma serię §c" + newStreak + " §fzabójstw!");
        }
    }

    public void resetKillstreak(UUID uuid) {
        currentKillstreak.put(uuid, 0);
    }

    // --- GETTERY ---
    public int getPoints(UUID uuid) { return points.getOrDefault(uuid, 0); }
    public int getTotalKills(UUID uuid) { return totalKills.getOrDefault(uuid, 0); }
    public int getDeaths(UUID uuid) { return deaths.getOrDefault(uuid, 0); }

    public boolean hasShadowStrike(UUID uuid) { return shadowStrikeEnabled.getOrDefault(uuid, false); }
    public boolean hasMinotaurSpeed(UUID uuid) { return minotaurSpeedEnabled.getOrDefault(uuid, false); }
    public boolean hasPaladinHeal(UUID uuid) { return paladinHealEnabled.getOrDefault(uuid, false); }
    public boolean hasBerserkerPassive(UUID uuid) { return berserkerPassiveEnabled.getOrDefault(uuid, false); } // NOWE

    // --- OBSŁUGA SKLEPU (GUI) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopOpen(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.GOLD_INGOT) return;
        if (item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("SKLEP")) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
                openShop(e.getPlayer());
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.5f);
            }
        }
    }

    @EventHandler
    public void onGoldDrop(PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        if (item.getType() == Material.GOLD_INGOT && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("SKLEP")) {
            e.setCancelled(true);
        }
    }

    public void openShop(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lKILL SHOP");

        inv.setItem(10, createItem(Material.ENCHANTED_BOOK, "§c§lBlade Edge (Sharpness I)",
                Arrays.asList("§7Increases your weapon's Sharpness by 1.", "§fCost: §a5 Kills")));

        ItemStack weapon = p.getInventory().getItem(0);
        if (weapon != null) {
            Material type = weapon.getType();

            if (type == Material.WOODEN_SWORD || type == Material.IRON_SWORD) {
                boolean bought = hasShadowStrike(p.getUniqueId());
                String status = bought ? "§a§lPURCHASED" : "§fCost: §a10 Kills";
                inv.setItem(13, createItem(Material.NETHER_STAR, "§b§lShadow Strike",
                        Arrays.asList("§7If you don't attack anyone while", "§7invisible, you receive §cStrength I §7for 1s.", "", status)));
            }
            else if (type == Material.IRON_AXE) {
                boolean bought = hasMinotaurSpeed(p.getUniqueId());
                String status = bought ? "§a§lPURCHASED" : "§fCost: §a10 Kills";
                inv.setItem(13, createItem(Material.LEATHER_BOOTS, "§e§lLight Hooves",
                        Arrays.asList("§7Reduces your heaviness.", "§7Receive permanent §bSpeed I§7.", "", status)));
            }
            else if (type == Material.DIAMOND_SWORD) {
                boolean bought = hasPaladinHeal(p.getUniqueId());
                String status = bought ? "§a§lPURCHASED" : "§fCost: §a10 Kills";
                inv.setItem(13, createItem(Material.GHAST_TEAR, "§6§lHoly Breath",
                        Arrays.asList("§7Your prayer becomes stronger.", "§7Healing increased to §c2 hearts §7(from 1.5).", "", status)));
            }
            // --- NOWA SEKCA DLA BERSERKERA ---
            else if (type == Material.GOLDEN_AXE) {
                boolean bought = hasBerserkerPassive(p.getUniqueId());
                String status = bought ? "§a§lPURCHASED" : "§fCost: §a10 Kills";
                inv.setItem(13, createItem(Material.NETHER_WART, "§4§lBattle Frenzy",
                        Arrays.asList("§7The lower your health, the more", "§7damage you deal.", "", status)));
            }
        }

        inv.setItem(16, createItem(Material.DIAMOND_CHESTPLATE, "§b§lArmor Reinforcement",
                Arrays.asList("§7Permanently increases your armor by §f1§7.", "§fCost: §a5 Kills")));

        inv.setItem(22, createItem(Material.GOLD_INGOT, "§eYour Currency",
                Collections.singletonList("§7You have: §6" + getPoints(p.getUniqueId()) + "⛁")));

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("§6§lKILL SHOP")) return;
        e.setCancelled(true);

        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;

        Player p = (Player) e.getWhoClicked();
        String name = e.getCurrentItem().getItemMeta().getDisplayName();

        if (name.contains("Blade Edge")) {
            buyEnchant(p, 5, Enchantment.DAMAGE_ALL);
        } else if (name.contains("Armor Reinforcement")) {
            buyArmorUpgrade(p, 5, 1);
        } else if (name.contains("Shadow Strike")) {
            buyShadowStrike(p, 10);
        } else if (name.contains("Light Hooves")) {
            buyMinotaurSpeed(p, 10);
        } else if (name.contains("Holy Breath")) {
            buyPaladinHeal(p, 10);
        } else if (name.contains("Battle Frenzy")) { // NOWA OBSŁUGA
            buyBerserkerPassive(p, 10);
        }
    }

    private void buyBerserkerPassive(Player p, int cost) {
        if (hasBerserkerPassive(p.getUniqueId())) {
            p.sendMessage("§cJuż posiadasz to ulepszenie!");
            return;
        }
        int pPoints = getPoints(p.getUniqueId());
        if (pPoints >= cost) {
            points.put(p.getUniqueId(), pPoints - cost);
            berserkerPassiveEnabled.put(p.getUniqueId(), true);
            p.sendMessage("§4§lSZAŁ BITWNY! §7Twoje rany zwiększają Twoją siłę.");
            p.playSound(p.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 0.5f);
            openShop(p);
            plugin.sbManager.updateAll();
        } else {
            p.sendMessage("§cBrak punktów!");
        }
    }

    private void buyPaladinHeal(Player p, int cost) {
        if (hasPaladinHeal(p.getUniqueId())) {
            p.sendMessage("§cJuż posiadasz to ulepszenie!");
            return;
        }
        int pPoints = getPoints(p.getUniqueId());
        if (pPoints >= cost) {
            points.put(p.getUniqueId(), pPoints - cost);
            paladinHealEnabled.put(p.getUniqueId(), true);
            p.sendMessage("§6§lAMEN! §7Twoje leczenie zostało wzmocnione.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
            openShop(p);
            plugin.sbManager.updateAll();
        } else {
            p.sendMessage("§cBrak punktów!");
        }
    }

    private void buyShadowStrike(Player p, int cost) {
        if (hasShadowStrike(p.getUniqueId())) {
            p.sendMessage("§cJuż posiadasz to ulepszenie!");
            return;
        }
        int pPoints = getPoints(p.getUniqueId());
        if (pPoints >= cost) {
            points.put(p.getUniqueId(), pPoints - cost);
            shadowStrikeEnabled.put(p.getUniqueId(), true);
            p.sendMessage("§b§lODBLOKOWANO! §7Skup czakrę podczas bycia duchem...");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            openShop(p);
            plugin.sbManager.updateAll();
        } else {
            p.sendMessage("§cBrak punktów!");
        }
    }

    private void buyMinotaurSpeed(Player p, int cost) {
        if (hasMinotaurSpeed(p.getUniqueId())) {
            p.sendMessage("§cJuż posiadasz to ulepszenie!");
            return;
        }
        int pPoints = getPoints(p.getUniqueId());
        if (pPoints >= cost) {
            points.put(p.getUniqueId(), pPoints - cost);
            minotaurSpeedEnabled.put(p.getUniqueId(), true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
            p.sendMessage("§e§lSZARŻA! §7Twoje kopyta niosą Cię teraz szybciej.");
            p.playSound(p.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1.0f, 1.0f);
            openShop(p);
            plugin.sbManager.updateAll();
        } else {
            p.sendMessage("§cBrak punktów!");
        }
    }

    private void buyArmorUpgrade(Player p, int cost, double amount) {
        int pPoints = getPoints(p.getUniqueId());
        if (pPoints >= cost) {
            AttributeInstance armorAttr = p.getAttribute(Attribute.GENERIC_ARMOR);
            if (armorAttr != null) {
                points.put(p.getUniqueId(), pPoints - cost);
                armorAttr.setBaseValue(armorAttr.getBaseValue() + amount);
                p.sendMessage("§a§lULEPSZONO! §7Twój pancerz wynosi teraz: §f" + armorAttr.getBaseValue());
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 1.0f);
                openShop(p);
                plugin.sbManager.updateAll();
            }
        } else {
            p.sendMessage("§cBrak punktów!");
        }
    }

    private void buyEnchant(Player p, int cost, Enchantment ench) {
        int pPoints = getPoints(p.getUniqueId());
        if (pPoints >= cost) {
            ItemStack tool = p.getInventory().getItem(0);
            if (tool != null && tool.getType() != Material.AIR) {
                points.put(p.getUniqueId(), pPoints - cost);
                int nextLvl = tool.getEnchantmentLevel(ench) + 1;
                tool.addUnsafeEnchantment(ench, nextLvl);
                p.sendMessage("§aUlepszono broń na poziom " + nextLvl + "!");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                openShop(p);
                plugin.sbManager.updateAll();
            } else {
                p.sendMessage("§cMusisz trzymać broń w 1 slocie!");
            }
        } else {
            p.sendMessage("§cBrak punktów!");
        }
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- SETTERY ADMIN ---
    public void setPoints(UUID uuid, int amount) { this.points.put(uuid, amount); }
    public void setKills(UUID uuid, int amount) { this.totalKills.put(uuid, amount); }
    public void setDeaths(UUID uuid, int amount) { this.deaths.put(uuid, amount); }
}