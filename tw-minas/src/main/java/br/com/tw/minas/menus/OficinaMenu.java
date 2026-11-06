package br.com.tw.minas.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWMenuFrame;
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

public class OficinaMenu implements TWMenu {
    private final Inventory inventory;
    private final Player player;
    private final TWUser user;
    private final Map<Integer, SkillInfo> skillsMap = new HashMap<>();

    public OficinaMenu(Player player) {
        this.player = player;
        this.user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        this.inventory = Bukkit.createInventory(this, 54, TWMenuFrame.PREFIX_TITULO + "OFICINA DE MINAS");
        initSkillsMap();
        setupItems();
    }

    private void initSkillsMap() {
        // Linha 2
        skillsMap.put(11, new SkillInfo("fortuna", 100));
        skillsMap.put(12, new SkillInfo("cacador", 150));
        skillsMap.put(13, new SkillInfo("sabedoria", 200));
        skillsMap.put(14, new SkillInfo("sorteiro", 500));
        skillsMap.put(15, new SkillInfo("jackpot", 1000));

        // Linha 3
        skillsMap.put(20, new SkillInfo("explosao", 2000));
        skillsMap.put(21, new SkillInfo("broca", 2500));
        skillsMap.put(22, new SkillInfo("laser", 3000));
        skillsMap.put(23, new SkillInfo("trator", 5000));
        skillsMap.put(24, new SkillInfo("avalanche", 10000));
        skillsMap.put(25, new SkillInfo("bateria", 1500));
    }

    private void setupItems() {
        inventory.clear();
        if (user == null) return;

        int level = user.getPickaxeLevel();
        double xp = user.getPickaxeXp();

        // Slot 4: Picareta e estatísticas
        inventory.setItem(4, createItem(Material.DIAMOND_PICKAXE, "§b§lA Tua Picareta",
                "§7Acompanhe o nível e melhorias da sua ferramenta.",
                "",
                "§7Nível Atual: §f" + level,
                "§7Progresso de XP: §e" + (int)xp + " §8/ §e" + (level * 1000),
                "§7Tokens Disponíveis: §b" + TWFormat.formatarDinheiro(user.getTokens()),
                "",
                "§8TW Network • Oficina"));

        // Habilidades Linha 2
        renderSkill(11, "fortuna", Material.GOLD_NUGGET, "§eFortuna", "Aumenta a quantidade de dinheiro obtido.");
        renderSkill(12, "cacador", Material.SNOWBALL, "§bCaçador de Flocos", "Aumenta a taxa de drop de Tokens.");
        renderSkill(13, "sabedoria", Material.EXPERIENCE_BOTTLE, "§aSabedoria", "Acelera o ganho de XP da ferramenta.");
        renderSkill(14, "sorteiro", Material.TRIPWIRE_HOOK, "§6Sorteiro", "Aumenta a chance de encontrar Chaves.");
        renderSkill(15, "jackpot", Material.EMERALD, "§a§lJackpot", "Chance de dobrar os ganhos de um bloco.");

        // Habilidades Linha 3
        renderSkill(20, "explosao", Material.TNT, "§cExplosão 3x3", "Detona os blocos ao redor em área 3x3.");
        renderSkill(21, "broca", Material.PISTON, "§7Broca Perfuradora", "Destrói uma coluna de blocos para baixo.");
        renderSkill(22, "laser", Material.BEACON, "§bLaser Concentrado", "Quebra blocos em linha reta à sua frente.");
        renderSkill(23, "trator", Material.MINECART, "§8Trator Industrial", "Limpa a camada inteira ao minerar.");
        renderSkill(24, "avalanche", Material.NETHER_STAR, "§4§lAvalanche Cósmica", "O poder lendário mais raro e potente.");
        renderSkill(25, "bateria", Material.REDSTONE, "§cBateria de Combo", "Estende a duração do multiplicador de combo.");

        // Botões Universais (Slot 45 Voltar para a Central de Minas, Slot 49 Fechar)
        inventory.setItem(45, createItem(Material.ARROW, "§e➔ §lVoltar à Central",
                "§7Retorne para a Central de Mineração.",
                "",
                "§e▶ Clique para voltar"));
        inventory.setItem(49, TWMenuFrame.closeButton());

        TWMenuFrame.applyDesignSystemBorders(inventory);
    }

    private void renderSkill(int slot, String id, Material mat, String name, String desc) {
        int lvl = user.getSkillLevel(id);
        int baseCost = skillsMap.get(slot).baseCost;
        int cost = baseCost * (lvl + 1);

        inventory.setItem(slot, createItem(mat, name,
                "§7" + desc,
                "",
                "§7Nível Atual: §f" + lvl,
                "§7Custo de Upgrade: §b" + TWFormat.formatarDinheiro(cost) + " Tokens",
                "",
                "§a▶ Clique para evoluir"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 45) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            new MinaMainMenu(player).open();
            return;
        }

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        SkillInfo info = skillsMap.get(slot);
        if (info != null) {
            int currentLvl = user.getSkillLevel(info.id);
            int cost = info.baseCost * (currentLvl + 1);

            if (user.removeTokens(cost)) {
                user.upgradeSkill(info.id);
                TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                player.sendMessage(ChatColor.GREEN + "Habilidade " + info.id.toUpperCase() + " evoluída para o nível " + (currentLvl + 1) + "!");
                setupItems();
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                long faltam = cost - user.getTokens();
                player.sendMessage(ChatColor.RED + "Não tens Tokens suficientes! Faltam " + TWFormat.formatarDinheiro(faltam) + ".");
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

    private static class SkillInfo {
        String id;
        int baseCost;
        SkillInfo(String id, int baseCost) { this.id = id; this.baseCost = baseCost; }
    }
}
