package br.com.tw.spawners.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class PlacedSpawner {

    private final String locationKey;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String typeId;

    private long stackAmount;
    private long lastSpawnTime;

    private int speedLevel;
    private int lootLevel;
    private int amountLevel;

    private boolean virtualMode;
    private boolean autoSell;

    private double storedMoney;
    private long storedTokens;
    private long storedXp;
    private long storedSouls;
    private long totalKills;
    private long pendingPhysicalSpawn;

    private double boosterMultiplier;
    private long boosterEndTime;

    public PlacedSpawner(String locationKey, UUID ownerUuid, String ownerName, String typeId, long stackAmount, long lastSpawnTime, int speedLevel, int lootLevel, int amountLevel, boolean virtualMode, boolean autoSell, double storedMoney, long storedTokens, long storedXp, long storedSouls, long totalKills, double boosterMultiplier, long boosterEndTime) {
        if (locationKey == null || locationKey.trim().isEmpty() || ownerUuid == null
                || ownerName == null || ownerName.length() > 16 || typeId == null || typeId.trim().isEmpty()
                || stackAmount < 1L || lastSpawnTime < 0L || speedLevel < 1 || lootLevel < 1 || amountLevel < 1
                || !Double.isFinite(storedMoney) || storedMoney < 0.0 || storedTokens < 0L || storedXp < 0L
                || storedSouls < 0L || totalKills < 0L || !Double.isFinite(boosterMultiplier)
                || boosterMultiplier < 1.0 || boosterEndTime < 0L) {
            throw new IllegalArgumentException("Dados inválidos para o spawner.");
        }
        this.locationKey = locationKey;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.typeId = typeId.toUpperCase();
        this.stackAmount = stackAmount;
        this.lastSpawnTime = lastSpawnTime;
        this.speedLevel = speedLevel;
        this.lootLevel = lootLevel;
        this.amountLevel = amountLevel;
        this.virtualMode = virtualMode;
        this.autoSell = autoSell;
        this.storedMoney = storedMoney;
        this.storedTokens = storedTokens;
        this.storedXp = storedXp;
        this.storedSouls = storedSouls;
        this.totalKills = totalKills;
        this.boosterMultiplier = boosterMultiplier;
        this.boosterEndTime = boosterEndTime;
    }

    public PlacedSpawner(String locationKey, UUID ownerUuid, String ownerName, String typeId, long stackAmount) {
        this(locationKey, ownerUuid, ownerName, typeId, stackAmount, System.currentTimeMillis() / 1000L, 1, 1, 1, false, false, 0.0, 0L, 0L, 0L, 0L, 1.0, 0L);
    }

    public static String toKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public Location getBukkitLocation() {
        String[] parts = locationKey.split(";");
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return null;
        return new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    public String getLocationKey() { return locationKey; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public String getTypeId() { return typeId; }
    public long getStackAmount() { return stackAmount; }
    public void addStack(long amount) {
        if (amount < 1L) throw new IllegalArgumentException("A quantidade adicionada deve ser positiva.");
        this.stackAmount = Math.addExact(this.stackAmount, amount);
    }
    public void removeStack(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("A quantidade removida não pode ser negativa.");
        this.stackAmount = Math.max(0L, this.stackAmount - amount);
    }
    public long getLastSpawnTime() { return lastSpawnTime; }
    public void setLastSpawnTime(long lastSpawnTime) {
        if (lastSpawnTime < 0L) throw new IllegalArgumentException("Horário de geração inválido.");
        this.lastSpawnTime = lastSpawnTime;
    }

    public int getSpeedLevel() { return speedLevel; }
    public void upgradeSpeedLevel() { this.speedLevel = Math.addExact(this.speedLevel, 1); }
    public void setSpeedLevel(int speedLevel) {
        if (speedLevel < 1) throw new IllegalArgumentException("Nível de velocidade inválido.");
        this.speedLevel = speedLevel;
    }

    public int getLootLevel() { return lootLevel; }
    public void upgradeLootLevel() { this.lootLevel = Math.addExact(this.lootLevel, 1); }
    public void setLootLevel(int lootLevel) {
        if (lootLevel < 1) throw new IllegalArgumentException("Nível de fortuna inválido.");
        this.lootLevel = lootLevel;
    }

    public int getAmountLevel() { return amountLevel; }
    public void upgradeAmountLevel() { this.amountLevel = Math.addExact(this.amountLevel, 1); }
    public void setAmountLevel(int amountLevel) {
        if (amountLevel < 1) throw new IllegalArgumentException("Nível de quantidade inválido.");
        this.amountLevel = amountLevel;
    }

    public boolean isVirtualMode() { return virtualMode; }
    public void setVirtualMode(boolean virtualMode) { this.virtualMode = virtualMode; }
    public boolean isAutoSell() { return autoSell; }
    public void setAutoSell(boolean autoSell) { this.autoSell = autoSell; }

    public double getStoredMoney() { return storedMoney; }
    public void addStoredMoney(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0 || !Double.isFinite(storedMoney + amount)) {
            throw new IllegalArgumentException("Valor inválido para o cofre do spawner.");
        }
        this.storedMoney += amount;
    }
    public void setStoredMoney(double storedMoney) {
        if (!Double.isFinite(storedMoney) || storedMoney < 0.0) throw new IllegalArgumentException("Saldo inválido.");
        this.storedMoney = storedMoney;
    }
    public long getStoredTokens() { return storedTokens; }
    public void addStoredTokens(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("Tokens não podem ser negativos.");
        this.storedTokens = Math.addExact(this.storedTokens, amount);
    }
    public void setStoredTokens(long storedTokens) {
        if (storedTokens < 0L) throw new IllegalArgumentException("Tokens não podem ser negativos.");
        this.storedTokens = storedTokens;
    }
    public long getStoredXp() { return storedXp; }
    public void addStoredXp(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("XP não pode ser negativo.");
        this.storedXp = Math.addExact(this.storedXp, amount);
    }
    public void setStoredXp(long storedXp) {
        if (storedXp < 0L) throw new IllegalArgumentException("XP não pode ser negativo.");
        this.storedXp = storedXp;
    }
    public long getStoredSouls() { return storedSouls; }
    public void addStoredSouls(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("Almas não podem ser negativas.");
        this.storedSouls = Math.addExact(this.storedSouls, amount);
    }
    public void setStoredSouls(long storedSouls) {
        if (storedSouls < 0L) throw new IllegalArgumentException("Almas não podem ser negativas.");
        this.storedSouls = storedSouls;
    }

    public long getTotalKills() { return totalKills; }
    public void addTotalKills(long amount) {
        if (amount < 0L) throw new IllegalArgumentException("Abates não podem ser negativos.");
        this.totalKills = Math.addExact(this.totalKills, amount);
    }

    public long getPendingPhysicalSpawn() { return pendingPhysicalSpawn; }
    public void setPendingPhysicalSpawn(long pendingPhysicalSpawn) {
        if (pendingPhysicalSpawn < 0L) throw new IllegalArgumentException("A produção pendente não pode ser negativa.");
        this.pendingPhysicalSpawn = pendingPhysicalSpawn;
    }

    public double getBoosterMultiplier() { return boosterMultiplier; }
    public void setBoosterMultiplier(double boosterMultiplier) {
        if (!Double.isFinite(boosterMultiplier) || boosterMultiplier < 1.0) {
            throw new IllegalArgumentException("Multiplicador de booster inválido.");
        }
        this.boosterMultiplier = boosterMultiplier;
    }
    public long getBoosterEndTime() { return boosterEndTime; }
    public void setBoosterEndTime(long boosterEndTime) {
        if (boosterEndTime < 0L) throw new IllegalArgumentException("Duração de booster inválida.");
        this.boosterEndTime = boosterEndTime;
    }
    public boolean hasActiveBooster() { return (System.currentTimeMillis() / 1000L) < boosterEndTime; }

    public PlacedSpawner snapshot() {
        PlacedSpawner copy = new PlacedSpawner(locationKey, ownerUuid, ownerName, typeId, stackAmount, lastSpawnTime,
                speedLevel, lootLevel, amountLevel, virtualMode, autoSell, storedMoney, storedTokens,
                storedXp, storedSouls, totalKills, boosterMultiplier, boosterEndTime);
        copy.setPendingPhysicalSpawn(pendingPhysicalSpawn);
        return copy;
    }
}