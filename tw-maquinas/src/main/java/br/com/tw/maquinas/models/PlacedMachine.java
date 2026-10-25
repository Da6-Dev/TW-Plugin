package br.com.tw.maquinas.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class PlacedMachine {

    private final String locationKey;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String typeId;

    private long stackAmount;
    private long fuel;
    private double storedEarnings;
    private long lastTickEpochSeconds;

    // Novos atributos para a Árvore de Upgrades e Histórico
    private int speedLevel;
    private int storageLevel;
    private int fuelEfficiencyLevel;
    private double totalGenerated;
    private long batteryCycles;
    private long lubricantCycles;

    // Construtor completo (Usado pelo MachineRepository ao carregar do banco de dados)
    public PlacedMachine(String locationKey, UUID ownerUuid, String ownerName, String typeId, long stackAmount, long fuel, double storedEarnings, int speedLevel, int storageLevel, int fuelEfficiencyLevel, double totalGenerated) {
        if (locationKey == null || locationKey.trim().isEmpty() || ownerUuid == null
                || ownerName == null || ownerName.length() > 16 || typeId == null || typeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Identificação da máquina inválida.");
        }
        if (stackAmount < 1L || fuel < 0L || speedLevel < 1 || storageLevel < 1
                || fuelEfficiencyLevel < 1) {
            throw new IllegalArgumentException("Os atributos da máquina não podem ser negativos ou nulos.");
        }
        requireFiniteNonNegative(storedEarnings, "saldo armazenado");
        requireFiniteNonNegative(totalGenerated, "produção histórica");
        this.locationKey = locationKey;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.typeId = typeId.toUpperCase();
        this.stackAmount = stackAmount;
        this.fuel = fuel;
        this.storedEarnings = storedEarnings;
        this.speedLevel = speedLevel;
        this.storageLevel = storageLevel;
        this.fuelEfficiencyLevel = fuelEfficiencyLevel;
        this.totalGenerated = totalGenerated;
        this.lastTickEpochSeconds = System.currentTimeMillis() / 1000L;
    }

    // Construtor simplificado (Usado pelo MachineListener ao colocar uma máquina nova no chão)
    public PlacedMachine(String locationKey, UUID ownerUuid, String ownerName, String typeId, long stackAmount, long fuel, double storedEarnings) {
        // Por padrão, todos os upgrades começam no nível 1 e o total gerado em 0.0
        this(locationKey, ownerUuid, ownerName, typeId, stackAmount, fuel, storedEarnings, 1, 1, 1, 0.0);
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

    public synchronized long getStackAmount() { return stackAmount; }
    public synchronized void addStack(long amount) {
        if (amount < 1L) throw new IllegalArgumentException("A quantidade de máquinas deve ser positiva.");
        this.stackAmount = Math.addExact(this.stackAmount, amount);
    }
    public void removeStack(long amount) { this.stackAmount = Math.max(0, this.stackAmount - amount); }

    public synchronized long getFuel() { return fuel; }
    public synchronized void addFuel(long amount) {
        if (amount < 1L) throw new IllegalArgumentException("A quantidade de combustível deve ser positiva.");
        this.fuel = Math.addExact(this.fuel, amount);
    }
    public synchronized boolean consumeFuel(long amount) {
        if (amount < 1L) throw new IllegalArgumentException("A quantidade de combustível deve ser positiva.");
        if (this.fuel >= amount) {
            this.fuel -= amount;
            return true;
        }
        return false;
    }

    public synchronized double getStoredEarnings() { return storedEarnings; }
    public synchronized void addStoredEarnings(double amount) {
        requireFiniteNonNegative(amount, "produção");
        this.storedEarnings = requireFiniteNonNegative(this.storedEarnings + amount, "saldo armazenado");
    }
    public synchronized void setStoredEarnings(double storedEarnings) {
        this.storedEarnings = requireFiniteNonNegative(storedEarnings, "saldo armazenado");
    }

    public synchronized long getLastTickEpochSeconds() { return lastTickEpochSeconds; }
    public synchronized void setLastTickEpochSeconds(long lastTickEpochSeconds) {
        if (lastTickEpochSeconds < 0L) throw new IllegalArgumentException("O horário do ciclo não pode ser negativo.");
        this.lastTickEpochSeconds = lastTickEpochSeconds;
    }

    // --- GETTERS E SETTERS DOS UPGRADES ---

    public synchronized int getSpeedLevel() { return speedLevel; }
    public synchronized void upgradeSpeedLevel() { this.speedLevel = Math.addExact(this.speedLevel, 1); }

    public synchronized int getStorageLevel() { return storageLevel; }
    public synchronized void upgradeStorageLevel() { this.storageLevel = Math.addExact(this.storageLevel, 1); }

    public synchronized int getFuelEfficiencyLevel() { return fuelEfficiencyLevel; }
    public synchronized void upgradeFuelEfficiencyLevel() { this.fuelEfficiencyLevel = Math.addExact(this.fuelEfficiencyLevel, 1); }

    public synchronized double getTotalGenerated() { return totalGenerated; }
    public synchronized void addTotalGenerated(double amount) {
        requireFiniteNonNegative(amount, "produção histórica");
        this.totalGenerated = requireFiniteNonNegative(this.totalGenerated + amount, "produção histórica");
    }

    public PlacedMachine(String locationKey, UUID ownerUuid, String ownerName, String typeId,
            long stackAmount, long fuel, double storedEarnings, int speedLevel, int storageLevel,
            int fuelEfficiencyLevel, double totalGenerated, long batteryCycles, long lubricantCycles) {
        this(locationKey, ownerUuid, ownerName, typeId, stackAmount, fuel, storedEarnings,
                speedLevel, storageLevel, fuelEfficiencyLevel, totalGenerated);
        if (batteryCycles < 0 || lubricantCycles < 0) {
            throw new IllegalArgumentException("Cargas de máquina não podem ser negativas.");
        }
        this.batteryCycles = batteryCycles;
        this.lubricantCycles = lubricantCycles;
    }

    public synchronized long getBatteryCycles() { return batteryCycles; }
    public synchronized long getLubricantCycles() { return lubricantCycles; }

    public synchronized void addBatteryCycles(long amount) {
        if (amount < 1) throw new IllegalArgumentException("Ciclos de bateria devem ser positivos.");
        batteryCycles = Math.addExact(batteryCycles, amount);
    }

    public synchronized void addLubricantCycles(long amount) {
        if (amount < 1) throw new IllegalArgumentException("Ciclos de lubrificante devem ser positivos.");
        lubricantCycles = Math.addExact(lubricantCycles, amount);
    }

    public synchronized boolean consumeBatteryCycle() {
        if (batteryCycles < 1) return false;
        batteryCycles--;
        return true;
    }

    public synchronized boolean consumeLubricantCycle() {
        if (lubricantCycles < 1) return false;
        lubricantCycles--;
        return true;
    }

    public synchronized PlacedMachine snapshot() {
        PlacedMachine copy = new PlacedMachine(locationKey, ownerUuid, ownerName, typeId, stackAmount, fuel,
                storedEarnings, speedLevel, storageLevel, fuelEfficiencyLevel, totalGenerated,
                batteryCycles, lubricantCycles);
        copy.setLastTickEpochSeconds(lastTickEpochSeconds);
        return copy;
    }

    private static double requireFiniteNonNegative(double amount, String name) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Valor inválido para " + name + ".");
        }
        return amount;
    }
}