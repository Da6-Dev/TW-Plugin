package br.com.tw.maquinas.menus;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class MachineMenuStyle {

    private MachineMenuStyle() {
    }

    static void frame(Inventory inventory, Material accent) {
        ItemStack background = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background.clone());
        }

        int rows = inventory.getSize() / 9;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                inventory.setItem(slot, pane(accent, " "));
            }
        }
    }

    static ItemStack button(Material material, String title, String... lore) {
        return button(material, title, Arrays.asList(lore));
    }

    static ItemStack button(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore == null ? Collections.emptyList() : lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material, String title) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        item.setItemMeta(meta);
        return item;
    }
}
