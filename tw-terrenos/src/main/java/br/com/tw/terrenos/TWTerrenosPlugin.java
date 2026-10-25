package br.com.tw.terrenos;

import br.com.tw.terrenos.commands.TerrenoCommand;
import br.com.tw.terrenos.commands.TerrenosAdminCommand;
import br.com.tw.terrenos.listeners.PlotWorldRulesListener;
import br.com.tw.terrenos.managers.PlotPurchaseManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

public class TWTerrenosPlugin extends JavaPlugin {
    private static TWTerrenosPlugin instance;
    private PlotPurchaseManager plotPurchaseManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getCommand("terreno").setExecutor(new TerrenoCommand());
        getCommand("terrenosadmin").setExecutor(new TerrenosAdminCommand(this));

        Plugin plotSquared = getServer().getPluginManager().getPlugin("PlotSquared");
        if (plotSquared != null && plotSquared.isEnabled()) {
            new PlotWorldRulesListener(this, getConfig().getString("plot-world", "terrenos"));
            plotPurchaseManager = new PlotPurchaseManager(this);
            getLogger().info("Integração com PlotSquared ativada.");
        } else {
            getLogger().warning("PlotSquared não está carregado. Compra e teleporte de terrenos ficarão indisponíveis.");
        }
    }

    public static TWTerrenosPlugin getInstance() {
        return instance;
    }

    public PlotPurchaseManager getPlotPurchaseManager() {
        return plotPurchaseManager;
    }
}