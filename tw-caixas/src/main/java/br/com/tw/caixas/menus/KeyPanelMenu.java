package br.com.tw.caixas.menus;

import br.com.tw.caixas.TWCaixasPlugin;
import br.com.tw.caixas.data.CrateAccount;
import br.com.tw.caixas.managers.CrateOpenings;
import br.com.tw.caixas.managers.CrateService;
import br.com.tw.caixas.model.CrateDefinition;
import br.com.tw.caixas.model.CrateType;
import br.com.tw.caixas.model.PityProgress;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWMenuFrame;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class KeyPanelMenu implements TWMenu {
    private final TWCaixasPlugin plugin;
    private final CrateService service;
    private final CrateOpenings openings;
    private final CrateAccount account;
    private final Inventory inventory;

    private KeyPanelMenu(TWCaixasPlugin plugin, CrateService service, CrateOpenings openings,
                         Player player, CrateAccount account) {
        this.plugin = plugin;
        this.service = service;
        this.openings = openings;
        this.account = account;
        inventory = Bukkit.createInventory(this, 54, TWMenuFrame.PREFIX_TITULO + "CHAVES & CAIXAS VIRTUAIS");
        fillBackground();
        for (CrateType type : CrateType.values()) {
            inventory.setItem(type.getMenuSlot(), crateItem(type));
        }
        inventory.setItem(31, forgeItem());
        inventory.setItem(4, infoItem());
        TWMenuFrame.applyDesignSystemBorders(inventory);
        inventory.setItem(45, TWMenuFrame.backButton());
        inventory.setItem(49, TWMenuFrame.closeButton());
    }

    public static void open(TWCaixasPlugin plugin, CrateService service, CrateOpenings openings, Player player) {
        service.loadAccount(player.getUniqueId(), account ->
                player.openInventory(new KeyPanelMenu(plugin, service, openings, player, account).inventory),
                () -> player.sendMessage("§cNão foi possível carregar o teu painel de chaves."));
    }

    @Override
    public Inventory getInventory() { return inventory; }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == 45 || slot == 49) {
            TWMenuFrame.handleNavigationClick(player, slot, 54);
            return;
        }
        if (slot == 31) {
            player.openInventory(new KeyForgeMenu(plugin, service, player, account).getInventory());
            return;
        }
        for (CrateType type : CrateType.values()) {
            if (event.getRawSlot() != type.getMenuSlot()) continue;
            if (event.isRightClick()) {
                player.openInventory(new CrateBulkMenu(service, openings, player, account, type).getInventory());
            } else if (event.isLeftClick()) {
                openings.openManual(player, type);
            }
            return;
        }
    }

    private ItemStack crateItem(CrateType type) {
        CrateDefinition definition = service.getCatalog().get(type);
        ItemStack item = new ItemStack(definition.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(definition.getDisplayName());
        int pity = account.getPity(type);
        int remaining = PityProgress.openingsUntilGuaranteed(pity, service.getCatalog().getPityThreshold());
        List<String> lore = new ArrayList<>();
        lore.add("§7As tuas chaves: §e" + account.getKeys(type) + "x");
        lore.add("§7Sorte garantida em: §b" + remaining + " aberturas");
        lore.add("");
        lore.add("§a[Esquerdo] §7Sortear 1 recompensa");
        lore.add("§c[Direito] §7Sortear várias recompensas");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack forgeItem() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§lForja de Chaves");
        meta.setLore(Arrays.asList("§7Funde chaves inferiores em chaves superiores.",
                "§eClica para abrir a forja."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bComo funciona");
        meta.setLore(Arrays.asList("§7As chaves e o pity são virtuais e guardados na conta.",
                "§7Cada caixa sorteia aleatoriamente uma recompensa.",
                "§7Na abertura em massa, o pity avança à metade da velocidade."));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBackground() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack pane = TWMenuFrame.stainedGlassPane((slot % 2 == 0) ? 15 : 7);
            inventory.setItem(slot, pane);
        }
    }
}
