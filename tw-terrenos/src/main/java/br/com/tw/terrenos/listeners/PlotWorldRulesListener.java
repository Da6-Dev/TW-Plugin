package br.com.tw.terrenos.listeners;

import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlotWorldRulesListener implements Listener {
    private final JavaPlugin plugin;
    private final String plotWorld;
    private final Map<UUID, Boolean> flightBeforeEntering = new HashMap<>();

    public PlotWorldRulesListener(JavaPlugin plugin, String plotWorld) {
        this.plugin = plugin;
        this.plotWorld = plotWorld;
        for (World world : Bukkit.getWorlds()) {
            applyWorldRules(world);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyWorldRules(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        if (plotWorld.equals(event.getTo().getWorld().getName())) {
            captureFlightState(player);
            applyFlightAccess(player);
        } else if (plotWorld.equals(event.getFrom().getWorld().getName())) {
            restoreFlightState(player);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plotWorld.equals(event.getFrom().getName())) {
            restoreFlightState(player);
        }
        if (plotWorld.equals(player.getWorld().getName())) {
            captureFlightState(player);
            applyFlightAccess(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restoreFlightState(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (plotWorld.equals(player.getWorld().getName())
                && !player.hasPermission("tw-terrenos.admin")
                && !canFlyHere(player)) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(ChatColor.RED + "O voo só é permitido no seu terreno ou em um terreno que você integra.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player
                && plotWorld.equals(event.getEntity().getWorld().getName())) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)
                || !plotWorld.equals(event.getEntity().getWorld().getName())) {
            return;
        }
        switch (event.getCause()) {
            case FALL:
            case FIRE:
            case FIRE_TICK:
            case LAVA:
            case HOT_FLOOR:
                event.setCancelled(true);
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHostileMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)
                || !plotWorld.equals(event.getEntity().getWorld().getName())) {
            return;
        }
        if (event.getDamager() instanceof Monster) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Monster) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (plotWorld.equals(event.getWorld().getName()) && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (plotWorld.equals(event.getWorld().getName())) {
            event.setCancelled(true);
            event.getWorld().setTime(6000L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (plotWorld.equals(event.getFrom().getWorld().getName())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Portais não podem ser usados no mundo de terrenos.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (plotWorld.equals(event.getBlock().getWorld().getName())
                && event.getSource().getType().name().contains("FIRE")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireIgnite(BlockIgniteEvent event) {
        if (plotWorld.equals(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plotWorld.equals(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plotWorld.equals(event.getLocation().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plotWorld.equals(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    private void applyWorldRules(World world) {
        if (!plotWorld.equals(world.getName())) {
            return;
        }
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    private void captureFlightState(Player player) {
        if (player.hasPermission("tw-terrenos.admin")) {
            return;
        }
        flightBeforeEntering.putIfAbsent(player.getUniqueId(), player.getAllowFlight());
    }

    private void restoreFlightState(Player player) {
        Boolean previousState = flightBeforeEntering.remove(player.getUniqueId());
        if (previousState != null && !player.hasPermission("tw-terrenos.admin")) {
            player.setFlying(false);
            player.setAllowFlight(previousState);
        }
    }

    private void applyFlightAccess(Player player) {
        if (player.hasPermission("tw-terrenos.admin")) {
            return;
        }
        boolean allowed = canFlyHere(player);
        player.setAllowFlight(allowed);
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
        }
    }

    private boolean canFlyHere(Player player) {
        Plot plot = PlotPlayer.from(player).getLocation().getPlot();
        return plot != null && plotWorld.equalsIgnoreCase(plot.getWorldName())
                && (plot.isOwner(player.getUniqueId()) || plot.isAdded(player.getUniqueId()));
    }
}
