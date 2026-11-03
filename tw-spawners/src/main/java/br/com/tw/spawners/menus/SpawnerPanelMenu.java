package br.com.tw.spawners.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWMenuFrame;
import br.com.tw.economia.TWEconomiaPlugin;
import br.com.tw.spawners.TWSpawnersPlugin;
import br.com.tw.spawners.managers.SpawnerManager;
import br.com.tw.spawners.models.PlacedSpawner;
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

public class SpawnerPanelMenu implements TWMenu {

    private final Inventory inventory;
    private final Player player;
    private final PlacedSpawner spawner;
    private final SpawnerType type;
    private final SpawnerManager manager;

    public SpawnerPanelMenu(Player player, PlacedSpawner spawner) {
        this.player = player;
        this.spawner = spawner;
        this.manager = TWSpawnersPlugin.getInstance().getSpawnerManager();
        this.type = manager.getSpawnerTypes().get(spawner.getTypeId());

        this.inventory = Bukkit.createInventory(this, 54, ChatColor.DARK_GRAY + "Painel do Spawner");
        setupItems();
    }

    private void setupItems() {
        for (int i = 0; i < 54; i++) {
            ItemStack glass = TWMenuFrame.stainedGlassPane(15);
            inventory.setItem(i, glass);
        }
        TWMenuFrame.apply(inventory);

        if (type == null) return;
        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        double prestigeMult = manager.getPrestigeMultiplier(user.getPrestigeId());
        double globalBooster = TWEconomiaPlugin.getInstance().getGlobalBooster();
        double currentPayoutMoney = spawner.getStoredMoney() * prestigeMult * globalBooster;

        ItemStack info = new ItemStack(Material.SPAWNER);
        ItemMeta iMeta = info.getItemMeta();
        iMeta.setDisplayName(type.getDisplayName());
        iMeta.setLore(Arrays.asList(
                "§7Dono: §f" + spawner.getOwnerName(),
                "§7Entidade: §a" + type.getEntityType().name(),
                "§7Stack Acumulado: §e" + spawner.getStackAmount() + "x",
                "§7Abates Históricos: §c" + spawner.getTotalKills(),
                "§7Unidades neste ciclo: §b" + br.com.tw.spawners.managers.SpawnerSpawnMath.amount(
                        spawner.getStackAmount(), spawner.getAmountLevel()),
                "§7Produção física pendente: §d" + spawner.getPendingPhysicalSpawn()
        ));
        info.setItemMeta(iMeta);
        inventory.setItem(13, info);

        ItemStack mode = new ItemStack(Material.COMPARATOR);
        ItemMeta mMeta = mode.getItemMeta();
        boolean isVirtual = spawner.isVirtualMode();
        mMeta.setDisplayName("§e§lAlternar Modo");
        mMeta.setLore(Arrays.asList(
                "§7Modo Atual: " + (isVirtual ? "§d§lVIRTUAL (AUTO-KILL)" : "§a§lFÍSICO (SPAWN NORMAL)"),
                "",
                "§7Virtual: acumula recompensas no cofre.",
                "§7Físico: mobs empilhados; exige jogador perto.",
                "§7Sem jogador/capacidade, unidades ficam pendentes",
                "§7até o limite de §e" + manager.getMaxPendingPhysical() + "§7.",
                "",
                "§eClica para alternar o modo!"
        ));
        mode.setItemMeta(mMeta);
        inventory.setItem(11, mode);

        ItemStack cofre = new ItemStack(Material.GOLD_INGOT);
        ItemMeta cMeta = cofre.getItemMeta();
        cMeta.setDisplayName("§a§lCofre do Spawner");
        cMeta.setLore(Arrays.asList(
                "§7Dinheiro a Receber: §2§l$" + TWFormat.formatarDinheiro(currentPayoutMoney),
                "§7Tokens a Receber: §b§l" + TWFormat.formatarDinheiro(spawner.getStoredTokens()),
                "§7XP Oculto: §a" + TWFormat.formatarDinheiro(spawner.getStoredXp()) + " XP",
                "§7Almas Ocultas: §d" + spawner.getStoredSouls() + "x",
                "",
                "§eClica para depositar tudo na tua conta!"
        ));
        cofre.setItemMeta(cMeta);
        inventory.setItem(15, cofre);

        // UPGRADES
        long speedCost = upgradeCost(spawner.getSpeedLevel(), 1500L);
        ItemStack speedUp = new ItemStack(Material.SUGAR);
        ItemMeta sMeta = speedUp.getItemMeta();
        sMeta.setDisplayName("§b§lUpgrade: Velocidade");
        sMeta.setLore(Arrays.asList(
                "§7Reduz o tempo de spawn em §a0.5s§7.",
                "§7Nível: §f" + spawner.getSpeedLevel() + "§7/§f" + manager.getMaxSpeedLevel(),
                "§7Custo: §b" + TWFormat.formatarDinheiro(speedCost) + " Tokens",
                "",
                "§eClica para melhorar!"
        ));
        speedUp.setItemMeta(sMeta);
        inventory.setItem(29, speedUp);

        long lootCost = upgradeCost(spawner.getLootLevel(), 2000L);
        ItemStack lootUp = new ItemStack(Material.EMERALD);
        ItemMeta lMeta = lootUp.getItemMeta();
        lMeta.setDisplayName("§a§lUpgrade: Fortuna (Loot)");
        lMeta.setLore(Arrays.asList(
                "§7Aumenta o dinheiro por abate em §a+10%§7",
                "§7e a chance de tokens em §b+5%§7 por nível.",
                "§7Nível: §f" + spawner.getLootLevel() + "§7/§f" + manager.getMaxLootLevel(),
                "§7Custo: §b" + TWFormat.formatarDinheiro(lootCost) + " Tokens",
                "",
                "§eClica para melhorar!"
        ));
        lootUp.setItemMeta(lMeta);
        inventory.setItem(31, lootUp);

        long amountCost = upgradeCost(spawner.getAmountLevel(), 2500L);
        ItemStack amountUp = new ItemStack(Material.EGG);
        ItemMeta aMeta = amountUp.getItemMeta();
        aMeta.setDisplayName("§6§lUpgrade: Quantidade (+1)");
        aMeta.setLore(Arrays.asList(
                "§7Multiplica a quantidade de monstros",
                "§7que nascem por cada ciclo de spawn.",
                "§7Nível: §f" + spawner.getAmountLevel() + "§7/§f" + manager.getMaxAmountLevel(),
                "§7Custo: §b" + TWFormat.formatarDinheiro(amountCost) + " Tokens",
                "",
                "§eClica para melhorar!"
        ));
        amountUp.setItemMeta(aMeta);
        inventory.setItem(33, amountUp);

        // AUTO SELL UPGRADE
        ItemStack autoSell = new ItemStack(Material.HOPPER);
        ItemMeta asMeta = autoSell.getItemMeta();
        asMeta.setDisplayName("§b§lMódulo: Auto-Sell");
        if (spawner.isAutoSell()) {
            asMeta.setLore(Arrays.asList("§7O Auto-Sell está §a§lATIVO§7.", "§7(Dinheiro e Tokens são vendidos automaticamente)"));
        } else {
            asMeta.setLore(Arrays.asList(
                    "§7O spawner deposita Dinheiro e Tokens",
                    "§7diretamente na tua conta a cada 10s.",
                    "",
                    "§7Custo Único: §b150,000 Tokens",
                    "§eClica para ativar!"
            ));
        }
        autoSell.setItemMeta(asMeta);
        inventory.setItem(40, autoSell);

        // DESMONTAR
        ItemStack pickup = new ItemStack(Material.TNT);
        ItemMeta pMeta = pickup.getItemMeta();
        pMeta.setDisplayName("§c§lDesmontar Spawner");
        pMeta.setLore(Arrays.asList("§7Guarda o spawner no teu inventário.", "§4Clica para desmontar."));
        pickup.setItemMeta(pMeta);
        inventory.setItem(49, pickup);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!spawner.getOwnerUuid().equals(player.getUniqueId())
                && !player.hasPermission("tw-spawners.admin")) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(ChatColor.RED + "Apenas o proprietário pode gerir este spawner!");
            return;
        }

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        int slot = event.getRawSlot();

        if (slot == 11) {
            spawner.setVirtualMode(!spawner.isVirtualMode());
            manager.saveSpawnerAsync(spawner);
            TWSpawnersPlugin.getInstance().getHologramManager().updateOrCreateHologram(spawner, type);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            setupItems();
        }
        else if (slot == 15) {
            if (!spawner.getOwnerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Somente o proprietário pode recolher este cofre.");
                return;
            }
            if (spawner.getStoredMoney() <= 0 && spawner.getStoredTokens() <= 0
                    && spawner.getStoredXp() <= 0 && spawner.getStoredSouls() <= 0) return;

            double prestigeMult = manager.getPrestigeMultiplier(user.getPrestigeId());
            double globalBooster = TWEconomiaPlugin.getInstance().getGlobalBooster();
            br.com.tw.spawners.data.SpawnerPayout payout = null;
            if (spawner.getStoredMoney() > 0.0 || spawner.getStoredTokens() > 0L) {
                try {
                    payout = manager.collectStored(spawner, user, prestigeMult * globalBooster);
                } catch (IllegalStateException e) {
                    TWSpawnersPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                            "Falha ao recolher o cofre em " + spawner.getLocationKey() + ".", e);
                    player.sendMessage(ChatColor.RED + "Não foi possível recolher o cofre. Tenta novamente.");
                    return;
                }
                if (payout == null) {
                    player.sendMessage(ChatColor.RED + "O cofre foi alterado; fecha e abre o painel novamente.");
                    return;
                }
            }

            long finalXp = spawner.getStoredXp();
            long deliveredXp = Math.min(finalXp, Integer.MAX_VALUE);
            spawner.setStoredXp(finalXp - deliveredXp);
            long finalSouls = spawner.getStoredSouls();
            long soulsRemaining = finalSouls;
            if (finalSouls > 0) {
                try {
                    soulsRemaining = manager.giveSoulItems(player, finalSouls);
                } catch (ArithmeticException e) {
                    TWSpawnersPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                            "Overflow ao combinar almas antigas ao recolher o cofre "
                                    + spawner.getLocationKey() + ".", e);
                }
                spawner.setStoredSouls(soulsRemaining);
            }

            boolean soulsRemainInSilo = soulsRemaining > 0L;
            if (deliveredXp > 0 || soulsRemaining != finalSouls) manager.saveSpawnerAsync(spawner);
            TWSpawnersPlugin.getInstance().getHologramManager().updateOrCreateHologram(spawner, type);

            if (deliveredXp > 0) player.giveExp((int) deliveredXp);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            double paidMoney = payout == null ? 0.0 : payout.getMoney();
            long paidTokens = payout == null ? 0L : payout.getPaidTokens();
            long paidSouls = Math.max(0L, finalSouls - Math.min(finalSouls, soulsRemaining));
            player.sendMessage("§a✔ Cofre resgatado! §2$" + TWFormat.formatarDinheiro(paidMoney)
                    + " §8| §b" + paidTokens + " T §8| §a" + deliveredXp + " XP §8| §d" + paidSouls + " Almas");
            if (soulsRemainInSilo) {
                player.sendMessage("§eO inventário está cheio; as almas continuam guardadas no silo.");
            }
            setupItems();
        }
        else if (slot == 29) {
            upgrade(user, "speed", upgradeCost(spawner.getSpeedLevel(), 1500L));
        }
        else if (slot == 31) {
            upgrade(user, "loot", upgradeCost(spawner.getLootLevel(), 2000L));
        }
        else if (slot == 33) {
            upgrade(user, "amount", upgradeCost(spawner.getAmountLevel(), 2500L));
        }
        else if (slot == 40 && !spawner.isAutoSell()) {
            upgrade(user, "auto-sell", 150000L);
        }
        else if (slot == 49) {
            if (spawner.getStoredMoney() > 0.0 || spawner.getStoredTokens() > 0L
                    || spawner.getStoredXp() > 0L || spawner.getStoredSouls() > 0L
                    || spawner.getPendingPhysicalSpawn() > 0L) {
                player.sendMessage(ChatColor.RED + "Recolhe os recursos pendentes antes de desmontar o spawner.");
                return;
            }
            if (player.getInventory().firstEmpty() == -1) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(ChatColor.RED + "Inventário cheio!");
                return;
            }
            player.closeInventory();

            // AQUI ESTÁ O SEGREDO: Usamos o método que passa o 'spawner' para salvar os upgrades
            org.bukkit.Location loc = spawner.getBukkitLocation();
            if (loc == null) {
                player.sendMessage(ChatColor.RED + "O mundo do spawner não está carregado.");
                return;
            }
            ItemStack[] previousContents = snapshotInventoryContents();
            java.util.Map<Integer, ItemStack> leftovers = player.getInventory()
                    .addItem(manager.createSpawnerItem(spawner, type));
            if (!leftovers.isEmpty()) {
                player.getInventory().setContents(previousContents);
                player.sendMessage(ChatColor.RED + "Libera espaço no inventário e tenta novamente.");
                return;
            }
            try {
                if (!manager.removeSpawner(loc)) {
                    player.getInventory().setContents(previousContents);
                    player.sendMessage(ChatColor.RED + "O spawner já foi removido; nenhum item foi entregue.");
                    return;
                }
            } catch (IllegalStateException e) {
                player.getInventory().setContents(previousContents);
                TWSpawnersPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                        "Falha ao desmontar o spawner " + spawner.getLocationKey() + ".", e);
                player.sendMessage(ChatColor.RED + "Não foi possível desmontar o spawner. Tenta novamente.");
                return;
            }
            TWSpawnersPlugin.getInstance().getHologramManager().removeHologram(spawner.getLocationKey());
            loc.getBlock().setType(Material.AIR);

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            player.sendMessage("§eDesmontaste o spawner com sucesso! §aOs teus upgrades foram salvos no item.");
        }
    }

    private void upgrade(TWUser user, String id, long cost) {
        if (!manager.canUpgrade(spawner, id)) {
            player.sendMessage(ChatColor.RED + "Este spawner já atingiu o limite dessa melhoria.");
            return;
        }
        boolean applied;
        try {
            applied = manager.upgradeSpawner(spawner, user, id, cost);
        } catch (IllegalStateException e) {
            TWSpawnersPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "Falha ao evoluir o spawner " + spawner.getLocationKey() + ".", e);
            player.sendMessage(ChatColor.RED + "Não foi possível concluir a melhoria. Tenta novamente.");
            return;
        }
        if (!applied) {
            sendTokenError(cost);
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
        setupItems();
    }

    private long upgradeCost(int level, long multiplier) {
        return Math.multiplyExact((long) level, multiplier);
    }

    private ItemStack[] snapshotInventoryContents() {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null) contents[slot] = contents[slot].clone();
        }
        return contents;
    }

    private void sendTokenError(long cost) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        player.sendMessage(ChatColor.RED + "Não tens Tokens suficientes! Custo: " + TWFormat.formatarDinheiro(cost));
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() { player.openInventory(inventory); }
}