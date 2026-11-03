package br.com.tw.ranks.commands;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.ranks.TWRanksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public class RankupMaxCommand implements CommandExecutor {
    private final TWRanksPlugin plugin;

    public RankupMaxCommand(TWRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());

        if (user == null) {
            player.sendMessage(ChatColor.RED + "Os teus dados ainda estão a ser carregados.");
            return true;
        }
        if ("MAX".equalsIgnoreCase(user.getRankId())) {
            player.sendMessage(ChatColor.AQUA + "Já atingiste o Rank Máximo!");
            return true;
        }

        int ranksEvoluidos = 0;
        boolean mailRewardFailed = false;
        String novoRank = user.getRankId();

        while (true) {
            String nextRank = plugin.getRanksConfig().getString("ranks." + novoRank + ".next");
            if (nextRank == null) break;
            if (nextRank.equalsIgnoreCase("MAX")) {
                novoRank = "MAX";
                ranksEvoluidos++;
                break;
            }

            double cost = plugin.getRanksConfig().getDouble("ranks." + novoRank + ".cost");
            if (user.removeBalance(cost)) {
                if (!plugin.deliverMailRewards(player, nextRank)) {
                    user.addBalance(cost);
                    mailRewardFailed = true;
                    break;
                }
                novoRank = nextRank;
                ranksEvoluidos++;

                // Executa comandos de recompensa para cada rank passado
                List<String> cmds = plugin.getRanksConfig().getStringList("ranks." + novoRank + ".commands");
                for (String cmd : cmds) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }
            } else {
                break;
            }
        }

        if (ranksEvoluidos > 0) {
            user.setRankId(novoRank);
            TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
            TWCorePlugin.getInstance().syncLuckPermsGroups(player.getName(), user);

            player.sendMessage(ChatColor.GREEN + "Evoluíste " + ranksEvoluidos + " ranks! Agora estás no Rank " + ChatColor.YELLOW + ChatColor.BOLD + novoRank);
            if (mailRewardFailed) {
                player.sendMessage(ChatColor.RED + "A evolução foi interrompida porque a recompensa seguinte não chegou ao correio; "
                        + "o valor dessa etapa foi devolvido.");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        } else if (mailRewardFailed) {
            TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
            player.sendMessage(ChatColor.RED + "O rankup foi cancelado: a recompensa não chegou ao correio. "
                    + "O valor foi devolvido; contacta a equipa.");
        } else {
            player.sendMessage(ChatColor.RED + "Não tens dinheiro suficiente para evoluir de rank.");
        }
        return true;
    }
}