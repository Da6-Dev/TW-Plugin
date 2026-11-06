package br.com.tw.minas.menus;

import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWMenuFrame;
import br.com.tw.minas.TWMinasPlugin;
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

public class CatalogoMenu implements TWMenu {

    private final Inventory inventory;
    private final Player player;
    private final Map<Integer, WarpInfo> warpsMap = new HashMap<>();

    public CatalogoMenu(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54, TWMenuFrame.PREFIX_TITULO + "CATÁLOGO DE MINAS");

        initWarps();
        setupItems();
    }

    private void initWarps() {
        // Linha 2 e 3: Jornada Principal (10 Ranks)
        warpsMap.put(10, new WarpInfo("carvao", "realmines.carvao.break", Material.COAL_BLOCK, "§8[§fCarvão§8]"));
        warpsMap.put(11, new WarpInfo("neve", "realmines.neve.break", Material.SNOW_BLOCK, "§8[§fNeve§8]"));
        warpsMap.put(12, new WarpInfo("sino", "realmines.sino.break", Material.GOLD_BLOCK, "§8[§eSino§8]"));
        warpsMap.put(13, new WarpInfo("biscoito", "realmines.biscoito.break", Material.BROWN_TERRACOTTA, "§8[§6Biscoito§8]"));
        warpsMap.put(14, new WarpInfo("duende", "realmines.duende.break", Material.GREEN_WOOL, "§8[§aDuende§8]"));
        warpsMap.put(19, new WarpInfo("rena", "realmines.rena.break", Material.SPRUCE_LOG, "§8[§cRena§8]"));
        warpsMap.put(20, new WarpInfo("treno", "realmines.treno.break", Material.QUARTZ_BLOCK, "§8[§4Trenó§8]"));
        warpsMap.put(21, new WarpInfo("quebranozes", "realmines.quebranozes.break", Material.RED_TERRACOTTA, "§8[§5Quebra-Nozes§8]"));
        warpsMap.put(22, new WarpInfo("estrela", "realmines.estrela.break", Material.GLOWSTONE, "§8[§e§lEstrela§8]"));
        warpsMap.put(23, new WarpInfo("noel", "realmines.noel.break", Material.EMERALD_BLOCK, "§8[§c§lNoel§8]"));

        // Linha 4: VIPs (3 Minas)
        warpsMap.put(30, new WarpInfo("vip_festivo", "realmines.vip_festivo.break", Material.LIME_WOOL, "§a[VIP Festivo]"));
        warpsMap.put(31, new WarpInfo("vip_nevasca", "realmines.vip_nevasca.break", Material.LIGHT_BLUE_WOOL, "§b[VIP Nevasca]"));
        warpsMap.put(32, new WarpInfo("vip_polar", "realmines.vip_polar.break", Material.RED_WOOL, "§c[VIP Polar]"));

        // Linha 5: Prestígios (3 Minas)
        warpsMap.put(38, new WarpInfo("grinch", "realmines.grinch.break", Material.EMERALD_BLOCK, "§2Prestígio I [Grinch]"));
        warpsMap.put(39, new WarpInfo("yeti", "realmines.yeti.break", Material.DARK_PRISMARINE, "§bPrestígio II [Yeti]"));
        warpsMap.put(40, new WarpInfo("krampus", "realmines.krampus.break", Material.BEACON, "§4Prestígio III [Krampus]"));

        // PvP (Sempre desbloqueada)
        warpsMap.put(42, new WarpInfo(null, "", Material.DIAMOND_SWORD, "§c⚒ Mina PvP"));
    }

    private void setupItems() {
        // Slot 4: Cabeçalho explicativo
        inventory.setItem(4, createItem(Material.COMPASS, "§b§lSETOR DE MINAS",
                "§7Explore o catálogo completo de minas do servidor.",
                "",
                "§7Total: §f16 Minas Disponíveis",
                "§7Acesso: §eProgresso de Ranks, VIPs e Prestígios",
                "",
                "§8TW Network • Mineração"));

        // Renderiza cada mina verificando as permissões dinamicamente
        for (Map.Entry<Integer, WarpInfo> entry : warpsMap.entrySet()) {
            int slot = entry.getKey();
            WarpInfo info = entry.getValue();

            boolean unlocked = info.perm.isEmpty() || player.hasPermission(info.perm);
            if (unlocked) {
                inventory.setItem(slot, createItem(info.mat, info.name + " §a✔",
                        "§7Mina de recursos do setor " + info.name + ".",
                        "",
                        "§7Status: §aDesbloqueada",
                        "§7Acesso: §fLiberado para o seu nível",
                        "",
                        "§a▶ Clique para viajar"));
            } else {
                inventory.setItem(slot, createItem(Material.GRAY_DYE, info.name + " §c🔒",
                        "§7Mina de recursos bloqueada para o seu nível.",
                        "",
                        "§7Status: §cBloqueada",
                        "§7Requisito: §eEvolua seu Rank ou VIP",
                        "",
                        "§c▶ Requisito não atingido"));
            }
        }

        // Slot 48: Atalho para o Menu Principal
        inventory.setItem(48, createItem(Material.NETHER_STAR, "§b§lMenu Principal",
                "§7Retorne ao Hub Central de menus do servidor.",
                "",
                "§7Destino: §f/menu",
                "",
                "§b▶ Clique para ir ao menu"));

        // Botões Universais (Slot 45 Voltar para a Central de Minas, Slot 49 Fechar)
        inventory.setItem(45, createItem(Material.ARROW, "§e➔ §lVoltar à Central",
                "§7Retorne para a Central de Mineração.",
                "",
                "§e▶ Clique para voltar"));
        inventory.setItem(49, TWMenuFrame.closeButton());

        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 45) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            new MinaMainMenu(player).open();
            return;
        }

        if (slot == 48) {
            player.closeInventory();
            player.performCommand("menu");
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // Se clicou numa mina bloqueada
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.GRAY_DYE) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(ChatColor.RED + "Não tens Rank/VIP suficiente para entrar nesta mina!");
            return;
        }

        WarpInfo info = warpsMap.get(slot);
        if (info != null) {
            if (info.mineName != null) {
                if (!Bukkit.getPluginManager().isPluginEnabled("RealMines")) {
                    player.sendMessage(ChatColor.RED + "O sistema de minas está indisponível. Avise a equipe.");
                    return;
                }
                if (!player.hasPermission("realmines.tp")) {
                    player.sendMessage(ChatColor.RED + "Você não tem permissão para se teleportar às minas.");
                    return;
                }
            }

            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            player.sendMessage(ChatColor.GREEN + "⚒ A viajar para a mina " + info.name + "...");
            boolean dispatched;
            if (info.mineName == null) {
                dispatched = TWMinasPlugin.getInstance().getPvpArenaManager().enter(player);
            } else {
                dispatched = Bukkit.dispatchCommand(player, "mine tp " + info.mineName);
            }
            if (!dispatched) {
                player.sendMessage(ChatColor.RED + "Não foi possível executar o teleporte para essa mina. Avise a equipe.");
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
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

    private static class WarpInfo {
        String mineName;
        String perm;
        Material mat;
        String name;

        WarpInfo(String mineName, String perm, Material mat, String name) {
            this.mineName = mineName;
            this.perm = perm;
            this.mat = mat;
            this.name = name;
        }
    }
}
