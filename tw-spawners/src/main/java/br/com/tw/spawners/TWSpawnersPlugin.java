package br.com.tw.spawners;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.managers.TWScoreboardStatsProvider;
import br.com.tw.spawners.commands.SpawnersCommand;
import br.com.tw.spawners.data.SpawnerRepository;
import br.com.tw.spawners.listeners.SpawnerListener;
import br.com.tw.spawners.managers.SpawnerHologramManager;
import br.com.tw.spawners.managers.SpawnerManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class TWSpawnersPlugin extends JavaPlugin {

    private static TWSpawnersPlugin instance;
    private SpawnerManager spawnerManager;
    private SpawnerHologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();

        this.hologramManager = new SpawnerHologramManager(this);

        SpawnerRepository repo = new SpawnerRepository(TWCorePlugin.getInstance().getDatabaseManager());
        this.spawnerManager = new SpawnerManager(this, repo);
        hologramManager.startOrphanCleanup(key -> spawnerManager.getPlacedSpawners().containsKey(key));

        getServer().getServicesManager().register(TWScoreboardStatsProvider.class, player -> {
            String worldPrefix = player.getWorld().getName() + ";";
            long spawnerCount = spawnerManager.getPlacedSpawners().values().stream()
                    .filter(spawner -> spawner.getOwnerUuid().equals(player.getUniqueId())
                            && spawner.getLocationKey().startsWith(worldPrefix))
                    .mapToLong(spawner -> spawner.getStackAmount())
                    .sum();
            return Collections.singletonList("§fSpawners: §e" + spawnerCount);
        }, this, ServicePriority.Normal);

        getCommand("spawners").setExecutor(new SpawnersCommand());
        getServer().getPluginManager().registerEvents(new SpawnerListener(spawnerManager), this);

        getLogger().info("[TW-Spawners] Sistema de spawners, stacking e hologramas iniciado com sucesso!");
    }

    private void migrateConfig() {
        FileConfiguration config = getConfig();
        int configVersion = config.getInt("config-version", 0);

        if (configVersion < 3) {
            config.set("limits.max-spawn-per-cycle", null);
        }

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                throw new IllegalStateException("O recurso config.yml não foi encontrado no JAR.");
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            config.set("config-version", Math.max(configVersion, 3));
            saveConfig();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Não foi possível atualizar a configuração dos spawners.", e);
        }
    }

    @Override
    public void onDisable() {
        if (spawnerManager != null) {
            spawnerManager.saveAllSync();
        }
        if (hologramManager != null) {
            hologramManager.clearAll();
        }
    }

    public static TWSpawnersPlugin getInstance() {
        return instance;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public SpawnerHologramManager getHologramManager() {
        return hologramManager;
    }
}