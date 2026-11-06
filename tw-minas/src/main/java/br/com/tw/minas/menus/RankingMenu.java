package br.com.tw.minas.menus;

import br.com.tw.core.TWCorePlugin;
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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.Map;

public class RankingMenu implements TWMenu {
    private final Inventory inventory;
    private final Player player;

    public RankingMenu(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, TWMenuFrame.PREFIX_TITULO + "RANKING DE MINERADORES");

        // Coloca os itens de "A carregar..."
        setupLoadingItems();

        // Puxa os dados reais de forma assíncrona
        loadRankingAsync();
    }

    private void setupLoadingItems() {
        inventory.clear();

        // Slot 4: Cabeçalho explicativo
        inventory.setItem(4, createItem(Material.GOLD_BLOCK, "§e§lRANKING GLOBAL",
                "§7Os mineradores mais dedicados e ativos da rede.",
                "",
                "§7Classificação: §fTop 3 Mineradores",
                "§7Critério: §bBlocos Quebrados",
                "",
                "§8TW Network • Mineração"));

        inventory.setItem(11, createLoadingSkull("§e§l1º Lugar"));
        inventory.setItem(13, createLoadingSkull("§f§l2º Lugar"));
        inventory.setItem(15, createLoadingSkull("§6§l3º Lugar"));

        // Botões Universais (Slot 18 Voltar para a Central de Minas, Slot 22 Fechar)
        inventory.setItem(18, createItem(Material.ARROW, "§e➔ §lVoltar à Central",
                "§7Retorne para a Central de Mineração.",
                "",
                "§e▶ Clique para voltar"));
        inventory.setItem(22, TWMenuFrame.closeButton());

        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    private void loadRankingAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(TWMinasPlugin.getInstance(), () -> {
            try {
                Map<String, Long> topMiners = TWCorePlugin.getInstance().getUserManager().getTopMiners(3);
                Bukkit.getScheduler().runTask(TWMinasPlugin.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    int[] slots = {11, 13, 15};
                    String[] titulos = {"§e§l1º O Rei da Mina", "§f§l2º Prata", "§6§l3º Bronze"};

                    int i = 0;
                    for (Map.Entry<String, Long> entry : topMiners.entrySet()) {
                        if (i >= 3) break;
                        String nome = entry.getKey();
                        long blocos = entry.getValue();
                        inventory.setItem(slots[i], getPlayerSkull(nome, titulos[i],
                                "§7Classificação no Rank de Mineração.",
                                "",
                                "§7Jogador: §f" + nome,
                                "§7Blocos Quebrados: §b" + String.format("%,d", blocos),
                                "",
                                "§8TW Network • Top Mineradores"));
                        i++;
                    }
                });
            } catch (IllegalStateException e) {
                TWMinasPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                        "Falha ao consultar o ranking de mineradores.", e);
                Bukkit.getScheduler().runTask(TWMinasPlugin.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "Não foi possível carregar o ranking agora.");
                    }
                });
            }
        });
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 18) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            new MinaMainMenu(player).open();
            return;
        }
        if (slot == 22) {
            player.closeInventory();
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }

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

    private ItemStack createLoadingSkull(String titulo) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(titulo);
            meta.setLore(Arrays.asList(
                    "§7A consultar o Banco de Dados...",
                    "",
                    "§7Aguarde alguns instantes...",
                    "",
                    "§8TW Network • Mineração"
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack getPlayerSkull(String playerName, String titulo, String... lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwner(playerName);
            meta.setDisplayName(titulo + " §7- §a" + playerName);
            meta.setLore(Arrays.asList(lore));
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
