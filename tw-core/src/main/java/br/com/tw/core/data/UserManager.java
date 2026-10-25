package br.com.tw.core.data;

import br.com.tw.core.TWCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class UserManager implements Listener {

    private final Map<UUID, TWUser> usersCache = new ConcurrentHashMap<>();
    private final UserRepository repository;
    private final TWCorePlugin plugin;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tw-core-player-saves");
        thread.setDaemon(true);
        return thread;
    });

    public UserManager(TWCorePlugin plugin, UserRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        startAutoSaveTask();
    }

    private void startAutoSaveTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<PendingSave> pendingSaves = new ArrayList<>();
            for (TWUser user : usersCache.values()) {
                if (user.isDirty()) {
                    pendingSaves.add(new PendingSave(user, user.snapshot()));
                }
            }
            if (!pendingSaves.isEmpty()) {
                enqueueSave(() -> saveAll(pendingSaves));
            }
        }, 2400L, 2400L);
    }

    public TWUser getUser(UUID uuid) {
        return usersCache.get(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.util.Optional<TWUser> loaded = repository.loadUser(uuid, name);
                TWUser user = loaded.orElseGet(() -> {
                    TWUser newUser = new TWUser(uuid, name, 0L, "CARVAO", "NENHUM");
                    newUser.setDirty(true);
                    return newUser;
                });
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(uuid);
                    if (onlinePlayer == null || !onlinePlayer.isOnline()) return;
                    usersCache.put(uuid, user);
                    plugin.syncLuckPermsGroups(name, user);
                    if (!loaded.isPresent()) {
                        saveUserAsync(user);
                    }
                });
            } catch (IllegalStateException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(uuid);
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        onlinePlayer.sendMessage("§cNão foi possível carregar os teus dados. Tenta reconectar mais tarde.");
                    }
                });
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        TWUser user = usersCache.remove(uuid);
        if (user != null) {
            saveUserAsync(user);
        }
    }

    public void saveAllSync() {
        saveExecutor.shutdown();
        boolean interrupted = false;
        while (!saveExecutor.isTerminated()) {
            try {
                if (!saveExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    plugin.getLogger().severe("Ainda aguardando salvamentos pendentes do TW-Core.");
                }
            } catch (InterruptedException e) {
                interrupted = true;
                saveExecutor.shutdownNow();
                plugin.getLogger().log(Level.SEVERE,
                        "Interrompido ao aguardar salvamentos do TW-Core; aguardando a conclusão antes de fechar o banco.", e);
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        List<PendingSave> pendingSaves = new ArrayList<>();
        for (TWUser user : usersCache.values()) {
            pendingSaves.add(new PendingSave(user, user.snapshot()));
        }
        for (PendingSave pendingSave : pendingSaves) {
            try {
                repository.saveUser(pendingSave.snapshot);
                pendingSave.user.markSaved(pendingSave.snapshot.getVersion());
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar um jogador durante o desligamento.", e);
            }
        }
        usersCache.clear();
    }

    public void saveUserAsync(TWUser user) {
        TWUserSnapshot snapshot = user.snapshot();
        enqueueSave(() -> save(new PendingSave(user, snapshot)));
    }

    public boolean flushUsersSync() {
        Future<Boolean> flush;
        try {
            flush = saveExecutor.submit(() -> {
                for (TWUser user : usersCache.values()) {
                    PendingSave pendingSave = new PendingSave(user, user.snapshot());
                    try {
                        repository.saveUser(pendingSave.snapshot);
                        user.markSaved(pendingSave.snapshot.getVersion());
                    } catch (IllegalStateException e) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Falha ao sincronizar os dados do jogador " + pendingSave.snapshot.getUuid() + ".", e);
                        return false;
                    }
                }
                return true;
            });
            return flush.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Interrompido ao sincronizar os dados dos jogadores.", e);
            return false;
        } catch (ExecutionException | java.util.concurrent.RejectedExecutionException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível sincronizar os dados dos jogadores.", e);
            return false;
        }
    }

    public boolean flushUserSync(TWUser user) {
        if (user == null) return false;
        TWUserSnapshot snapshot = user.snapshot();
        Future<Boolean> flush;
        try {
            flush = saveExecutor.submit(() -> {
                try {
                    repository.saveUser(snapshot);
                    user.markSaved(snapshot.getVersion());
                    return true;
                } catch (IllegalStateException e) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Falha ao sincronizar os dados do jogador " + snapshot.getUuid() + ".", e);
                    return false;
                }
            });
            return flush.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Interrompido ao sincronizar os dados do jogador.", e);
            return false;
        } catch (ExecutionException | java.util.concurrent.RejectedExecutionException e) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível sincronizar os dados do jogador.", e);
            return false;
        }
    }

    private void enqueueSave(Runnable saveOperation) {
        try {
            saveExecutor.execute(saveOperation);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            plugin.getLogger().log(Level.SEVERE, "Salvamento solicitado após o encerramento do executor do TW-Core.", e);
        }
    }

    private void saveAll(List<PendingSave> pendingSaves) {
        for (PendingSave pendingSave : pendingSaves) {
            save(pendingSave);
        }
    }

    private void save(PendingSave pendingSave) {
        try {
            repository.saveUser(pendingSave.snapshot);
            pendingSave.user.markSaved(pendingSave.snapshot.getVersion());
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Salvamento pendente para o jogador " + pendingSave.snapshot.getUuid() + ".", e);
        }
    }

    public Map<String, Double> getTopBalances(int limit) {
        return repository.getTopBalances(limit);
    }

    public boolean updateOfflineBalance(String playerName, double amount, String action) {
        if (playerName == null || !Double.isFinite(amount) || amount < 0 || action == null) return false;
        for (TWUser user : usersCache.values()) {
            if (!user.getName().equalsIgnoreCase(playerName)) continue;
            switch (action.toLowerCase()) {
                case "give":
                case "add":
                    user.addBalance(amount);
                    break;
                case "take":
                case "remove":
                    user.removeBalance(Math.min(amount, user.getBalance()));
                    break;
                case "set":
                    user.setBalance(amount);
                    break;
                default:
                    return false;
            }
            saveUserAsync(user);
            return true;
        }
        return repository.updateOfflineBalance(playerName, amount, action);
    }

    public Map<String, Long> getTopMiners(int limit) {
        return repository.getTopMiners(limit);
    }

    public UserRepository getRepository() {
        return this.repository;
    }

    private static final class PendingSave {
        private final TWUser user;
        private final TWUserSnapshot snapshot;

        private PendingSave(TWUser user, TWUserSnapshot snapshot) {
            this.user = user;
            this.snapshot = snapshot;
        }
    }
}
