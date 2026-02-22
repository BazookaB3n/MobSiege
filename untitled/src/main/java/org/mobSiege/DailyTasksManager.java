package org.mobSiege;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class DailyTasksManager implements CommandExecutor, Listener {

    private final Plugin plugin;
    private final NamespacedKey matchesKey, killsKey, flagsKey;
    private final String GUI_TITLE = ChatColor.DARK_AQUA + "Daily Tasks";

    // Timezone setup for the midnight EST reset
    private final ZoneId estZone = ZoneId.of("America/New_York");

    public DailyTasksManager(Plugin plugin) {
        this.plugin = plugin;
        this.matchesKey = new NamespacedKey(plugin, "task_matches_date");
        this.killsKey = new NamespacedKey(plugin, "task_kills_date");
        this.flagsKey = new NamespacedKey(plugin, "task_flags_date");
    }

    // --- 1. THE COMMAND (/opentasks) ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can view tasks.");
            return true;
        }

        openTasksGui(player);
        return true;
    }

    private void openTasksGui(Player player) {
        Inventory gui = Bukkit.createInventory(player, 9, GUI_TITLE);

        // Task 1: Play 3 Matches
        gui.setItem(2, createTaskItem(player, Material.IRON_SWORD, "Play 3 Matches", "matches_played", 3, matchesKey, "coins", 150));

        // Task 2: Get 3 Kills
        gui.setItem(4, createTaskItem(player, Material.SKELETON_SKULL, "Get 3 Kills", "kills", 3, killsKey, "xp", 50));

        // Task 3: Return 1 Flag
        gui.setItem(6, createTaskItem(player, Material.BLUE_BANNER, "Return 1 Flag", "flags_returned", 1, flagsKey, "stars", 3));

        player.openInventory(gui);
    }

    // Helper method to build the GUI items dynamically
    private ItemStack createTaskItem(Player player, Material mat, String taskName, String objective, int required, NamespacedKey dateKey, String rewardObj, int rewardAmt) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int currentScore = getScore(player, objective);
        String todayEST = LocalDate.now(estZone).toString();

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        boolean claimedToday = todayEST.equals(pdc.get(dateKey, PersistentDataType.STRING));

        meta.setDisplayName(ChatColor.GOLD + taskName);
        List<String> lore = new ArrayList<>();

        if (claimedToday) {
            lore.add(ChatColor.GREEN + "Status: " + ChatColor.DARK_GREEN + "COMPLETED");
            lore.add(ChatColor.GRAY + "Come back tomorrow at 12:00 AM EST!");
        } else {
            lore.add(ChatColor.YELLOW + "Progress: " + currentScore + " / " + required);
            lore.add(ChatColor.GRAY + "Reward: " + ChatColor.AQUA + rewardAmt + " " + rewardObj.toUpperCase());

            if (currentScore >= required) {
                lore.add("");
                lore.add(ChatColor.GREEN + "► Click to Claim Reward!");
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // --- 2. THE CLICK LISTENER ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Handle clicks based on slot
        if (slot == 2) handleClaim(player, "matches_played", 3, matchesKey, "coins", 150);
        if (slot == 4) handleClaim(player, "kills", 3, killsKey, "xp", 50);
        if (slot == 6) handleClaim(player, "flags_returned", 1, flagsKey, "stars", 3);
    }

    private void handleClaim(Player player, String objective, int required, NamespacedKey dateKey, String rewardObj, int rewardAmt) {
        String todayEST = LocalDate.now(estZone).toString();
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        // Check if already claimed today
        if (todayEST.equals(pdc.get(dateKey, PersistentDataType.STRING))) {
            player.sendMessage(ChatColor.RED + "You have already completed this task today!");
            return;
        }

        int currentScore = getScore(player, objective);
        if (currentScore >= required) {
            // Give reward
            addScore(player, rewardObj, rewardAmt);

            // Stamp the player with today's date so they can't claim it again
            pdc.set(dateKey, PersistentDataType.STRING, todayEST);

            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Objective obj = board.getObjective(objective);
            obj.getScore(player.getName()).setScore(0);

            player.sendMessage(ChatColor.GREEN + "Task completed! You received " + rewardAmt + " " + rewardObj + ".");

            // Refresh the GUI to show it as completed
            openTasksGui(player);
        } else {
            player.sendMessage(ChatColor.RED + "You haven't completed this task yet!");
        }
    }

    // --- 3. DATAPACK SCOREBOARD HOOKS ---
    private int getScore(Player player, String objectiveName) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(objectiveName);
        return (obj != null) ? obj.getScore(player.getName()).getScore() : 0;
    }

    private void addScore(Player player, String objectiveName, int amount) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(objectiveName);
        if (obj != null) {
            int current = obj.getScore(player.getName()).getScore();
            obj.getScore(player.getName()).setScore(current + amount);
        } else {
            player.sendMessage(ChatColor.RED + "Error: Datapack scoreboard '" + objectiveName + "' does not exist!");
        }
    }
}