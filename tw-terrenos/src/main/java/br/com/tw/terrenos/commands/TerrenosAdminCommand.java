package br.com.tw.terrenos.commands;

import br.com.tw.terrenos.TWTerrenosPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class TerrenosAdminCommand implements CommandExecutor {
    private final TWTerrenosPlugin plugin;

    public TerrenosAdminCommand(TWTerrenosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tw-terrenos.admin")) {
            sender.sendMessage(ChatColor.RED + "Você não tem permissão para usar este comando.");
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("auditoria")) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /terrenosadmin auditoria <jogador>");
            return true;
        }
        if (plugin.getPlotPurchaseManager() == null) {
            sender.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
            return true;
        }

        List<String> lines;
        try {
            lines = plugin.getPlotPurchaseManager().getAudit(args[1]);
        } catch (IllegalStateException exception) {
            sender.sendMessage(ChatColor.RED + "Não foi possível carregar a auditoria agora. Consulte o console.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "Últimas ações de terrenos para " + args[1] + ":");
        if (lines.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nenhuma compra, devolução ou alteração registrada.");
            return true;
        }
        for (String line : lines) {
            sender.sendMessage(ChatColor.GRAY + line);
        }
        return true;
    }
}
