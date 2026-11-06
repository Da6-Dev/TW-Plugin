package br.com.tw.core.menus;

import br.com.tw.core.utils.TWMenuFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class MenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();

        // Verifica se o inventário implementa a nossa interface TWMenu
        if (inv != null && inv.getHolder() instanceof TWMenu) {
            event.setCancelled(true); // Bloqueia pegar o item

            int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= inv.getSize() || event.getCurrentItem() == null) return;

            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                if (TWMenuFrame.handleNavigationClick(player, rawSlot, inv.getSize())) {
                    return;
                }
            }

            // Passa a responsabilidade do clique para a própria classe do menu
            TWMenu menu = (TWMenu) inv.getHolder();
            menu.onClick(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder() instanceof TWMenu)) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
