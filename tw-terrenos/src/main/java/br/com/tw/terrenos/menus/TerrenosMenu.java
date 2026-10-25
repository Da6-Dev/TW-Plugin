package br.com.tw.terrenos.menus;

import br.com.tw.core.menus.TWMenu;
import br.com.tw.terrenos.TWTerrenosPlugin;
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

public class TerrenosMenu implements TWMenu {
    private final Inventory inventory;
    private final Player player;
    private final double precoTerreno;
    private final int terrenosPossuidos;
    private final int limiteTerrenos;

    public TerrenosMenu(Player player) {
        this.player = player;
        br.com.tw.terrenos.managers.PlotPurchaseManager manager =
                TWTerrenosPlugin.getInstance().getPlotPurchaseManager();
        this.precoTerreno = manager == null ? 50000.0 : manager.getNextPrice(player);
        this.terrenosPossuidos = manager == null ? 0 : manager.getOwnedPlotCount(player);
        this.limiteTerrenos = manager == null ? 1 : manager.getPlotLimit(player);
        this.inventory = Bukkit.createInventory(this, 27, ChatColor.GREEN + "§lAgência de Terrenos");
        setupItems();
    }

    private void setupItems() {
        // Botão de Comprar Terreno
        ItemStack comprar = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta comprarMeta = comprar.getItemMeta();
        comprarMeta.setDisplayName("§a§lComprar Novo Terreno");
        comprarMeta.setLore(Arrays.asList(
                "§7Adquire um espaço seguro para",
                "§7construir a tua base e máquinas.",
                "",
                "§7Terrenos: §f" + terrenosPossuidos + "§7/§f" + limiteTerrenos,
                "§7Preço: §2$" + br.com.tw.core.utils.TWFormat.formatarDinheiro(precoTerreno),
                "§8O preço dobra a cada nova compra.",
                "",
                "§eClica para adquirir!"
        ));
        comprar.setItemMeta(comprarMeta);
        inventory.setItem(11, comprar);

        ItemStack visitar = new ItemStack(Material.COMPASS);
        ItemMeta visitarMeta = visitar.getItemMeta();
        visitarMeta.setDisplayName("§e§lVisitar um Terreno");
        visitarMeta.setLore(Arrays.asList(
                "§7Digite o nome do jogador",
                "§7que deseja visitar.",
                "",
                "§eClica para procurar!"
        ));
        visitar.setItemMeta(visitarMeta);
        inventory.setItem(13, visitar);

        // Botão de Ir para o Terreno
        ItemStack teleportar = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportarMeta = teleportar.getItemMeta();
        teleportarMeta.setDisplayName("§b§lIr para o meu Terreno");
        teleportarMeta.setLore(Arrays.asList(
                "§7Teleporte instantâneo para a",
                "§7tua propriedade principal.",
                "",
                "§eClica para viajar!"
        ));
        teleportar.setItemMeta(teleportarMeta);
        inventory.setItem(15, teleportar);

        ItemStack gerenciar = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta gerenciarMeta = gerenciar.getItemMeta();
        gerenciarMeta.setDisplayName("§6§lGerenciar Terreno");
        gerenciarMeta.setLore(Arrays.asList(
                "§7Membros, permissões, união,",
                "§7nome, descrição e status.",
                "",
                "§eClica para ver os comandos!"
        ));
        gerenciar.setItemMeta(gerenciarMeta);
        inventory.setItem(22, gerenciar);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 11) { // Comprar
            player.closeInventory();
            if (TWTerrenosPlugin.getInstance().getPlotPurchaseManager() == null) {
                player.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
                return;
            }
            TWTerrenosPlugin.getInstance().getPlotPurchaseManager().purchase(player);
        }
        else if (slot == 15) { // Ir para o terreno
            player.closeInventory();
            if (TWTerrenosPlugin.getInstance().getPlotPurchaseManager() == null) {
                player.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
                return;
            }
            TWTerrenosPlugin.getInstance().getPlotPurchaseManager().teleportHome(player);
        }
        else if (slot == 13) {
            player.closeInventory();
            if (TWTerrenosPlugin.getInstance().getPlotPurchaseManager() == null) {
                player.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
                return;
            }
            TWTerrenosPlugin.getInstance().getPlotPurchaseManager().beginVisitSearch(player);
        }
        else if (slot == 22) {
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Comandos do seu terreno:");
            player.sendMessage(ChatColor.YELLOW + "/plot trust <jogador> " + ChatColor.GRAY + "- construir e interagir");
            player.sendMessage(ChatColor.YELLOW + "/plot add <jogador> " + ChatColor.GRAY + "- permissões básicas");
            player.sendMessage(ChatColor.YELLOW + "/plot remove <jogador> " + ChatColor.GRAY + "- remover acesso");
            player.sendMessage(ChatColor.YELLOW + "/plot merge " + ChatColor.GRAY + "- unir lotes adjacentes seus");
            player.sendMessage(ChatColor.YELLOW + "/plot set alias <nome> " + ChatColor.GRAY + "- nome curto");
            player.sendMessage(ChatColor.YELLOW + "/plot set desc <texto> " + ChatColor.GRAY + "- descrição");
            player.sendMessage(ChatColor.YELLOW + "/plot done " + ChatColor.GRAY + "- marcar como concluído");
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }
}