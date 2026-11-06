package br.com.tw.terrenos.menus;

import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWMenuFrame;
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
        this.inventory = Bukkit.createInventory(this, 27, TWMenuFrame.PREFIX_TITULO + "AGÊNCIA DE TERRENOS");
        setupItems();
    }

    private void setupItems() {
        // Topo / Informações (Slot 4)
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§b§lSEUS TERRENOS");
            infoMeta.setLore(Arrays.asList(
                    "§7Gerencie seus lotes de construção.",
                    "",
                    "§7Terrenos Atuais: §f" + terrenosPossuidos + "§7/§f" + limiteTerrenos,
                    "§7Próximo Terreno: §e$" + TWFormat.formatarDinheiro(precoTerreno),
                    "",
                    "§8TW Network • Terrenos"
            ));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(4, info);

        // Botão de Comprar Terreno (Slot 10)
        ItemStack comprar = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta comprarMeta = comprar.getItemMeta();
        if (comprarMeta != null) {
            comprarMeta.setDisplayName("§a§lComprar Novo Terreno");
            comprarMeta.setLore(Arrays.asList(
                    "§7Adquire um lote seguro no mundo de terrenos",
                    "§7para instalar suas máquinas e armazéns.",
                    "",
                    "§7Preço: §e$" + TWFormat.formatarDinheiro(precoTerreno),
                    "§7Limite: §f" + terrenosPossuidos + "§7/§f" + limiteTerrenos,
                    "",
                    "§a▶ Clique para adquirir"
            ));
            comprar.setItemMeta(comprarMeta);
        }
        inventory.setItem(10, comprar);

        // Botão de Ir para o Terreno (Slot 12)
        ItemStack teleportar = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportarMeta = teleportar.getItemMeta();
        if (teleportarMeta != null) {
            teleportarMeta.setDisplayName("§b§lIr para o meu Terreno");
            teleportarMeta.setLore(Arrays.asList(
                    "§7Teleporte instantâneo para a sua propriedade.",
                    "",
                    "§7Destino: §fTerreno Principal",
                    "§7Mundo: §aterrenos",
                    "",
                    "§b▶ Clique para viajar"
            ));
            teleportar.setItemMeta(teleportarMeta);
        }
        inventory.setItem(12, teleportar);

        // Botão de Visitar Terreno (Slot 14)
        ItemStack visitar = new ItemStack(Material.COMPASS);
        ItemMeta visitarMeta = visitar.getItemMeta();
        if (visitarMeta != null) {
            visitarMeta.setDisplayName("§e§lVisitar um Terreno");
            visitarMeta.setLore(Arrays.asList(
                    "§7Digite o nome do jogador para visitar o lote dele.",
                    "",
                    "§7Modo: §fBusca por Jogador",
                    "",
                    "§e▶ Clique para procurar"
            ));
            visitar.setItemMeta(visitarMeta);
        }
        inventory.setItem(14, visitar);

        // Botão de Gerenciamento / Comandos (Slot 16)
        ItemStack gerenciar = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta gerenciarMeta = gerenciar.getItemMeta();
        if (gerenciarMeta != null) {
            gerenciarMeta.setDisplayName("§6§lGerenciar Terreno");
            gerenciarMeta.setLore(Arrays.asList(
                    "§7Membros, permissões, união e configurações do lote.",
                    "",
                    "§7Comandos: §f/plot add, /plot trust, /plot merge",
                    "",
                    "§6▶ Clique para ver comandos"
            ));
            gerenciar.setItemMeta(gerenciarMeta);
        }
        inventory.setItem(16, gerenciar);

        // Aplica bordas e botões universais (Voltar no 18, Fechar no 22)
        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 10) { // Comprar
            player.closeInventory();
            if (TWTerrenosPlugin.getInstance().getPlotPurchaseManager() == null) {
                player.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
                return;
            }
            TWTerrenosPlugin.getInstance().getPlotPurchaseManager().purchase(player);
        }
        else if (slot == 12) { // Ir para o terreno
            player.closeInventory();
            if (TWTerrenosPlugin.getInstance().getPlotPurchaseManager() == null) {
                player.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
                return;
            }
            TWTerrenosPlugin.getInstance().getPlotPurchaseManager().teleportHome(player);
        }
        else if (slot == 14) { // Visitar
            player.closeInventory();
            if (TWTerrenosPlugin.getInstance().getPlotPurchaseManager() == null) {
                player.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
                return;
            }
            TWTerrenosPlugin.getInstance().getPlotPurchaseManager().beginVisitSearch(player);
        }
        else if (slot == 16) { // Gerenciar
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Comandos do seu terreno:");
            player.sendMessage(ChatColor.YELLOW + "/plot trust <jogador> " + ChatColor.GRAY + "- construir e interagir");
            player.sendMessage(ChatColor.YELLOW + "/plot add <jogador> " + ChatColor.GRAY + "- permissões básicas");
            player.sendMessage(ChatColor.YELLOW + "/plot remove <jogador> " + ChatColor.GRAY + "- remover acesso");
            player.sendMessage(ChatColor.YELLOW + "/plot merge " + ChatColor.GRAY + "- unir lotes adjacentes seus");
            player.sendMessage(ChatColor.YELLOW + "/plot set alias <nome> " + ChatColor.GRAY + "- definir nome curto");
            player.sendMessage(ChatColor.YELLOW + "/plot set desc <texto> " + ChatColor.GRAY + "- definir descrição");
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
