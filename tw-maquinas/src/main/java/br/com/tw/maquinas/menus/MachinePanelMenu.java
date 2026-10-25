package br.com.tw.maquinas.menus;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWMenu;
import br.com.tw.core.menus.TWConfirmationMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.economia.TWEconomiaPlugin;
import br.com.tw.maquinas.TWMaquinasPlugin;
import br.com.tw.maquinas.managers.MachineManager;
import br.com.tw.maquinas.models.MachineType;
import br.com.tw.maquinas.models.PlacedMachine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalDouble;

public class MachinePanelMenu implements TWMenu {

    private final Inventory inventory;
    private final Player player;
    private final PlacedMachine machine;
    private final MachineType type;
    private final MachineManager manager;

    public MachinePanelMenu(Player player, PlacedMachine machine) {
        this.player = player;
        this.machine = machine;
        this.manager = TWMaquinasPlugin.getInstance().getMachineManager();
        this.type = manager.getMachineTypes().get(machine.getTypeId());

        this.inventory = Bukkit.createInventory(this, 54, ChatColor.DARK_GRAY + "TW | Painel da Máquina");
        setupItems();
    }

    private void setupItems() {
        inventory.clear();
        MachineMenuStyle.frame(inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        if (type == null) return;

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        double maxStorage = manager.getStorageCapacity(machine, type);
        boolean isOwner = machine.getOwnerUuid().equals(player.getUniqueId());
        double multiplier = 1.0;
        if (isOwner && user != null) {
            multiplier = manager.getPrestigeMultiplier(user.getPrestigeId())
                    * TWEconomiaPlugin.getInstance().getGlobalBooster();
        }

        inventory.setItem(4, MachineMenuStyle.button(Material.NETHER_STAR,
                "§b§lTW NETWORK §8» §f" + type.getDisplayName(),
                "§7Dono: §f" + machine.getOwnerName(),
                "§7Máquinas neste stack: §e" + machine.getStackAmount() + "x",
                "§7Saldo: §a$" + TWFormat.formatarDinheiro(user == null ? 0.0 : user.getBalance())));
        inventory.setItem(13, MachineMenuStyle.button(type.getMaterial(), "§f§lEstado da máquina",
                "§7Combustível: §e" + machine.getFuel() + " ciclos",
                "§7Bateria: §b" + machine.getBatteryCycles() + " ciclos",
                "§7Lubrificante: §a" + machine.getLubricantCycles() + " ciclos",
                "§7Armazenado: §a$" + TWFormat.formatarDinheiro(machine.getStoredEarnings())
                        + " §8/ §2$" + TWFormat.formatarDinheiro(maxStorage),
                "§7Produção histórica: §6$" + TWFormat.formatarDinheiro(machine.getTotalGenerated()),
                "§8Produz apenas com o chunk carregado."));

        double payout = machine.getStoredEarnings() * multiplier;
        inventory.setItem(20, isOwner
                ? MachineMenuStyle.button(Material.GOLD_INGOT, "§a§lRECOLHER PRODUÇÃO",
                        "§7Saldo guardado: §e$" + TWFormat.formatarDinheiro(machine.getStoredEarnings()),
                        "§7Multiplicador: §b" + String.format(java.util.Locale.US, "%.2f", multiplier) + "x",
                        "§7Você recebe: §2$" + TWFormat.formatarDinheiro(payout),
                        "",
                        "§eClique para recolher")
                : MachineMenuStyle.button(Material.BARRIER, "§c§lSAQUE DO DONO",
                        "§7Somente o proprietário pode receber",
                        "§7o saldo desta máquina."));

        long speedCost = upgradeCost(machine.getSpeedLevel(), 1000L);
        inventory.setItem(29, MachineMenuStyle.button(
                manager.canUpgradeSpeed(machine) ? Material.SUGAR : Material.BARRIER,
                "§b§lVelocidade §8[" + machine.getSpeedLevel() + "/" + manager.getMaxSpeedLevel(machine) + "]",
                "§7Reduz o intervalo em §a0,5s§7 por nível.",
                manager.canUpgradeSpeed(machine)
                        ? "§7Próximo nível: §f" + (machine.getSpeedLevel() + 1)
                        : "§cNível máximo atingido",
                manager.canUpgradeSpeed(machine) ? "§7Custo: §b" + speedCost + " Tokens" : "",
                "",
                manager.canUpgradeSpeed(machine) ? "§eClique para melhorar" : "§8Sem mais benefício"));

        long storageCost = upgradeCost(machine.getStorageLevel(), 800L);
        inventory.setItem(31, MachineMenuStyle.button(
                manager.canUpgradeStorage(machine) ? Material.CHEST : Material.BARRIER,
                "§6§lArmazenamento §8[" + machine.getStorageLevel() + "/" + manager.getMaxStorageLevel() + "]",
                "§7Aumenta o espaço de armazenamento.",
                manager.canUpgradeStorage(machine)
                        ? "§7Custo: §b" + storageCost + " Tokens"
                        : "§cNível máximo atingido",
                "",
                manager.canUpgradeStorage(machine) ? "§eClique para melhorar" : "§8Sem mais benefício"));

        long fuelCost = upgradeCost(machine.getFuelEfficiencyLevel(), 500L);
        inventory.setItem(33, MachineMenuStyle.button(
                manager.canUpgradeFuelEfficiency(machine) ? Material.COAL : Material.BARRIER,
                "§8§lEficiência §8[" + machine.getFuelEfficiencyLevel() + "/"
                        + manager.getMaxFuelEfficiencyLevel() + "]",
                "§7Chance de economizar combustível: §a"
                        + ((machine.getFuelEfficiencyLevel() - 1) * 5) + "%",
                manager.canUpgradeFuelEfficiency(machine)
                        ? "§7Custo: §b" + fuelCost + " Tokens"
                        : "§cNível máximo atingido",
                "",
                manager.canUpgradeFuelEfficiency(machine) ? "§eClique para melhorar" : "§8Sem mais benefício"));

        inventory.setItem(40, MachineMenuStyle.button(Material.BOOK,
                "§f§lInformações e limites",
                "§7Capacidade atual: §a$" + TWFormat.formatarDinheiro(maxStorage),
                "§7Stack máximo: §e" + machine.getStackAmount() + "x",
                "§7Recolha os lucros antes de desmontar."));

        inventory.setItem(49, MachineMenuStyle.button(Material.HOPPER, "§c§lDESMONTAR MÁQUINA",
                "§7Devolve o item com §e" + machine.getStackAmount() + "x§7.",
                "§cO saldo armazenado não é pago no desmonte.",
                "",
                "§eClique para recolher a máquina"));
    }

    private long upgradeCost(int level, long multiplier) {
        return Math.multiplyExact((long) level, multiplier);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!machine.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("tw.admin")) {
            player.sendMessage(ChatColor.RED + "Apenas o proprietário pode gerir esta máquina!");
            return;
        }

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        int slot = event.getRawSlot();

        if (slot == 20) {
            if (!machine.getOwnerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Somente o proprietário pode recolher os lucros desta máquina.");
                return;
            }
            double baseStored = machine.getStoredEarnings();
            if (baseStored <= 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(ChatColor.RED + "Esta máquina ainda não tem recursos acumulados.");
                return;
            }

            double prestigeMult = manager.getPrestigeMultiplier(user.getPrestigeId());
            double globalBooster = TWEconomiaPlugin.getInstance().getGlobalBooster();
            double finalPayout = baseStored * prestigeMult * globalBooster;
            if (!Double.isFinite(finalPayout) || finalPayout <= 0.0) {
                player.sendMessage(ChatColor.RED + "O pagamento está indisponível; nenhum saldo foi removido da máquina.");
                return;
            }

            OptionalDouble updatedBalance;
            try {
                updatedBalance = manager.collectEarnings(machine, user, finalPayout);
            } catch (IllegalStateException e) {
                TWMaquinasPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                        "Falha ao recolher a produção em " + machine.getLocationKey() + ".", e);
                player.sendMessage(ChatColor.RED + "Não foi possível recolher os lucros. Tenta novamente mais tarde.");
                return;
            }
            if (!updatedBalance.isPresent()) {
                player.sendMessage(ChatColor.RED + "O saldo desta máquina mudou; fecha e abre o painel novamente.");
                return;
            }

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            player.sendMessage("§a✔ Recolheste §2$" + TWFormat.formatarDinheiro(finalPayout) + " §ada máquina!");
            setupItems();
        }
        else if (slot == 29) {
            if (!manager.canUpgradeSpeed(machine)) {
                player.sendMessage(ChatColor.RED + "Esta máquina já atingiu a velocidade máxima útil.");
                return;
            }
            long cost = upgradeCost(machine.getSpeedLevel(), 1000L);
            performUpgrade(user, "speed", cost);
        }
        else if (slot == 31) {
            if (!manager.canUpgradeStorage(machine)) {
                player.sendMessage(ChatColor.RED + "Esta máquina já atingiu o armazenamento máximo.");
                return;
            }
            long cost = upgradeCost(machine.getStorageLevel(), 800L);
            performUpgrade(user, "storage", cost);
        }
        else if (slot == 33) {
            if (!manager.canUpgradeFuelEfficiency(machine)) {
                player.sendMessage(ChatColor.RED + "Esta máquina já atingiu a eficiência máxima.");
                return;
            }
            long cost = upgradeCost(machine.getFuelEfficiencyLevel(), 500L);
            performUpgrade(user, "fuel-efficiency", cost);
        }
        else if (slot == 49) {
            if (!machine.getOwnerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Somente o proprietário pode desmontar esta máquina.");
                return;
            }
            if (machine.getStoredEarnings() > 0.0) {
                player.sendMessage(ChatColor.RED + "Recolhe os lucros antes de desmontar; o saldo armazenado não é devolvido.");
                return;
            }
            new TWConfirmationMenu(player,
                    "Desmontar " + machine.getStackAmount() + "x " + type.getDisplayName() + "?",
                    this::dismantleMachine).open();
        }
    }

    private void dismantleMachine() {
        org.bukkit.Location loc = machine.getBukkitLocation();
        if (loc == null) {
            player.sendMessage(ChatColor.RED + "O mundo da máquina não está carregado; desmontagem cancelada.");
            return;
        }

        ItemStack returnedMachine = manager.createMachineItem(type, machine.getStackAmount());
        ItemStack[] previousContents = snapshotInventoryContents();
        java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(returnedMachine);
        if (!leftovers.isEmpty()) {
            player.getInventory().setContents(previousContents);
            player.sendMessage(ChatColor.RED + "Libera espaço no inventário para desmontar a máquina.");
            return;
        }

        boolean removed;
        try {
            removed = manager.removeMachine(loc);
        } catch (IllegalStateException e) {
            player.getInventory().setContents(previousContents);
            TWMaquinasPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "Falha ao desmontar a máquina em " + machine.getLocationKey() + ".", e);
            player.sendMessage(ChatColor.RED + "Não foi possível desmontar a máquina. Tenta novamente mais tarde.");
            return;
        }
        if (!removed) {
            player.getInventory().setContents(previousContents);
            player.sendMessage(ChatColor.RED + "Esta máquina já foi removida ou os dados mudaram.");
            return;
        }

        player.closeInventory();
        TWMaquinasPlugin.getInstance().getHologramManager().removeHologram(machine.getLocationKey());
        loc.getBlock().setType(Material.AIR);

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        player.sendMessage(ChatColor.YELLOW + "Desmontaste a máquina com sucesso!");
    }

    private ItemStack[] snapshotInventoryContents() {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null) contents[slot] = contents[slot].clone();
        }
        return contents;
    }

    private void performUpgrade(TWUser user, String upgrade, long cost) {
        boolean upgraded;
        try {
            upgraded = manager.upgradeMachine(machine, user, upgrade, cost);
        } catch (IllegalStateException e) {
            TWMaquinasPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "Falha ao evoluir a máquina em " + machine.getLocationKey() + ".", e);
            player.sendMessage(ChatColor.RED + "Não foi possível concluir a melhoria. Tenta novamente.");
            return;
        }
        if (!upgraded) {
            sendTokenError(cost);
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
        player.sendMessage(ChatColor.GREEN + "Atributo da máquina evoluído com sucesso!");
        setupItems();
    }

    private void sendTokenError(long cost) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        player.sendMessage(ChatColor.RED + "Não tens Tokens suficientes! Precisas de " + TWFormat.formatarDinheiro(cost) + " Tokens.");
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }
}