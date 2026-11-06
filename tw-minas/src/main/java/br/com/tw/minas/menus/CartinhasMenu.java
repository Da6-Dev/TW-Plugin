package br.com.tw.minas.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWMenuFrame;
import br.com.tw.minas.TWMinasPlugin;
import br.com.tw.minas.managers.CartinhasManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class CartinhasMenu implements TWMenu {

    private final Inventory inventory;
    private final Player player;
    private final TWUser user;

    public CartinhasMenu(Player player) {
        this.player = player;
        this.user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        this.inventory = Bukkit.createInventory(this, 27, TWMenuFrame.PREFIX_TITULO + "MISSÕES DE MINERAÇÃO");
        setupItems();
    }

    private void setupItems() {
        inventory.clear();

        // Slot 4: Cabeçalho explicativo
        inventory.setItem(4, createItem(Material.BOOK, "§b§lCARTINHAS DE MISSÕES",
                "§7Cumpra as missões diárias nas minas para",
                "§7resgatar Tokens e Dinheiro diretamente na sua conta.",
                "",
                "§7Missões Ativas: §a3 Cartinhas",
                "§7Troca: §e50 Tokens (Botão Direito)",
                "",
                "§8TW Network • Mineração"));

        if (user != null) {
            List<CartinhasManager.MissionSlot> missions = TWMinasPlugin.getInstance().getCartinhasManager().getMissions(user);
            int[] slots = {11, 13, 15};
            String[] cardNames = {
                    "§aCartinha do Duende §7(Comum)",
                    "§bCartinha do Boneco de Neve §9(Rara)",
                    "§eCartinha do Papai Noel §6(Lendária)"
            };

            for (int i = 0; i < missions.size() && i < 3; i++) {
                CartinhasManager.MissionSlot m = missions.get(i);
                boolean done = m.isCompleted();

                ItemStack paper = new ItemStack(done ? Material.MAP : Material.PAPER);
                ItemMeta meta = paper.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(cardNames[i]);

                    meta.setLore(Arrays.asList(
                            "§7Missão: §f" + m.type.getTitle(),
                            "§7Objetivo: §e" + m.type.getDescription(m.target),
                            "",
                            "§7Progresso: " + (done ? "§a§lCONCLUÍDA!" : "§f" + m.progress + " §7/ §f" + m.target + " " + buildBar(m.progress, m.target)),
                            "§7Recompensas: §b+" + TWFormat.formatarDinheiro(m.rewardTokens) + " Tokens §8| §2+$" + TWFormat.formatarDinheiro(m.rewardMoney),
                            "",
                            done ? "§a▶ Clique para resgatar recompensa" : "§e▶ Botão Direito: sortear outra (50 Tokens)"
                    ));

                    if (done) {
                        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }

                    paper.setItemMeta(meta);
                }
                inventory.setItem(slots[i], paper);
            }
        }

        // Botões Universais (Slot 18 Voltar para a Central de Minas, Slot 22 Fechar)
        inventory.setItem(18, createItem(Material.ARROW, "§e➔ §lVoltar à Central",
                "§7Retorne para a Central de Mineração.",
                "",
                "§e▶ Clique para voltar"));
        inventory.setItem(22, TWMenuFrame.closeButton());

        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    private String buildBar(long current, long max) {
        int bars = (int) ((double) current / Math.max(1, max) * 10);
        StringBuilder sb = new StringBuilder("§8[§a");
        for (int i = 0; i < 10; i++) {
            if (i == bars) sb.append("§7");
            sb.append("■");
        }
        return sb.append("§8]").toString();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (user == null) return;
        int slot = event.getSlot();

        if (slot == 18) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            new MinaMainMenu(player).open();
            return;
        }

        if (slot == 22) {
            player.closeInventory();
            return;
        }

        int missionIdx = (slot == 11) ? 0 : (slot == 13) ? 1 : (slot == 15) ? 2 : -1;
        if (missionIdx != -1) {
            boolean updated = TWMinasPlugin.getInstance().getCartinhasManager()
                    .claimOrReroll(player, user, missionIdx, event.isRightClick());
            if (updated) {
                setupItems();
            }
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
