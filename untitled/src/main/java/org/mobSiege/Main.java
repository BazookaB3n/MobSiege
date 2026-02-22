package org.mobSiege;

import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        // New Instance of tasksManager
        DailyTasksManager tasksManager = new DailyTasksManager(this);
        // Pass 'this' into the new SlimeManager constructor
        getServer().getPluginManager().registerEvents(new SlimeManager(this), this);
        getServer().getPluginManager().registerEvents(tasksManager, this);
        getCommand("tasks").setExecutor(tasksManager);
        getCommand("giveslimeegg").setExecutor(new GiveSlimeEggCommand());


    }

    @Override
    public void onDisable() {
        System.out.println("MobSiegeMC Shutting Down!");
    }
}
