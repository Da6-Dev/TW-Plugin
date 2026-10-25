package br.com.tw.maquinas.managers;

import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWHologram;
import br.com.tw.maquinas.TWMaquinasPlugin;
import br.com.tw.maquinas.models.MachineType;
import br.com.tw.maquinas.models.PlacedMachine;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final Map<String, List<ArmorStand>> holograms = new ConcurrentHashMap<>();
    private final TWHologram hologram;

    public HologramManager(TWMaquinasPlugin plugin) {
        this.hologram = new TWHologram(plugin);
    }

    public void updateOrCreateHologram(PlacedMachine machine, MachineType type) {
        Location loc = machine.getBukkitLocation();
        if (loc == null || loc.getWorld() == null) return;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        List<String> lines = new ArrayList<>();
        lines.add(type.getDisplayName());

        lines.add("§7Stack: §e" + machine.getStackAmount() + "x §8| §7Vel: §aLv." + machine.getSpeedLevel());

        long nowSec = System.currentTimeMillis() / 1000L;
        double realCycleSeconds = Math.max(1.0, type.getCycleSeconds() - ((machine.getSpeedLevel() - 1) * 0.5));
        double percent = Math.min(1.0, (nowSec - machine.getLastTickEpochSeconds()) / realCycleSeconds);

        int bars = (int) (percent * 10);
        StringBuilder progress = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i == bars) progress.append("§7");
            progress.append("■");
        }
        lines.add("§7Progresso: " + progress.toString());

        double maxStorage = TWMaquinasPlugin.getInstance().getMachineManager()
                .getStorageCapacity(machine, type);
        lines.add("§7Armazenado: §2$" + TWFormat.formatarDinheiro(machine.getStoredEarnings()) + " §8/ §a$" + TWFormat.formatarDinheiro(maxStorage));

        if (machine.getTotalGenerated() > 0) {
            lines.add("§7Total Histórico: §6$" + TWFormat.formatarDinheiro(machine.getTotalGenerated()));
        }

        if (machine.getStoredEarnings() >= maxStorage) {
            lines.add("§c§lARMAZÉM CHEIO! RECOLHE OS LUCROS!");
        } else if (machine.getFuel() <= 0 && machine.getBatteryCycles() <= 0) {
            lines.add("§c§lSEM COMBUSTÍVEL!");
        } else {
            lines.add("§7Combustível: §e" + machine.getFuel() + " §8| §bBateria: "
                    + machine.getBatteryCycles());
        }

        List<ArmorStand> stands = holograms.get(machine.getLocationKey());

        if (stands == null || stands.size() != lines.size() || stands.stream().anyMatch(ArmorStand::isDead)) {
            removeHologram(machine.getLocationKey());
            stands = new ArrayList<>();

            double yOffset = 1.0 + (lines.size() * 0.28);
            for (String text : lines) {
                Location lineLoc = loc.clone().add(0.5, yOffset, 0.5);
                ArmorStand stand = hologram.spawn(lineLoc, text, machine.getLocationKey());
                stands.add(stand);
                yOffset -= 0.28;
            }
            holograms.put(machine.getLocationKey(), stands);
        } else {
            for (int i = 0; i < lines.size(); i++) {
                ArmorStand stand = stands.get(i);
                if (!lines.get(i).equals(stand.getCustomName())) {
                    stand.setCustomName(lines.get(i));
                }
            }
        }
    }

    public void removeHologram(String locKey) {
        holograms.remove(locKey);
        hologram.purgeAt(locKey);
    }

    public void onChunkUnload(Chunk chunk) {
        hologram.removeChunkHolograms(chunk);
        holograms.keySet().removeIf(key -> hologram.belongsToChunk(
                key, chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public void refreshInChunk(Chunk chunk, Iterable<PlacedMachine> machines, Map<String, MachineType> types) {
        for (PlacedMachine machine : machines) {
            if (!hologram.belongsToChunk(machine.getLocationKey(), chunk.getWorld().getName(),
                    chunk.getX(), chunk.getZ())) continue;

            MachineType type = types.get(machine.getTypeId());
            if (type != null) {
                removeHologram(machine.getLocationKey());
                updateOrCreateHologram(machine, type);
            }
        }
    }

    public void startOrphanCleanup(java.util.function.Predicate<String> locationExists) {
        hologram.startOrphanCleanup(locationExists);
    }

    public void stopOrphanCleanup() {
        hologram.stopOrphanCleanup();
    }

    public void clearAll() {
        hologram.stopOrphanCleanup();
        hologram.removeAll();
        holograms.clear();
    }
}