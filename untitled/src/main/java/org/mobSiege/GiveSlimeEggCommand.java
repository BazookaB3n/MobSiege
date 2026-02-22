package org.mobSiege;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GiveSlimeEggCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Ensure the command was sent by an in-game player, not the server console
        if (sender instanceof Player player) {

            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "You do not have permission to get the cloning slime egg.");
                return true;
            }

            // Create the spawn egg
            ItemStack egg = new ItemStack(Material.SLIME_SPAWN_EGG);
            ItemMeta meta = egg.getItemMeta();

            if (meta != null) {
                // This exact name is what our SlimeManager checks for
                meta.setDisplayName(ChatColor.AQUA + "Cloning Slime Egg");
                egg.setItemMeta(meta);
            }

            // Give the egg to the player
            player.getInventory().addItem(egg);
            player.sendMessage(ChatColor.GREEN + "You received a Cloning Slime Egg!");

            return true;
        }

        sender.sendMessage(ChatColor.RED + "Only players can use this command.");
        return true;
    }
}