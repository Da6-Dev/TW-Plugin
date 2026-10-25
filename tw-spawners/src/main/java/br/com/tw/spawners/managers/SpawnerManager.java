package br.com.tw.spawners.managers;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWItemTags;
import br.com.tw.core.utils.TWAsyncTaskQueue;
import br.com.tw.core.utils.TWConfigValidation;
import br.com.tw.economia.TWEconomiaPlugin;
import br.com.tw.spawners.TWSpawnersPlugin;
import br.com.tw.spawners.data.SpawnerRepository;
import br.com.tw.spawners.data.SpawnerPayout;
import br.com.tw.spawners.models.PlacedSpawner;
import br.com.tw.spawners.models.SpawnerType;
import com.plotsquared.core.plot.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerManager {

    public static final String META_STACK = "TW_MOB_STACK";
    public static final String META_TYPE = "TW_MOB_TYPE";
    public static final String META_LOOT = "TW_MOB_LOOT";
    public static final String META_LOC = "TW_MOB_LOC";

    private final TWSpawnersPlugin plugin;
    private final SpawnerRepository repository;
    private final TWAsyncTaskQueue saveQueue;
    private final Map<String, SpawnerType> spawnerTypes = new LinkedHashMap<>();
    private final Map<String, PlacedSpawner> placedSpawners = new ConcurrentHashMap<>();
    private final List<String> rankHierarchy;
    private final int maxSpawnPerCycle;
    private final int maxEntitiesPerSpawner;
    private final int maxStackPurchase;
    private final long maxSpawnerStack;
    private final long maxOwnedSpawners;
    private final long maxSpawnersPerPlot;
    private final long maxPendingPhysical;
    private final int maxSpeedLevel;
    private final int maxLootLevel;
    private final int maxAmountLevel;
    private final long autosaveIntervalTicks;
    private final String plotWorld;
    private final Set<String> loggedProcessingFailures = ConcurrentHashMap.newKeySet();
    private final Set<String> activeSpawnerKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> dirtySpawnerKeys = ConcurrentHashMap.newKeySet();

    public SpawnerManager(TWSpawnersPlugin plugin, SpawnerRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.saveQueue = new TWAsyncTaskQueue(plugin, "tw-spawners-db");
        this.rankHierarchy = TWCorePlugin.getInstance().getRankHierarchy();
        ConfigurationSection limits = plugin.getConfig().getConfigurationSection("limits");
        if (limits == null) throw new IllegalArgumentException("Configuração inválida: seção limits ausente.");
        this.maxSpawnPerCycle = TWConfigValidation.requireIntRange(plugin, "limits.max-spawn-per-cycle",
                limits.getInt("max-spawn-per-cycle", 10000), 1, 10000);
        this.maxEntitiesPerSpawner = TWConfigValidation.requireIntRange(plugin, "limits.max-entities-per-spawner",
                limits.getInt("max-entities-per-spawner", 32), 1, 100);
        this.maxStackPurchase = TWConfigValidation.requireIntRange(plugin, "limits.max-stack-purchase",
                limits.getInt("max-stack-purchase", 1000), 1, 100000);
        this.maxSpawnerStack = TWConfigValidation.requireIntRange(plugin, "limits.max-spawner-stack",
                limits.getInt("max-spawner-stack", 1_000_000), 1, Integer.MAX_VALUE);
        this.maxOwnedSpawners = TWConfigValidation.requireIntRange(plugin, "limits.max-owned-spawners",
                limits.getInt("max-owned-spawners", 100), 1, Integer.MAX_VALUE);
        this.maxSpawnersPerPlot = TWConfigValidation.requireIntRange(plugin, "limits.max-spawners-per-plot",
                limits.getInt("max-spawners-per-plot", 25), 1, Integer.MAX_VALUE);
        this.maxPendingPhysical = TWConfigValidation.requireIntRange(plugin, "limits.max-pending-physical",
                limits.getInt("max-pending-physical", 100000), 1, Integer.MAX_VALUE);
        this.maxSpeedLevel = TWConfigValidation.requireIntRange(plugin, "limits.max-speed-level",
                limits.getInt("max-speed-level", 19), 1, 1000);
        this.maxLootLevel = TWConfigValidation.requireIntRange(plugin, "limits.max-loot-level",
                limits.getInt("max-loot-level", 100), 1, 10000);
        this.maxAmountLevel = TWConfigValidation.requireIntRange(plugin, "limits.max-amount-level",
                limits.getInt("max-amount-level", 10000), 1, 10000);
        this.plotWorld = TWConfigValidation.requireText(plugin, "limits.plot-world",
                limits.getString("plot-world", "terrenos"));
        long autosaveSeconds = TWConfigValidation.requireIntRange(plugin, "limits.autosave-seconds",
                limits.getInt("autosave-seconds", 60), 5, 3600);
        this.autosaveIntervalTicks = autosaveSeconds * 20L;
        loadConfigTypes();
        this.placedSpawners.putAll(repository.loadAllSpawners());
        long now = System.currentTimeMillis() / 1000L;
        for (PlacedSpawner spawner : placedSpawners.values()) {
            Location location = spawner.getBukkitLocation();
            if (location != null && location.getWorld().isChunkLoaded(
                    location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                activeSpawnerKeys.add(spawner.getLocationKey());
                spawner.setLastSpawnTime(now);
                dirtySpawnerKeys.add(spawner.getLocationKey());
            }
        }
        startSpawnerTask();
        startAutoSellTask();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (PlacedSpawner s : placedSpawners.values()) {
                SpawnerType type = spawnerTypes.get(s.getTypeId());
                if (type != null) plugin.getHologramManager().updateOrCreateHologram(s, type);
            }
        }, 40L);
    }

    public void loadConfigTypes() {
        spawnerTypes.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("spawners");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("Configuração inválida em spawners: é necessário definir ao menos um tipo.");
        }
        for (String key : sec.getKeys(false)) {
            String name = TWConfigValidation.requireText(plugin, "spawners." + key + ".nome",
                    sec.getString(key + ".nome"));
            String entityName = TWConfigValidation.requireText(plugin, "spawners." + key + ".entidade",
                    sec.getString(key + ".entidade"));
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Entidade inválida em spawners." + key + ".entidade: " + entityName, e);
            }
            if (!entityType.isAlive()) {
                throw new IllegalArgumentException("Entidade de spawner não viva em spawners." + key + ".entidade.");
            }
            String reqRank = TWConfigValidation.requireText(plugin, "spawners." + key + ".rank-necessario",
                    sec.getString(key + ".rank-necessario"));
            if (!rankHierarchy.contains(reqRank.toUpperCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Rank desconhecido em spawners." + key + ".rank-necessario: " + reqRank);
            }
            double cost = TWConfigValidation.requireFiniteAtLeast(plugin, "spawners." + key + ".custo-compra",
                    sec.getDouble(key + ".custo-compra", Double.NaN), 0.0);
            double valKill = TWConfigValidation.requireFiniteAtLeast(plugin, "spawners." + key + ".valor-por-kill",
                    sec.getDouble(key + ".valor-por-kill", Double.NaN), 0.0);
            double tokenChance = TWConfigValidation.requireProbability(plugin, "spawners." + key + ".chance-token",
                    sec.getDouble(key + ".chance-token", Double.NaN));
            spawnerTypes.put(key.toUpperCase(), new SpawnerType(key, name, entityType, reqRank, cost, valKill, tokenChance));
        }
    }

    private void startSpawnerTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long nowSec = System.currentTimeMillis() / 1000L;

                for (String key : activeSpawnerKeys) {
                    PlacedSpawner spawner = placedSpawners.get(key);
                    if (spawner == null) continue;
                    try {
                        processSpawner(spawner, nowSec);
                    } catch (RuntimeException e) {
                        if (loggedProcessingFailures.add(spawner.getLocationKey())) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                    "Falha ao processar spawner em " + spawner.getLocationKey() + ".", e);
                        }
                    }
                }
            }

            private void processSpawner(PlacedSpawner spawner, long nowSec) {
                SpawnerType type = spawnerTypes.get(spawner.getTypeId());
                if (type == null) return;

                Location loc = spawner.getBukkitLocation();
                if (loc == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

                double currentBooster = spawner.hasActiveBooster() ? spawner.getBoosterMultiplier() : 1.0;
                double baseCycle = 10.0 - ((spawner.getSpeedLevel() - 1) * 0.5);
                double realCycleSeconds = Math.max(1.0, baseCycle / currentBooster);

                if (nowSec - spawner.getLastSpawnTime() >= realCycleSeconds) {
                    spawner.setLastSpawnTime(nowSec);
                    long spawnAmount = cappedSpawnAmount(spawner);
                    if (spawnAmount <= 0L) return;

                    if (spawner.isVirtualMode()) {
                        double lootBonus = 1.0 + ((spawner.getLootLevel() - 1) * 0.10);
                        double moneyGenerated = type.getValuePerKill() * spawnAmount * lootBonus * currentBooster;
                        if (!Double.isFinite(moneyGenerated)) {
                            throw new IllegalStateException("Produção de dinheiro excedeu o limite numérico.");
                        }

                        long tokensGenerated = 0;
                        long soulsGenerated = 0;
                        long xpGenerated = (long) (spawnAmount * 2 * currentBooster);

                        double tokenChanceBase = Math.min(1.0, type.getTokenChance()
                                * (1.0 + ((spawner.getLootLevel() - 1) * 0.05)) * currentBooster);
                        double soulChanceBase = Math.min(1.0, 0.001 * currentBooster);

                        java.util.Random random = new java.util.Random();
                        tokensGenerated = SpawnerSpawnMath.sampleBinomial(spawnAmount, tokenChanceBase, random);
                        soulsGenerated = SpawnerSpawnMath.sampleBinomial(spawnAmount, soulChanceBase, random);

                        spawner.addStoredMoney(moneyGenerated);
                        spawner.addStoredTokens(tokensGenerated);
                        spawner.addStoredXp(xpGenerated);
                        spawner.addStoredSouls(soulsGenerated);
                        spawner.addTotalKills(spawnAmount);

                        playVirtualEffects(loc, type);
                    } else {
                        boolean playerNear = false;
                        for (Entity e : loc.getWorld().getNearbyEntities(loc, 16, 16, 16)) {
                            if (e instanceof Player) { playerNear = true; break; }
                        }
                        long pending = spawner.getPendingPhysicalSpawn();
                        long queued = Math.min(maxPendingPhysical, pending + Math.min(spawnAmount,
                                maxPendingPhysical - Math.min(pending, maxPendingPhysical)));
                        spawner.setPendingPhysicalSpawn(queued);
                        if (playerNear && spawnOrStackEntity(loc, type, queued, spawner.getLootLevel())) {
                            spawner.setPendingPhysicalSpawn(0L);
                            spawner.addTotalKills(queued);
                        }
                    }
                    dirtySpawnerKeys.add(spawner.getLocationKey());
                }
                plugin.getHologramManager().updateOrCreateHologram(spawner, type);
            }

            private long cappedSpawnAmount(PlacedSpawner spawner) {
                return SpawnerSpawnMath.amount(spawner.getStackAmount(), spawner.getAmountLevel(), maxSpawnPerCycle);
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() { saveAllAsync(); }
        }.runTaskTimer(plugin, autosaveIntervalTicks, autosaveIntervalTicks);
    }

    private void startAutoSellTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlacedSpawner spawner : placedSpawners.values()) {
                    try {
                        if (spawner.isVirtualMode() && spawner.isAutoSell()
                                && (spawner.getStoredMoney() > 0 || spawner.getStoredTokens() > 0)) {
                            Player p = Bukkit.getPlayer(spawner.getOwnerUuid());
                            if (p != null && p.isOnline()) {
                                TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(p.getUniqueId());
                                if (user != null) {
                                    double prestigeMult = getPrestigeMultiplier(user.getPrestigeId());
                                    double globalBooster = TWEconomiaPlugin.getInstance().getGlobalBooster();
                                    double payout = spawner.getStoredMoney() * prestigeMult * globalBooster;
                                    if (!Double.isFinite(payout)) {
                                        throw new IllegalStateException("Auto-sell excedeu o limite numérico.");
                                    }

                                    SpawnerPayout result = collectStored(spawner, user, prestigeMult * globalBooster);
                                    if (result != null) {
                                        p.sendMessage("§aAuto-Sell: §2$" + TWFormat.formatarDinheiro(result.getMoney())
                                                + " §8| §b+" + result.getPaidTokens() + " Tokens");
                                    }
                                }
                            }
                        }
                    } catch (RuntimeException e) {
                        if (loggedProcessingFailures.add("autosell:" + spawner.getLocationKey())) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                    "Falha no auto-sell do spawner em " + spawner.getLocationKey() + ".", e);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    private void playVirtualEffects(Location loc, SpawnerType type) {
        Location center = loc.clone().add(0.5, 1.0, 0.5);
        loc.getWorld().spawnParticle(Particle.LAVA, center, 1);
        loc.getWorld().playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.05f, 1.5f);
    }

    public SpawnerPayout collectStored(PlacedSpawner spawner, TWUser owner, double moneyMultiplier) {
        if (spawner == null || owner == null || !spawner.getOwnerUuid().equals(owner.getUuid())) {
            throw new IllegalArgumentException("Apenas o proprietário pode recolher o cofre.");
        }
        if (!Double.isFinite(moneyMultiplier) || moneyMultiplier < 0.0) {
            throw new IllegalArgumentException("Multiplicador de pagamento inválido.");
        }
        double storedMoney = spawner.getStoredMoney();
        long storedTokens = spawner.getStoredTokens();
        if (storedMoney <= 0.0 && storedTokens <= 0L) return null;
        double payout = storedMoney * moneyMultiplier;
        if (!Double.isFinite(payout)) {
            throw new IllegalStateException("O pagamento excede o limite numérico; o cofre não foi alterado.");
        }
        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(owner)) {
            throw new IllegalStateException("Não foi possível persistir os dados antes do pagamento.");
        }

        SpawnerPayout result = saveQueue.submitAndWait(
                "Falha ao recolher o cofre do spawner " + spawner.getLocationKey() + ".",
                () -> repository.collectToUser(spawner.getLocationKey(), owner.getUuid(),
                        storedMoney, storedTokens, payout));
        if (result == null) return null;

        spawner.setStoredMoney(0.0);
        spawner.setStoredTokens(0L);
        owner.setBalance(result.getBalance());
        owner.setTokens(result.getTokens());
        TWCorePlugin.getInstance().getUserManager().saveUserAsync(owner);
        saveSpawnerAsync(spawner);
        return result;
    }

    public boolean spawnOrStackEntity(Location spawnerLoc, SpawnerType type, long amountToAdd, int lootLevel) {
        if (amountToAdd < 1L) return false;
        Location spawnPoint = spawnerLoc.clone().add(1.5, 0.0, 0.5);
        int matchingEntities = 0;
        LivingEntity stackTarget = null;
        for (Entity nearby : spawnPoint.getWorld().getNearbyEntities(spawnPoint, 6, 4, 6)) {
            if (nearby instanceof LivingEntity && nearby.getType() == type.getEntityType() && !nearby.isDead()
                    && nearby.hasMetadata(META_LOC)
                    && spawnerLoc.equals(nearby.getMetadata(META_LOC).get(0).value())) {
                if (nearby.hasMetadata(META_STACK)) {
                    matchingEntities++;
                    if (stackTarget == null) stackTarget = (LivingEntity) nearby;
                }
            }
        }
        if (stackTarget != null) {
            long currentStack = stackTarget.getMetadata(META_STACK).get(0).asLong();
            updateEntityStack(stackTarget, type, Math.addExact(currentStack, amountToAdd), lootLevel, spawnerLoc);
            return true;
        }
        if (matchingEntities >= maxEntitiesPerSpawner) return false;
        LivingEntity entity = (LivingEntity) spawnPoint.getWorld().spawnEntity(spawnPoint, type.getEntityType());
        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 250, false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 250, false, false));
        updateEntityStack(entity, type, amountToAdd, lootLevel, spawnerLoc);
        return true;
    }

    public void updateEntityStack(LivingEntity entity, SpawnerType type, long stack, int lootLevel, Location anchor) {
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "mob_stack"), PersistentDataType.LONG, stack);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "mob_type"), PersistentDataType.STRING, type.getId());
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "mob_loot"), PersistentDataType.INTEGER, lootLevel);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "mob_loc"), PersistentDataType.STRING,
                PlacedSpawner.toKey(anchor));
        entity.setMetadata(META_STACK, new FixedMetadataValue(plugin, stack));
        entity.setMetadata(META_TYPE, new FixedMetadataValue(plugin, type.getId()));
        entity.setMetadata(META_LOOT, new FixedMetadataValue(plugin, lootLevel));
        entity.setMetadata(META_LOC, new FixedMetadataValue(plugin, anchor.clone()));
        entity.setCustomName(ChatColor.YELLOW + "§l" + stack + "x " + type.getDisplayName());
        entity.setCustomNameVisible(true);
    }

    public boolean hasMobStack(Entity entity) {
        return entity != null && (entity.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "mob_stack"), PersistentDataType.LONG)
                || entity.hasMetadata(META_STACK));
    }

    public long getMobStack(Entity entity) {
        Long stored = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_stack"), PersistentDataType.LONG);
        return stored != null ? stored : entity.getMetadata(META_STACK).get(0).asLong();
    }

    public String getMobType(Entity entity) {
        String stored = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_type"), PersistentDataType.STRING);
        return stored != null ? stored : entity.getMetadata(META_TYPE).get(0).asString();
    }

    public int getMobLoot(Entity entity) {
        Integer stored = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_loot"), PersistentDataType.INTEGER);
        return stored != null ? stored : entity.hasMetadata(META_LOOT)
                ? entity.getMetadata(META_LOOT).get(0).asInt() : 1;
    }

    public Location getMobOrigin(Entity entity) {
        String stored = entity.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "mob_loc"), PersistentDataType.STRING);
        if (stored != null) {
            String[] parts = stored.split(";");
            if (parts.length == 4) {
                org.bukkit.World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    try {
                        return new Location(world, Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Origem de mob empilhado inválida: " + stored);
                    }
                }
            }
        }
        return entity.hasMetadata(META_LOC)
                ? ((Location) entity.getMetadata(META_LOC).get(0).value()).clone()
                : entity.getLocation();
    }

    public boolean hasRequiredRank(String playerRank, String playerPrestige, String requiredRank) {
        if (playerPrestige != null && !playerPrestige.equalsIgnoreCase("NENHUM")) return true;
        int playerIdx = rankHierarchy.indexOf(playerRank.toUpperCase());
        int reqIdx = rankHierarchy.indexOf(requiredRank.toUpperCase());
        return playerIdx >= reqIdx && reqIdx != -1;
    }

    public double getPrestigeMultiplier(String prestigeId) {
        return TWCorePlugin.getInstance().getPrestigeMultiplier(prestigeId);
    }

    // =========================================================================
    // MÉTODOS DE CRIAÇÃO DO ITEM DE SPAWNER (COM OVERLOADS PARA EVITAR ERROS)
    // =========================================================================

    // Método original (usado pelas compras na loja e /spawners - spawners virgens nível 1)
    public ItemStack createSpawnerItem(SpawnerType type, long amount) {
        return createSpawnerItem(type, amount, 1, 1, 1, false);
    }

    // Método que salva o Spawner atual (usado ao Desmontar)
    public ItemStack createSpawnerItem(PlacedSpawner spawner, SpawnerType type) {
        return createSpawnerItem(type, spawner.getStackAmount(), spawner.getSpeedLevel(), spawner.getLootLevel(), spawner.getAmountLevel(), spawner.isAutoSell());
    }

    // O gerador real de Itens com os dados ocultos
    private ItemStack createSpawnerItem(SpawnerType type, long amount, int speed, int loot, int amtLvl, boolean autoSell) {
        ItemStack item = new ItemStack(Material.SPAWNER, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7Entidade: §f" + type.getEntityType().name());
        lore.add("§7Quantia (Stack): §e" + amount + "x");
        lore.add("§7Base por Abate: §a$" + TWFormat.formatarDinheiro(type.getValuePerKill()));
        lore.add("");

        if (speed > 1 || loot > 1 || amtLvl > 1 || autoSell) {
            lore.add("§d§lUpgrades Salvos:");
            if (speed > 1) lore.add("§7 ➥ Velocidade: §aLv." + speed);
            if (loot > 1) lore.add("§7 ➥ Fortuna: §aLv." + loot);
            if (amtLvl > 1) lore.add("§7 ➥ Quantidade: §aLv." + amtLvl);
            if (autoSell) lore.add("§7 ➥ Auto-Sell: §aAtivo");
            lore.add("");
        }

        lore.add("§eColoca no teu terreno para gerar mobs!");
        lore.add("§0TWSPAWNER:" + type.getId() + ":" + amount + ":" + speed + ":" + loot + ":" + amtLvl + ":" + autoSell);

        meta.setLore(lore);
        item.setItemMeta(meta);
        Map<String, Object> tags = new HashMap<>();
        tags.put("tw_spawner_type", type.getId());
        tags.put("tw_spawner_stack", amount);
        tags.put("tw_spawner_speed", speed);
        tags.put("tw_spawner_loot", loot);
        tags.put("tw_spawner_amount", amtLvl);
        tags.put("tw_spawner_auto_sell", autoSell);
        return TWItemTags.set(item, tags);
    }

    public ItemStack createBoosterItem(double multiplier, int minutes) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lBooster de Spawner §7(" + multiplier + "x)");
        meta.setLore(Arrays.asList(
                "§7Acelera o tempo de geração e",
                "§7multiplica os lucros de um",
                "§7gerador específico.",
                "",
                "§7Duração: §f" + minutes + " minutos",
                "",
                "§eClica com o botão direito num Spawner!",
                "§0TWBOOSTER:" + multiplier + ":" + minutes
        ));
        item.setItemMeta(meta);
        Map<String, Object> tags = new HashMap<>();
        tags.put("tw_spawner_booster", true);
        tags.put("tw_booster_multiplier", multiplier);
        tags.put("tw_booster_minutes", minutes);
        return TWItemTags.set(item, tags);
    }

    public ItemStack createSoulItem(long amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d§lAlma de Monstro");
        meta.setLore(Arrays.asList(
                "§7Uma essência raríssima dropada",
                "§7pelos monstros nos spawners.",
                "§7Usa-a para trocar por itens místicos!",
                "",
                "§7Quantia: §f" + amount + "x"
        ));
        item.setItemMeta(meta);
        return item;
    }

    public PlacedSpawner getSpawnerAt(Location loc) { return placedSpawners.get(PlacedSpawner.toKey(loc)); }

    public PlacedSpawner findNearbySameSpawner(Location center, UUID owner, String typeId, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    PlacedSpawner existing = getSpawnerAt(center.clone().add(x, y, z));
                    if (existing != null && existing.getOwnerUuid().equals(owner) && existing.getTypeId().equalsIgnoreCase(typeId)) {
                        return existing;
                    }
                }
            }
        }
        return null;
    }

    public void registerSpawner(PlacedSpawner spawner) {
        placedSpawners.put(spawner.getLocationKey(), spawner);
        Location location = spawner.getBukkitLocation();
        if (location != null && location.getWorld().isChunkLoaded(
                location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            activeSpawnerKeys.add(spawner.getLocationKey());
        }
        saveSpawnerAsync(spawner);
    }

    public boolean removeSpawner(Location loc) {
        String key = PlacedSpawner.toKey(loc);
        boolean deleted = saveQueue.submitAndWait("Falha ao remover spawner " + key + " do banco.",
                () -> repository.deleteSpawner(key));
        if (!deleted) return false;
        placedSpawners.remove(key);
        activeSpawnerKeys.remove(key);
        return true;
    }

    public void saveSpawnerAsync(PlacedSpawner spawner) {
        PlacedSpawner snapshot = spawner.snapshot();
        dirtySpawnerKeys.remove(snapshot.getLocationKey());
        saveQueue.submit("Falha ao salvar spawner " + snapshot.getLocationKey() + " no banco.",
                () -> persistSnapshot(snapshot));
    }

    public void saveAllSync() {
        for (PlacedSpawner s : placedSpawners.values()) {
            PlacedSpawner snapshot = s.snapshot();
            saveQueue.submit("Falha ao salvar spawner " + snapshot.getLocationKey() + " no banco.",
                    () -> repository.saveSpawner(snapshot));
        }
        saveQueue.shutdownAndAwait();
    }

    private void saveAllAsync() {
        for (String key : new ArrayList<>(dirtySpawnerKeys)) {
            PlacedSpawner spawner = placedSpawners.get(key);
            if (spawner == null || !dirtySpawnerKeys.remove(key)) continue;
            PlacedSpawner snapshot = spawner.snapshot();
            saveQueue.submit("Falha ao salvar spawner " + snapshot.getLocationKey() + " no banco.",
                    () -> persistSnapshot(snapshot));
        }
    }

    private void persistSnapshot(PlacedSpawner snapshot) {
        try {
            repository.saveSpawner(snapshot);
        } catch (RuntimeException e) {
            dirtySpawnerKeys.add(snapshot.getLocationKey());
            throw e;
        }
    }

    public Map<String, SpawnerType> getSpawnerTypes() { return spawnerTypes; }
    public Map<String, PlacedSpawner> getPlacedSpawners() { return placedSpawners; }
    public int getMaxStackPurchase() { return maxStackPurchase; }
    public long getMaxSpawnerStack() { return maxSpawnerStack; }
    public long getMaxPendingPhysical() { return maxPendingPhysical; }
    public int getMaxSpawnPerCycle() { return maxSpawnPerCycle; }

    public boolean upgradeSpawner(PlacedSpawner spawner, TWUser user, String upgrade, long cost) {
        if (spawner == null || user == null || cost < 0L
                || !spawner.getOwnerUuid().equals(user.getUuid())) {
            throw new IllegalArgumentException("Apenas o proprietário pode evoluir este spawner.");
        }
        int currentLevel;
        switch (upgrade) {
            case "speed":
                currentLevel = spawner.getSpeedLevel();
                if (currentLevel >= maxSpeedLevel) return false;
                break;
            case "loot":
                currentLevel = spawner.getLootLevel();
                if (currentLevel >= maxLootLevel) return false;
                break;
            case "amount":
                currentLevel = spawner.getAmountLevel();
                if (currentLevel >= maxAmountLevel) return false;
                break;
            case "auto-sell":
                if (spawner.isAutoSell()) return false;
                currentLevel = 0;
                break;
            default:
                throw new IllegalArgumentException("Melhoria de spawner desconhecida.");
        }
        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
            throw new IllegalStateException("Não foi possível salvar os tokens antes da melhoria.");
        }
        java.util.OptionalLong remaining = saveQueue.submitAndWait(
                "Falha ao evoluir o spawner " + spawner.getLocationKey() + ".",
                () -> repository.upgradeSpawner(spawner.getLocationKey(), user.getUuid(),
                        upgrade, currentLevel, cost));
        if (!remaining.isPresent()) return false;

        switch (upgrade) {
            case "speed": spawner.upgradeSpeedLevel(); break;
            case "loot": spawner.upgradeLootLevel(); break;
            case "amount": spawner.upgradeAmountLevel(); break;
            case "auto-sell": spawner.setAutoSell(true); break;
            default: throw new IllegalStateException("Melhoria validada mas não aplicada.");
        }
        user.setTokens(remaining.getAsLong());
        TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
        saveSpawnerAsync(spawner);
        SpawnerType type = spawnerTypes.get(spawner.getTypeId());
        if (type != null) plugin.getHologramManager().updateOrCreateHologram(spawner, type);
        return true;
    }

    public boolean canUpgrade(PlacedSpawner spawner, String upgrade) {
        switch (upgrade) {
            case "speed": return spawner.getSpeedLevel() < maxSpeedLevel;
            case "loot": return spawner.getLootLevel() < maxLootLevel;
            case "amount": return spawner.getAmountLevel() < maxAmountLevel;
            case "auto-sell": return !spawner.isAutoSell();
            default: throw new IllegalArgumentException("Melhoria de spawner desconhecida.");
        }
    }

    public int getMaxSpeedLevel() { return maxSpeedLevel; }
    public int getMaxLootLevel() { return maxLootLevel; }
    public int getMaxAmountLevel() { return maxAmountLevel; }

    public void onChunkLoad(Chunk chunk) {
        long now = System.currentTimeMillis() / 1000L;
        for (PlacedSpawner spawner : placedSpawners.values()) {
            if (belongsToChunk(spawner, chunk)) {
                activeSpawnerKeys.add(spawner.getLocationKey());
                spawner.setLastSpawnTime(now);
                dirtySpawnerKeys.add(spawner.getLocationKey());
            }
        }
        for (Entity entity : chunk.getEntities()) {
            if (!hasMobStack(entity) || !(entity instanceof LivingEntity)) continue;
            if (!entity.getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "mob_type"), PersistentDataType.STRING)
                    && !entity.hasMetadata(META_TYPE)) continue;
            SpawnerType type = spawnerTypes.get(getMobType(entity));
            if (type != null) {
                updateEntityStack((LivingEntity) entity, type, getMobStack(entity), getMobLoot(entity),
                        getMobOrigin(entity));
            }
        }
        plugin.getHologramManager().refreshInChunk(chunk, placedSpawners.values(), spawnerTypes);
    }

    public void onChunkUnload(Chunk chunk) {
        for (PlacedSpawner spawner : placedSpawners.values()) {
            if (belongsToChunk(spawner, chunk)) activeSpawnerKeys.remove(spawner.getLocationKey());
        }
    }

    private boolean belongsToChunk(PlacedSpawner spawner, Chunk chunk) {
        Location location = spawner.getBukkitLocation();
        return location != null && location.getWorld().equals(chunk.getWorld())
                && (location.getBlockX() >> 4) == chunk.getX()
                && (location.getBlockZ() >> 4) == chunk.getZ();
    }

    public boolean canPlaceStack(Player player, Location location, long amount, boolean merging) {
        if (player == null || location == null || location.getWorld() == null || amount < 1L
                || amount > maxSpawnerStack) return false;
        long owned = placedSpawners.values().stream()
                .filter(spawner -> spawner.getOwnerUuid().equals(player.getUniqueId()))
                .count();
        if (!merging && owned >= maxOwnedSpawners) return false;
        if (!plotWorld.equalsIgnoreCase(location.getWorld().getName())) return true;

        Plot plot = plotAt(location);
        if (plot == null || (!player.hasPermission("tw-spawners.admin")
                && !plot.isOwner(player.getUniqueId()) && !plot.isAdded(player.getUniqueId()))) return false;
        long inPlot = placedSpawners.values().stream()
                .filter(spawner -> {
                    Location placed = spawner.getBukkitLocation();
                    return placed != null && plot.equals(plotAt(placed));
                })
                .count();
        return merging || inPlot < maxSpawnersPerPlot;
    }

    private Plot plotAt(Location location) {
        com.plotsquared.core.location.Location plotLocation =
                com.plotsquared.core.location.Location.at(location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return plotLocation.getPlot();
    }

    public long getActiveSpawnerCount() { return activeSpawnerKeys.size(); }
    public long getTotalStackedSpawnerCount() {
        long total = 0L;
        for (PlacedSpawner spawner : placedSpawners.values()) {
            total = Math.addExact(total, spawner.getStackAmount());
        }
        return total;
    }
    public long getTotalPendingPhysical() {
        long total = 0L;
        for (PlacedSpawner spawner : placedSpawners.values()) {
            total = Math.addExact(total, spawner.getPendingPhysicalSpawn());
        }
        return total;
    }
}