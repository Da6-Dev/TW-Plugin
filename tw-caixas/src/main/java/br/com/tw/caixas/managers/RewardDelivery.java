package br.com.tw.caixas.managers;

import br.com.tw.caixas.TWCaixasPlugin;
import br.com.tw.caixas.model.CrateReward;
import br.com.tw.caixas.model.RewardGrant;
import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.economia.TWEconomiaPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class RewardDelivery {
    private final TWCaixasPlugin plugin;
    private final CrateService crateService;

    public RewardDelivery(TWCaixasPlugin plugin, CrateService crateService) {
        this.plugin = plugin;
        this.crateService = crateService;
    }

    public List<String> deliver(Player player, List<RewardGrant> grants) {
        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            throw new IllegalStateException("Os dados do jogador ainda não foram carregados.");
        }
        boolean changedUser = false;
        List<ItemStack> overflow = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        for (RewardGrant grant : grants) {
            CrateReward reward = grant.getReward();
            long amount = grant.getAmount();
            switch (reward.getKind()) {
                case MONEY:
                    double multiplier = TWEconomiaPlugin.getInstance().getGlobalBooster()
                            * crateService.getCatalog().getPrestigeMultiplier(user.getPrestigeId());
                    double payout = amount * multiplier;
                    if (!Double.isFinite(payout)) throw new IllegalStateException("Valor de recompensa inválido.");
                    user.addBalance(payout);
                    changedUser = true;
                    descriptions.add("§a$" + br.com.tw.core.utils.TWFormat.formatarDinheiro(payout));
                    break;
                case TOKENS:
                    user.addTokens(amount);
                    changedUser = true;
                    descriptions.add("§b" + amount + " Tokens");
                    break;
                case XP:
                    if (amount > Integer.MAX_VALUE) throw new IllegalStateException("Recompensa de XP excede o limite permitido.");
                    player.giveExpLevels((int) amount);
                    descriptions.add("§d" + amount + " níveis de XP");
                    break;
                case SOULS:
                    long remainingSouls;
                    try {
                        remainingSouls = getSpawnerManager().giveSoulItems(player, amount);
                    } catch (ArithmeticException e) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Overflow ao combinar almas antigas com uma recompensa de " + player.getName() + ".", e);
                        remainingSouls = amount;
                    }
                    if (remainingSouls > 0L) overflow.add(createSoulsItem(remainingSouls));
                    descriptions.add("§c" + amount + " Almas");
                    break;
                case ITEM:
                    if ("farm_growth".equals(reward.getEffect())
                            || "golden_scythe".equals(reward.getEffect())) {
                        for (long i = 0; i < amount; i++) overflow.add(createNativeFarmItem(reward));
                    } else if (reward.getEffect() != null) {
                        for (long i = 0; i < amount; i++) overflow.add(reward.createItem(1L));
                    } else {
                        overflow.addAll(createStacks(reward.createItem(1L), amount));
                    }
                    descriptions.add(reward.getDisplayName() == null ? "§fItem de recompensa" : reward.getDisplayName());
                    break;
                default:
                    throw new IllegalStateException("Tipo de recompensa sem tratamento: " + reward.getKind());
            }
        }
        if (changedUser) TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
        List<ItemStack> undelivered = new ArrayList<>();
        for (ItemStack item : overflow) {
            java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            undelivered.addAll(leftovers.values());
        }
        if (!undelivered.isEmpty()) {
            plugin.getLogger().info("A enviar " + undelivered.size() + " itens excedentes para o correio de "
                    + player.getName() + ".");
            crateService.storeMail(player.getUniqueId(), undelivered);
        }
        return descriptions;
    }

    public void deliverNatureFragments(Player player, long amount) {
        ItemStack fragment = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = fragment.getItemMeta();
        meta.setDisplayName("§aFragmento da Natureza");
        meta.setLore(java.util.Collections.singletonList("§7Fragmento mutante de uma plantação."));
        fragment.setItemMeta(meta);
        List<ItemStack> items = createStacks(fragment, amount);
        List<ItemStack> undelivered = new ArrayList<>();
        for (ItemStack item : items) undelivered.addAll(player.getInventory().addItem(item).values());
        if (!undelivered.isEmpty()) crateService.storeMail(player.getUniqueId(), undelivered);
        player.sendMessage("§aA mutação produziu §e" + amount + " §aFragmento(s) da Natureza.");
    }

    private List<ItemStack> createStacks(ItemStack template, long amount) {
        List<ItemStack> items = new ArrayList<>();
        long remaining = amount;
        while (remaining > 0) {
            int stackAmount = (int) Math.min(template.getMaxStackSize(), remaining);
            ItemStack item = template.clone();
            item.setAmount(stackAmount);
            items.add(item);
            remaining -= stackAmount;
        }
        return items;
    }

    private ItemStack createSoulsItem(long amount) {
        return getSpawnerManager().createSoulItem(amount);
    }

    private br.com.tw.spawners.managers.SpawnerManager getSpawnerManager() {
        br.com.tw.spawners.TWSpawnersPlugin spawners = br.com.tw.spawners.TWSpawnersPlugin.getInstance();
        if (spawners == null || spawners.getSpawnerManager() == null) {
            throw new IllegalStateException("tw-spawners não está inicializado para criar a recompensa.");
        }
        return spawners.getSpawnerManager();
    }

    private ItemStack createNativeFarmItem(CrateReward reward) {
        br.com.tw.farms.TWFarmsPlugin farms = br.com.tw.farms.TWFarmsPlugin.getInstance();
        if (farms == null || farms.getFarmManager() == null) {
            throw new IllegalStateException("tw-farms não está inicializado para criar a recompensa.");
        }
        ItemStack item;
        if ("golden_scythe".equals(reward.getEffect())) {
            item = farms.getFarmManager().createHarvesterHoe();
        } else {
            item = farms.getFarmManager().createBoosterItem(2.0, 1800L);
        }
        java.util.Map<String, Object> tags = new java.util.HashMap<>();
        tags.put("tw_crate_effect", reward.getEffect());
        tags.put("tw_crate_charges", reward.getEffect().equals("golden_scythe") ? 2500 : 1);
        tags.put("tw_crate_instance", java.util.UUID.randomUUID().toString());
        return br.com.tw.core.utils.TWItemTags.set(item, tags);
    }
}
