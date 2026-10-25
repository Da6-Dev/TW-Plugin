package br.com.tw.minas;

import br.com.tw.minas.commands.MinaCommand;
import br.com.tw.minas.listeners.MineProtectionListener;
import br.com.tw.minas.listeners.MineracaoListener;
import br.com.tw.minas.managers.CartinhasManager;
import br.com.tw.minas.managers.MinePickaxeManager;
import br.com.tw.minas.managers.MineEventManager;
import br.com.tw.minas.integrations.RealMinesBridge;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TWMinasPlugin extends JavaPlugin implements Listener {

    private static TWMinasPlugin instance;
    private CartinhasManager cartinhasManager;
    private MineEventManager mineEventManager;
    private MinePickaxeManager minePickaxeManager;
    private RealMinesBridge realMinesBridge;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        org.bukkit.plugin.Plugin realMines = getServer().getPluginManager().getPlugin("RealMines");
        if (realMines != null && realMines.isEnabled()) {
            try {
                realMinesBridge = new RealMinesBridge(realMines);
            } catch (ReflectiveOperationException e) {
                getLogger().log(java.util.logging.Level.SEVERE,
                        "RealMines foi detetado, mas a integração com a API carregada não pôde ser iniciada.", e);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        this.cartinhasManager = new CartinhasManager();
        this.minePickaxeManager = new MinePickaxeManager(this);
        this.mineEventManager = new MineEventManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new MineracaoListener(), this);
        getServer().getPluginManager().registerEvents(new MineProtectionListener(), this);
        getCommand("mina").setExecutor(new MinaCommand());
        World mineWorld = getServer().getWorld(br.com.tw.core.TWCorePlugin.getInstance().getMineWorldName());
        if (mineWorld != null) configureMineWorld(mineWorld);

        getLogger().info("[TW-Minas] Motor de mineracao, Cartinhas (Missoes) e Eventos carregados com sucesso!");
    }

    @Override
    public void onDisable() {
        if (mineEventManager != null) {
            mineEventManager.cleanupAll();
        }
    }

    public static TWMinasPlugin getInstance() { return instance; }
    public CartinhasManager getCartinhasManager() { return cartinhasManager; }
    public MineEventManager getMineEventManager() { return mineEventManager; }
    public MinePickaxeManager getMinePickaxeManager() { return minePickaxeManager; }
    public RealMinesBridge getRealMinesBridge() { return realMinesBridge; }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (br.com.tw.core.TWCorePlugin.getInstance().isMineWorld(event.getWorld().getName())) {
            configureMineWorld(event.getWorld());
        }
    }

    private void configureMineWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
    }
}