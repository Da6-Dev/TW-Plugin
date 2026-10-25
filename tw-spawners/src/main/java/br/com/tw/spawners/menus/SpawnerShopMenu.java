package br.com.tw.spawners.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWConfirmationMenu;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWMenuFrame;
import br.com.tw.spawners.TWSpawnersPlugin;
import br.com.tw.spawners.listeners.SpawnerListener;
import br.com.tw.spawners.managers.SpawnerManager;
import br.com.tw.spawners.models.SpawnerType;
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

public class SpawnerShopMenu implements TWMenu {

    private final Inventory inventory;
    private final Player player;
    private final TWUser user;
    private final SpawnerManager manager;
    private final Map<Integer, SpawnerType> slotMap = new HashMap<>();
    private static final int[] SPAWNER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private int page;

    public SpawnerShopMenu(Player player) {
        this.player = player;
        this.user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        this.manager = TWSpawnersPlugin.getInstance().getSpawnerManager();
        this.inventory = Bukkit.createInventory(this, 54, ChatColor.DARK_GRAY + "Loja de Geradores");
        setupItems();
    }

    private void setupItems() {
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, createGlass(15));
        }

        slotMap.clear();
        java.util.List<SpawnerType> types = new java.util.ArrayList<>(manager.getSpawnerTypes().values());
        int pageCount = Math.max(1, (types.size() + SPAWNER_SLOTS.length - 1) / SPAWNER_SLOTS.length);
        if (page >= pageCount) page = pageCount - 1;
        int start = page * SPAWNER_SLOTS.length;
        int end = Math.min(types.size(), start + SPAWNER_SLOTS.length);
        for (int idx = start; idx < end; idx++) {
            SpawnerType type = types.get(idx);
            int slot = SPAWNER_SLOTS[idx - start];
            slotMap.put(slot, type);

            boolean unlocked = user != null && manager.hasRequiredRank(user.getRankId(), user.getPrestigeId(), type.getRequiredRank());
            ItemStack item = new ItemStack(Material.SPAWNER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(type.getDisplayName());
            meta.setLore(Arrays.asList(
                    "§7Rank Necessário: §f" + type.getRequiredRank(),
                    "§7Preço Base: §a$" + TWFormat.formatarDinheiro(type.getBuyCost()),
                    "",
                    unlocked ? "§aBotão Esquerdo: §7Comprar 1x" : "§c§lRANK INSUFICIENTE",
                    unlocked ? "§eBotão Direito: §7Digitar Quantidade" : ""
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }

        // Boosters na Loja
        inventory.setItem(38, createBoosterButton("§bBooster Menor §7(1.5x)", 1.5, 30, 250000.0));
        inventory.setItem(40, createBoosterButton("§aBooster Médio §7(2.0x)", 2.0, 60, 1000000.0));
        inventory.setItem(42, createBoosterButton("§dBooster Divino §7(3.0x)", 3.0, 60, 5000000.0));
        inventory.setItem(4, createInfoButton(page, pageCount));
        if (page > 0) inventory.setItem(45, createNavigationButton("§ePágina anterior"));
        if (page + 1 < pageCount) inventory.setItem(53, createNavigationButton("§ePróxima página"));
        if (pageCount > 1) inventory.setItem(49, createNavigationButton("§fPágina §e" + (page + 1) + "§7/§e" + pageCount));
        TWMenuFrame.apply(inventory);
    }

    private ItemStack createInfoButton(int currentPage, int pageCount) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lTW NETWORK §8» §fLoja de Spawners");
        meta.setLore(Arrays.asList("§7Compra geradores e boosters.", "§7Stack colocado por item: até §e"
                + manager.getMaxSpawnerStack(), "§7Página §e" + (currentPage + 1) + "§7/§e" + pageCount));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationButton(String title) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBoosterButton(String name, double mult, int mins, double price) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(
                "§7Acelera um Spawner específico",
                "§7e aumenta os ganhos em §f" + mult + "x§7.",
                "§7Duração: §f" + mins + " Minutos",
                "",
                "§7Preço: §a$" + TWFormat.formatarDinheiro(price),
                "§eClica para comprar!"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlass(int color) {
        return TWMenuFrame.stainedGlassPane(color);
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
                    (manager.getSpawnerTypes().size() + SPAWNER_SLOTS.length - 1) / SPAWNER_SLOTS.length);
            if (page + 1 < pageCount) {
                page++;
                setupItems();
            }
            return;
        }

        if (slot == 38) confirmPurchase(250000.0, "Comprar booster 1.5x", () -> buyBooster(1.5, 30, 250000.0));
        if (slot == 40) confirmPurchase(1000000.0, "Comprar booster 2x", () -> buyBooster(2.0, 60, 1000000.0));
        if (slot == 42) confirmPurchase(5000000.0, "Comprar booster 3x", () -> buyBooster(3.0, 60, 5000000.0));

        SpawnerType type = slotMap.get(slot);
        if (type != null) {
            if (!manager.hasRequiredRank(user.getRankId(), user.getPrestigeId(), type.getRequiredRank())) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(ChatColor.RED + "Precisas do rank " + type.getRequiredRank() + " para comprar isto!");
                return;
            }

            if (event.isRightClick()) {
                player.closeInventory();
                SpawnerListener.pendingPurchases.put(player.getUniqueId(), type);
                player.sendMessage("§eEscreve no chat a quantidade de §f" + type.getDisplayName() + " §eque desejas comprar (ou 'cancelar' para sair).");
                return;
            }

            if (player.getInventory().firstEmpty() == -1) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(ChatColor.RED + "O teu inventário está cheio!");
                return;
            }

            double totalCost = type.getBuyCost();
            confirmPurchase(totalCost, "Comprar 1x " + type.getDisplayName(),
                    () -> purchaseSpawner(type, 1, totalCost));
        }
    }

    private void purchaseSpawner(SpawnerType type, long amount, double price) {
        if (!user.removeBalance(price)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(ChatColor.RED + "Faltam $" + TWFormat.formatarDinheiro(price - user.getBalance()) + ".");
            return;
        }
        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
            refund(price, "compra de spawner");
            player.sendMessage(ChatColor.RED + "Compra cancelada: o saldo não pôde ser persistido.");
            return;
        }
        ItemStack[] previous = snapshotInventoryContents();
        java.util.Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(manager.createSpawnerItem(type, amount));
        if (!leftovers.isEmpty()) {
            player.getInventory().setContents(previous);
            refund(price, "compra de spawner com inventário cheio");
            player.sendMessage(ChatColor.RED + "Inventário cheio; a compra foi cancelada e o saldo devolvido.");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.sendMessage(ChatColor.GREEN + "Compraste " + amount + "x " + type.getDisplayName()
                + ChatColor.GREEN + " por $" + TWFormat.formatarDinheiro(price) + "!");
    }

    private void refund(double amount, String reason) {
        user.addBalance(amount);
        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
            TWSpawnersPlugin.getInstance().getLogger().severe(
                    "Falha ao persistir o reembolso de " + reason + " para " + player.getUniqueId() + ".");
            player.sendMessage(ChatColor.RED + "O reembolso precisa de assistência da equipe; não saias do servidor.");
        }
    }

    private void confirmPurchase(double price, String description, Runnable purchase) {
        double threshold = TWSpawnersPlugin.getInstance().getConfig()
                .getDouble("confirmation.price-threshold", 250000.0);
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

    private void buyBooster(double mult, int mins, double price) {
        if (!user.removeBalance(price)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(ChatColor.RED + "Saldo insuficiente!");
            return;
        }
        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
            refund(price, "compra de booster");
            player.sendMessage(ChatColor.RED + "Compra cancelada: o saldo não pôde ser persistido.");
            return;
        }
        ItemStack[] previous = snapshotInventoryContents();
        java.util.Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(manager.createBoosterItem(mult, mins));
        if (!leftovers.isEmpty()) {
            player.getInventory().setContents(previous);
            refund(price, "compra de booster com inventário cheio");
            player.sendMessage(ChatColor.RED + "Inventário cheio; a compra foi cancelada e o saldo devolvido.");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        player.sendMessage(ChatColor.GREEN + "Booster comprado com sucesso!");
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