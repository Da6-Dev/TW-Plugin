package br.com.tw.ranks;

import br.com.tw.ranks.commands.AdminRanksCommand;
import br.com.tw.ranks.commands.PrestigioCommand;
import br.com.tw.ranks.commands.RanksCommand;
import br.com.tw.ranks.commands.RankupCommand;
import br.com.tw.ranks.commands.RankupMaxCommand;
import br.com.tw.ranks.hooks.TWRankExpansion;
import br.com.tw.ranks.integrations.MailboxRewardDelivery;
import br.com.tw.ranks.listeners.JoinTagListener;
import com.rismr1.mailboxgui.api.MailboxGUIApi;
import com.rismr1.mailboxgui.api.MailboxGUIApiProvider;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.utils.TWConfigValidation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TWRanksPlugin extends JavaPlugin {
    private static TWRanksPlugin instance;
    private FileConfiguration ranksConfig;
    private JoinTagListener tabListManager;
    private MailboxRewardDelivery mailboxRewardDelivery;
    private Map<String, List<ItemStack>> mailRewardsByRank = Collections.emptyMap();

    @Override
    public void onEnable() {
        instance = this;
        MailboxGUIApi mailboxApi = MailboxGUIApiProvider.get();
        if (mailboxApi == null) {
            throw new IllegalStateException("MailboxGUI não disponibilizou a API de correio.");
        }
        mailboxRewardDelivery = new MailboxRewardDelivery(this, mailboxApi);
        createCustomConfig();

        // Carrega a configuração do ranks.yml com as novas opções
        File ranksFile = new File(getDataFolder(), "ranks.yml");
        this.ranksConfig = YamlConfiguration.loadConfiguration(ranksFile);
        validateRanksConfiguration(ranksFile);

        // Registo da Expansão do PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TWRankExpansion().register();
            getLogger().info("[TW-Ranks] PlaceholderAPI detetado! Variaveis %tw_rank%, %tw_prestigio% e %tw_tokens% ativadas.");
        }

        // Registo de todos os comandos
        getCommand("rankup").setExecutor(new RankupCommand(this));
        getCommand("prestigio").setExecutor(new PrestigioCommand(this));
        getCommand("twadmin").setExecutor(new AdminRanksCommand());
        getCommand("ranks").setExecutor(new RanksCommand());
        getCommand("rankupmax").setExecutor(new RankupMaxCommand(this));
        tabListManager = new JoinTagListener(this);
        tabListManager.start();

        getLogger().info("[TW-Ranks] Sistema de progressao iniciado!");
    }

    @Override
    public void onDisable() {
        if (tabListManager != null) {
            tabListManager.stop();
        }
    }

    private void createCustomConfig() {
        File configFile = new File(getDataFolder(), "ranks.yml");
        if (!configFile.exists()) {
            saveResource("ranks.yml", false);
        }
    }

    private List<ItemStack> loadMailRewards(String rankId) {
        String path = "ranks." + rankId + ".mail-rewards";
        if (!ranksConfig.contains(path)) return Collections.emptyList();
        Object configuredRewards = ranksConfig.get(path);
        if (!(configuredRewards instanceof List<?>)) {
            throw new IllegalArgumentException(path + " deve ser uma lista de itens.");
        }

        List<ItemStack> rewards = new ArrayList<>();
        for (Object entry : (List<?>) configuredRewards) {
            if (!(entry instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(path + " contém uma recompensa inválida.");
            }
            Map<?, ?> reward = (Map<?, ?>) entry;
            Object materialValue = reward.get("material");
            Material material = materialValue == null
                    ? null : Material.matchMaterial(materialValue.toString());
            Object amountValue = reward.get("amount");
            if (material == null || material == Material.AIR || !(amountValue instanceof Number)) {
                throw new IllegalArgumentException(path + " exige material e quantidade válidos.");
            }
            Number amountNumber = (Number) amountValue;
            long amount = amountNumber.longValue();
            if (amount < 1L || amount > material.getMaxStackSize()
                    || amountNumber.doubleValue() != amount) {
                throw new IllegalArgumentException(path + " tem quantidade inválida para " + material + ".");
            }
            rewards.add(new ItemStack(material, (int) amount));
        }
        return Collections.unmodifiableList(rewards);
    }

    private void validateRanksConfiguration(File ranksFile) {
        List<String> rankOrder = TWCorePlugin.getInstance().getRankHierarchy();
        List<String> prestigeOrder = TWCorePlugin.getInstance().getPrestigeHierarchy();
        org.bukkit.configuration.ConfigurationSection ranks = ranksConfig.getConfigurationSection("ranks");
        org.bukkit.configuration.ConfigurationSection prestiges = ranksConfig.getConfigurationSection("prestiges");
        if (ranks == null || prestiges == null) {
            throw new IllegalArgumentException("ranks.yml precisa conter as seções ranks e prestiges.");
        }
        Map<String, List<ItemStack>> configuredMailRewards = new HashMap<>();
        Set<String> configuredRanks = new HashSet<>();
        for (String id : ranks.getKeys(false)) {
            String normalized = id.toUpperCase(Locale.ROOT);
            if (!rankOrder.contains(normalized) || normalized.equals("MAX")) {
                throw new IllegalArgumentException("Rank fora de progression.rank-order: " + id);
            }
            configuredRanks.add(normalized);
            TWConfigValidation.requireText(this, "ranks." + id + ".prefix", ranks.getString(id + ".prefix"));
            TWConfigValidation.requireFiniteAtLeast(this, "ranks." + id + ".cost",
                    ranks.getDouble(id + ".cost", Double.NaN), 0.0);
            String next = ranks.getString(id + ".next");
            int position = rankOrder.indexOf(normalized);
            if (position < 0 || position + 1 >= rankOrder.size()
                    || next == null || !rankOrder.get(position + 1).equalsIgnoreCase(next)) {
                throw new IllegalArgumentException("Rank seguinte inválido em ranks." + id + ".next.");
            }
            List<ItemStack> mailRewards = loadMailRewards(id);
            if (!mailRewards.isEmpty()) configuredMailRewards.put(normalized, mailRewards);
        }
        for (String rank : rankOrder) {
            if (!rank.equals("MAX") && !configuredRanks.contains(rank)) {
                throw new IllegalArgumentException("Rank " + rank + " de progression.rank-order não existe em ranks.yml.");
            }
        }

        Set<String> configuredPrestiges = new HashSet<>();
        for (String id : prestiges.getKeys(false)) {
            String normalized = id.toUpperCase(Locale.ROOT);
            if (!prestigeOrder.contains(normalized)) {
                throw new IllegalArgumentException("Prestígio fora de progression.prestige-order: " + id);
            }
            configuredPrestiges.add(normalized);
            TWConfigValidation.requireText(this, "prestiges." + id + ".prefix",
                    ranksConfig.getString("prestiges." + id + ".prefix"));
            TWConfigValidation.requireFiniteAtLeast(this, "prestiges." + id + ".cost",
                    ranksConfig.getDouble("prestiges." + id + ".cost", Double.NaN), 0.0);
        }
        if (!configuredPrestiges.containsAll(prestigeOrder)) {
            throw new IllegalArgumentException("Todo progression.prestige-order deve existir em ranks.yml.");
        }
        ranksConfig.set("rank-order", rankOrder);
        mailRewardsByRank = Collections.unmodifiableMap(configuredMailRewards);
        try {
            ranksConfig.save(ranksFile);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível salvar rank-order em ranks.yml.", e);
        }
    }

    public static TWRanksPlugin getInstance() {
        return instance;
    }

    public FileConfiguration getRanksConfig() {
        return ranksConfig;
    }

    public boolean deliverMailRewards(Player player, String rankId) {
        List<ItemStack> rewards = mailRewardsByRank.get(rankId.toUpperCase(Locale.ROOT));
        return rewards == null || mailboxRewardDelivery.send(player, rankId, rewards);
    }
}