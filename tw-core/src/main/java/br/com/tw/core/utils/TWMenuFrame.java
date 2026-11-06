package br.com.tw.core.utils;

import br.com.tw.core.TWCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public final class TWMenuFrame {

    public static final String PREFIX_TITULO = "§8➔ §b§l";

    private static final Material[] STAINED_GLASS_PANES = {
            Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE
    };

    private TWMenuFrame() {
    }

    public static ItemStack stainedGlassPane(int legacyColor) {
        if (legacyColor < 0 || legacyColor >= STAINED_GLASS_PANES.length) {
            throw new IllegalArgumentException("Cor inválida para vidro: " + legacyColor);
        }
        ItemStack pane = new ItemStack(STAINED_GLASS_PANES[legacyColor]);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e➔ §lVoltar ao Menu");
            meta.setLore(Arrays.asList(
                    "§7Retorne ao menu principal",
                    "§7do servidor.",
                    "",
                    "§e▶ Clique para voltar"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c➔ §lFechar");
            meta.setLore(Arrays.asList(
                    "§7Encerre a visualização desta interface",
                    "§7e retorne à sua jogatina.",
                    "",
                    "§c▶ Clique para fechar"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void applyDesignSystemBorders(Inventory inventory) {
        int size = inventory.getSize();
        ItemStack blackPane = stainedGlassPane(15); // BLACK_STAINED_GLASS_PANE

        if (size == 54) {
            int[] borderSlots = {
                    0, 1, 2, 3, 5, 6, 7, 8,
                    9, 17, 18, 26, 27, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44,
                    46, 47, 48, 50, 51, 52
            };
            for (int s : borderSlots) {
                if (inventory.getItem(s) == null) {
                    inventory.setItem(s, blackPane);
                }
            }
            if (inventory.getItem(45) == null) inventory.setItem(45, backButton());
            if (inventory.getItem(49) == null) inventory.setItem(49, closeButton());
        } else if (size == 27) {
            int[] borderSlots = {
                    0, 1, 2, 3, 5, 6, 7, 8,
                    9, 17,
                    19, 20, 21, 23, 24, 25
            };
            for (int s : borderSlots) {
                if (inventory.getItem(s) == null) {
                    inventory.setItem(s, blackPane);
                }
            }
            if (inventory.getItem(18) == null) inventory.setItem(18, backButton());
            if (inventory.getItem(22) == null) inventory.setItem(22, closeButton());
        }
    }

    public static boolean handleNavigationClick(Player player, int slot, int inventorySize) {
        if (inventorySize == 54) {
            if (slot == 45) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(TWCorePlugin.getInstance(), () -> player.performCommand("menu"));
                return true;
            } else if (slot == 49) {
                player.closeInventory();
                return true;
            }
        } else if (inventorySize == 27) {
            if (slot == 18) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(TWCorePlugin.getInstance(), () -> player.performCommand("menu"));
                return true;
            } else if (slot == 22) {
                player.closeInventory();
                return true;
            }
        }
        return false;
    }

    public static void apply(Inventory inventory) {
        applyDesignSystemBorders(inventory);
    }
}
