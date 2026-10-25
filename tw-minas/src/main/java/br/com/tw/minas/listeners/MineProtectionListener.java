package br.com.tw.minas.listeners;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.minas.TWMinasPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

public class MineProtectionListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isMine(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickaxeDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isMinePickaxe(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        player.sendMessage("§cNão podes dropar a picareta na mina!");
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player) || !isMine((Player) entity)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isMine(player)) return;

        if (event instanceof InventoryCreativeEvent) {
            event.setCancelled(true);
            return;
        }

        boolean clickedPlayerInventory = event.getClickedInventory() == player.getInventory();
        ItemStack hotbarItem = event.getHotbarButton() < 0
                ? null
                : player.getInventory().getItem(event.getHotbarButton());
        boolean swapsOffhandPickaxe = event.getClick() == ClickType.SWAP_OFFHAND
                && (isMinePickaxe(player.getInventory().getItemInMainHand())
                || isMinePickaxe(player.getInventory().getItemInOffHand()));
        if (isMinePickaxe(event.getCurrentItem())
                || isMinePickaxe(event.getCursor())
                || isMinePickaxe(hotbarItem)
                || swapsOffhandPickaxe) {
            event.setCancelled(true);
            player.sendMessage("§cNão podes mover a picareta na mina!");
            return;
        }

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }
        if (!clickedPlayerInventory && hasItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (clickedPlayerInventory && hasItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick() && !clickedPlayerInventory && hasItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isMine(player)) return;

        boolean touchesPlayerInventory = event.getRawSlots().stream()
                .anyMatch(slot -> slot >= event.getView().getTopInventory().getSize());
        if (isMinePickaxe(event.getOldCursor())) {
            event.setCancelled(true);
            player.sendMessage("§cNão podes mover a picareta na mina!");
        } else if (hasItem(event.getOldCursor()) && touchesPlayerInventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickaxeHandSwap(PlayerSwapHandItemsEvent event) {
        if (!isMine(event.getPlayer())) return;
        if (!isMinePickaxe(event.getMainHandItem()) && !isMinePickaxe(event.getOffHandItem())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cNão podes mover a picareta na mina!");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (isMine(event.getPlayer())) schedulePickaxe(event.getPlayer(), 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && TWCorePlugin.getInstance().isMineWorld(event.getTo().getWorld().getName())) {
            schedulePickaxe(event.getPlayer(), 0);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isMine(event.getPlayer())) schedulePickaxe(event.getPlayer(), 0);
    }

    private boolean isMine(Player player) {
        return TWCorePlugin.getInstance().isMineWorld(player.getWorld().getName());
    }

    private boolean isMinePickaxe(ItemStack item) {
        return TWMinasPlugin.getInstance().getMinePickaxeManager().isMinePickaxe(item);
    }

    private boolean hasItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private void schedulePickaxe(Player player, int attempts) {
        Bukkit.getScheduler().runTaskLater(TWMinasPlugin.getInstance(), () -> {
            if (!player.isOnline() || !isMine(player)) return;
            TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                TWMinasPlugin.getInstance().getMinePickaxeManager().ensurePickaxe(player, user);
            } else if (attempts < 60) {
                schedulePickaxe(player, attempts + 1);
            } else {
                TWMinasPlugin.getInstance().getLogger().warning(
                        "Os dados do jogador " + player.getName() + " não carregaram; não foi possível entregar a picareta.");
                player.sendMessage("§cNão foi possível carregar a tua picareta. Contacta a equipa.");
            }
        }, attempts == 0 ? 1L : 20L);
    }
}
