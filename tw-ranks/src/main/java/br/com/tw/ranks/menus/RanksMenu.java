package br.com.tw.ranks.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWMenuFrame;
import br.com.tw.ranks.TWRanksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class RanksMenu implements TWMenu {
    private final Inventory inventory;
    private final Player player;
    private final TWUser user;
    private final List<String> rankOrder;

    public RanksMenu(Player player) {
        this.player = player;
        this.user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        this.rankOrder = new java.util.ArrayList<>();
        for (String rank : TWCorePlugin.getInstance().getRankHierarchy()) {
            if (!rank.equalsIgnoreCase("MAX")
                    && TWRanksPlugin.getInstance().getRanksConfig().contains("ranks." + rank)) {
                rankOrder.add(rank);
            }
        }
        this.inventory = Bukkit.createInventory(this, 54, TWMenuFrame.PREFIX_TITULO + "TRILHA DE RANKS");
        setupItems();
    }

    private void setupItems() {
        if (user == null) return;
        String currentRank = user.getRankId();
        int currentIndex = currentRank.equalsIgnoreCase("MAX") ? 999 : rankOrder.indexOf(currentRank.toUpperCase());

        // Cabeçalho de Perfil no Slot 4
        inventory.setItem(4, createItem(Material.NETHER_STAR, "§b§lSEU PROGRESSO",
                "§7Acompanhe o seu avanço na trilha de ranks.",
                "",
                "§7Rank Atual: §a" + currentRank,
                "§7Saldo: §e$" + TWFormat.formatarDinheiro(user.getBalance()),
                "",
                "§8TW Network • Ranks"));

        // Desenha a trilha nos slots das linhas 2 e 3
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        for (int i = 0; i < rankOrder.size() && i < slots.length; i++) {
            String rankName = rankOrder.get(i);
            int slot = slots[i];

            if (i < currentIndex) {
                inventory.setItem(slot, createItem(Material.LIME_STAINED_GLASS_PANE, "§a§l" + rankName + " §a✔",
                        "§7Rank já desbloqueado anteriormente.",
                        "",
                        "§7Status: §aConcluído",
                        "",
                        "§a▶ Nível superado"));
            } else if (i == currentIndex) {
                inventory.setItem(slot, createItem(Material.EMERALD_BLOCK, "§e§l" + rankName + " §6★",
                        "§7Este é o teu rank atual em atividade.",
                        "",
                        "§7Status: §eEm Progresso",
                        "",
                        "§e▶ Rank Atual"));
            } else {
                double cost = TWRanksPlugin.getInstance().getRanksConfig().getDouble("ranks." + rankOrder.get(i - 1) + ".cost");
                inventory.setItem(slot, createItem(Material.RED_STAINED_GLASS_PANE, "§c§l" + rankName + " §c🔒",
                        "§7Requer completar os ranks anteriores.",
                        "",
                        "§7Custo: §e$" + String.format("%,.2f", cost),
                        "§7Status: §cBloqueado",
                        "",
                        "§c▶ Requisito não atingido"));
            }
        }

        // Botões de Ação no centro da Linha 4
        inventory.setItem(31, createItem(Material.EXPERIENCE_BOTTLE, "§a§lEVOLUIR (RANKUP)",
                "§7Evolui para o próximo rank da lista.",
                "",
                "§7Rank Atual: §a" + currentRank,
                "§7Saldo: §e$" + TWFormat.formatarDinheiro(user.getBalance()),
                "",
                "§a▶ Clique para subir de rank"));

        inventory.setItem(32, createItem(Material.GOLD_BLOCK, "§e§lEVOLUÇÃO MAX",
                "§7Compra o máximo de ranks possíveis",
                "§7de acordo com o teu saldo disponível.",
                "",
                "§7Modo: §6Automático",
                "",
                "§e▶ Clique para dar rankup max"));

        // Molduras e navegação padronizadas (Design System)
        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 31) {
            player.closeInventory();
            player.performCommand(user.getRankId().equalsIgnoreCase("MAX") ? "prestigio" : "rankup");
        } else if (slot == 32) {
            player.closeInventory();
            player.performCommand("rankupmax");
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public void open() { player.openInventory(inventory); }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
