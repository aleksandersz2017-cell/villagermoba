package org.assasin.test_assa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Komando administrasi zintegrowana /tvtadmin sareng /tvt
 */
public class TvTAdminCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public TvTAdminCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "team":
                handleTeamCommand(sender, args);
                break;
            case "emeralds":
                handleEmeraldsCommand(sender, args);
                break;
            case "end":
            case "forceend":
                handleEndCommand(sender);
                break;
            case "setsize":
                handleSetSizeCommand(sender, args);
                break;
            case "queue":
                handleQueueCommand(sender, args);
                break;
            default:
                sender.sendMessage("§cUnknown subcommand. Use §f/tvtadmin help §cfor a list of commands.");
                break;
        }
        return true;
    }

    // --- KOMANDO /tvtadmin queue <pamiarsa|all> ---
    private void handleQueueCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: §f/tvtadmin queue <player|all>");
            return;
        }

        if (plugin.tvtQueueManager == null) {
            sender.sendMessage("§cTvT Queue Manager is not available.");
            return;
        }

        String targetName = args[1];

        if (targetName.equalsIgnoreCase("all")) {
            int addedCount = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Mung ngasupkeun pamiarsa anu teu acan aya dina antrean atanapi pertandingan
                if (!plugin.tvtQueueManager.isInQueue(p) && !plugin.isPlayerInTvT(p)) {
                    plugin.tvtQueueManager.joinQueue(p);
                    addedCount++;
                }
            }
            sender.sendMessage("§a§lTvT ADMIN §8» §7Added §f" + addedCount + " §7eligible player(s) to the queue.");
        } else {
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("§cPlayer '" + targetName + "' is not online.");
                return;
            }

            if (plugin.tvtQueueManager.isInQueue(target)) {
                sender.sendMessage("§cPlayer '" + target.getName() + "' is already in the queue.");
                return;
            }

            if (plugin.isPlayerInTvT(target)) {
                sender.sendMessage("§cPlayer '" + target.getName() + "' is already in a match.");
                return;
            }

            plugin.tvtQueueManager.joinQueue(target);
            sender.sendMessage("§a§lTvT ADMIN §8» §7Forced §f" + target.getName() + " §7into the TvT queue.");
        }
    }

    // --- SUBKOMANDO SÈJÈNNA ---

    // /tvt end | /tvt forceend
    private void handleEndCommand(CommandSender sender) {
        if (plugin.tvtNexusManager != null) {
            plugin.tvtNexusManager.forceEndGame();
            sender.sendMessage("§a§lTvT §8» §7The TvT match was successfully reset and ended!");
        } else {
            sender.sendMessage("§c§l(!) §7TvTNexusManager is unavailable.");
        }
    }

    // /tvt setsize <jumlah>
    private void handleSetSizeCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: §f/tvt setsize <amount>");
            return;
        }

        if (plugin.tvtManager == null) {
            sender.sendMessage("§cTvT is not currently available.");
            return;
        }

        try {
            int size = Integer.parseInt(args[1]);
            if (size < 1) {
                sender.sendMessage("§cTeam size must be at least 1!");
                return;
            }
            plugin.tvtManager.setTeamSize(size);
            sender.sendMessage("§a§lTvT §8» §7Team size changed to: §e" + size + "v" + size);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cPlease enter a valid number!");
        }
    }

    // /tvtadmin team <blue|red> level set <nomer>
    // /tvtadmin team <blue|red> add|remove <pamiarsa>
    private void handleTeamCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: §f/tvtadmin team <blue|red> level set <number>");
            sender.sendMessage("§cUsage: §f/tvtadmin team <blue|red> add|remove <player>");
            return;
        }

        String team = args[1].toUpperCase();
        if (!team.equals("BLUE") && !team.equals("RED")) {
            sender.sendMessage("§cInvalid team '" + args[1] + "'. Use §fblue§c or §fred§c.");
            return;
        }

        String action = args[2].toLowerCase();

        switch (action) {
            case "level":
                handleTeamLevelCommand(sender, args, team);
                break;
            case "add":
            case "remove":
                handleTeamMembershipCommand(sender, args, team, action);
                break;
            default:
                sender.sendMessage("§cUnknown team action '" + action + "'. Use §flevel§c, §fadd§c or §fremove§c.");
        }
    }

    private void handleTeamLevelCommand(CommandSender sender, String[] args, String team) {
        if (args.length < 5 || !args[3].equalsIgnoreCase("set")) {
            sender.sendMessage("§cUsage: §f/tvtadmin team <blue|red> level set <number>");
            return;
        }

        int level = parseInt(sender, args[4]);
        if (level == Integer.MIN_VALUE) return;

        if (plugin.tvtManager == null) {
            sender.sendMessage("§cTvT is not currently available.");
            return;
        }

        plugin.tvtManager.setTeamLevel(team, level);
        sender.sendMessage("§a§lTvT ADMIN §8» §7Set §f" + team + "§7's team level to §f" + level + "§7.");
    }

    private void handleTeamMembershipCommand(CommandSender sender, String[] args, String team, String action) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: §f/tvtadmin team <blue|red> " + action + " <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[3]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[3] + "' is not online.");
            return;
        }

        if (plugin.tvtNexusManager == null) {
            sender.sendMessage("§cTvT is not currently available.");
            return;
        }

        if (action.equals("add")) {
            plugin.tvtNexusManager.setPlayerTeam(target, team);
            sender.sendMessage("§a§lTvT ADMIN §8» §7Added §f" + target.getName() + "§7 to team §f" + team + "§7.");
        } else {
            plugin.tvtNexusManager.setPlayerTeam(target, null);
            sender.sendMessage("§a§lTvT ADMIN §8» §7Removed §f" + target.getName() + "§7 from their team.");
        }
    }

    private void handleEmeraldsCommand(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[2].equalsIgnoreCase("set")) {
            sender.sendMessage("§cUsage: §f/tvtadmin emeralds <player> set <number>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[1] + "' is not online.");
            return;
        }

        int amount = parseInt(sender, args[3]);
        if (amount == Integer.MIN_VALUE) return;

        if (plugin.tvtShopManager == null) {
            sender.sendMessage("§cTvT is not currently available.");
            return;
        }

        plugin.tvtShopManager.setEmeralds(target, amount);
        sender.sendMessage("§a§lTvT ADMIN §8» §7Set §f" + target.getName() + "§7's Emeralds to §a" + amount + "§7.");
    }

    private int parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c'" + raw + "' is not a valid number.");
            return Integer.MIN_VALUE;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m----------§r §6§lTvT Admin Commands §8§m----------");
        sender.sendMessage("§e/tvtadmin help §7- Shows this list of commands.");
        sender.sendMessage("§e/tvtadmin queue <player|all> §7- Forces a player or all players into queue.");
        sender.sendMessage("§e/tvtadmin setsize <amount> §7- Sets max players per team.");
        sender.sendMessage("§e/tvtadmin end §7- Forces the match to end immediately.");
        sender.sendMessage("§e/tvtadmin team <blue|red> level set <number> §7- Sets a team's level.");
        sender.sendMessage("§e/tvtadmin team <blue|red> add <player> §7- Adds a player to a team.");
        sender.sendMessage("§e/tvtadmin team <blue|red> remove <player> §7- Removes a player from a team.");
        sender.sendMessage("§e/tvtadmin emeralds <player> set <number> §7- Sets a player's Emeralds.");
        sender.sendMessage("§8§m------------------------------------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) return new ArrayList<>();

        if (args.length == 1) {
            return filter(Arrays.asList("help", "queue", "team", "emeralds", "end", "forceend", "setsize"), args[0]);
        }

        if (args[0].equalsIgnoreCase("queue")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("all");
                options.addAll(onlinePlayerNames(args[1]));
                return filter(options, args[1]);
            }
        }

        if (args[0].equalsIgnoreCase("setsize")) {
            if (args.length == 2) return Arrays.asList("1", "2", "3", "5");
        }

        if (args[0].equalsIgnoreCase("team")) {
            if (args.length == 2) {
                return filter(Arrays.asList("blue", "red"), args[1]);
            }
            if (args.length == 3) {
                return filter(Arrays.asList("level", "add", "remove"), args[2]);
            }
            if (args.length == 4) {
                if (args[2].equalsIgnoreCase("level")) {
                    return filter(Arrays.asList("set"), args[3]);
                }
                if (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove")) {
                    return onlinePlayerNames(args[3]);
                }
            }
            return new ArrayList<>();
        }

        if (args[0].equalsIgnoreCase("emeralds")) {
            if (args.length == 2) {
                return onlinePlayerNames(args[1]);
            }
            if (args.length == 3) {
                return filter(Arrays.asList("set"), args[2]);
            }
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    private List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}