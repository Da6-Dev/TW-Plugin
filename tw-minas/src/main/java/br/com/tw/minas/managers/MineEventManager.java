package br.com.tw.minas.managers;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWConfigValidation;
import br.com.tw.minas.TWMinasPlugin;
import br.com.tw.minas.utils.ActionBarUtil;
import br.com.tw.minas.utils.TWBossBar;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MineEventManager implements Listener {

    private final long communityGoal;
    private final long happyHourDurationMillis;
    private final double happyHourMultiplier;
    private final double giftChance;
    private final long giftLifetimeMillis;
    private final double meteoriteChance;
    private final int meteoriteHealth;
    private final int meteoriteHitRewardMin;
    private final int meteoriteHitRewardMax;
    private final long meteoriteCompletionReward;
    private long communityProgress = 0L;
    private long happyHourEndTime = 0L;

    private final Map<Location, Long> activeGifts = new ConcurrentHashMap<>();
    private final Map<Location, MeteoriteState> activeMeteorites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingMeteoriteRewards = new ConcurrentHashMap<>();
    private final File pendingRewardsFile;
    private final BukkitTask bossBarTask;
    private final BukkitTask cleanupTask;

    private final Random random = new Random();

    public MineEventManager(TWMinasPlugin plugin) {
        communityGoal = requireLongRange(plugin, "events.community-goal", 1L, 1_000_000_000L);
        happyHourDurationMillis = requireLongRange(plugin, "events.happy-hour-seconds", 1L, 86400L) * 1000L;
        happyHourMultiplier = TWConfigValidation.requireFiniteAtLeast(plugin,
                "events.happy-hour-multiplier", plugin.getConfig().getDouble("events.happy-hour-multiplier", Double.NaN), 1.0);
        giftChance = TWConfigValidation.requireProbability(plugin, "events.gift-chance",
                plugin.getConfig().getDouble("events.gift-chance", Double.NaN));
        giftLifetimeMillis = requireLongRange(plugin, "events.gift-lifetime-seconds", 1L, 3600L) * 1000L;
        meteoriteChance = TWConfigValidation.requireProbability(plugin, "events.meteorite-chance",
                plugin.getConfig().getDouble("events.meteorite-chance", Double.NaN));
        meteoriteHealth = TWConfigValidation.requireIntRange(plugin, "events.meteorite-health",
                plugin.getConfig().getInt("events.meteorite-health", -1), 1, 10000);
        meteoriteHitRewardMin = TWConfigValidation.requireIntRange(plugin, "events.meteorite-hit-reward-min",
                plugin.getConfig().getInt("events.meteorite-hit-reward-min", -1), 1, 1_000_000);
        meteoriteHitRewardMax = TWConfigValidation.requireIntRange(plugin, "events.meteorite-hit-reward-max",
                plugin.getConfig().getInt("events.meteorite-hit-reward-max", -1), 1, 1_000_000);
        if (meteoriteHitRewardMax < meteoriteHitRewardMin) {
            throw new IllegalArgumentException("events.meteorite-hit-reward-max não pode ser menor que o valor mínimo.");
        }
        meteoriteCompletionReward = requireLongRange(plugin, "events.meteorite-completion-reward",
                1L, 1_000_000_000L);
        pendingRewardsFile = new File(plugin.getDataFolder(), "meteorite-rewards.yml");
        loadPendingMeteoriteRewards(plugin);
        long actionBarTicks = requireLongRange(plugin, "events.actionbar-update-ticks", 1L, 1200L);
        long cleanupTicks = requireLongRange(plugin, "events.cleanup-interval-ticks", 1L, 1200L);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        bossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean happyHour = isHappyHourActive();
            long remainingMillis = Math.max(0L, happyHourEndTime - System.currentTimeMillis());
            float progress = happyHour
                    ? Math.min(1.0f, (float) remainingMillis / happyHourDurationMillis)
                    : Math.min(1.0f, (float) communityProgress / communityGoal);
            String barMessage = happyHour
                    ? "§d§lHAPPY HOUR §8» §f" + happyHourMultiplier + "x §8| §e" + formatTime(remainingMillis) + " restante"
                    : "§b§lMETA DA MINA §8» §f" + communityProgress + "§7/§f" + communityGoal
                            + " blocos §8| §e" + (int) (progress * 100) + "%";
            BarColor barColor = happyHour ? BarColor.PURPLE : BarColor.BLUE;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (TWCorePlugin.getInstance().isMineWorld(p.getWorld().getName())) {
                    TWBossBar.sendBossBar(p, barMessage, progress, barColor);
                } else {
                    TWBossBar.removeBossBar(p);
                }
            }
        }, actionBarTicks, actionBarTicks);

        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<Location, Long> entry : activeGifts.entrySet()) {
                Location loc = entry.getKey();
                if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
                if (now > entry.getValue()) {
                    activeGifts.remove(loc);
                    if (loc.getBlock().getType() == Material.ENDER_CHEST) {
                        loc.getBlock().setType(Material.AIR);
                    }
                }
            }
        }, cleanupTicks, cleanupTicks);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (TWCorePlugin.getInstance().isMineWorld(event.getFrom().getName())) {
            TWBossBar.removeBossBar(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        TWBossBar.removeBossBar(event.getPlayer());
    }

    private String formatTime(long millis) {
        long seconds = (millis + 999L) / 1000L;
        return (seconds / 60L) + ":" + String.format("%02d", seconds % 60L);
    }

    public boolean isHappyHourActive() {
        return System.currentTimeMillis() < happyHourEndTime;
    }

    public double getEventMultiplier() {
        return isHappyHourActive() ? happyHourMultiplier : 1.0;
    }

    public long getCommunityProgress() {
        return communityProgress;
    }

    public long getCommunityGoal() {
        return communityGoal;
    }

    public void addCommunityBlocks(Player triggerPlayer, Block brokenBlock, int count) {
        if (count <= 0 || !TWCorePlugin.getInstance().isMineWorld(triggerPlayer.getWorld().getName())) return;
        if (!isHappyHourActive()) {
            communityProgress = Math.min(communityGoal, communityProgress + count);
            if (communityProgress >= communityGoal) {
                communityProgress = 0L;
                happyHourEndTime = System.currentTimeMillis() + happyHourDurationMillis;
                Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "[TW-MINAS] " + ChatColor.YELLOW + "" + ChatColor.BOLD + "HAPPY HOUR NATALINO ATIVADO!");
                Bukkit.broadcastMessage(ChatColor.GREEN + "A meta comunitária de " + communityGoal + " blocos foi atingida!");
                Bukkit.broadcastMessage(ChatColor.GOLD + "Todos os blocos e tokens nas minas valem "
                        + ChatColor.BOLD + happyHourMultiplier + "x" + ChatColor.GOLD + " pelos próximos "
                        + (happyHourDurationMillis / 60000L) + " minutos!");
                Bukkit.broadcastMessage("");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
                }
            }
        }

        // 0.3% de chance de spawnar uma Caixa dos Duendes
        if (random.nextDouble() < giftChance && !activeGifts.containsKey(brokenBlock.getLocation()) && !activeMeteorites.containsKey(brokenBlock.getLocation())) {
            Location loc = brokenBlock.getLocation();
            loc.getBlock().setType(Material.ENDER_CHEST);
            activeGifts.put(loc, System.currentTimeMillis() + giftLifetimeMillis);

            triggerPlayer.playSound(loc, Sound.BLOCK_CHEST_OPEN, 1f, 1.5f);
            triggerPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "PRESENTE OCULTO! " +
                    ChatColor.YELLOW + "Encontraste uma Caixa dos Duendes! Clica nela antes que desapareça ("
                            + (giftLifetimeMillis / 1000L) + "s)!");
        }
        // A chance é aplicada por bloco elegível minerado.
        else if (random.nextDouble() < meteoriteChance && !activeMeteorites.containsKey(brokenBlock.getLocation()) && !activeGifts.containsKey(brokenBlock.getLocation())) {
            Location loc = brokenBlock.getLocation();
            loc.getBlock().setType(Material.SEA_LANTERN);
            MeteoriteState meteorite = new MeteoriteState(meteoriteHealth);
            meteorite.hologram = spawnMeteoriteHologram(loc, meteorite);
            activeMeteorites.put(loc, meteorite);

            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            triggerPlayer.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[METEORITO] " +
                    ChatColor.YELLOW + "Um Meteorito Estelar raro caiu perto de " + ChatColor.AQUA + triggerPlayer.getName() +
                    ChatColor.YELLOW + " na mina! Juntem-se para o destruir e dividir o prémio!");
        }
    }

    public boolean isSpecialBlock(Location loc) {
        return activeGifts.containsKey(loc) || activeMeteorites.containsKey(loc);
    }

    public boolean handleGiftInteract(Player player, Block block) {
        Location loc = block.getLocation();
        if (!activeGifts.containsKey(loc)) return false;

        activeGifts.remove(loc);
        block.setType(Material.AIR);

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) return true;

        long bonusTokens = 50 + random.nextInt(151);
        double bonusMoney = (user.getPickaxeLevel() * 15000.0) * (1 + random.nextInt(4));

        user.addTokens(bonusTokens);
        user.addBalance(bonusMoney);

        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 15, 3), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 15, 2), true);

        player.getWorld().spawnParticle(Particle.HEART, loc.add(0.5, 0.5, 0.5), 1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "CAIXA DOS DUENDES ABERTA! " +
                ChatColor.AQUA + "+" + bonusTokens + " Tokens" + ChatColor.WHITE + ", " +
                ChatColor.GREEN + "+$" + TWFormat.formatarDinheiro(bonusMoney) + ChatColor.WHITE + " e " +
                ChatColor.YELLOW + "Frenesi (15s)!");

        TWMinasPlugin.getInstance().getCartinhasManager().incrementProgress(
                player, user, CartinhasManager.MissionType.PRESENTES, 1, false
        );
        return true;
    }

    public boolean handleMeteoriteHit(Player player, Block block) {
        Location loc = block.getLocation();
        MeteoriteState meteorite = activeMeteorites.get(loc);
        if (meteorite == null) return false;

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) return true;

        meteorite.contributions.merge(player.getUniqueId(), 1, Integer::sum);
        meteorite.health--;
        long hitTokens = meteoriteHitRewardMin
                + random.nextInt(meteoriteHitRewardMax - meteoriteHitRewardMin + 1);
        user.addTokens(hitTokens);
        ActionBarUtil.send(player, ChatColor.GOLD + "☄ Meteorito: " + ChatColor.AQUA
                + "+" + hitTokens + " Tokens");

        if (meteorite.health <= 0) {
            activeMeteorites.remove(loc);
            removeMeteoriteHologram(meteorite);
            block.setType(Material.AIR);
            Map<UUID, Long> shares = splitMeteoriteReward(meteorite);
            payMeteoriteReward(shares);
            player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.5f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0.5, 0.5, 0.5), 2);
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[METEORITO] "
                    + ChatColor.YELLOW + "O meteorito foi destruído! O prémio de "
                    + ChatColor.AQUA + meteoriteCompletionReward + " Tokens"
                    + ChatColor.YELLOW + " foi dividido entre " + shares.size() + " minerador(es).");
        } else {
            updateMeteoriteHologram(meteorite);
            player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
            loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0.5, 0.5, 0.5), 1);
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (pendingMeteoriteRewards.containsKey(uuid)) {
            deliverPendingMeteoriteReward(event.getPlayer(), 0);
        }
    }

    public void cleanupAll() {
        bossBarTask.cancel();
        cleanupTask.cancel();
        TWBossBar.removeAll();
        for (Location loc : activeGifts.keySet()) {
            if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)
                    && loc.getBlock().getType() == Material.ENDER_CHEST) loc.getBlock().setType(Material.AIR);
        }
        for (Map.Entry<Location, MeteoriteState> entry : activeMeteorites.entrySet()) {
            Location loc = entry.getKey();
            removeMeteoriteHologram(entry.getValue());
            if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)
                    && loc.getBlock().getType() == Material.SEA_LANTERN) loc.getBlock().setType(Material.AIR);
        }
        savePendingMeteoriteRewards();
        activeGifts.clear();
        activeMeteorites.clear();
    }

    private TextDisplay spawnMeteoriteHologram(Location loc, MeteoriteState meteorite) {
        Location hologramLocation = loc.clone().add(0.5, 1.35, 0.5);
        TextDisplay hologram = loc.getWorld().spawn(hologramLocation, TextDisplay.class);
        hologram.setBillboard(Display.Billboard.CENTER);
        hologram.setSeeThrough(true);
        hologram.setShadowed(true);
        hologram.setDefaultBackground(false);
        hologram.setPersistent(false);
        updateMeteoriteHologram(meteorite, hologram);
        return hologram;
    }

    private void updateMeteoriteHologram(MeteoriteState meteorite) {
        if (meteorite.hologram != null && meteorite.hologram.isValid()) {
            updateMeteoriteHologram(meteorite, meteorite.hologram);
        }
    }

    private void updateMeteoriteHologram(MeteoriteState meteorite, TextDisplay hologram) {
        int totalHits = meteorite.contributions.values().stream().mapToInt(Integer::intValue).sum();
        int remaining = Math.max(0, meteorite.health);
        int filledBars = (int) Math.ceil((double) remaining / meteoriteHealth * 20);
        StringBuilder healthBar = new StringBuilder("§8[§c");
        for (int i = 0; i < 20; i++) {
            if (i == filledBars) healthBar.append("§7");
            healthBar.append('■');
        }
        healthBar.append("§8]");
        hologram.setText("§6§l✦ METEORITO ESTELAR ✦\n"
                + "§c❤ §fVida: §e" + remaining + " §7/ §f" + meteoriteHealth + "\n"
                + healthBar + "\n"
                + "§b" + meteorite.contributions.size() + " §7minerador(es) • §f"
                + totalHits + " §7golpes");
    }

    private void removeMeteoriteHologram(MeteoriteState meteorite) {
        if (meteorite.hologram != null && meteorite.hologram.isValid()) {
            meteorite.hologram.remove();
        }
    }

    private Map<UUID, Long> splitMeteoriteReward(MeteoriteState meteorite) {
        Map<UUID, Long> shares = new LinkedHashMap<>();
        long totalHits = meteorite.contributions.values().stream().mapToLong(Integer::longValue).sum();
        if (totalHits <= 0L) return shares;

        long distributed = 0L;
        for (Map.Entry<UUID, Integer> entry : meteorite.contributions.entrySet()) {
            long share = meteoriteCompletionReward * entry.getValue() / totalHits;
            shares.put(entry.getKey(), share);
            distributed += share;
        }

        long remainder = meteoriteCompletionReward - distributed;
        for (UUID contributor : shares.keySet()) {
            if (remainder == 0L) break;
            shares.put(contributor, shares.get(contributor) + 1L);
            remainder--;
        }
        return shares;
    }

    private void payMeteoriteReward(Map<UUID, Long> shares) {
        boolean hasPendingRewards = false;
        for (Map.Entry<UUID, Long> entry : shares.entrySet()) {
            long amount = entry.getValue();
            if (amount <= 0L) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(entry.getKey());
            if (player != null && player.isOnline() && user != null) {
                user.addTokens(amount);
                TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "PRÉMIO DO METEORITO! "
                        + ChatColor.AQUA + "+" + amount + " Tokens"
                        + ChatColor.GRAY + " pela tua contribuição.");
            } else {
                pendingMeteoriteRewards.merge(entry.getKey(), amount, Long::sum);
                hasPendingRewards = true;
            }
        }
        if (hasPendingRewards) savePendingMeteoriteRewards();
    }

    private void loadPendingMeteoriteRewards(TWMinasPlugin plugin) {
        if (!pendingRewardsFile.isFile()) return;
        YamlConfiguration rewards = YamlConfiguration.loadConfiguration(pendingRewardsFile);
        ConfigurationSection entries = rewards.getConfigurationSection("rewards");
        if (entries == null) return;
        for (String key : entries.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long amount = entries.getLong(key, 0L);
                if (amount > 0L) pendingMeteoriteRewards.put(uuid, amount);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido em meteorite-rewards.yml: " + key);
            }
        }
    }

    private void savePendingMeteoriteRewards() {
        YamlConfiguration rewards = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : pendingMeteoriteRewards.entrySet()) {
            if (entry.getValue() > 0L) {
                rewards.set("rewards." + entry.getKey(), entry.getValue());
            }
        }
        try {
            if (!pendingRewardsFile.getParentFile().isDirectory()
                    && !pendingRewardsFile.getParentFile().mkdirs()) {
                throw new IOException("Não foi possível criar a pasta de dados do TW-Minas.");
            }
            rewards.save(pendingRewardsFile);
        } catch (IOException e) {
            TWMinasPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "Não foi possível salvar as recompensas pendentes de meteoritos.", e);
        }
    }

    private void deliverPendingMeteoriteReward(Player player, int attempts) {
        Bukkit.getScheduler().runTaskLater(TWMinasPlugin.getInstance(), () -> {
            if (!player.isOnline()) return;
            Long amount = pendingMeteoriteRewards.get(player.getUniqueId());
            if (amount == null) return;
            TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                if (attempts < 60) {
                    deliverPendingMeteoriteReward(player, attempts + 1);
                } else {
                    TWMinasPlugin.getInstance().getLogger().warning(
                            "Os dados de " + player.getName() + " não carregaram; prémio de meteorito continua pendente.");
                }
                return;
            }

            user.addTokens(amount);
            TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
            pendingMeteoriteRewards.remove(player.getUniqueId(), amount);
            savePendingMeteoriteRewards();
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "PRÉMIO DO METEORITO! "
                    + ChatColor.AQUA + "+" + amount + " Tokens"
                    + ChatColor.GRAY + " pela tua contribuição anterior.");
        }, 20L);
    }

    private static final class MeteoriteState {
        private int health;
        private final Map<UUID, Integer> contributions = new LinkedHashMap<>();
        private TextDisplay hologram;

        private MeteoriteState(int health) {
            this.health = health;
        }
    }

    private long requireLongRange(TWMinasPlugin plugin, String path, long minimum, long maximum) {
        long value = plugin.getConfig().getLong(path, -1L);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Configuração inválida em " + path + ": permitido de "
                    + minimum + " até " + maximum + ".");
        }
        return value;
    }
}