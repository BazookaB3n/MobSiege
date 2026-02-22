package org.mobSiege;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.event.entity.EntitySpawnEvent;

public class SlimeManager implements Listener {

    private final String SLIME_NAME = ChatColor.GREEN + "Cloning Slime";
    private final String EGG_NAME = ChatColor.AQUA + "Cloning Slime Egg";

    // Configurable settings
    private final int MAX_SLIMES_PER_WORLD = 5;
    private final long COOLDOWN_MS = 1000; // 1000 milliseconds = 1 seconds
    private final NamespacedKey cooldownKey;

    // Constructor to get the Plugin instance (needed for the NamespacedKey)
    public SlimeManager(Plugin plugin) {
        this.cooldownKey = new NamespacedKey(plugin, "clone_cooldown");
    }

    @EventHandler
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SLIME_SPAWN_EGG) return;

        if (item.getItemMeta() != null && EGG_NAME.equals(item.getItemMeta().getDisplayName())) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();

            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team playerTeam = board.getEntryTeam(player.getName());

            spawnCustomSlime(spawnLoc, playerTeam);
            spawnCustomSlime(spawnLoc, playerTeam);
        }
    }

    @EventHandler
    public void onSlimeHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && event.getDamager() instanceof Slime slime) {

            if (SLIME_NAME.equals(slime.getCustomName())) {

                // 1. Check the Cooldown
                PersistentDataContainer pdc = slime.getPersistentDataContainer();
                if (pdc.has(cooldownKey, PersistentDataType.LONG)) {
                    long readyTime = pdc.get(cooldownKey, PersistentDataType.LONG);

                    // If the current time hasn't passed the ready time, stop here.
                    if (System.currentTimeMillis() < readyTime) {
                        return;
                    }
                }

                // 2. Check the Max Limit
                int currentCustomSlimes = 0;
                for (Slime worldSlime : slime.getWorld().getEntitiesByClass(Slime.class)) {
                    if (SLIME_NAME.equals(worldSlime.getCustomName())) {
                        currentCustomSlimes++;
                    }
                }

                // If we hit the limit, stop here.
                if (currentCustomSlimes >= MAX_SLIMES_PER_WORLD) {
                    return;
                }

                // 3. Reset the cooldown for the attacking slime so it can't clone again instantly
                pdc.set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + COOLDOWN_MS);

                // 4. Clone the slime
                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                Team slimeTeam = board.getEntryTeam(slime.getUniqueId().toString());
                spawnCustomSlime(slime.getLocation(), slimeTeam);
            }
        }
    }

    // Spawns a new slime from scratch (used by the egg and cloning)
    private void spawnCustomSlime(Location loc, Team team) {
        Slime slime = (Slime) loc.getWorld().spawnEntity(loc, EntityType.SLIME);
        convertToCustomSlime(slime, team);
    }

    // Applies our custom stats to ANY existing slime
    private void convertToCustomSlime(Slime slime, Team team) {
        slime.setSize(2);
        slime.setCustomName(SLIME_NAME);
        slime.setCustomNameVisible(true);

        if (slime.getAttribute(Attribute.MAX_HEALTH) != null) {
            slime.getAttribute(Attribute.MAX_HEALTH).setBaseValue(4.0);
        }
        slime.setHealth(4.0);

        if (team != null) {
            team.addEntry(slime.getUniqueId().toString());
        }

        // Apply the cooldown
        slime.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + COOLDOWN_MS);

        // Add the tag just in case it wasn't summoned by a datapack,
        // so datapacks can still target egg-spawned slimes!
        slime.addScoreboardTag("cloning_slime");
    }

    // Stop them from splitting into tiny slimes on death
    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        if (SLIME_NAME.equals(event.getEntity().getCustomName())) {
            event.setCancelled(true);
        }
    }

    // Stop them from targeting teammates
    @EventHandler
    public void onSlimeTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Slime slime && event.getTarget() != null) {
            if (SLIME_NAME.equals(slime.getCustomName())) {

                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                Team slimeTeam = board.getEntryTeam(slime.getUniqueId().toString());
                if (slimeTeam == null) return;

                String targetEntry = event.getTarget() instanceof Player targetPlayer ?
                        targetPlayer.getName() : event.getTarget().getUniqueId().toString();

                Team targetTeam = board.getEntryTeam(targetEntry);
                if (slimeTeam.equals(targetTeam)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    @EventHandler
    public void onSlimeSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Slime slime) {

            // Check if the datapack or command block summoned it with our special tag
            if (slime.getScoreboardTags().contains("cloning_slime")) {

                // If it already has our custom name, it was spawned by our plugin's egg/cloning,
                // so we don't need to convert it twice.
                if (SLIME_NAME.equals(slime.getCustomName())) return;

                // Convert the vanilla summoned slime into our custom one!
                convertToCustomSlime(slime, null);
            }
        }
    }
}