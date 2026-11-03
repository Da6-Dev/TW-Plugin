package br.com.tw.maquinas.listeners;

import br.com.tw.maquinas.TWMaquinasPlugin;
import br.com.tw.maquinas.managers.MachineManager;
import br.com.tw.maquinas.menus.MachinePanelMenu;
import br.com.tw.maquinas.menus.MachineShopMenu;
import br.com.tw.maquinas.models.MachineType;
import br.com.tw.maquinas.models.PlacedMachine;
import br.com.tw.core.utils.TWItemTags;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MachineListener implements Listener {

    public static final Map<UUID, MachineType> pendingPurchases = new ConcurrentHashMap<>();

    private final MachineManager manager;

    public MachineListener(MachineManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatPurchase(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MachineType type = pendingPurchases.remove(player.getUniqueId());
        if (type == null) return;

        event.setCancelled(true);
        if (event.getMessage().equalsIgnoreCase("cancelar")) {
            player.sendMessage("§cCompra cancelada.");
            return;
        }

        final long amount;
        try {
            amount = Long.parseLong(event.getMessage());
            if (amount < 1L) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage("§cQuantidade inválida. Digite um número inteiro positivo ou 'cancelar'.");
            return;
        }

        Bukkit.getScheduler().runTask(TWMaquinasPlugin.getInstance(), () -> {
            if (player.isOnline()) new MachineShopMenu(player).purchaseMachine(type, amount);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingPurchases.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (hand == null) return;

        String typeId;
        long itemStack;
        if (TWItemTags.has(hand, "tw_machine_type")) {
            typeId = TWItemTags.getString(hand, "tw_machine_type");
            itemStack = TWItemTags.getLong(hand, "tw_machine_stack", 0L);
        } else {
            String legacyData = getLegacyData(hand, "TWMACHINE:");
            if (legacyData == null) return;

            String[] split = legacyData.split(":");
            if (split.length < 3) return;
            typeId = split[1];
            itemStack = parseLong(split[2], "máquina");
        }
        if (typeId == null || typeId.isEmpty() || itemStack <= 0) return;
        long stackToPlace = itemStack;

        MachineType type = manager.getMachineTypes().get(typeId);
        if (type == null) return;

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!manager.canPlaceStack(player, block.getLocation(), stackToPlace)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Não podes colocar essa máquina neste local ou não tens acesso ao terreno.");
            return;
        }

        PlacedMachine nearby = manager.findNearbySameMachine(block.getLocation(), player.getUniqueId(), typeId, 3);
        if (nearby != null) {
            if (!manager.canMergeStack(nearby, player, stackToPlace)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "O stack excede o limite numérico permitido ou não tens acesso ao terreno.");
                return;
            }
            event.setCancelled(true);
            nearby.addStack(stackToPlace);
            manager.saveMachineAsync(nearby);

            TWMaquinasPlugin.getInstance().getHologramManager().updateOrCreateHologram(nearby, type);

            consumeOne(player, event.getHand());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            player.sendMessage(ChatColor.GREEN + "+" + stackToPlace + "x " + type.getDisplayName() +
                    ChatColor.GREEN + " agrupada! Total no stack: " + ChatColor.YELLOW + nearby.getStackAmount() + "x");
            return;
        }

        PlacedMachine placed = new PlacedMachine(
                PlacedMachine.toKey(block.getLocation()),
                player.getUniqueId(),
                player.getName(),
                typeId,
                stackToPlace,
                100L,
                0.0
        );
        manager.registerMachine(placed);

        TWMaquinasPlugin.getInstance().getHologramManager().updateOrCreateHologram(placed, type);
        player.playSound(block.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
        player.sendMessage(ChatColor.GREEN + "Colocaste " + stackToPlace + "x " + type.getDisplayName() + ChatColor.GREEN + "!");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        PlacedMachine machine = manager.getMachineAt(block.getLocation());
        if (machine == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        player.sendMessage(ChatColor.RED + "§lAtenção! §cPara recolheres a máquina, clica nela e usa o botão §4'Recolher' §cno menu.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        PlacedMachine machine = manager.getMachineAt(block.getLocation());
        if (machine == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (TWItemTags.has(hand, "tw_crate_effect")) return;

        if (hand != null) {
            long fuelUnits;
            if (TWItemTags.has(hand, "tw_fuel_units")) {
                fuelUnits = TWItemTags.getLong(hand, "tw_fuel_units", 0L);
            } else {
                String legacyData = getLegacyData(hand, "TWFUEL:");
                if (legacyData == null) {
                    new MachinePanelMenu(player, machine).open();
                    return;
                }
                String[] split = legacyData.split(":");
                if (split.length < 2) return;
                fuelUnits = parseLong(split[1], "combustível");
            }
            if (fuelUnits > 0) {
                if (!machine.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("tw.admin")) {
                    player.sendMessage(ChatColor.RED + "Apenas o dono pode abastecer esta máquina!");
                    return;
                }

                // NOVO: Se o jogador estiver no Shift, consome o pack inteiro. Se não, consome só 1.
                int itemsToConsume = player.isSneaking() ? hand.getAmount() : 1;
                long totalUnits;
                try {
                    totalUnits = Math.multiplyExact(fuelUnits, itemsToConsume);
                } catch (ArithmeticException e) {
                    player.sendMessage(ChatColor.RED + "A quantidade de combustível excede o limite permitido.");
                    return;
                }
                if (!manager.canAddFuel(machine, totalUnits)) {
                    player.sendMessage(ChatColor.RED + "Esta máquina atingiu o limite de combustível armazenado.");
                    return;
                }

                machine.addFuel(totalUnits);
                manager.saveMachineAsync(machine);

                MachineType type = manager.getMachineTypes().get(machine.getTypeId());
                if (type != null) {
                    TWMaquinasPlugin.getInstance().getHologramManager().updateOrCreateHologram(machine, type);
                }

                if (hand.getAmount() > itemsToConsume) {
                    hand.setAmount(hand.getAmount() - itemsToConsume);
                } else {
                    player.getInventory().setItemInMainHand(null); // Gastou o pack todo
                }

                player.updateInventory();
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.5f);
                player.sendMessage(ChatColor.GREEN + "Abasteceste a máquina com +" + totalUnits + " ciclos de combustível!");
                return;
            }
        }

        new MachinePanelMenu(player, machine).open();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.getMachineAt(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.getMachineAt(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.getMachineAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> manager.getMachineAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMachineBurn(BlockBurnEvent event) {
        if (manager.getMachineAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeMachineBlock(EntityChangeBlockEvent event) {
        if (manager.getMachineAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.onChunkLoad(event.getChunk());
        TWMaquinasPlugin.getInstance().getHologramManager().refreshInChunk(
                event.getChunk(), manager.getPlacedMachines().values(), manager.getMachineTypes());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        manager.onChunkUnload(event.getChunk());
        TWMaquinasPlugin.getInstance().getHologramManager().onChunkUnload(event.getChunk());
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
            return;
        }
        held.setAmount(held.getAmount() - 1);
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(held);
        else player.getInventory().setItemInMainHand(held);
    }

    private String getLegacyData(ItemStack item, String prefix) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        List<String> lore = item.getItemMeta().getLore();
        if (lore.isEmpty()) return null;

        String lastLine = ChatColor.stripColor(lore.get(lore.size() - 1));
        return lastLine.startsWith(prefix) ? lastLine : null;
    }

    private long parseLong(String value, String itemName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            TWMaquinasPlugin.getInstance().getLogger()
                    .warning("Dados antigos inválidos no item de " + itemName + ": " + value);
            return 0L;
        }
    }
}