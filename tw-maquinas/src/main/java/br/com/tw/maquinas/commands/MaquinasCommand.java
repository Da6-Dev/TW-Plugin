package br.com.tw.maquinas.commands;

import br.com.tw.maquinas.TWMaquinasPlugin;
import br.com.tw.maquinas.menus.MachineShopMenu;
import br.com.tw.maquinas.models.PlacedMachine;
import br.com.tw.core.utils.TWFormat;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MaquinasCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 && sender instanceof Player) {
            new MachineShopMenu((Player) sender).open();
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (!sender.hasPermission("tw-maquinas.admin")) {
                sender.sendMessage(ChatColor.RED + "Não tens permissão para consultar o sistema.");
                return true;
            }
            TWMaquinasPlugin plugin = TWMaquinasPlugin.getInstance();
            sender.sendMessage(ChatColor.AQUA + "§lTW-Máquinas §8» §fDiagnóstico");
            sender.sendMessage(ChatColor.GRAY + "Máquinas registradas: §e"
                    + plugin.getMachineManager().getPlacedMachines().size());
            sender.sendMessage(ChatColor.GRAY + "Máquinas agrupadas: §e"
                    + plugin.getMachineManager().getTotalStackedMachines());
            sender.sendMessage(ChatColor.GRAY + "Ativas em chunks carregados: §e"
                    + plugin.getMachineManager().getActiveMachineCount());
            sender.sendMessage(ChatColor.GRAY + "Produção guardada: §a$"
                    + TWFormat.formatarDinheiro(plugin.getMachineManager().getTotalStoredEarnings()));
            sender.sendMessage(ChatColor.GRAY + "Tipos disponíveis: §e"
                    + plugin.getMachineManager().getMachineTypes().size());
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("jogador")) {
            if (!sender.hasPermission("tw-maquinas.admin")) {
                sender.sendMessage(ChatColor.RED + "Não tens permissão para consultar máquinas de jogadores.");
                return true;
            }
            String targetName = args[1];
            long machines = 0L;
            int locations = 0;
            double stored = 0.0;
            for (PlacedMachine machine : TWMaquinasPlugin.getInstance()
                    .getMachineManager().getPlacedMachines().values()) {
                if (!machine.getOwnerName().equalsIgnoreCase(targetName)) continue;
                machines = Math.addExact(machines, machine.getStackAmount());
                stored += machine.getStoredEarnings();
                locations++;
            }
            sender.sendMessage(ChatColor.AQUA + "§lTW-Máquinas §8» §f" + targetName);
            sender.sendMessage(ChatColor.GRAY + "Máquinas agrupadas: §e" + machines
                    + ChatColor.GRAY + " | Blocos registrados: §e" + locations);
            sender.sendMessage(ChatColor.GRAY + "Produção guardada: §a$" + TWFormat.formatarDinheiro(stored));
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label
                + " §7| §e/" + label + " status §7| §e/" + label + " jogador <nome>");
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "O menu de compras só pode ser aberto por um jogador.");
        }
        return true;
    }
}