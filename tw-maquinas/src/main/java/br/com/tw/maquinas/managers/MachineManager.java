package br.com.tw.maquinas.managers;

import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWItemTags;
import br.com.tw.core.utils.TWAsyncTaskQueue;
import br.com.tw.core.utils.TWConfigValidation;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.TWCorePlugin;
import br.com.tw.maquinas.TWMaquinasPlugin;
import br.com.tw.maquinas.data.MachineRepository;
import br.com.tw.maquinas.models.MachineType;
import br.com.tw.maquinas.models.PlacedMachine;
import com.plotsquared.core.plot.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.OptionalDouble;
import java.util.OptionalLong;

public class MachineManager {

    private final TWMaquinasPlugin plugin;
    private final MachineRepository repository;
    private final TWAsyncTaskQueue saveQueue;

    private final Map<String, MachineType> machineTypes = new LinkedHashMap<>();
    private final Map<String, PlacedMachine> placedMachines = new ConcurrentHashMap<>();
    private final Set<String> loggedProcessingFailures = ConcurrentHashMap.newKeySet();
    private final Set<String> activeMachineKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> dirtyMachineKeys = ConcurrentHashMap.newKeySet();

    private final List<String> rankHierarchy;
    private final String plotWorld;
    private final int maxMachinesPerPlot;
    private final long maxStackSize;
    private final int maxSpeedLevel;
    private final int maxStorageLevel;
    private final int maxFuelEfficiencyLevel;
    private final long maxFuelCycles;
    private final long autosaveIntervalTicks;

    public MachineManager(TWMaquinasPlugin plugin, MachineRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.saveQueue = new TWAsyncTaskQueue(plugin, "tw-machines-db");
        this.rankHierarchy = br.com.tw.core.TWCorePlugin.getInstance().getRankHierarchy();

        loadConfigTypes();
        ConfigurationSection rules = plugin.getConfig().getConfigurationSection("machine-rules");
        if (rules == null) throw new IllegalArgumentException("Configuração ausente: machine-rules.");
        this.plotWorld = TWConfigValidation.requireText(plugin, "machine-rules.plot-world",
                rules.getString("plot-world", "terrenos"));
        this.maxMachinesPerPlot = TWConfigValidation.requireIntRange(plugin,
                "machine-rules.max-machines-per-plot", rules.getInt("max-machines-per-plot", 25), 1, 100000);
        this.maxStackSize = rules.getLong("max-stack-size", 1_000_000L);
        if (maxStackSize < 1L) throw new IllegalArgumentException("machine-rules.max-stack-size deve ser positivo.");
        this.maxFuelCycles = rules.getLong("max-fuel-cycles", 10_000_000L);
        if (maxFuelCycles < 1L) throw new IllegalArgumentException("machine-rules.max-fuel-cycles deve ser positivo.");
        ConfigurationSection maxUpgrades = rules.getConfigurationSection("max-upgrades");
        if (maxUpgrades == null) throw new IllegalArgumentException("Configuração ausente: machine-rules.max-upgrades.");
        this.maxSpeedLevel = TWConfigValidation.requireIntRange(plugin,
                "machine-rules.max-upgrades.speed", maxUpgrades.getInt("speed", 19), 1, 1000);
        this.maxStorageLevel = TWConfigValidation.requireIntRange(plugin,
                "machine-rules.max-upgrades.storage", maxUpgrades.getInt("storage", 100), 1, 1000);
        this.maxFuelEfficiencyLevel = TWConfigValidation.requireIntRange(plugin,
                "machine-rules.max-upgrades.fuel-efficiency", maxUpgrades.getInt("fuel-efficiency", 20), 1, 1000);
        long autosaveSeconds = TWConfigValidation.requireIntRange(plugin, "performance.autosave-seconds",
                plugin.getConfig().getInt("performance.autosave-seconds", 60), 5, 3600);
        this.autosaveIntervalTicks = autosaveSeconds * 20L;
        this.placedMachines.putAll(repository.loadAllMachines());
        for (PlacedMachine machine : placedMachines.values()) {
            Location location = machine.getBukkitLocation();
            if (location != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                activeMachineKeys.add(machine.getLocationKey());
            }
        }

        startEngineTask();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (PlacedMachine m : placedMachines.values()) {
                MachineType type = machineTypes.get(m.getTypeId());
                if (type != null) {
                    plugin.getHologramManager().updateOrCreateHologram(m, type);
                }
            }
        }, 40L);
    }

    public void loadConfigTypes() {
        machineTypes.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("maquinas");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("Configuração inválida em maquinas: é necessário definir ao menos um tipo.");
        }

        for (String key : sec.getKeys(false)) {
            String name = TWConfigValidation.requireText(plugin, "maquinas." + key + ".nome",
                    sec.getString(key + ".nome"));
            Material mat = Material.matchMaterial(sec.getString(key + ".material", "COAL_BLOCK"));
            if (mat == null || mat == Material.AIR) {
                throw new IllegalArgumentException("Material inválido em maquinas." + key + ".material.");
            }

            String reqRank = TWConfigValidation.requireText(plugin, "maquinas." + key + ".rank-necessario",
                    sec.getString(key + ".rank-necessario"));
            if (!rankHierarchy.contains(reqRank.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Rank desconhecido em maquinas." + key + ".rank-necessario: " + reqRank);
            }
            double cost = TWConfigValidation.requireFiniteAtLeast(plugin, "maquinas." + key + ".custo-compra",
                    sec.getDouble(key + ".custo-compra", Double.NaN), 0.0);
            double val = TWConfigValidation.requireFiniteAtLeast(plugin, "maquinas." + key + ".valor-por-ciclo",
                    sec.getDouble(key + ".valor-por-ciclo", Double.NaN), 0.0);
            int cycle = TWConfigValidation.requireIntRange(plugin, "maquinas." + key + ".ciclo-segundos",
                    sec.getInt(key + ".ciclo-segundos", -1), 1, 86400);

            machineTypes.put(key.toUpperCase(Locale.ROOT), new MachineType(key, name, mat, reqRank, cost, val, cycle));
        }
    }

    private void startEngineTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long nowSec = System.currentTimeMillis() / 1000L;

                for (String key : activeMachineKeys) {
                    PlacedMachine machine = placedMachines.get(key);
                    if (machine == null) continue;
                    try {
                        processMachine(machine, nowSec);
                    } catch (RuntimeException e) {
                        if (loggedProcessingFailures.add(machine.getLocationKey())) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                    "Falha ao processar máquina em " + machine.getLocationKey() + ".", e);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllAsync();
            }
        }.runTaskTimer(plugin, autosaveIntervalTicks, autosaveIntervalTicks);
    }

    private void processMachine(PlacedMachine machine, long nowSec) {
        MachineType type = machineTypes.get(machine.getTypeId());
        if (type == null) return;

        Location loc = machine.getBukkitLocation();
        if (loc == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        if (machine.getFuel() > 0 || machine.getBatteryCycles() > 0) {
            double realCycleSeconds = Math.max(1.0,
                    type.getCycleSeconds() - ((machine.getSpeedLevel() - 1) * 0.5));
            if (nowSec - machine.getLastTickEpochSeconds() >= realCycleSeconds) {
                machine.setLastTickEpochSeconds(nowSec);
                double baseGenerated = type.getValuePerCycle() * machine.getStackAmount();
                double maxStorage = getStorageCapacity(machine, type);
                double availableStorage = Math.max(0.0, maxStorage - machine.getStoredEarnings());
                if (!Double.isFinite(baseGenerated) || !Double.isFinite(maxStorage)) {
                    throw new IllegalStateException("Produção excedeu o limite numérico.");
                }
                if (availableStorage <= 0.0) {
                    plugin.getHologramManager().updateOrCreateHologram(machine, type);
                    return;
                }
                double fuelSaveChance = Math.min(1.0, Math.max(0.0,
                        (machine.getFuelEfficiencyLevel() - 1) * 0.05));
                boolean batteryCycle = machine.getBatteryCycles() > 0;
                boolean consumeFuel = !batteryCycle && ThreadLocalRandom.current().nextDouble() >= fuelSaveChance;

                if (batteryCycle || !consumeFuel || machine.consumeFuel(1)) {
                    if (batteryCycle) machine.consumeBatteryCycle();
                    double generated = baseGenerated;
                    boolean lubricantCycle = machine.getLubricantCycles() > 0;
                    if (lubricantCycle) {
                        generated *= 3.0;
                    }
                    br.com.tw.core.events.TWMachineCycleEvent cycleEvent =
                            new br.com.tw.core.events.TWMachineCycleEvent(machine.getOwnerUuid(),
                                    machine.getBukkitLocation(), generated);
                    Bukkit.getPluginManager().callEvent(cycleEvent);
                    generated = cycleEvent.getGenerated();
                    if (!Double.isFinite(generated)) {
                        throw new IllegalStateException("Produção excedeu o limite numérico.");
                    }
                    double credited = Math.min(generated, availableStorage);
                    if (credited > 0.0) {
                        machine.addStoredEarnings(credited);
                        machine.addTotalGenerated(credited);
                        if (lubricantCycle) machine.consumeLubricantCycle();
                        dirtyMachineKeys.add(machine.getLocationKey());
                        playMachineEffects(loc, type.getId());
                    }
                }
            }
        }
        plugin.getHologramManager().updateOrCreateHologram(machine, type);
    }

    public double applyOverclock(PlacedMachine machine, long seconds) {
        if (machine == null || seconds < 1L) {
            throw new IllegalArgumentException("Máquina e duração de overclock são obrigatórias.");
        }
        MachineType type = machineTypes.get(machine.getTypeId());
        if (type == null) throw new IllegalArgumentException("Tipo de máquina desconhecido.");

        double cycleSeconds = Math.max(1.0,
                type.getCycleSeconds() - ((machine.getSpeedLevel() - 1) * 0.5));
        double cycles = Math.floor(seconds / cycleSeconds);
        double perCycle = type.getValuePerCycle() * machine.getStackAmount();
        double production = perCycle * cycles;
        double capacity = getStorageCapacity(machine, type);
        if (!Double.isFinite(production) || !Double.isFinite(capacity)) {
            throw new IllegalStateException("Overclock excedeu o limite numérico.");
        }

        br.com.tw.core.events.TWMachineCycleEvent cycleEvent =
                new br.com.tw.core.events.TWMachineCycleEvent(machine.getOwnerUuid(),
                        machine.getBukkitLocation(), production);
        Bukkit.getPluginManager().callEvent(cycleEvent);
        production = cycleEvent.getGenerated();
        double credited = Math.min(production, Math.max(0.0, capacity - machine.getStoredEarnings()));
        if (credited > 0.0) {
            machine.addStoredEarnings(credited);
            machine.addTotalGenerated(credited);
            dirtyMachineKeys.add(machine.getLocationKey());
            saveMachineAsync(machine);
        }
        Location location = machine.getBukkitLocation();
        if (location != null) {
            plugin.getHologramManager().updateOrCreateHologram(machine, type);
        }
        return credited;
    }

    public double getStorageCapacity(PlacedMachine machine, MachineType type) {
        return type.getValuePerCycle() * machine.getStackAmount() * 10.0 * machine.getStorageLevel();
    }

    public boolean canUpgradeSpeed(PlacedMachine machine) {
        return machine.getSpeedLevel() < getMaxSpeedLevel(machine);
    }

    public boolean canUpgradeStorage(PlacedMachine machine) {
        return machine.getStorageLevel() < maxStorageLevel;
    }

    public boolean canUpgradeFuelEfficiency(PlacedMachine machine) {
        return machine.getFuelEfficiencyLevel() < maxFuelEfficiencyLevel;
    }

    public int getMaxSpeedLevel() { return maxSpeedLevel; }
    public int getMaxSpeedLevel(PlacedMachine machine) {
        MachineType type = machineTypes.get(machine.getTypeId());
        if (type == null) return 1;
        int usefulMax = (int) Math.ceil((type.getCycleSeconds() - 1.0) / 0.5) + 1;
        return Math.min(maxSpeedLevel, Math.max(1, usefulMax));
    }
    public int getMaxStorageLevel() { return maxStorageLevel; }
    public int getMaxFuelEfficiencyLevel() { return maxFuelEfficiencyLevel; }

    public boolean canPlaceStack(Player player, Location location, long amount) {
        if (player == null || location == null || amount < 1L) return false;
        if (amount > maxStackSize) return false;
        if (!plotWorld.equalsIgnoreCase(location.getWorld().getName())) return true;

        Plot plot = plotAt(location);
        if (plot == null) return false;
        if (!player.hasPermission("tw.admin") && !plot.isOwner(player.getUniqueId())
                && !plot.isAdded(player.getUniqueId())) return false;

        long currentCount = countMachinesInPlot(plot);
        return currentCount <= maxMachinesPerPlot
                && amount <= Math.min(maxStackSize, maxMachinesPerPlot - currentCount);
    }

    public boolean canMergeStack(PlacedMachine existing, Player player, long amount) {
        if (existing == null || amount < 1L || existing.getStackAmount() > maxStackSize - amount) return false;
        Location location = existing.getBukkitLocation();
        return canPlaceStack(player, location, amount);
    }

    public boolean canAddFuel(PlacedMachine machine, long amount) {
        return machine != null && amount > 0L && machine.getFuel() <= maxFuelCycles
                && amount <= maxFuelCycles - machine.getFuel();
    }

    public long countMachinesInPlot(Plot plot) {
        if (plot == null) return 0L;
        long count = 0L;
        for (PlacedMachine machine : placedMachines.values()) {
            Location location = machine.getBukkitLocation();
            if (location == null || !plotWorld.equalsIgnoreCase(location.getWorld().getName())) continue;
            if (plot.equals(plotAt(location))) {
                count = Math.addExact(count, machine.getStackAmount());
            }
        }
        return count;
    }

    public long countMachinesInPlotAt(Location location) {
        if (location == null || !plotWorld.equalsIgnoreCase(location.getWorld().getName())) return 0L;
        return countMachinesInPlot(plotAt(location));
    }

    public int getMaxMachinesPerPlot() {
        return maxMachinesPerPlot;
    }

    private Plot plotAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        com.plotsquared.core.location.Location plotLocation =
                com.plotsquared.core.location.Location.at(location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return plotLocation.getPlot();
    }

    public void onChunkLoad(Chunk chunk) {
        long now = System.currentTimeMillis() / 1000L;
        for (PlacedMachine machine : placedMachines.values()) {
            if (!belongsToChunk(machine, chunk)) continue;
            machine.setLastTickEpochSeconds(now);
            activeMachineKeys.add(machine.getLocationKey());
        }
    }

    public void onChunkUnload(Chunk chunk) {
        long now = System.currentTimeMillis() / 1000L;
        for (PlacedMachine machine : placedMachines.values()) {
            if (!belongsToChunk(machine, chunk)) continue;
            machine.setLastTickEpochSeconds(now);
            activeMachineKeys.remove(machine.getLocationKey());
            dirtyMachineKeys.add(machine.getLocationKey());
        }
        saveAllAsync();
    }

    private boolean belongsToChunk(PlacedMachine machine, Chunk chunk) {
        Location location = machine.getBukkitLocation();
        return location != null && location.getWorld().equals(chunk.getWorld())
                && (location.getBlockX() >> 4) == chunk.getX()
                && (location.getBlockZ() >> 4) == chunk.getZ();
    }

    public OptionalDouble collectEarnings(PlacedMachine machine, TWUser owner, double payout) {
        if (machine == null || owner == null || !machine.getOwnerUuid().equals(owner.getUuid())) {
            throw new IllegalArgumentException("Apenas o proprietário pode recolher os lucros.");
        }
        double stored = machine.getStoredEarnings();
        if (stored <= 0.0 || !Double.isFinite(payout) || payout <= 0.0) return OptionalDouble.empty();

        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(owner)) {
            throw new IllegalStateException("Não foi possível persistir o saldo antes do pagamento.");
        }
        OptionalDouble updatedBalance = saveQueue.submitAndWait(
                "Falha ao recolher os lucros da máquina " + machine.getLocationKey() + ".",
                () -> repository.collectEarnings(machine.getLocationKey(), owner.getUuid(), stored, payout));
        if (!updatedBalance.isPresent()) return OptionalDouble.empty();

        machine.setStoredEarnings(0.0);
        dirtyMachineKeys.remove(machine.getLocationKey());
        owner.setBalance(updatedBalance.getAsDouble());
        TWCorePlugin.getInstance().getUserManager().saveUserAsync(owner);
        saveMachineAsync(machine);
        MachineType type = machineTypes.get(machine.getTypeId());
        if (type != null) plugin.getHologramManager().updateOrCreateHologram(machine, type);
        return updatedBalance;
    }

    public boolean upgradeMachine(PlacedMachine machine, TWUser player, String upgrade, long cost) {
        if (machine == null || player == null || cost < 0L) {
            throw new IllegalArgumentException("Máquina, jogador e custo são obrigatórios.");
        }
        int currentLevel;
        switch (upgrade) {
            case "speed":
                if (!canUpgradeSpeed(machine)) return false;
                currentLevel = machine.getSpeedLevel();
                break;
            case "storage":
                if (!canUpgradeStorage(machine)) return false;
                currentLevel = machine.getStorageLevel();
                break;
            case "fuel-efficiency":
                if (!canUpgradeFuelEfficiency(machine)) return false;
                currentLevel = machine.getFuelEfficiencyLevel();
                break;
            default:
                throw new IllegalArgumentException("Tipo de melhoria desconhecido.");
        }

        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(player)) {
            throw new IllegalStateException("Não foi possível persistir os tokens antes da melhoria.");
        }
        OptionalLong remainingTokens = saveQueue.submitAndWait(
                "Falha ao evoluir a máquina " + machine.getLocationKey() + ".",
                () -> repository.upgradeMachine(machine.getLocationKey(), player.getUuid(), upgrade,
                        currentLevel, cost));
        if (!remainingTokens.isPresent()) return false;

        switch (upgrade) {
            case "speed":
                machine.upgradeSpeedLevel();
                break;
            case "storage":
                machine.upgradeStorageLevel();
                break;
            case "fuel-efficiency":
                machine.upgradeFuelEfficiencyLevel();
                break;
            default:
                throw new IllegalArgumentException("Tipo de melhoria desconhecido.");
        }
        player.setTokens(remainingTokens.getAsLong());
        TWCorePlugin.getInstance().getUserManager().saveUserAsync(player);
        saveMachineAsync(machine);
        MachineType type = machineTypes.get(machine.getTypeId());
        if (type != null) plugin.getHologramManager().updateOrCreateHologram(machine, type);
        return true;
    }

    private void playMachineEffects(Location loc, String typeId) {
        Location center = loc.clone().add(0.5, 1.0, 0.5);
        if (typeId.equals("NEVE") || typeId.equals("RENA") || typeId.equals("NOEL")) {
            loc.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 1);
        } else if (typeId.equals("SINO") || typeId.equals("ESTRELA") || typeId.equals("DUENDE")) {
            loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center, 1);
        } else {
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 1);
        }
        loc.getWorld().playSound(center, Sound.ENTITY_CHICKEN_EGG, 0.1f, 0.5f);
    }

    public boolean hasRequiredRank(String playerRank, String playerPrestige, String requiredRank) {
        if (playerPrestige != null && !playerPrestige.equalsIgnoreCase("NENHUM")) {
            return true;
        }
        int playerIdx = rankHierarchy.indexOf(playerRank.toUpperCase());
        int reqIdx = rankHierarchy.indexOf(requiredRank.toUpperCase());
        return playerIdx >= reqIdx && reqIdx != -1;
    }

    public double getPrestigeMultiplier(String prestigeId) {
        if (prestigeId == null) return 1.0;
        switch (prestigeId.toUpperCase()) {
            case "KRAMPUS": return 3.0;
            case "YETI": return 2.0;
            case "GRINCH": return 1.5;
            default: return 1.0;
        }
    }

    public ItemStack createMachineItem(MachineType type, long amount) {
        ItemStack item = new ItemStack(type.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getDisplayName());
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Tipo: " + ChatColor.WHITE + type.getId(),
                ChatColor.GRAY + "Quantia (Stack): " + ChatColor.YELLOW + amount + "x",
                ChatColor.GRAY + "Produção Base: " + ChatColor.GREEN + "$" + TWFormat.formatarDinheiro(type.getValuePerCycle()) + " / " + type.getCycleSeconds() + "s",
                "",
                ChatColor.YELLOW + "Coloca no teu terreno para produzir!",
                ChatColor.BLACK + "TWMACHINE:" + type.getId() + ":" + amount
        ));
        item.setItemMeta(meta);
        Map<String, Object> tags = new HashMap<>();
        tags.put("tw_machine_type", type.getId());
        tags.put("tw_machine_stack", amount);
        return TWItemTags.set(item, tags);
    }

    public ItemStack createFuelItem(long fuelUnits) {
        ItemStack item = new ItemStack(Material.CHARCOAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Combustível Natalino");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Unidades: " + ChatColor.AQUA + fuelUnits,
                ChatColor.GRAY + "Abastece as tuas máquinas para",
                ChatColor.GRAY + "gerarem recursos automaticamente.",
                "",
                ChatColor.YELLOW + "Clica com o botão direito numa máquina!",
                ChatColor.BLACK + "TWFUEL:" + fuelUnits
        ));
        item.setItemMeta(meta);
        Map<String, Object> tags = new HashMap<>();
        tags.put("tw_fuel_units", fuelUnits);
        return TWItemTags.set(item, tags);
    }

    public PlacedMachine getMachineAt(Location loc) {
        return placedMachines.get(PlacedMachine.toKey(loc));
    }

    public PlacedMachine findNearbySameMachine(Location center, UUID owner, String typeId, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location check = center.clone().add(x, y, z);
                    PlacedMachine existing = getMachineAt(check);
                    if (existing != null && existing.getOwnerUuid().equals(owner) && existing.getTypeId().equalsIgnoreCase(typeId)) {
                        return existing;
                    }
                }
            }
        }
        return null;
    }

    public void registerMachine(PlacedMachine machine) {
        placedMachines.put(machine.getLocationKey(), machine);
        Location location = machine.getBukkitLocation();
        if (location != null && location.getWorld().isChunkLoaded(
                location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            activeMachineKeys.add(machine.getLocationKey());
        }
        saveMachineAsync(machine);
    }

    public boolean removeMachine(Location loc) {
        String key = PlacedMachine.toKey(loc);
        boolean deleted = saveQueue.submitAndWait("Falha ao remover máquina " + key + " do banco.",
                () -> repository.deleteMachine(key));
        if (!deleted) return false;
        placedMachines.remove(key);
        activeMachineKeys.remove(key);
        dirtyMachineKeys.remove(key);
        return true;
    }

    public int getActiveMachineCount() {
        return activeMachineKeys.size();
    }

    public long getTotalStackedMachines() {
        long count = 0L;
        for (PlacedMachine machine : placedMachines.values()) {
            count = Math.addExact(count, machine.getStackAmount());
        }
        return count;
    }

    public double getTotalStoredEarnings() {
        double total = 0.0;
        for (PlacedMachine machine : placedMachines.values()) {
            total += machine.getStoredEarnings();
            if (!Double.isFinite(total)) return Double.MAX_VALUE;
        }
        return total;
    }

    public void saveMachineAsync(PlacedMachine machine) {
        PlacedMachine snapshot = machine.snapshot();
        dirtyMachineKeys.remove(snapshot.getLocationKey());
        saveQueue.submit("Falha ao salvar máquina " + snapshot.getLocationKey() + " no banco.",
                () -> repository.saveMachine(snapshot));
    }

    public void saveAllSync() {
        for (PlacedMachine m : placedMachines.values()) {
            PlacedMachine snapshot = m.snapshot();
            saveQueue.submit("Falha ao salvar máquina " + snapshot.getLocationKey() + " no banco.",
                    () -> repository.saveMachine(snapshot));
        }
        saveQueue.shutdownAndAwait();
    }

    private void saveAllAsync() {
        for (String key : new ArrayList<>(dirtyMachineKeys)) {
            PlacedMachine machine = placedMachines.get(key);
            if (machine == null || !dirtyMachineKeys.remove(key)) continue;
            PlacedMachine snapshot = machine.snapshot();
            saveQueue.submit("Falha ao salvar máquina " + snapshot.getLocationKey() + " no banco.",
                    () -> repository.saveMachine(snapshot));
        }
    }

    public Map<String, MachineType> getMachineTypes() {
        return machineTypes;
    }

    public Map<String, PlacedMachine> getPlacedMachines() {
        return placedMachines;
    }
}