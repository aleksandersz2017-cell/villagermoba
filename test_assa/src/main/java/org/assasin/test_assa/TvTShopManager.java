package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TvTShopManager implements Listener {

    private final Main plugin;
    private final String guiTitle = "§8§lTvT SHOP";

    private final UUID ARMOR_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789012");
    private final UUID SPEED_MODIFIER_UUID  = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-345678901234");

    private static final int MAX_EXTRA_HEARTS = 5;

    // --- SLOTY GUI ---
    // Zakupy przesunięte o 1 rząd (9 slotów) w górę względem starego layoutu,
    // żeby zrobić miejsce na talenty w ostatnim rzędzie (patrz TALENT_SLOT_1/2).
    private static final int HEART_SLOT = 11;
    private static final int WEAPON_SLOT = 13;
    private static final int SPEED_SLOT = 15;
    private static final int ARMOR_SLOT = 22;
    private static final int TALENT_SLOT_1 = 48;
    private static final int TALENT_SLOT_2 = 50;

    // Poziom drużyny wymagany do odblokowania wyboru talentu.
    // UWAGA: musi być zsynchronizowany z TvTManager.TALENT_UNLOCK_LEVEL.
    private static final int TALENT_REQUIRED_LEVEL = 5;

    private final Map<UUID, Integer> playerEmeralds = new HashMap<>();
    private final Map<UUID, Integer> extraHearts = new HashMap<>();
    private final Map<UUID, Integer> weaponUpgradeLevel = new HashMap<>();
    private final Map<UUID, Boolean> hasSpeed = new HashMap<>();
    private final Map<UUID, Boolean> hasArmor = new HashMap<>();

    // NOWE: id wybranego talentu klasowego gracza w tej rundzie (brak wpisu = jeszcze nie wybrał).
    private final Map<UUID, String> chosenTalent = new HashMap<>();

    public TvTShopManager(Main plugin) {
        this.plugin = plugin;
    }

    // ==========================================================================================
    // SYSTEM TALENTÓW - dane
    // Każda klasa ma dokładnie 2 talenty do wyboru (wybór jest jednorazowy i wykluczający się).
    // Same efekty talentów są odczytywane "na żywo" metodą hasTalent(...) bezpośrednio
    // z klas postaci (Assasyn.java, Berserker.java, itd.) - tu trzymamy tylko definicje i wybór.
    // ==========================================================================================
    private static class Talent {
        final String id;
        final String displayName;
        final Material icon;
        final List<String> lore;

        Talent(String id, String displayName, Material icon, String... lore) {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.lore = Arrays.asList(lore);
        }
    }

    private static final Map<String, Talent[]> CLASS_TALENTS = new HashMap<>();
    static {
        CLASS_TALENTS.put("Assasyn", new Talent[]{
                new Talent("assasyn_invis_no_break", "§8§lMaster of Shadows", Material.INK_SAC,
                        "§7Taking damage no longer breaks",
                        "§7your §fShadow Veil§7."),
                new Talent("assasyn_invis_speed", "§b§lShadow Agility", Material.SUGAR,
                        "§7While using §fShadow Veil§7 you gain",
                        "§7an extra §b+20% speed§7.")
        });
        CLASS_TALENTS.put("Berserker", new Talent[]{
                new Talent("berserker_longer_chain", "§7§lLonger Chain", Material.CHAIN,
                        "§7The §7Iron Chain's§7 range",
                        "§7is increased from §f16§7 to §f25§7 blocks."),
                new Talent("berserker_whirlwind_less_slow", "§c§lStable Whirlwind", Material.COBWEB,
                        "§7§lRage Whirlwind§7 slows you down",
                        "§7by §c50% less§7 than normal.")
        });
        CLASS_TALENTS.put("Minotaur", new Talent[]{
                new Talent("minotaur_longer_rage", "§4§lLonger Rage", Material.IRON_BLOCK,
                        "§7§lMinotaur's Rage§7 lasts",
                        "§7§425% longer§7."),
                new Talent("minotaur_dash_cooldown_reduction", "§b§lUnstoppable Charge", Material.BONE,
                        "§7Every target hit by §lCharge§7",
                        "§7reduces its cooldown by §b2 seconds§7.")
        });
        CLASS_TALENTS.put("Paladyn", new Talent[]{
                new Talent("paladyn_better_seal", "§e§lEmpowered Mark", Material.GOLD_NUGGET,
                        "§7Healing from §eSinner's Mark§7",
                        "§7is increased from §c1.5§7 to §c2 hearts§7."),
                new Talent("paladyn_seal_heals_team", "§a§lHoly Bond", Material.GOLDEN_APPLE,
                        "§7Hitting the marked target",
                        "§7also heals nearby allies.")
        });
        CLASS_TALENTS.put("Hunter", new Talent[]{
                new Talent("hunter_arrow_hit_slide_cooldown", "§2§lLight Foot", Material.WHITE_CARPET,
                        "§7Hitting with an arrow reduces",
                        "§7§2Slide's§7 cooldown by §a5 seconds§7."),
                new Talent("hunter_speed_after_slide", "§f§lPursuit", Material.SUGAR,
                        "§7After using §2Slide§7 you gain",
                        "§7§fSpeed I§7 for §f8 seconds§7.")
        });
    }

    private Talent[] getTalentsForClass(String playerClass) {
        if (playerClass == null) return null;
        for (Map.Entry<String, Talent[]> entry : CLASS_TALENTS.entrySet()) {
            if (playerClass.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    // NOWE: wołane z klas postaci (Assasyn, Berserker, Minotaur, Paladyn, Hunter),
    // żeby sprawdzić czy dany gracz ma aktywny konkretny talent.
    public boolean hasTalent(Player p, String talentId) {
        if (p == null || talentId == null) return false;
        return talentId.equals(chosenTalent.get(p.getUniqueId()));
    }

    // --- CURRENCY MANAGEMENT (EMERALDS) ---

    public int getEmeralds(Player p) {
        return playerEmeralds.getOrDefault(p.getUniqueId(), 0);
    }

    public void addEmeralds(Player p, int amount) {
        int current = getEmeralds(p);
        playerEmeralds.put(p.getUniqueId(), current + amount);
    }

    public boolean removeEmeralds(Player p, int amount) {
        int current = getEmeralds(p);
        if (current >= amount) {
            playerEmeralds.put(p.getUniqueId(), current - amount);
            return true;
        }
        return false;
    }

    // NOWE: ustawia saldo Emeraldów gracza bezpośrednio (np. z komendy /tvtadmin).
    public void setEmeralds(Player p, int amount) {
        playerEmeralds.put(p.getUniqueId(), Math.max(0, amount));
    }

    public void clearEmeralds(Player p) {
        if (p == null) return;
        UUID uuid = p.getUniqueId();

        playerEmeralds.remove(uuid);
        extraHearts.remove(uuid);
        weaponUpgradeLevel.remove(uuid);
        hasSpeed.remove(uuid);
        hasArmor.remove(uuid);
        chosenTalent.remove(uuid);

        if (p.isOnline()) {
            AttributeInstance maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealth != null) {
                removeHealthModifier(maxHealth);
                p.setHealth(Math.min(p.getHealth(), maxHealth.getValue()));
            }

            AttributeInstance speed = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                removeSpeedModifier(speed);
            }

            AttributeInstance armor = p.getAttribute(Attribute.GENERIC_ARMOR);
            if (armor != null) {
                removeArmorModifier(armor);
            }
        }
    }

    // --- GIVE SHOP ITEM ---

    public void giveShopItem(Player p) {
        if (p == null || !p.isOnline()) return;

        ItemStack shopFlower = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = shopFlower.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lTvT SHOP §7(Right Click)");
            meta.setLore(Arrays.asList(
                    "§7Use this item to open the shop.",
                    "§7Purchases are only allowed within",
                    "§710 blocks of your Nexus."
            ));
            shopFlower.setItemMeta(meta);
        }

        p.getInventory().setItem(7, shopFlower);
    }

    // --- OPEN GUI ON ITEM INTERACT ---

    @EventHandler
    public void onShopInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (item == null || item.getType() != Material.SUNFLOWER) return;
        if (!item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) return;
        if (!item.getItemMeta().getDisplayName().contains("TvT SHOP")) return;

        e.setCancelled(true);

        if (!plugin.isPlayerInTvT(p)) {
            p.sendMessage("§c§l(!) §7This shop is only available during a TvT match!");
            return;
        }

        openTvTShopGUI(p);
    }

    // --- OPEN SHOP GUI ---

    public void openTvTShopGUI(Player p) {
        UUID uuid = p.getUniqueId();
        Inventory gui = Bukkit.createInventory(null, 54, guiTitle);
        int emeralds = getEmeralds(p);

        // ITEM 1: +1 Extra Heart - Max Limit: 5
        int boughtHearts = extraHearts.getOrDefault(uuid, 0);
        boolean maxHeartsReached = boughtHearts >= MAX_EXTRA_HEARTS;
        ItemStack heartItem = new ItemStack(Material.RED_DYE);
        ItemMeta heartMeta = heartItem.getItemMeta();
        if (heartMeta != null) {
            heartMeta.setDisplayName("§c§lExtra Heart (+1 ❤)");
            heartMeta.setLore(Arrays.asList(
                    "§7Buy a permanent extra heart",
                    "§7until the end of this match.",
                    "",
                    "§7Limit: §e" + MAX_EXTRA_HEARTS + " purchases",
                    "§7Purchased: §c+" + boughtHearts + " / " + MAX_EXTRA_HEARTS + " ❤",
                    "",
                    "§7Cost: §a60 Emeralds",
                    "§7Your Balance: §a" + emeralds,
                    "",
                    maxHeartsReached ? "§cYou have reached the maximum limit!" : "§eClick to purchase!"
            ));
            heartItem.setItemMeta(heartMeta);
        }
        gui.setItem(HEART_SLOT, heartItem);

        int weaponLevel = weaponUpgradeLevel.getOrDefault(uuid, 0);
        boolean maxWeaponReached = weaponLevel >= 3;

        int nextWeaponCost = 150;
        if (weaponLevel == 1) nextWeaponCost = 200;
        else if (weaponLevel == 2) nextWeaponCost = 250;

        ItemStack sharpItem = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta sharpMeta = sharpItem.getItemMeta();
        if (sharpMeta != null) {
            sharpMeta.setDisplayName("§b§lWeapon Upgrade (Level " + (maxWeaponReached ? 3 : (weaponLevel + 1)) + ")");
            sharpMeta.setLore(Arrays.asList(
                    "§7Enchants your equipment:",
                    "§7• All Swords & Axes » §bSharpness " + (weaponLevel == 0 ? "I" : (weaponLevel == 1 ? "II" : "III")),
                    "§7• All Bows » §bPower " + (weaponLevel == 0 ? "I" : (weaponLevel == 1 ? "II" : "III")),
                    "",
                    "§7Current Level: §eLevel " + weaponLevel + " / 3",
                    maxWeaponReached ? "§cMaximum level reached!" : "§7Cost: §a" + nextWeaponCost + " Emeralds",
                    "§7Your Balance: §a" + emeralds,
                    "",
                    maxWeaponReached ? "§cYou own the maximum upgrade!" : "§eClick to purchase!"
            ));
            sharpItem.setItemMeta(sharpMeta);
        }
        gui.setItem(WEAPON_SLOT, sharpItem);

        // ITEM 3: Speed I - Koszt 200
        boolean speedBought = hasSpeed.getOrDefault(uuid, false);
        ItemStack speedItem = new ItemStack(Material.SUGAR);
        ItemMeta speedMeta = speedItem.getItemMeta();
        if (speedMeta != null) {
            speedMeta.setDisplayName("§f§lSpeed I (+10% Speed)");
            speedMeta.setLore(Arrays.asList(
                    "§7Increases movement speed by 10%",
                    "§7until the end of the match.",
                    "",
                    "§7Limit: §e1 purchase",
                    "§7Status: " + (speedBought ? "§aPurchased" : "§cNot Purchased"),
                    "",
                    "§7Cost: §a200 Emeralds",
                    "§7Your Balance: §a" + emeralds,
                    "",
                    speedBought ? "§cYou already own this upgrade!" : "§eClick to purchase!"
            ));
            speedItem.setItemMeta(speedMeta);
        }
        gui.setItem(SPEED_SLOT, speedItem);

        // ITEM 4: Armor I - Koszt 100
        boolean armorBought = hasArmor.getOrDefault(uuid, false);
        ItemStack armorItem = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta armorMeta = armorItem.getItemMeta();
        if (armorMeta != null) {
            armorMeta.setDisplayName("§9§lArmor I (+2 Armor Points / 1 Bar)");
            armorMeta.setLore(Arrays.asList(
                    "§7Adds +2 permanent armor points",
                    "§7until the end of the match.",
                    "",
                    "§7Limit: §e1 purchase",
                    "§7Status: " + (armorBought ? "§aPurchased" : "§cNot Purchased"),
                    "",
                    "§7Cost: §a100 Emeralds",
                    "§7Your Balance: §a" + emeralds,
                    "",
                    armorBought ? "§cYou already own this upgrade!" : "§eClick to purchase!"
            ));
            armorItem.setItemMeta(armorMeta);
        }
        gui.setItem(ARMOR_SLOT, armorItem);

        // --- TALENTY POSTACI ---
        // Zawsze widoczne (jeśli gracz ma już wybraną klasę), ale klikalne dopiero
        // gdy drużyna gracza osiągnie TALENT_REQUIRED_LEVEL - i tak jak reszta sklepu,
        // tylko gdy gracz jest w zasięgu Nexusa (patrz onInventoryClick).
        Talent[] talents = getTalentsForClass(plugin.getPlayerClass(uuid));
        if (talents != null) {
            String team = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getPlayerTeam(p) : null;
            int teamLevel = (team != null && plugin.tvtManager != null) ? plugin.tvtManager.getTeamLevel(team) : 0;
            boolean unlocked = teamLevel >= TALENT_REQUIRED_LEVEL;
            String chosenId = chosenTalent.get(uuid);

            gui.setItem(TALENT_SLOT_1, buildTalentItem(talents[0], unlocked, chosenId, teamLevel));
            gui.setItem(TALENT_SLOT_2, buildTalentItem(talents[1], unlocked, chosenId, teamLevel));
        }

        p.openInventory(gui);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    private ItemStack buildTalentItem(Talent talent, boolean unlocked, String chosenId, int teamLevel) {
        boolean isChosen = talent.id.equals(chosenId);
        boolean otherChosen = chosenId != null && !isChosen;

        ItemStack item = new ItemStack(talent.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((isChosen ? "§a✔ " : "") + talent.displayName);

            List<String> lore = new ArrayList<>(talent.lore);
            lore.add("");
            lore.add("§8Class Talent §7(Free)");
            lore.add("");

            if (isChosen) {
                lore.add("§a✔ Talent selected!");
            } else if (otherChosen) {
                lore.add("§7You already picked a different talent this round.");
            } else if (!unlocked) {
                lore.add("§cRequires team level §e" + TALENT_REQUIRED_LEVEL);
                lore.add("§7Current team level: §e" + teamLevel);
            } else {
                lore.add("§eClick to select this talent!");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- SHOP INVENTORY CLICKS & DISTANCE VERIFICATION ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(guiTitle)) return;

        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        UUID uuid = p.getUniqueId();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = e.getSlot();

        if (slot != HEART_SLOT && slot != WEAPON_SLOT && slot != SPEED_SLOT && slot != ARMOR_SLOT
                && slot != TALENT_SLOT_1 && slot != TALENT_SLOT_2) return;

        // Ta sama zasada odległości dla WSZYSTKIEGO w sklepie - łącznie z talentami.
        if (plugin.tvtNexusManager != null && !plugin.tvtNexusManager.isPlayerNearNexus(p)) {
            p.sendMessage("§c§lTvT SHOP §8» §7You are too far from spawn! You must be within 10 blocks to make a purchase.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // --- TALENTY ---
        if (slot == TALENT_SLOT_1 || slot == TALENT_SLOT_2) {
            handleTalentClick(p, slot);
            return;
        }

        // 1. PURCHASE: +1 Heart (Limit: 5)
        if (slot == HEART_SLOT) {
            int currentHearts = extraHearts.getOrDefault(uuid, 0);
            if (currentHearts >= MAX_EXTRA_HEARTS) {
                sendAlreadyBoughtMsg(p);
                return;
            }

            int cost = 60;
            if (removeEmeralds(p, cost)) {
                extraHearts.put(uuid, currentHearts + 1);

                applyPermanentUpgrades(p);
                healToMax(p);

                p.sendMessage("§a§lTvT SHOP §8» §7Purchased §c+1 Heart §7for §a60 Emeralds§7!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                openTvTShopGUI(p);
            } else {
                sendNotEnoughEmeraldsMsg(p, cost);
            }
        }

        // 2. PURCHASE: Weapon Upgrade (Levels 1, 2, 3)
        else if (slot == WEAPON_SLOT) {
            int currentLevel = weaponUpgradeLevel.getOrDefault(uuid, 0);
            if (currentLevel >= 3) {
                sendAlreadyBoughtMsg(p);
                return;
            }

            int cost = 150;
            if (currentLevel == 1) cost = 200;
            else if (currentLevel == 2) cost = 250;

            if (removeEmeralds(p, cost)) {
                int newLevel = currentLevel + 1;
                weaponUpgradeLevel.put(uuid, newLevel);
                applyPermanentUpgrades(p);

                p.sendMessage("§a§lTvT SHOP §8» §7Purchased §bWeapon Upgrade Level " + newLevel + " §7for §a" + cost + " Emeralds§7!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                openTvTShopGUI(p);
            } else {
                sendNotEnoughEmeraldsMsg(p, cost);
            }
        }

        // 3. PURCHASE: Speed I (200 Emeralds)
        else if (slot == SPEED_SLOT) {
            if (hasSpeed.getOrDefault(uuid, false)) {
                sendAlreadyBoughtMsg(p);
                return;
            }
            int cost = 200;
            if (removeEmeralds(p, cost)) {
                hasSpeed.put(uuid, true);
                applyPermanentUpgrades(p);

                p.sendMessage("§a§lTvT SHOP §8» §7Purchased §f+10% Speed §7for §a200 Emeralds§7!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                openTvTShopGUI(p);
            } else {
                sendNotEnoughEmeraldsMsg(p, cost);
            }
        }

        // 4. PURCHASE: Armor I (100 Emeralds)
        else if (slot == ARMOR_SLOT) {
            if (hasArmor.getOrDefault(uuid, false)) {
                sendAlreadyBoughtMsg(p);
                return;
            }
            int cost = 100;
            if (removeEmeralds(p, cost)) {
                hasArmor.put(uuid, true);
                applyPermanentUpgrades(p);

                p.sendMessage("§a§lTvT SHOP §8» §7Purchased §9+2 Armor Points (1 Armor Bar) §7for §a100 Emeralds§7!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                openTvTShopGUI(p);
            } else {
                sendNotEnoughEmeraldsMsg(p, cost);
            }
        }
    }

    private void handleTalentClick(Player p, int slot) {
        UUID uuid = p.getUniqueId();

        Talent[] talents = getTalentsForClass(plugin.getPlayerClass(uuid));
        if (talents == null) return;

        Talent talent = (slot == TALENT_SLOT_1) ? talents[0] : talents[1];

        if (chosenTalent.containsKey(uuid)) {
            p.sendMessage("§c§lTvT SHOP §8» §7You already picked a talent this round!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String team = plugin.tvtNexusManager != null ? plugin.tvtNexusManager.getPlayerTeam(p) : null;
        int teamLevel = (team != null && plugin.tvtManager != null) ? plugin.tvtManager.getTeamLevel(team) : 0;

        if (teamLevel < TALENT_REQUIRED_LEVEL) {
            p.sendMessage("§c§lTvT SHOP §8» §7Talents unlock at team level §e" + TALENT_REQUIRED_LEVEL + " §7(currently: §e" + teamLevel + "§7).");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        chosenTalent.put(uuid, talent.id);

        p.sendMessage("§a§lTvT SHOP §8» §7Talent selected: " + talent.displayName + "§7!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        openTvTShopGUI(p);
    }

    // --- AUTOMATIC ENCHANTING ON RESPAWN & ITEM PICKUP/HELD ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!plugin.isPlayerInTvT(p)) return;

        giveShopItem(p);
        applyPermanentUpgrades(p);

        // Kaskadowe sprawdzanie ekwipunku po respawnie
        int[] delays = {1, 5, 10, 20, 40};
        for (int delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    giveShopItem(p);
                    applyPermanentUpgrades(p);
                    healToMax(p);
                }
            }, delay);
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player p = (Player) e.getEntity();
            if (weaponUpgradeLevel.getOrDefault(p.getUniqueId(), 0) > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> applyWeaponUpgradesToInventory(p), 1L);
            }
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (weaponUpgradeLevel.getOrDefault(p.getUniqueId(), 0) > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyWeaponUpgradesToInventory(p), 1L);
        }
    }

    private void healToMax(Player p) {
        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            p.setHealth(maxHealthAttr.getValue());
        }
    }

    // --- APPLY PURCHASED UPGRADES ---

    public void applyPermanentUpgrades(Player p) {
        if (p == null || !p.isOnline()) return;
        UUID uuid = p.getUniqueId();

        // 1. Extra Hearts
        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            removeHealthModifier(maxHealthAttr);

            int heartsBought = extraHearts.getOrDefault(uuid, 0);
            if (heartsBought > 0) {
                AttributeModifier healthModifier = new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "TvTShopHealthBonus",
                        heartsBought * 2.0,
                        AttributeModifier.Operation.ADD_NUMBER
                );
                maxHealthAttr.addModifier(healthModifier);
            }
        }

        // 2. Extra Speed
        boolean speedBought = hasSpeed.getOrDefault(uuid, false);
        AttributeInstance speedAttr = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            removeSpeedModifier(speedAttr);

            if (speedBought) {
                AttributeModifier speedModifier = new AttributeModifier(
                        SPEED_MODIFIER_UUID,
                        "TvTShopSpeedBonus",
                        0.10,
                        AttributeModifier.Operation.ADD_SCALAR
                );
                speedAttr.addModifier(speedModifier);
            }
        }

        // 3. Extra Armor (+2 Armor Points)
        boolean armorBought = hasArmor.getOrDefault(uuid, false);
        AttributeInstance armorAttr = p.getAttribute(Attribute.GENERIC_ARMOR);
        if (armorAttr != null) {
            removeArmorModifier(armorAttr);

            if (armorBought) {
                AttributeModifier armorModifier = new AttributeModifier(
                        ARMOR_MODIFIER_UUID,
                        "TvTShopArmorBonus",
                        2.0,
                        AttributeModifier.Operation.ADD_NUMBER
                );
                armorAttr.addModifier(armorModifier);
            }
        }

        // 4. Weapon Upgrade
        applyWeaponUpgradesToInventory(p);
    }
    public void applyWeaponUpgradesToInventory(Player p) {
        UUID uuid = p.getUniqueId();
        int level = weaponUpgradeLevel.getOrDefault(uuid, 0);
        if (level <= 0) return;

        for (ItemStack item : p.getInventory().getContents()) {
            upgradeItem(item, level);
        }

        for (ItemStack item : p.getInventory().getArmorContents()) {
            upgradeItem(item, level);
        }

        upgradeItem(p.getInventory().getItemInOffHand(), level);
    }

    private void upgradeItem(ItemStack item, int level) {
        if (item == null || item.getType() == Material.AIR) return;

        if (isMeleeWeapon(item)) {
            if (item.getEnchantmentLevel(Enchantment.DAMAGE_ALL) < level) {
                item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, level);
            }
        } else if (item.getType() == Material.BOW) {
            if (item.getEnchantmentLevel(Enchantment.ARROW_DAMAGE) < level) {
                item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, level);
            }
        }
    }

    private boolean isMeleeWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        String matName = item.getType().name();

        if (matName.contains("SWORD") || matName.contains("AXE")) {
            return true;
        }

        if (item.hasItemMeta() && item.getItemMeta().getDisplayName() != null) {
            String displayName = item.getItemMeta().getDisplayName().toLowerCase();
            return displayName.contains("sword") || displayName.contains("dagger") || displayName.contains("axe");
        }

        return false;
    }

    private void removeArmorModifier(AttributeInstance armorAttr) {
        for (AttributeModifier modifier : armorAttr.getModifiers()) {
            if (modifier.getUniqueId().equals(ARMOR_MODIFIER_UUID)) {
                armorAttr.removeModifier(modifier);
            }
        }
    }

    private void removeHealthModifier(AttributeInstance healthAttr) {
        for (AttributeModifier modifier : healthAttr.getModifiers()) {
            if (modifier.getUniqueId().equals(HEALTH_MODIFIER_UUID)) {
                healthAttr.removeModifier(modifier);
            }
        }
    }

    private void removeSpeedModifier(AttributeInstance speedAttr) {
        for (AttributeModifier modifier : speedAttr.getModifiers()) {
            if (modifier.getUniqueId().equals(SPEED_MODIFIER_UUID)) {
                speedAttr.removeModifier(modifier);
            }
        }
    }

    private void sendNotEnoughEmeraldsMsg(Player p, int cost) {
        p.sendMessage("§c§lTvT SHOP §8» §7You do not have enough Emeralds! Required: §a" + cost + "§7, You have: §a" + getEmeralds(p));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void sendAlreadyBoughtMsg(Player p) {
        p.sendMessage("§c§lTvT SHOP §8» §7You have already purchased the maximum amount for this upgrade!");
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
}