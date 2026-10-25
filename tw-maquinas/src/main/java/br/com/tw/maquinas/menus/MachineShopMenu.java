package br.com.tw.maquinas.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWConfirmationMenu;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.maquinas.TWMaquinasPlugin;
import br.com.tw.maquinas.managers.MachineManager;
import br.com.tw.maquinas.models.MachineType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class MachineShopMenu implements TWMenu {

    private final Inventory inventory;
    private final Player player;
    private final TWUser user;
    private final MachineManager manager;
    private final Map<Integer, MachineType> slotMap = new HashMap<>();
    private int page;

    private static final int[] MACHINE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public MachineShopMenu(Player player) {
        this.player = player;
        this.user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        this.manager = TWMaquinasPlugin.getInstance().getMachineManager();
        this.inventory = Bukkit.createInventory(this, 54, ChatColor.DARK_GRAY + "Loja de Máquinas");
        setupItems();
    }

    private void setupItems() {
        slotMap.clear();
        inventory.clear();
        MachineMenuStyle.frame(inventory, Material.CYAN_STAINED_GLASS_PANE);

        ItemStack header = MachineMenuStyle.button(Material.EMERALD,
                "§b§lTW NETWORK §8» §fFábrica de Máquinas",
                "§7Saldo disponível: §a$" + TWFormat.formatarDinheiro(user == null ? 0.0 : user.getBalance()),
                "§7Máquinas no terreno: §e" + manager.countMachinesInPlotAt(player.getLocation())
                        + "§7/§e" + manager.getMaxMachinesPerPlot(),
                "§8Produção pausada quando o terreno descarrega.");
        inventory.setItem(4, header);

        List<MachineType> types = new ArrayList<>(manager.getMachineTypes().values());
        int pageCount = Math.max(1, (types.size() + MACHINE_SLOTS.length - 1) / MACHINE_SLOTS.length);
        if (page >= pageCount) page = pageCount - 1;
        int start = page * MACHINE_SLOTS.length;
        int end = Math.min(types.size(), start + MACHINE_SLOTS.length);

        for (int idx = start; idx < end; idx++) {
            MachineType type = types.get(idx);
            int slot = MACHINE_SLOTS[idx - start];
            slotMap.put(slot, type);

            boolean unlocked = user != null && manager.hasRequiredRank(user.getRankId(), user.getPrestigeId(), type.getRequiredRank());
            List<String> lore = Arrays.asList(
                    "§8━━━━━━━━━━━━━━━━",
                    "§7Rank necessário: §f" + type.getRequiredRank(),
                    "§7Preço por máquina: §a$" + TWFormat.formatarDinheiro(type.getBuyCost()),
                    "§7Produção: §b$" + TWFormat.formatarDinheiro(type.getValuePerCycle())
                            + " §7a cada §f" + type.getCycleSeconds() + "s",
                    "",
                    unlocked ? "§eClique esquerdo §8» §fComprar 1" : "§cRank insuficiente",
                    unlocked ? "§6Clique direito §8» §fComprar 10" : "",
                    unlocked ? "§bShift + clique §8» §fComprar até 64" : "",
                    "§8━━━━━━━━━━━━━━━━"
            );
            inventory.setItem(slot, MachineMenuStyle.button(type.getMaterial(), type.getDisplayName(), lore));
        }

        double baseFuelPrice = getBaseFuelPrice();
        long baseFuelUnits = getBaseFuelUnits();
        inventory.setItem(47, createFuelButton("§e§lCombustível I", baseFuelUnits, baseFuelPrice));
        inventory.setItem(49, createFuelButton("§6§lCombustível II", fuelBundleUnits(baseFuelUnits, 10),
                baseFuelPrice * 10.0));
        inventory.setItem(51, createFuelButton("§c§lCombustível III", fuelBundleUnits(baseFuelUnits, 100),
                baseFuelPrice * 100.0));
        inventory.setItem(40, MachineMenuStyle.button(Material.BOOK, "§f§lComo abastecer",
                "§7Clique com o combustível na máquina.",
                "§7Segure §eSHIFT §7para usar o stack inteiro."));

        if (pageCount > 1) {
            inventory.setItem(45, MachineMenuStyle.button(Material.ARROW, "§ePágina anterior"));
            inventory.setItem(53, MachineMenuStyle.button(Material.ARROW, "§ePróxima página"));
            inventory.setItem(48, MachineMenuStyle.button(Material.PAPER,
                    "§fPágina §e" + (page + 1) + "§7/§e" + pageCount));
        } else {
            inventory.setItem(48, MachineMenuStyle.button(Material.PAPER, "§f§lLoja de máquinas",
                    "§7Escolha uma máquina para ver seu custo e produção."));
        }
    }

    private ItemStack createFuelButton(String name, long units, double price) {
        return MachineMenuStyle.button(Material.BLAZE_POWDER, name,
                "§7Unidades: §b" + units + " ciclos",
                "§7Preço: §a$" + TWFormat.formatarDinheiro(price),
                "",
                "§eClique para adquirir");
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (user == null) return;
        int slot = event.getRawSlot();

        if (slot == 45 && page > 0) {
            page--;
            setupItems();
            return;
        }
        if (slot == 53) {
            int pageCount = Math.max(1,
                    (manager.getMachineTypes().size() + MACHINE_SLOTS.length - 1) / MACHINE_SLOTS.length);
            if (page + 1 < pageCount) {
                page++;
                setupItems();
            }
            return;
        }

        double baseFuelPrice = getBaseFuelPrice();
        long baseFuelUnits = getBaseFuelUnits();
        if (slot == 47) buyFuel(baseFuelUnits, baseFuelPrice);
        if (slot == 49) buyFuel(fuelBundleUnits(baseFuelUnits, 10), baseFuelPrice * 10.0);
        if (slot == 51) buyFuel(fuelBundleUnits(baseFuelUnits, 100), baseFuelPrice * 100.0);

        MachineType type = slotMap.get(slot);
        if (type != null) {
            if (!manager.hasRequiredRank(user.getRankId(), user.getPrestigeId(), type.getRequiredRank())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(ChatColor.RED + "Precisas do rank " + type.getRequiredRank() + " para comprar esta máquina!");
                return;
            }

            long amount = 1;
            if (event.isShiftClick()) {
                amount = type.getBuyCost() <= 0.0 ? 64L
                        : Math.min(64L, Math.max(1L, (long) Math.floor(user.getBalance() / type.getBuyCost())));
            } else if (event.isRightClick()) {
                amount = 10;
            }

            final long purchaseAmount = amount;
            double totalCost = type.getBuyCost() * purchaseAmount;
            if (!Double.isFinite(totalCost)) {
                player.sendMessage(ChatColor.RED + "O valor desta compra excede o limite permitido.");
                return;
            }
            confirmIfExpensive(totalCost, "Comprar " + purchaseAmount + "x " + type.getDisplayName(),
                    () -> purchaseMachine(type, purchaseAmount, totalCost));
        }
    }

    private void buyFuel(long units, double price) {
        if (units < 1L || !Double.isFinite(price) || price < 0.0) {
            player.sendMessage(ChatColor.RED + "O pacote de combustível está configurado incorretamente.");
            return;
        }
        confirmIfExpensive(price, "Comprar " + units + " unidades de combustível",
                () -> purchaseFuel(units, price));
    }

    private void purchaseMachine(MachineType type, long amount, double totalCost) {
        if (user.removeBalance(totalCost)) {
            if (!persistBalanceChange(totalCost)) return;
            if (!giveItem(manager.createMachineItem(type, amount))) {
                user.addBalance(totalCost);
                if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
                    TWMaquinasPlugin.getInstance().getLogger().severe(
                            "Falha ao persistir o reembolso da compra de máquina de " + player.getUniqueId() + ".");
                }
                player.sendMessage(ChatColor.RED + "O inventário encheu durante a confirmação; a compra foi cancelada e o saldo devolvido.");
                return;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            player.sendMessage(ChatColor.GREEN + "Compraste " + amount + "x " + type.getDisplayName()
                    + ChatColor.GREEN + " por $" + TWFormat.formatarDinheiro(totalCost) + "!");
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(ChatColor.RED + "Faltam $" + TWFormat.formatarDinheiro(totalCost - user.getBalance())
                    + " para concluir a compra.");
        }
    }

    private void purchaseFuel(long units, double price) {
        if (user.removeBalance(price)) {
            if (!persistBalanceChange(price)) return;
            if (!giveItem(manager.createFuelItem(units))) {
                user.addBalance(price);
                if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
                    TWMaquinasPlugin.getInstance().getLogger().severe(
                            "Falha ao persistir o reembolso de combustível de " + player.getUniqueId() + ".");
                }
                player.sendMessage(ChatColor.RED + "O inventário encheu durante a confirmação; a compra foi cancelada e o saldo devolvido.");
                return;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            player.sendMessage(ChatColor.GREEN + "Compraste " + units + "x ciclos de Combustível!");
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(ChatColor.RED + "Não tens saldo suficiente!");
        }
    }

    private void confirmIfExpensive(double price, String description, Runnable purchase) {
        double threshold = TWMaquinasPlugin.getInstance().getConfig().getDouble("confirmation.price-threshold", 250000.0);
        if (!Double.isFinite(threshold) || threshold < 0.0) {
            throw new IllegalStateException("Valor inválido em confirmation.price-threshold.");
        }
        if (price >= threshold) {
            new TWConfirmationMenu(player, description + " por $" + TWFormat.formatarDinheiro(price) + "?",
                    purchase).open();
        } else {
            purchase.run();
        }
    }

    private boolean persistBalanceChange(double amount) {
        if (TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) return true;
        user.addBalance(amount);
        TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
        player.sendMessage(ChatColor.RED + "A compra foi cancelada porque não foi possível salvar o saldo.");
        return false;
    }

    private double getBaseFuelPrice() {
        double price = TWMaquinasPlugin.getInstance().getConfig()
                .getDouble("combustivel.preco-money", 5000.0);
        if (!Double.isFinite(price) || price < 0.0 || !Double.isFinite(price * 100.0)) {
            throw new IllegalStateException("combustivel.preco-money precisa ser finito e não negativo.");
        }
        return price;
    }

    private long getBaseFuelUnits() {
        long units = TWMaquinasPlugin.getInstance().getConfig()
                .getLong("combustivel.unidades-por-compra", 100L);
        if (units < 1L || units > 1_000_000_000L) {
            throw new IllegalStateException("combustivel.unidades-por-compra precisa estar entre 1 e 1000000000.");
        }
        return units;
    }

    private long fuelBundleUnits(long baseUnits, int multiplier) {
        return Math.multiplyExact(baseUnits, multiplier);
    }

    private boolean giveItem(ItemStack item) {
        ItemStack[] previousContents = snapshotInventoryContents();
        java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        if (leftovers.isEmpty()) return true;
        player.getInventory().setContents(previousContents);
        return false;
    }

    private ItemStack[] snapshotInventoryContents() {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null) contents[slot] = contents[slot].clone();
        }
        return contents;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }
}