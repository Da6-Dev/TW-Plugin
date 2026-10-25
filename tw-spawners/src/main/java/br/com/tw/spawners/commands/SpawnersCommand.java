package br.com.tw.spawners.commands;

import br.com.tw.spawners.TWSpawnersPlugin;
import br.com.tw.spawners.managers.SpawnerManager;
import br.com.tw.spawners.models.PlacedSpawner;
import br.com.tw.spawners.menus.SpawnerShopMenu;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnersCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SpawnerManager manager = TWSpawnersPlugin.getInstance().getSpawnerManager();
        if (args.length > 0 && ("status".equalsIgnoreCase(args[0]) || "jogador".equalsIgnoreCase(args[0]))) {
            if (!sender.hasPermission("tw-spawners.admin")) {
                sender.sendMessage(ChatColor.RED + "Não tens permissão para esse diagnóstico.");
                return true;
            }
            if ("status".equalsIgnoreCase(args[0])) {
                sender.sendMessage(ChatColor.GOLD + "§lTW-Spawners — Diagnóstico");
                sender.sendMessage(ChatColor.GRAY + "Registados: §e" + manager.getPlacedSpawners().size()
                        + " §8| Carregados: §a" + manager.getActiveSpawnerCount());
                sender.sendMessage(ChatColor.GRAY + "Unidades em stacks: §e" + manager.getTotalStackedSpawnerCount()
                        + " §8| Físicos pendentes: §b" + manager.getTotalPendingPhysical());
                sender.sendMessage(ChatColor.GRAY + "Limite por ciclo: §e" + manager.getMaxSpawnPerCycle()
                        + " §8| Máximo pendente: §e" + manager.getMaxPendingPhysical());
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Uso: /spawners jogador <nome>");
                return true;
            }
            long stacks = 0L;
            int count = 0;
            for (PlacedSpawner spawner : manager.getPlacedSpawners().values()) {
                if (!spawner.getOwnerName().equalsIgnoreCase(args[1])) continue;
                count++;
                stacks = Math.addExact(stacks, spawner.getStackAmount());
            }
            sender.sendMessage(ChatColor.GOLD + args[1] + ChatColor.GRAY + ": §e" + count
                    + " spawners registados, §b" + stacks + " unidades.");
            return true;
        }
        if (sender instanceof Player) {
            new SpawnerShopMenu((Player) sender).open();
        } else {
            sender.sendMessage("§cEste comando só pode ser usado por um jogador.");
        }
        return true;
    }
}