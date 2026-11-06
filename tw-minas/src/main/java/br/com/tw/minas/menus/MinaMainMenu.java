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
        this.inventory = Bukkit.createInventory(this, 54, TWMenuFrame.PREFIX_TITULO + "CENTRAL DE MINAS");
        setupItems(player);
    }

    private void setupItems(Player player) {
        MineEventManager events = TWMinasPlugin.getInstance().getMineEventManager();
        String statusHappyHour = events.isHappyHourActive()
                ? "§a§lATIVO (2.0x Ganhos!)"
                : "§e" + events.getCommunityProgress() + " §7/ §e" + events.getCommunityGoal() + " blocos";

        // Slot 4: Cabeçalho com perfil e estatísticas de mineração
        inventory.setItem(4, getPlayerStatsItem(player));

        // Slot 20: Ir Minerar (Melhor mina disponível)
        inventory.setItem(20, createItem(Material.DIAMOND_PICKAXE, "§b§lIr Minerar",
                "§7Teleporte instantâneo para a sua melhor mina disponível.",
                "",
                "§7Destino: §aMelhor Rank Desbloqueado",
                "§7Status: §aDisponível",
                "",
                "§a▶ Clique para minerar"));

        // Slot 21: Catálogo de Minas
        inventory.setItem(21, createItem(Material.CHEST, "§6§lCatálogo de Minas",
                "§7Explore o catálogo completo com todas as 16 minas.",
                "",
                "§7Setores: §f10 Ranks§7, §b3 VIPs§7, §63 Prestígios",
                "§7Arena: §cMina PvP",
                "",
                "§a▶ Clique para ver o catálogo"));

        // Slot 22: Meta Comunitária
        inventory.setItem(22, createItem(Material.BEACON, "§b§lMeta Comunitária",
                "§7Quebre blocos para ativar o Happy Hour global da rede.",
                "",
                "§7Progresso Global: " + statusHappyHour,
                "§7Bônus: §a2.0x Ganhos",
                "",
                "§e▶ Minere para contribuir"));

        // Slot 23: Oficina de Picaretas
        inventory.setItem(23, createItem(Material.ANVIL, "§c§lOficina de Picaretas",
                "§7Evolua as habilidades e poderes da sua picareta.",
                "",
                "§7Habilidades: §e11 Upgrades (Broca, Laser, etc.)",
                "§7Moeda: §bTokens",
                "",
                "§a▶ Clique para abrir a oficina"));

        // Slot 24: Missões & Cartinhas
        inventory.setItem(24, createItem(Material.MAP, "§d§lMissões de Mineração",
                "§7Cumpra missões diárias para resgatar grandes recompensas.",
                "",
                "§7Missões Ativas: §a3 Cartinhas",
                "§7Recompensas: §eTokens & Money",
                "",
                "§a▶ Clique para abrir missões"));

        // Slot 31: Ranking de Mineradores
        inventory.setItem(31, createItem(Material.GOLD_BLOCK, "§e§lRanking de Mineradores",
                "§7Veja os jogadores mais dedicados da rede.",
                "",
                "§7Classificação: §fTop 3 Mineradores",
                "§7Atualização: §aTempo Real",
                "",
                "§a▶ Clique para ver o ranking"));

        // Slot 53: Atalho para o Spawn
        inventory.setItem(53, createItem(Material.ENDER_PEARL, "§b§lSpawn Principal",
                "§7Teleporte-se imediatamente de volta ao spawn.",
                "",
                "§7Mundo: §fworld",
                "§7Status: §aSeguro",
                "",
                "§a▶ Clique para ir ao spawn"));

        // Molduras e navegação padronizadas (Design System)
        // Coloca automaticamente Botão Voltar no 45 e Fechar no 49
        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 45 || slot == 49) {
            TWMenuFrame.handleNavigationClick(player, slot, 54);
            return;
        }

        if (slot == 20) {
            player.closeInventory();
            teleportToBestMine();
        } else if (slot == 21) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 2f);
            new CatalogoMenu(player).open();
        } else if (slot == 22) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        } else if (slot == 23) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
            new OficinaMenu(player).open();
        } else if (slot == 24) {
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.5f);
            new CartinhasMenu(player).open();
        } else if (slot == 31) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            new RankingMenu(player).open();
        } else if (slot == 53) {
            player.closeInventory();
            player.performCommand("spawn");
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }

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

    private ItemStack getPlayerStatsItem(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(player.getPlayerProfile());
            meta.setDisplayName(ChatColor.YELLOW + "§lEstatísticas de Mineração");

            TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String formatadoMina = TWFormat.formatarDinheiro(user.getDinheiroMina());
                meta.setLore(Arrays.asList(
                        "§7Acompanhe o seu progresso geral de mineração.",
                        "",
                        "§7Nível da Picareta: §a" + user.getPickaxeLevel(),
                        "§7Blocos Quebrados: §b" + String.format("%,d", user.getBlocosQuebrados()),
                        "§7Dinheiro Gerado: §2$" + formatadoMina,
                        "§7Tokens: §e" + TWFormat.formatarDinheiro(user.getTokens()),
                        "",
                        "§8TW Network • Mineração"
                ));
            }
            skull.setItemMeta(meta);
        }
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
