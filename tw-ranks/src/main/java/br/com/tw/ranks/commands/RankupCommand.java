package br.com.tw.ranks.commands;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.ranks.TWRanksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class RankupCommand implements CommandExecutor {

    private final TWRanksPlugin plugin;

    public RankupCommand(TWRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage(ChatColor.RED + "Os teus dados ainda estão a ser carregados. Tenta novamente!");
            return true;
        }

        // Sanitização: Garante maiúsculas e remove "RANK_" se tiver sido inserido por engano
        String rawRank = user.getRankId();
        if (rawRank == null || rawRank.isEmpty()) {
            rawRank = "CARVAO";
            user.setRankId("CARVAO");
        }
        String currentRank = rawRank.toUpperCase().replace("RANK_", "").trim();

        FileConfiguration config = plugin.getRanksConfig();
        if (config == null || !config.contains("ranks." + currentRank)) {
            player.sendMessage(ChatColor.RED + "Ocorreu um erro ao ler o teu rank atual (" + currentRank + "). Contacta a equipa!");
            return true;
        }

        String nextRank = config.getString("ranks." + currentRank + ".next");
        if (nextRank == null || nextRank.equalsIgnoreCase("MAX")) {
            player.sendMessage(ChatColor.AQUA + "Já atingiste o Rank Máximo! Prepara-te para o Prestígio.");
            return true;
        }

        double cost = config.getDouble("ranks." + currentRank + ".cost");
        double balance = user.getBalance();

        if (balance >= cost) {
            if (user.removeBalance(cost)) {
                if (!plugin.deliverMailRewards(player, nextRank)) {
                    user.addBalance(cost);
                    TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
                    player.sendMessage(ChatColor.RED + "O rankup foi cancelado: a recompensa não chegou ao correio. "
                            + "O valor foi devolvido; contacta a equipa.");
                    return true;
                }
                user.setRankId(nextRank);

                // Sincronização com o LuckPerms
                TWCorePlugin.getInstance().syncLuckPermsGroups(player.getName(), user);

                // Salva no banco de dados de forma assíncrona
                TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);

                // Executa comandos de recompensa
                List<String> cmds = config.getStringList("ranks." + nextRank + ".commands");
                if (cmds != null) {
                    for (String cmd : cmds) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                    }
                }

                boolean broadcast = config.getBoolean("ranks." + nextRank + ".broadcast");
                if (broadcast) {
                    Bukkit.broadcastMessage(ChatColor.AQUA + "[TW] " + ChatColor.YELLOW + player.getName() +
                            ChatColor.GREEN + " evoluiu para o Rank " + ChatColor.BOLD + nextRank + ChatColor.GREEN + "!");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Evoluíste para o Rank " + ChatColor.YELLOW + ChatColor.BOLD + nextRank + ChatColor.GREEN + "!");
                }
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
            } else {
                player.sendMessage(ChatColor.RED + "Erro na transação. Contacta um Administrador.");
            }
        } else {
            double missing = cost - balance;
            player.sendMessage(ChatColor.RED + "Faltam $" + TWFormat.formatarDinheiro(missing) + " para evoluir para o Rank " + nextRank + ".");
        }
        return true;
    }
}