package br.com.tw.spawners.managers;

import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWHologram;
import br.com.tw.spawners.TWSpawnersPlugin;
import br.com.tw.spawners.models.PlacedSpawner;
import br.com.tw.spawners.models.SpawnerType;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerHologramManager {

    private final Map<String, List<ArmorStand>> holograms = new ConcurrentHashMap<>();
    private final TWHologram hologram;

    public SpawnerHologramManager(TWSpawnersPlugin plugin) {
        this.hologram = new TWHologram(plugin);
    }

    public void updateOrCreateHologram(PlacedSpawner spawner, SpawnerType type) {
        Location loc = spawner.getBukkitLocation();
        if (loc == null || loc.getWorld() == null) return;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        List<String> lines = new ArrayList<>();
        lines.add(type.getDisplayName());

        String modeString = spawner.isVirtualMode() ? (spawner.isAutoSell() ? "§b[AUTO-SELL]" : "§d[VIRTUAL]") : "§e[FÍSICO]";
        lines.add("§7Stack: §e" + spawner.getStackAmount() + "x §8| " + modeString);
        long generatedPerCycle = br.com.tw.spawners.managers.SpawnerSpawnMath.amount(
                spawner.getStackAmount(), spawner.getAmountLevel());
        lines.add("§7Geração/ciclo: §e" + generatedPerCycle);
        if (!spawner.isVirtualMode() && spawner.getPendingPhysicalSpawn() > 0L) {
            lines.add("§7Mobs físicos pendentes: §b" + spawner.getPendingPhysicalSpawn()
                    + "§8/§b" + TWSpawnersPlugin.getInstance().getSpawnerManager().getMaxPendingPhysical());
        }

        if (spawner.hasActiveBooster()) {
            lines.add("§b🔥 BOOSTER ATIVO (" + spawner.getBoosterMultiplier() + "x) 🔥");
        }

        long nowSec = System.currentTimeMillis() / 1000L;
        double currentBooster = spawner.hasActiveBooster() ? spawner.getBoosterMultiplier() : 1.0;
        double realCycleSeconds = Math.max(1.0, (10.0 - ((spawner.getSpeedLevel() - 1) * 0.5)) / currentBooster);
        double percent = Math.min(1.0, (nowSec - spawner.getLastSpawnTime()) / realCycleSeconds);

        int bars = (int) (percent * 10);
        StringBuilder progress = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i == bars) progress.append("§7");
            progress.append("■");
        }
        lines.add("§7Progresso: " + progress.toString());

        if (spawner.isVirtualMode()) {
            lines.add("§7Cofre: §2$" + TWFormat.formatarDinheiro(spawner.getStoredMoney()) + " §8| §b" + TWFormat.formatarDinheiro(spawner.getStoredTokens()) + " T");
            if (spawner.getStoredXp() > 0 || spawner.getStoredSouls() > 0) {
                lines.add("§7Oculto: §a" + TWFormat.formatarDinheiro(spawner.getStoredXp()) + " XP §8| §d" + spawner.getStoredSouls() + " Almas");
            }
        }

        if (spawner.getTotalKills() > 0) {
            lines.add("§7Abates Totais: §c" + TWFormat.formatarDinheiro(spawner.getTotalKills()));
        }

        List<ArmorStand> stands = holograms.get(spawner.getLocationKey());

        if (stands == null || stands.size() != lines.size() || stands.stream().anyMatch(ArmorStand::isDead)) {
            removeHologram(spawner.getLocationKey());
            stands = new ArrayList<>();

            double yOffset = 1.0 + (lines.size() * 0.28);
            for (String text : lines) {
                Location lineLoc = loc.clone().add(0.5, yOffset, 0.5);
                ArmorStand stand = hologram.spawn(lineLoc, text, spawner.getLocationKey());
                stands.add(stand);
                yOffset -= 0.28;
            }
            holograms.put(spawner.getLocationKey(), stands);
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

    public void refreshInChunk(Chunk chunk, Iterable<PlacedSpawner> spawners, Map<String, SpawnerType> types) {
        for (PlacedSpawner spawner : spawners) {
            if (!hologram.belongsToChunk(spawner.getLocationKey(), chunk.getWorld().getName(),
                    chunk.getX(), chunk.getZ())) continue;

            SpawnerType type = types.get(spawner.getTypeId());
            if (type != null) {
                removeHologram(spawner.getLocationKey());
                updateOrCreateHologram(spawner, type);
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