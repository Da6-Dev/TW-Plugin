package br.com.tw.minas.managers;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.minas.TWMinasPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PvpArenaManager implements Listener {
    private static final String PREPARING = "PREPARING";
    private static final String ACTIVE = "ACTIVE";
    private static final String RESTORING = "RESTORING";
    private static final String FORFEIT = "FORFEIT";

    private final TWMinasPlugin plugin;
    private final PvpSessionStore sessionStore;
    private final Set<UUID> pendingEntry = new HashSet<>();
    private final Set<UUID> pendingExit = new HashSet<>();
    private final String arenaWorldName;
    private final PvpKitManager kitManager;
    private World arenaWorld;

    public PvpArenaManager(TWMinasPlugin plugin, PvpKitManager kitManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.arenaWorldName = plugin.getConfig().getString("pvp.world", "mina_pvp");
        if (arenaWorldName == null || arenaWorldName.trim().isEmpty()) {
            throw new IllegalStateException("pvp.world deve conter o nome do mundo da arena.");
        }
        this.arenaWorld = loadArenaWorld();
        this.sessionStore = new PvpSessionStore(plugin);
        configureArenaWorld(arenaWorld);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long saveInterval = plugin.getConfig().getLong("pvp.session-save-interval-ticks", 100L);
        if (saveInterval < 20L || saveInterval > 1200L) {
            throw new IllegalStateException("pvp.session-save-interval-ticks deve ficar entre 20 e 1200.");
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveActiveSessions, saveInterval, saveInterval);
    }

    public boolean isArenaWorld(World world) {
        return world != null && arenaWorldName.equalsIgnoreCase(world.getName());
    }

    public boolean isAllowedKitWorld(Player player) {
        return isArenaWorld(player.getWorld()) || isAllowedOrigin(player.getWorld());
    }

    public boolean enter(Player player) {
        if (!player.hasPermission("tw-minas.pvp.enter")) {
            player.sendMessage("§cVocê não tem permissão para entrar na Mina PvP.");
            return false;
        }
        if (isArenaWorld(player.getWorld())) {
            player.sendMessage("§eVocê já está na Mina PvP.");
            return false;
        }
        if (!isAllowedOrigin(player.getWorld())) {
            player.sendMessage("§cEntre na Mina PvP pelo lobby ou pelo mundo de plots.");
            return false;
        }
        if (sessionStore.get(player.getUniqueId()) != null) {
            player.sendMessage("§cSua sessão PvP anterior ainda precisa ser recuperada. Reconecte e tente novamente.");
            return false;
        }

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            player.sendMessage("§cSeus dados ainda estão carregando. Tente novamente em alguns segundos.");
            return false;
        }
        if (!containsMinePickaxe(player) && findEmptyHotbarSlot(player) < 0) {
            player.sendMessage("§cLibere um espaço na hotbar para levar sua picareta especial à arena.");
            return false;
        }

        PvpSession session = PvpSession.begin(player);
        if (!sessionStore.put(session)) {
            player.sendMessage("§cNão foi possível salvar sua sessão com segurança. Tente novamente.");
            return false;
        }

        clearInventory(player);
        pendingEntry.add(player.getUniqueId());
        boolean teleported = player.teleport(getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (!teleported) {
            restoreInventory(player, session.originInventory);
            sessionStore.remove(player.getUniqueId());
            pendingEntry.remove(player.getUniqueId());
            player.sendMessage("§cO teleporte para a Mina PvP foi cancelado.");
            return false;
        }
        scheduleEntryCompletion(player);
        return true;
    }

    public boolean leave(Player player) {
        if (!isArenaWorld(player.getWorld())) {
            player.sendMessage("§cVocê não está na Mina PvP.");
            return false;
        }
        PvpSession session = sessionStore.get(player.getUniqueId());
        if (session == null || !ACTIVE.equals(session.state)) {
            player.sendMessage("§cSua sessão PvP não está pronta para sair. Tente novamente em alguns segundos.");
            return false;
        }
        if (!player.hasPermission("tw-minas.pvp.leave")) {
            player.sendMessage("§cVocê não tem permissão para sair da Mina PvP.");
            return false;
        }
        if (!unequipPvpArmor(player)) {
            player.sendMessage("§cLibere espaço no inventário para guardar a armadura antes de sair.");
            return false;
        }

        session.returnInventory = InventorySnapshot.capture(player);
        session.lastArenaLocation = player.getLocation();
        session.state = RESTORING;
        if (!sessionStore.save()) {
            session.state = ACTIVE;
            session.returnInventory = null;
            player.sendMessage("§cNão foi possível salvar sua saída com segurança. Tente novamente.");
            return false;
        }

        Location destination = session.originLocation();
        if (destination == null) {
            session.state = ACTIVE;
            session.returnInventory = null;
            sessionStore.save();
            player.sendMessage("§cO mundo de origem não está disponível. Avise a equipe.");
            return false;
        }

        pendingExit.add(player.getUniqueId());
        if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            pendingExit.remove(player.getUniqueId());
            session.state = ACTIVE;
            session.returnInventory = null;
            sessionStore.save();
            player.sendMessage("§cA saída foi cancelada; você continua na arena.");
            return false;
        }
        scheduleExitCompletion(player);
        return true;
    }

    public void shutdown() {
        saveActiveSessions();
        sessionStore.save();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        boolean toArena = isArenaWorld(event.getTo() == null ? null : event.getTo().getWorld());
        boolean fromArena = isArenaWorld(event.getFrom().getWorld());
        if (toArena && !fromArena && !pendingEntry.contains(player.getUniqueId())
                && !player.hasPermission("tw-minas.pvp.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cUse /minapvp para entrar na arena.");
            return;
        }
        if (fromArena && !toArena && sessionStore.get(player.getUniqueId()) != null
                && !pendingExit.contains(player.getUniqueId())
                && !player.hasPermission("tw-minas.pvp.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cUse /pvpsair para voltar com seus itens.");
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PvpSession session = sessionStore.get(player.getUniqueId());
        if (isArenaWorld(player.getWorld()) && session != null && PREPARING.equals(session.state)) {
            scheduleEntryCompletion(player);
        } else if (isArenaWorld(event.getFrom()) && session != null && RESTORING.equals(session.state)) {
            scheduleExitCompletion(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PvpSession session = sessionStore.get(player.getUniqueId());
        if (session == null || !isArenaWorld(player.getWorld())) return;
        event.setKeepInventory(false);
        sessionStore.remove(player.getUniqueId());
        pendingEntry.remove(player.getUniqueId());
        pendingExit.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PvpSession session = sessionStore.get(player.getUniqueId());
        if (session == null || !ACTIVE.equals(session.state) || !isArenaWorld(player.getWorld())) return;

        session.lastArenaLocation = player.getLocation();
        session.arenaInventory = InventorySnapshot.capture(player);
        session.state = FORFEIT;
        if (!sessionStore.save()) {
            session.state = ACTIVE;
            plugin.getLogger().severe("Não foi possível registrar a desconexão PvP de "
                    + player.getName() + "; a sessão será recuperada como ativa.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> recoverSession(player), 2L);
    }

    private void recoverSession(Player player) {
        if (!player.isOnline()) return;
        PvpSession session = sessionStore.get(player.getUniqueId());
        if (session == null) return;

        if (FORFEIT.equals(session.state)) {
            if (isArenaWorld(player.getWorld())) clearInventory(player);
            dropSnapshot(session.arenaInventory, session.lastArenaLocation);
            sessionStore.remove(player.getUniqueId());
            player.sendMessage("§cSua desconexão encerrou a sessão PvP; seus itens foram deixados na arena.");
            return;
        }

        if (PREPARING.equals(session.state)) {
            if (isArenaWorld(player.getWorld())) {
                restoreInventory(player, session.originInventory);
                session.lastArenaLocation = player.getLocation();
                dropInventory(player);
            } else {
                restoreInventory(player, session.originInventory);
            }
            sessionStore.remove(player.getUniqueId());
            return;
        }

        if (RESTORING.equals(session.state)) {
            if (!isArenaWorld(player.getWorld())) {
                InventorySnapshot snapshot = session.returnInventory == null
                        ? session.originInventory : session.returnInventory;
                restoreInventory(player, snapshot);
                player.setGameMode(session.originGameMode);
                sessionStore.remove(player.getUniqueId());
            } else {
                Location destination = session.originLocation();
                if (destination != null) {
                    pendingExit.add(player.getUniqueId());
                    if (player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                        scheduleExitCompletion(player);
                    } else {
                        pendingExit.remove(player.getUniqueId());
                        player.sendMessage("§cSua saída PvP será recuperada quando o mundo de origem estiver disponível.");
                    }
                }
            }
            return;
        }

        if (ACTIVE.equals(session.state)) {
            if (isArenaWorld(player.getWorld())) clearInventory(player);
            InventorySnapshot snapshot = session.arenaInventory == null
                    ? session.originInventory : session.arenaInventory;
            dropSnapshot(snapshot, session.lastArenaLocation);
            sessionStore.remove(player.getUniqueId());
            player.sendMessage("§cSua sessão PvP foi encerrada como morte após uma desconexão.");
        }
    }

    private void scheduleEntryCompletion(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> completeEntry(player), 2L);
    }

    private void completeEntry(Player player) {
        if (!player.isOnline() || !isArenaWorld(player.getWorld())) return;
        PvpSession session = sessionStore.get(player.getUniqueId());
        if (session == null || !PREPARING.equals(session.state)) return;

        restoreInventory(player, session.originInventory);
        player.setGameMode(GameMode.SURVIVAL);
        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        if (user == null || !plugin.getMinePickaxeManager().ensurePickaxe(player, user)) {
            session.returnInventory = session.originInventory.copy();
            session.state = RESTORING;
            sessionStore.save();
            pendingExit.add(player.getUniqueId());
            if (!player.teleport(session.originLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                dropInventory(player);
                sessionStore.remove(player.getUniqueId());
                pendingExit.remove(player.getUniqueId());
                plugin.getLogger().severe("Não foi possível devolver " + player.getName()
                        + " à origem após falhar a entrega da picareta PvP.");
            }
            return;
        }

        session.state = ACTIVE;
        session.lastArenaLocation = player.getLocation();
        session.arenaInventory = InventorySnapshot.capture(player);
        if (!sessionStore.save()) {
            player.sendMessage("§cA sessão não pôde ser persistida. Saia da arena e avise a equipe.");
            plugin.getLogger().severe("Falha ao persistir a sessão PvP de " + player.getName() + ".");
            return;
        }
        pendingEntry.remove(player.getUniqueId());
        player.sendMessage("§aVocê entrou na Mina PvP. Use /pvpsair para sair com seus itens.");
    }

    private void scheduleExitCompletion(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> completeExit(player), 2L);
    }

    private void completeExit(Player player) {
        if (!player.isOnline() || isArenaWorld(player.getWorld())) return;
        PvpSession session = sessionStore.get(player.getUniqueId());
        if (session == null || !RESTORING.equals(session.state)) return;
        InventorySnapshot snapshot = session.returnInventory == null
                ? session.originInventory : session.returnInventory;
        restoreInventory(player, snapshot);
        player.setGameMode(session.originGameMode);
        sessionStore.remove(player.getUniqueId());
        pendingExit.remove(player.getUniqueId());
        player.sendMessage("§aVocê saiu da Mina PvP e levou seus itens.");
    }

    private void saveActiveSessions() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PvpSession session = sessionStore.get(player.getUniqueId());
            if (session == null || !ACTIVE.equals(session.state) || !isArenaWorld(player.getWorld())) continue;
            session.lastArenaLocation = player.getLocation();
            session.arenaInventory = InventorySnapshot.capture(player);
        }
        if (!sessionStore.save()) {
            plugin.getLogger().severe("Falha ao salvar as sessões ativas da Mina PvP.");
        }
    }

    private boolean unequipPvpArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < armor.length; i++) {
            if (kitManager.isPvpItem(armor[i])) slots.add(i);
        }
        if (slots.isEmpty()) return true;

        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) emptySlots++;
        }
        if (emptySlots < slots.size()) return false;

        for (int slot : slots) {
            ItemStack item = armor[slot];
            armor[slot] = null;
            player.getInventory().addItem(item);
        }
        player.getInventory().setArmorContents(armor);
        return true;
    }

    private boolean isAllowedOrigin(World world) {
        if (world == null || TWCorePlugin.getInstance().isMineWorld(world.getName())) return false;
        if (world.getName().equalsIgnoreCase("world")
                || TWCorePlugin.getInstance().isPlotsWorld(world.getName())) return true;
        for (String name : plugin.getConfig().getStringList("pvp.allowed-origin-worlds")) {
            if (world.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean containsMinePickaxe(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (plugin.getMinePickaxeManager().isMinePickaxe(item)) return true;
        }
        return plugin.getMinePickaxeManager().isMinePickaxe(player.getInventory().getItemInOffHand());
    }

    private int findEmptyHotbarSlot(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < 9 && i < storage.length; i++) {
            if (storage[i] == null || storage[i].getType() == Material.AIR) return i;
        }
        return -1;
    }

    private Location getSpawnLocation() {
        double x = plugin.getConfig().getDouble("pvp.spawn.x", 0.5);
        double y = plugin.getConfig().getDouble("pvp.spawn.y", 101.0);
        double z = plugin.getConfig().getDouble("pvp.spawn.z", 0.5);
        return new Location(arenaWorld, x, y, z,
                (float) plugin.getConfig().getDouble("pvp.spawn.yaw", 0.0),
                (float) plugin.getConfig().getDouble("pvp.spawn.pitch", 0.0));
    }

    private World loadArenaWorld() {
        World world = Bukkit.getWorld(arenaWorldName);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(arenaWorldName)
                    .environment(World.Environment.NORMAL).generator("VoidGen"));
        }
        if (world == null) {
            throw new IllegalStateException("Não foi possível carregar/criar o mundo " + arenaWorldName + ".");
        }
        return world;
    }

    private void configureArenaWorld(World world) {
        world.setPVP(true);
        world.setDifficulty(Difficulty.HARD);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);

        int floorY = plugin.getConfig().getInt("pvp.spawn.floor-y", 100);
        int radius = plugin.getConfig().getInt("pvp.arena.radius", 64);
        if (floorY < world.getMinHeight() + 2 || floorY > world.getMaxHeight() - 8
                || radius < 24 || radius > 256) {
            throw new IllegalStateException("Configuração de spawn/raio da Mina PvP fora dos limites.");
        }

        if (world.getBlockAt(0, floorY, 0).getType() != Material.POLISHED_BLACKSTONE_BRICKS) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    world.getBlockAt(x, floorY, z).setType(Material.STONE_BRICKS, false);
                }
            }
            world.getBlockAt(0, floorY, 0).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            for (int i = -radius; i <= radius; i++) {
                for (int y = floorY + 1; y <= floorY + 5; y++) {
                    if (Math.abs(i) < radius) {
                        world.getBlockAt(i, y, -radius).setType(Material.STONE_BRICKS, false);
                        world.getBlockAt(i, y, radius).setType(Material.STONE_BRICKS, false);
                        world.getBlockAt(-radius, y, i).setType(Material.STONE_BRICKS, false);
                        world.getBlockAt(radius, y, i).setType(Material.STONE_BRICKS, false);
                    }
                }
            }
        }

        int spawnY = plugin.getConfig().getInt("pvp.spawn.y", floorY + 1);
        world.setSpawnLocation(0, spawnY, 0);
        world.getWorldBorder().setCenter(0.0, 0.0);
        world.getWorldBorder().setSize((radius + 1) * 2.0);
    }

    private void restoreInventory(Player player, InventorySnapshot snapshot) {
        if (snapshot == null) return;
        player.getInventory().setStorageContents(snapshot.storageCopy());
        player.getInventory().setArmorContents(snapshot.armorCopy());
        player.getInventory().setItemInOffHand(snapshot.offhandCopy());
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private void dropInventory(Player player) {
        Location location = player.getLocation();
        for (ItemStack item : player.getInventory().getStorageContents()) dropItem(location, item);
        for (ItemStack item : player.getInventory().getArmorContents()) dropItem(location, item);
        dropItem(location, player.getInventory().getItemInOffHand());
        clearInventory(player);
    }

    private void dropSnapshot(InventorySnapshot snapshot, Location preferredLocation) {
        if (snapshot == null) return;
        Location location = preferredLocation != null && isArenaWorld(preferredLocation.getWorld())
                ? preferredLocation : getSpawnLocation();
        for (ItemStack item : snapshot.storage) dropItem(location, item);
        for (ItemStack item : snapshot.armor) dropItem(location, item);
        dropItem(location, snapshot.offhand);
    }

    private void dropItem(Location location, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return;
        Item drop = location.getWorld().dropItemNaturally(location, item.clone());
        drop.setPickupDelay(20);
    }

    private static final class InventorySnapshot {
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private ItemStack offhand;

        private InventorySnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
            this.storage = cloneItems(storage);
            this.armor = cloneItems(armor);
            this.offhand = cloneItem(offhand);
        }

        private static InventorySnapshot capture(Player player) {
            return new InventorySnapshot(player.getInventory().getStorageContents(),
                    player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand());
        }

        private InventorySnapshot copy() {
            return new InventorySnapshot(storage, armor, offhand);
        }

        private ItemStack[] storageCopy() {
            return cloneItems(storage);
        }

        private ItemStack[] armorCopy() {
            return cloneItems(armor);
        }

        private ItemStack offhandCopy() {
            return cloneItem(offhand);
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] result = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) result[i] = cloneItem(source[i]);
            return result;
        }

        private static ItemStack cloneItem(ItemStack item) {
            return item == null ? null : item.clone();
        }
    }

    private static final class PvpSession {
        private final UUID playerId;
        private final String originWorld;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final float originYaw;
        private final float originPitch;
        private final GameMode originGameMode;
        private final InventorySnapshot originInventory;
        private String state;
        private InventorySnapshot arenaInventory;
        private InventorySnapshot returnInventory;
        private Location lastArenaLocation;

        private PvpSession(UUID playerId, String originWorld, double originX, double originY,
                           double originZ, float originYaw, float originPitch, GameMode originGameMode,
                           InventorySnapshot originInventory, String state,
                           InventorySnapshot arenaInventory, InventorySnapshot returnInventory,
                           Location lastArenaLocation) {
            this.playerId = playerId;
            this.originWorld = originWorld;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.originYaw = originYaw;
            this.originPitch = originPitch;
            this.originGameMode = originGameMode;
            this.originInventory = originInventory;
            this.state = state;
            this.arenaInventory = arenaInventory;
            this.returnInventory = returnInventory;
            this.lastArenaLocation = lastArenaLocation;
        }

        private static PvpSession begin(Player player) {
            Location origin = player.getLocation();
            return new PvpSession(player.getUniqueId(), origin.getWorld().getName(),
                    origin.getX(), origin.getY(), origin.getZ(), origin.getYaw(), origin.getPitch(),
                    player.getGameMode(), InventorySnapshot.capture(player), PREPARING, null, null, null);
        }

        private Location originLocation() {
            World world = Bukkit.getWorld(originWorld);
            return world == null ? null : new Location(world, originX, originY, originZ,
                    originYaw, originPitch);
        }
    }

    private static final class PvpSessionStore {
        private final TWMinasPlugin plugin;
        private final File file;
        private final Map<UUID, PvpSession> sessions = new HashMap<>();

        private PvpSessionStore(TWMinasPlugin plugin) {
            this.plugin = plugin;
            this.file = new File(plugin.getDataFolder(), "pvp-sessions.yml");
            load();
        }

        private PvpSession get(UUID playerId) {
            return sessions.get(playerId);
        }

        private boolean put(PvpSession session) {
            sessions.put(session.playerId, session);
            if (save()) return true;
            sessions.remove(session.playerId);
            return false;
        }

        private void remove(UUID playerId) {
            PvpSession previous = sessions.remove(playerId);
            if (!save() && previous != null) sessions.put(playerId, previous);
        }

        private boolean save() {
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    new org.bukkit.configuration.file.YamlConfiguration();
            for (PvpSession session : sessions.values()) {
                String path = "sessions." + session.playerId;
                yaml.set(path + ".origin-world", session.originWorld);
                yaml.set(path + ".origin-x", session.originX);
                yaml.set(path + ".origin-y", session.originY);
                yaml.set(path + ".origin-z", session.originZ);
                yaml.set(path + ".origin-yaw", session.originYaw);
                yaml.set(path + ".origin-pitch", session.originPitch);
                yaml.set(path + ".origin-gamemode", session.originGameMode.name());
                yaml.set(path + ".state", session.state);
                writeSnapshot(yaml, path + ".origin-inventory", session.originInventory);
                writeSnapshot(yaml, path + ".arena-inventory", session.arenaInventory);
                writeSnapshot(yaml, path + ".return-inventory", session.returnInventory);
                if (session.lastArenaLocation != null) {
                    yaml.set(path + ".arena-world", session.lastArenaLocation.getWorld().getName());
                    yaml.set(path + ".arena-x", session.lastArenaLocation.getX());
                    yaml.set(path + ".arena-y", session.lastArenaLocation.getY());
                    yaml.set(path + ".arena-z", session.lastArenaLocation.getZ());
                    yaml.set(path + ".arena-yaw", session.lastArenaLocation.getYaw());
                    yaml.set(path + ".arena-pitch", session.lastArenaLocation.getPitch());
                }
            }

            Path target = file.toPath();
            Path temporary = target.resolveSibling(file.getName() + ".tmp");
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao salvar " + file.getName() + ".", e);
                return false;
            }
        }

        private void load() {
            if (!file.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    new org.bukkit.configuration.file.YamlConfiguration();
            try {
                yaml.load(file);
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                throw new IllegalStateException("Não foi possível carregar as sessões da Mina PvP.", e);
            }
            org.bukkit.configuration.ConfigurationSection root = yaml.getConfigurationSection("sessions");
            if (root == null) return;
            for (String key : root.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Ignorando sessão PvP com UUID inválido: " + key);
                    continue;
                }
                String path = "sessions." + key;
                String originWorld = yaml.getString(path + ".origin-world");
                String state = yaml.getString(path + ".state");
                InventorySnapshot originInventory = readSnapshot(yaml, path + ".origin-inventory");
                String gameModeName = yaml.getString(path + ".origin-gamemode", GameMode.SURVIVAL.name());
                GameMode originGameMode;
                try {
                    originGameMode = GameMode.valueOf(gameModeName);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Gamemode inválido na sessão PvP de " + playerId
                            + "; usando SURVIVAL.");
                    originGameMode = GameMode.SURVIVAL;
                }
                if (originWorld == null || state == null || originInventory == null) {
                    plugin.getLogger().warning("Ignorando sessão PvP incompleta para " + playerId + ".");
                    continue;
                }
                Location lastArena = null;
                String arenaWorld = yaml.getString(path + ".arena-world");
                World loadedWorld = arenaWorld == null ? null : Bukkit.getWorld(arenaWorld);
                if (loadedWorld != null) {
                    lastArena = new Location(loadedWorld, yaml.getDouble(path + ".arena-x"),
                            yaml.getDouble(path + ".arena-y"), yaml.getDouble(path + ".arena-z"),
                            (float) yaml.getDouble(path + ".arena-yaw"),
                            (float) yaml.getDouble(path + ".arena-pitch"));
                }
                PvpSession session = new PvpSession(playerId, originWorld,
                        yaml.getDouble(path + ".origin-x"), yaml.getDouble(path + ".origin-y"),
                        yaml.getDouble(path + ".origin-z"), (float) yaml.getDouble(path + ".origin-yaw"),
                        (float) yaml.getDouble(path + ".origin-pitch"), originGameMode, originInventory, state,
                        readSnapshot(yaml, path + ".arena-inventory"),
                        readSnapshot(yaml, path + ".return-inventory"), lastArena);
                sessions.put(playerId, session);
            }
        }

        private void writeSnapshot(org.bukkit.configuration.file.YamlConfiguration yaml,
                                   String path, InventorySnapshot snapshot) {
            if (snapshot == null) return;
            yaml.set(path + ".storage-size", snapshot.storage.length);
            for (int i = 0; i < snapshot.storage.length; i++) {
                if (snapshot.storage[i] != null && snapshot.storage[i].getType() != Material.AIR) {
                    yaml.set(path + ".storage." + i, snapshot.storage[i]);
                }
            }
            yaml.set(path + ".armor-size", snapshot.armor.length);
            for (int i = 0; i < snapshot.armor.length; i++) {
                if (snapshot.armor[i] != null && snapshot.armor[i].getType() != Material.AIR) {
                    yaml.set(path + ".armor." + i, snapshot.armor[i]);
                }
            }
            if (snapshot.offhand != null && snapshot.offhand.getType() != Material.AIR) {
                yaml.set(path + ".offhand", snapshot.offhand);
            }
        }

        private InventorySnapshot readSnapshot(org.bukkit.configuration.file.YamlConfiguration yaml,
                                               String path) {
            int storageSize = yaml.getInt(path + ".storage-size", -1);
            int armorSize = yaml.getInt(path + ".armor-size", -1);
            if (storageSize < 0 || storageSize > 100 || armorSize < 0 || armorSize > 10) return null;
            ItemStack[] storage = new ItemStack[storageSize];
            ItemStack[] armor = new ItemStack[armorSize];
            for (int i = 0; i < storageSize; i++) {
                storage[i] = yaml.getItemStack(path + ".storage." + i);
            }
            for (int i = 0; i < armorSize; i++) {
                armor[i] = yaml.getItemStack(path + ".armor." + i);
            }
            return new InventorySnapshot(storage, armor, yaml.getItemStack(path + ".offhand"));
        }
    }
}
