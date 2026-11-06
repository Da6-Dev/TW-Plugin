package br.com.tw.farms.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWMenuFrame;
import br.com.tw.farms.TWFarmsPlugin;
import br.com.tw.farms.managers.FarmManager;
import br.com.tw.farms.models.FarmStorage;
import br.com.tw.farms.models.FarmType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class FarmStorageMenu implements TWMenu {
    private final Inventory inventory;
    private final Player player;
    private final FarmManager manager;
    private final Map<Integer, FarmStorage> slots = new HashMap<>();
    private final FarmStorage selectedSilo;
    private final FarmType selectedType;
    private final int[] storageSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};

    public FarmStorageMenu(Player player, FarmManager manager) {
        this.player = player;
        this.manager = manager;
        this.selectedSilo = null;
        this.selectedType = null;
        inventory = Bukkit.createInventory(this, 54, TWMenuFrame.PREFIX_TITULO + "CENTRAL AGRÍCOLA");
        setup();
    }

    private FarmStorageMenu(Player player, FarmManager manager, FarmStorage silo, FarmType type) {
        this.player = player;
        this.manager = manager;
        this.selectedSilo = silo;
        this.selectedType = type;
        inventory = Bukkit.createInventory(this, 27, TWMenuFrame.PREFIX_TITULO + "GESTÃO DA SAFRA");
        setup();
    }

    private void setup() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack pane = TWMenuFrame.stainedGlassPane(13);
            inventory.setItem(slot, pane);
        }
        if (selectedSilo != null && selectedType != null) {
            setupSelectedSilo();
            return;
        }
        slots.clear();
        int index = 0;
        for (FarmType type : manager.getFarmTypes().values()) {
            if (index >= storageSlots.length) break;
            FarmStorage silo = manager.getStorage(player.getUniqueId(), type.getId());
            int slot = storageSlots[index++];
            slots.put(slot, silo);
            ItemStack item = new ItemStack(type.getProduct());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));
            meta.setLore(Arrays.asList("§7Colheita: §e" + silo.getStoredTotal(),
                    "§7Normal: §f" + silo.getStored(), "§6Dourada: §e" + silo.getStoredGolden(),
                    "§7Total histórico: §f" + silo.getTotalHarvested(),
                    "§7Auto-Sell: " + (silo.isAutoSell() ? "§aLigado"
                            : silo.isAutoSellUnlocked() ? "§cDesligado" : "§cBloqueado"), "",
                    "§7Booster de crescimento: "
                            + (silo.getGrowthBoostEndTime() > System.currentTimeMillis() / 1000L
                            ? "§bx" + silo.getGrowthMultiplier() + " por "
                            + (silo.getGrowthBoostEndTime() - System.currentTimeMillis() / 1000L) / 60 + " min"
                            : "§cInativo"),
                    "§eClique para gerir esta cultura",
                    silo.isAutoSellUnlocked() ? "" : "§7Desbloqueio: §b"
                            + TWFarmsPlugin.getInstance().getConfig().getLong("upgrades.auto-sell-token-cost", 5000L)
                            + " Tokens"));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
        inventory.setItem(40, make(Material.EMERALD, "§aVender toda a safra",
                "§7Vende os silos de culturas com stock."));
        inventory.setItem(42, make(Material.HOPPER, "§bDevolver itens ao armazém",
                "§7Guarda novamente os itens que",
                "§7recolheste anteriormente do silo."));
        inventory.setItem(44, make(Material.WHEAT, "§eLoja de plantações", "§7Comprar sementes e boosters."));
        inventory.setItem(45, TWMenuFrame.backButton());
        inventory.setItem(49, TWMenuFrame.closeButton());
        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    private void setupSelectedSilo() {
        long now = System.currentTimeMillis() / 1000L;
        long seconds = Math.max(0, selectedSilo.getGrowthBoostEndTime() - now);
        ItemStack crop = new ItemStack(selectedType.getProduct());
        ItemMeta cropMeta = crop.getItemMeta();
        cropMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', selectedType.getDisplayName()));
        cropMeta.setLore(Arrays.asList("§7No armazém: §e" + selectedSilo.getStoredTotal(),
                "§7Normal: §f" + selectedSilo.getStored(),
                "§6Dourada: §e" + selectedSilo.getStoredGolden(),
                "§7Total colhido: §f" + selectedSilo.getTotalHarvested(),
                "§7Total vendido: §f" + selectedSilo.getTotalSold()));
        crop.setItemMeta(cropMeta);
        inventory.setItem(13, crop);
        inventory.setItem(10, make(Material.EMERALD, "§aVender safra",
                "§7Vende toda a colheita desta cultura."));
        inventory.setItem(12, make(Material.CHEST, "§eRecolher safra",
                "§7Retira itens para o inventário."));
        inventory.setItem(14, make(selectedSilo.isAutoSell() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                selectedSilo.isAutoSell() ? "§aAuto-Sell ligado" : "§cAuto-Sell desligado",
                "§7Clique para " + (selectedSilo.isAutoSell() ? "desligar" : "ligar") + " Auto-Sell.",
                selectedSilo.isAutoSellUnlocked() ? "" : "§7Desbloqueio: §b"
                        + TWFarmsPlugin.getInstance().getConfig().getLong("upgrades.auto-sell-token-cost", 5000L)
                        + " Tokens"));
        inventory.setItem(16, make(Material.SUGAR, "§bBooster de crescimento",
                seconds > 0 ? "§7Ativo: §ex" + selectedSilo.getGrowthMultiplier()
                        + " §7por mais §f" + seconds / 60 + " min"
                        : "§7Nenhum booster ativo.",
                "§7A duração não acumula."));
        inventory.setItem(22, make(Material.HOPPER, "§6Devolver itens recolhidos",
                "§7Devolve itens desta cultura",
                "§7ao respetivo silo."));
        inventory.setItem(18, make(Material.ARROW, "§eVoltar ao armazém"));
    }

    private ItemStack make(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        FarmStorage silo = slots.get(slot);
        if (silo != null) {
            FarmType type = manager.getFarmTypes().get(silo.getTypeId());
            if (type != null) new FarmStorageMenu(player, manager, silo, type).open();
            return;
        }
        if (selectedSilo != null) {
            handleSelectedSiloClick(slot);
            return;
        }
        if (slot == 40) {
            for (FarmStorage item : manager.getPlayerStorage(player.getUniqueId())) {
                if (item.getStoredTotal() > 0) manager.sellPhysicalStorage(player, item);
            }
            setup();
        } else if (slot == 42) {
            manager.depositWithdrawnItems(player, null);
            setup();
        } else if (slot == 44) {
            new FarmShopMenu(player, manager).open();
        }
    }

    private void handleSelectedSiloClick(int slot) {
        if (slot == 10) {
            manager.sellPhysicalStorage(player, selectedSilo);
            setup();
        } else if (slot == 12) {
            manager.withdrawPhysicalStorage(player, selectedSilo);
            setup();
        } else if (slot == 14) {
            toggleAutoSell();
            setup();
        } else if (slot == 22) {
            manager.depositWithdrawnItems(player, selectedSilo);
            setup();
        } else if (slot == 18) {
            new FarmStorageMenu(player, manager).open();
        }
    }

    private void toggleAutoSell() {
        if (selectedSilo.isAutoSell()) {
            selectedSilo.setAutoSell(false);
            manager.saveStorageAsync(selectedSilo);
            player.sendMessage(ChatColor.YELLOW + "Auto-Sell desligado para esta cultura.");
            return;
        }
        if (!selectedSilo.isAutoSellUnlocked()) {
            TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                player.sendMessage(ChatColor.RED + "Os teus dados ainda estão a ser carregados.");
                return;
            }
            long cost = TWFarmsPlugin.getInstance().getConfig().getLong("upgrades.auto-sell-token-cost", 5000L);
            if (!user.removeTokens(cost)) {
                player.sendMessage(ChatColor.RED + "Precisas de " + cost + " Tokens para desbloquear o Auto-Sell.");
                return;
            }
            selectedSilo.setAutoSellUnlocked(true);
            TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
        }
        selectedSilo.setAutoSell(true);
        manager.saveStorageAsync(selectedSilo);
        player.sendMessage(ChatColor.GREEN + "Auto-Sell ligado para esta cultura.");
    }

    public void open() {
        if (TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId()) == null) {
            player.sendMessage(ChatColor.RED + "Os teus dados ainda estão a ser carregados.");
            return;
        }
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
