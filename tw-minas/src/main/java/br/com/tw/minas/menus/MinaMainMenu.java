package br.com.tw.minas.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWMenuFrame;
import br.com.tw.minas.TWMinasPlugin;
import br.com.tw.minas.managers.MineEventManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

public class MinaMainMenu implements TWMenu {

    private static final List<String> MINE_PRIORITY = Arrays.asList(
            "krampus",
            "yeti",
            "grinch",
            "vip_festivo",
            "vip_nevasca",
            "vip_polar",
            "noel",
            "estrela",
            "quebranozes",
            "treno",
            "rena",
            "duende",
            "biscoito",
            "sino",
            "neve",
            "carvao"
    );

    private final Inventory inventory;
    private final Player player;

    public MinaMainMenu(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, TWMenuFrame.PREFIX_TITULO + "CENTRAL DE MINAS");
        setupItems(player);
    }

    private void setupItems(Player player) {
        MineEventManager events = TWMinasPlugin.getInstance().getMineEventManager();
        String statusHappyHour = events.isHappyHourActive()
                ? "§a§lATIVO (2.0x Ganhos!)"
                : "§e" + events.getCommunityProgress() + " §7/ §e" + events.getCommunityGoal() + " blocos";

        inventory.setItem(4, createItem(Material.BEACON, "§b§lMeta Comunitária (Happy Hour)",
                "§7Progresso Global: " + statusHappyHour));

        inventory.setItem(11, createItem(Material.MAP, "§d§lCartinhas de Missões",
                "§7Cumpre missões aleatórias na mina por grandes prémios!"));
        inventory.setItem(12, createItem(Material.CHEST, "§6§lCatálogo de Minas",
                "§7Explore todas as minas do servidor."));
        inventory.setItem(13, createItem(Material.DIAMOND_PICKAXE, "§b§lIr Minerar",
                "§7Teleporte instantâneo para a sua mina."));
        inventory.setItem(14, createItem(Material.ANVIL, "§c§lOficina dos Duendes",
                "§7Evolua a sua picareta."));
        inventory.setItem(15, createItem(Material.GOLD_INGOT, "§e§lRanking",
                "§7Veja os melhores jogadores do servidor."));
        inventory.setItem(26, getPlayerStatsItem(player));
        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 13) {
            player.closeInventory();
            teleportToBestMine();
        } else if (slot == 11) {
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.5f);
            new CartinhasMenu(player).open();
        } else if (slot == 12) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 2f);
            new CatalogoMenu(player).open();
        } else if (slot == 14) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
            new OficinaMenu(player).open();
        } else if (slot == 15) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            new RankingMenu(player).open();
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }

    private ItemStack createItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getPlayerStatsItem(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwnerProfile(player.getPlayerProfile());
        meta.setDisplayName(ChatColor.YELLOW + "§lEstatísticas Pessoais");

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String formatadoMina = TWFormat.formatarDinheiro(user.getDinheiroMina());
            meta.setLore(Arrays.asList(
                    "§7Nível da Picareta: §a" + user.getPickaxeLevel(),
                    "§7Blocos Quebrados: §b" + String.format("%,d", user.getBlocosQuebrados()),
                    "§7Dinheiro Gerado: §2$" + formatadoMina
            ));
        }

        skull.setItemMeta(meta);
        return skull;
    }

    private void teleportToBestMine() {
        if (!Bukkit.getPluginManager().isPluginEnabled("RealMines")) {
            player.sendMessage(ChatColor.RED + "O sistema de minas está indisponível. Avise a equipe.");
            return;
        }
        if (!player.hasPermission("realmines.tp")) {
            player.sendMessage(ChatColor.RED + "Você não tem permissão para se teleportar às minas.");
            return;
        }

        for (String mine : MINE_PRIORITY) {
            if (!player.hasPermission("realmines." + mine + ".break")) continue;

            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            player.sendMessage(ChatColor.AQUA + "A teleportar para a tua melhor mina...");
            if (!Bukkit.dispatchCommand(player, "mine tp " + mine)) {
                player.sendMessage(ChatColor.RED + "Não foi possível executar o teleporte para a mina "
                        + mine + ". Avise a equipe.");
            }
            return;
        }

        player.sendMessage(ChatColor.RED + "Você não tem acesso a nenhuma mina. Avise a equipe.");
    }
}