package br.com.tw.terrenos.managers;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.terrenos.TWTerrenosPlugin;
import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.events.PlayerAutoPlotEvent;
import com.plotsquared.core.events.PlayerAutoPlotsChosenEvent;
import com.plotsquared.core.events.PlayerClaimPlotEvent;
import com.plotsquared.core.events.PlayerPlotAddEvent;
import com.plotsquared.core.events.PlayerPlotLimitEvent;
import com.plotsquared.core.events.PlayerPlotTrustEvent;
import com.plotsquared.core.events.Result;
import com.plotsquared.core.events.post.PostPlayerAutoPlotEvent;
import com.plotsquared.core.events.post.PostPlayerPlotAddedEvent;
import com.plotsquared.core.events.post.PostPlayerPlotTrustedEvent;
import com.plotsquared.core.events.post.PostPlotChangeOwnerEvent;
import com.plotsquared.core.events.post.PostPlotClearEvent;
import com.plotsquared.core.events.post.PostPlotMergeEvent;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlotPurchaseManager implements Listener {
    private static final int RECOVERY_ATTEMPTS = 30;
    private static final long RECOVERY_INTERVAL_TICKS = 20L;

    private final TWTerrenosPlugin plugin;
    private final String plotWorld;
    private final double basePrice;
    private final double priceMultiplier;
    private final int defaultPlotLimit;
    private final int prestigePlotBonus;
    private final int memberLimit;
    private final long purchaseTimeoutTicks;
    private final Map<String, Integer> plotLimits = new HashMap<>();
    private final Map<UUID, PurchaseAttempt> pendingPurchases = new HashMap<>();
    private final Map<UUID, PermissionAttachment> plotCommandPermissions = new HashMap<>();
    private final Set<UUID> awaitingVisitName = ConcurrentHashMap.newKeySet();
    private final PlotPurchaseRepository repository;
    private final PlotAPI plotApi = new PlotAPI();

    public PlotPurchaseManager(TWTerrenosPlugin plugin) {
        this.plugin = plugin;
        this.plotWorld = plugin.getConfig().getString("plot-world", "terrenos");
        this.basePrice = plugin.getConfig().getDouble("purchase.base-price", 50000.0);
        this.priceMultiplier = plugin.getConfig().getDouble("purchase.price-multiplier", 2.0);
        this.defaultPlotLimit = plugin.getConfig().getInt("limits.default", 1);
        this.prestigePlotBonus = plugin.getConfig().getInt("limits.prestige-bonus", 1);
        this.memberLimit = plugin.getConfig().getInt("limits.members-per-plot", 10);
        this.purchaseTimeoutTicks = plugin.getConfig().getLong("purchase.timeout-seconds", 180) * 20L;

        if (plotWorld == null || plotWorld.isBlank()) {
            throw new IllegalStateException("A configuração plot-world do tw-terrenos está vazia.");
        }
        if (!Double.isFinite(basePrice) || basePrice < 0 || !Double.isFinite(priceMultiplier) || priceMultiplier < 1) {
            throw new IllegalStateException("A configuração de preços do tw-terrenos é inválida.");
        }
        if (defaultPlotLimit < 1 || prestigePlotBonus < 0 || memberLimit < 0 || purchaseTimeoutTicks < 20) {
            throw new IllegalStateException("Os limites ou o tempo de compra do tw-terrenos são inválidos.");
        }

        if (plugin.getConfig().isConfigurationSection("limits.by-rank")) {
            for (String rank : plugin.getConfig().getConfigurationSection("limits.by-rank").getKeys(false)) {
                int limit = plugin.getConfig().getInt("limits.by-rank." + rank, -1);
                if (limit < 1) {
                    throw new IllegalStateException("Limite inválido em limits.by-rank." + rank + ".");
                }
                plotLimits.put(rank.toUpperCase(Locale.ROOT), limit);
            }
        }

        repository = new PlotPurchaseRepository(TWCorePlugin.getInstance().getDatabaseManager());
        plotApi.registerListener(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void purchase(Player player) {
        if (!plotWorld.equals(player.getWorld().getName())) {
            World world = plugin.getServer().getWorld(plotWorld);
            if (world == null) {
                plugin.getLogger().severe("O mundo configurado para terrenos '" + plotWorld + "' não está carregado.");
                player.sendMessage(ChatColor.RED + "O mundo de terrenos está indisponível no momento.");
                return;
            }

            player.sendMessage(ChatColor.YELLOW + "Teleportando para o mundo de terrenos para concluir a compra...");
            player.teleportAsync(world.getSpawnLocation()).whenComplete((teleported, failure) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (failure != null) {
                            plugin.getLogger().log(Level.SEVERE,
                                    "Falha ao teleportar " + player.getName() + " para o mundo de terrenos.", failure);
                            player.sendMessage(ChatColor.RED + "Não foi possível teleportar. Nenhum valor foi cobrado.");
                            return;
                        }
                        if (!Boolean.TRUE.equals(teleported) || !plotWorld.equals(player.getWorld().getName())) {
                            player.sendMessage(ChatColor.RED + "Não foi possível acessar o mundo de terrenos. Nenhum valor foi cobrado.");
                            return;
                        }
                        purchase(player);
                    }));
            return;
        }

        UUID playerId = player.getUniqueId();
        if (pendingPurchases.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "Sua compra de terreno já está sendo processada.");
            return;
        }

        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(playerId);
        if (user == null) {
            player.sendMessage(ChatColor.RED + "Não foi possível carregar seus dados. Tente novamente mais tarde.");
            return;
        }

        PlotPlayer<?> plotPlayer = PlotPlayer.from(player);
        int ownedPlots = plotPlayer.getPlotCount(plotWorld);
        int limit = getPlotLimit(user);
        if (!player.hasPermission("tw-terrenos.admin") && ownedPlots >= limit) {
            player.sendMessage(ChatColor.RED + "Seu rank permite até " + limit + " terrenos. "
                    + "Consulte /terreno para ver as opções disponíveis.");
            return;
        }

        double price;
        try {
            price = PlotPricing.nextPrice(basePrice, priceMultiplier, ownedPlots);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.SEVERE, "Não foi possível calcular o preço para " + player.getName() + ".", exception);
            player.sendMessage(ChatColor.RED + "Não foi possível calcular o preço. Avise a equipe.");
            return;
        }

        if (!TWCorePlugin.getInstance().getUserManager().flushUsersSync()) {
            player.sendMessage(ChatColor.RED + "Não foi possível confirmar seu saldo. Tente novamente mais tarde.");
            return;
        }

        PlotPurchaseRepository.PurchaseDebit debit;
        try {
            debit = repository.debitForPurchase(playerId, player.getName(), plotWorld, price, ownedPlots);
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao iniciar compra de terreno de " + player.getName() + ".", exception);
            player.sendMessage(ChatColor.RED + "Não foi possível iniciar a compra. Nenhum valor foi cobrado.");
            return;
        }
        if (debit == null) {
            player.sendMessage(ChatColor.RED + "Seu saldo mudou ou é insuficiente para esta compra.");
            return;
        }

        user.setBalance(debit.getBalance());
        TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
        PurchaseAttempt attempt = new PurchaseAttempt(user, debit.getId(), price, ownedPlots);
        pendingPurchases.put(playerId, attempt);
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("plots.auto", true);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> reconcilePurchase(playerId, attempt), purchaseTimeoutTicks);

        try {
            attempt.commandExecuting = true;
            player.performCommand("plotsquared:plots auto");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao processar a compra de terreno para " + player.getName() + ".", exception);
            refundIfUnclaimed(playerId, attempt);
        } finally {
            attempt.commandExecuting = false;
            attachment.remove();
        }

        if (pendingPurchases.containsKey(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "Compra em processamento. O valor será confirmado quando o terreno for adquirido.");
        }
    }

    public double getNextPrice(Player player) {
        int ownedPlots = PlotPlayer.from(player).getPlotCount(plotWorld);
        return PlotPricing.nextPrice(basePrice, priceMultiplier, ownedPlots);
    }

    public int getOwnedPlotCount(Player player) {
        return PlotPlayer.from(player).getPlotCount(plotWorld);
    }

    public int getPlotLimit(Player player) {
        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
        return user == null ? defaultPlotLimit : getPlotLimit(user);
    }

    public List<String> getAudit(String playerName) {
        return repository.getAudit(playerName, 50);
    }

    public void teleportHome(Player player) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlotSquared")) {
            player.sendMessage(ChatColor.RED + "O sistema de terrenos está temporariamente indisponível.");
            return;
        }
        player.performCommand("plotsquared:plots home");
    }

    public void visitPlayer(Player player, String targetName) {
        if (!targetName.matches("[A-Za-z0-9_]{1,16}")) {
            player.sendMessage(ChatColor.RED + "Nome inválido. Use apenas letras, números e sublinhado.");
            return;
        }
        player.performCommand("plotsquared:plots visit " + targetName);
    }

    public void beginVisitSearch(Player player) {
        awaitingVisitName.add(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "Digite o nome do jogador que deseja visitar no chat. Digite cancelar para voltar.");
    }

    @Subscribe
    public void onAutoPlot(PlayerAutoPlotEvent event) {
        if (!plotWorld.equalsIgnoreCase(event.getPlotArea().getWorldName())) {
            return;
        }

        UUID playerId = event.getPlayer().getUUID();
        PurchaseAttempt attempt = pendingPurchases.get(playerId);
        if (attempt == null || !attempt.debited || !attempt.commandExecuting) {
            event.setEventResult(Result.DENY);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Adquira terrenos pelo menu /terreno.");
            }
        }
    }

    @Subscribe
    public void onClaimPlot(PlayerClaimPlotEvent event) {
        if (!plotWorld.equalsIgnoreCase(event.getPlot().getWorldName())) {
            return;
        }
        UUID playerId = event.getPlotPlayer().getUUID();
        PurchaseAttempt attempt = pendingPurchases.get(playerId);
        if (attempt == null || !attempt.debited) {
            event.setEventResult(Result.DENY);
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "A compra e a aquisição de terrenos devem ser feitas pelo menu /terreno.");
            }
        }
    }

    @Subscribe
    public void onPlotLimit(PlayerPlotLimitEvent event) {
        if (!plotWorld.equalsIgnoreCase(event.player().getLocation().getWorldName())) {
            return;
        }
        Player player = Bukkit.getPlayer(event.player().getUUID());
        if (player != null && player.hasPermission("tw-terrenos.admin")) {
            return;
        }
        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(event.player().getUUID());
        event.limit(user == null ? defaultPlotLimit : getPlotLimit(user));
    }

    @Subscribe
    public void onPlotAdd(PlayerPlotAddEvent event) {
        enforceMemberLimit(event.getPlot(), event.getPlayer(), event.getInitiator().getUUID(), event);
    }

    @Subscribe
    public void onPlotTrust(PlayerPlotTrustEvent event) {
        enforceMemberLimit(event.getPlot(), event.getPlayer(), event.getInitiator().getUUID(), event);
    }

    @Subscribe
    public void onPlotAdded(PostPlayerPlotAddedEvent event) {
        recordAccessChange("MEMBER_ADD", event.getPlot(), event.getInitiator().getUUID(), event.getPlayer(), true);
    }

    @Subscribe
    public void onPlotTrusted(PostPlayerPlotTrustedEvent event) {
        recordAccessChange("MEMBER_TRUST", event.getPlot(), event.getInitiator().getUUID(),
                event.getPlayer(), event.added());
    }

    @Subscribe
    public void onPlotOwnerChanged(PostPlotChangeOwnerEvent event) {
        UUID newOwner = event.getPlot().getOwnerAbs();
        try {
            repository.recordAudit("OWNER_CHANGE", event.getPlotPlayer().getUUID(),
                    nameOf(event.getPlotPlayer().getUUID()), newOwner, nameOf(newOwner),
                    event.getPlot().getWorldName(), event.getPlot().getId().toString(),
                    "Proprietário anterior: " + (event.hasOldOwner() ? event.getOldOwner() : "nenhum"));
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao auditar a troca de proprietário do terreno "
                    + event.getPlot().getId() + ".", exception);
        }
    }

    @Subscribe
    public void onPlotMerged(PostPlotMergeEvent event) {
        try {
            repository.recordAudit("PLOT_MERGE", event.getPlotPlayer().getUUID(),
                    nameOf(event.getPlotPlayer().getUUID()), null, null, event.getPlot().getWorldName(),
                    event.getPlot().getId().toString(), "Terreno unido a lotes adjacentes.");
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao auditar a união do terreno "
                    + event.getPlot().getId() + ".", exception);
        }
    }

    @Subscribe
    public void onPlotCleared(PostPlotClearEvent event) {
        recordPlotAction("PLOT_CLEAR", event.getPlotPlayer().getUUID(), event.getPlot(),
                "Conteúdo do terreno removido.");
    }

    @Subscribe
    public void onAutoPlotsChosen(PlayerAutoPlotsChosenEvent event) {
        UUID playerId = event.getPlotPlayer().getUUID();
        PurchaseAttempt attempt = pendingPurchases.get(playerId);
        if (attempt != null && attempt.debited && event.getPlots().isEmpty()) {
            refundIfUnclaimed(playerId, attempt);
        }
    }

    @Subscribe
    public void onAutoPlotClaimed(PostPlayerAutoPlotEvent event) {
        UUID playerId = event.getPlotPlayer().getUUID();
        PurchaseAttempt attempt = pendingPurchases.get(playerId);
        if (attempt != null && attempt.debited) {
            try {
                repository.recordAudit("PLOT_CLAIM", playerId, nameOf(playerId), null, null,
                        event.getPlot().getWorldName(), event.getPlot().getId().toString(),
                        "Terreno adquirido pelo menu.");
            } catch (IllegalStateException exception) {
                plugin.getLogger().log(Level.SEVERE, "O terreno foi adquirido, mas não foi possível auditar o claim.",
                        exception);
            }
            completePurchase(playerId, attempt);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        grantPlayerPlotCommands(event.getPlayer());
        recoverPendingPurchases(event.getPlayer(), 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = plotCommandPermissions.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            attachment.remove();
        }
    }

    @EventHandler
    public void onVisitSearch(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!awaitingVisitName.remove(playerId)) {
            return;
        }
        event.setCancelled(true);
        String targetName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return;
            }
            if (targetName.equalsIgnoreCase("cancelar")) {
                player.sendMessage(ChatColor.GRAY + "Busca de visita cancelada.");
                return;
            }
            visitPlayer(player, targetName);
        });
    }

    private void enforceMemberLimit(Plot plot, UUID targetId, UUID actorId,
                                   com.plotsquared.core.events.PlayerPlotAddRemoveCancellableEvent event) {
        if (!plotWorld.equalsIgnoreCase(plot.getWorldName()) || plot.getMembers().contains(targetId)
                || plot.getOwnerAbs() != null && plot.getOwnerAbs().equals(targetId)) {
            return;
        }
        Player actor = Bukkit.getPlayer(actorId);
        if (actor != null && actor.hasPermission("tw-terrenos.admin")) {
            return;
        }
        if (plot.getMembers().size() >= memberLimit) {
            event.setEventResult(Result.DENY);
            if (actor != null) {
                actor.sendMessage(ChatColor.RED + "Cada terreno pode ter no máximo " + memberLimit + " membros.");
            }
        }
    }

    private void grantPlayerPlotCommands(Player player) {
        PermissionAttachment previous = plotCommandPermissions.remove(player.getUniqueId());
        if (previous != null) {
            previous.remove();
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("plots.plot.7", true);
        attachment.setPermission("plots.trust", true);
        attachment.setPermission("plots.add", true);
        attachment.setPermission("plots.remove", true);
        attachment.setPermission("plots.merge", true);
        attachment.setPermission("plots.set.alias", true);
        attachment.setPermission("plots.set.description", true);
        attachment.setPermission("plots.done", true);
        plotCommandPermissions.put(player.getUniqueId(), attachment);
    }

    private void recordAccessChange(String action, Plot plot, UUID actorId, UUID targetId, boolean added) {
        String actualAction = added ? action : action + "_REMOVE";
        try {
            repository.recordAudit(actualAction, actorId, nameOf(actorId), targetId, nameOf(targetId), plot.getWorldName(),
                    plot.getId().toString(), "Alteração de membros do terreno.");
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao auditar a alteração de acesso ao terreno "
                    + plot.getId() + ".", exception);
        }
    }

    private void recordPlotAction(String action, UUID actorId, Plot plot, String details) {
        try {
            repository.recordAudit(action, actorId, nameOf(actorId), null, null, plot.getWorldName(),
                    plot.getId().toString(), details);
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao auditar a ação " + action
                    + " no terreno " + plot.getId() + ".", exception);
        }
    }

    private String nameOf(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? null : player.getName();
    }

    private int getPlotLimit(TWUser user) {
        String rank = user.getRankId() == null ? "" : user.getRankId().toUpperCase(Locale.ROOT);
        int limit = plotLimits.getOrDefault(rank, defaultPlotLimit);
        String prestige = user.getPrestigeId();
        if (prestige != null && !prestige.equalsIgnoreCase("NENHUM")) {
            limit += prestigePlotBonus;
        }
        return limit;
    }

    private void completePurchase(UUID playerId, PurchaseAttempt attempt) {
        if (!pendingPurchases.containsKey(playerId) || attempt.claimed) {
            return;
        }
        try {
            if (!repository.complete(attempt.purchaseId)) {
                pendingPurchases.remove(playerId, attempt);
                attempt.claimed = true;
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(ChatColor.RED + "A compra expirou antes da confirmação do terreno. Avise a equipe.");
                }
                plugin.getLogger().severe("A compra " + attempt.purchaseId
                        + " já não estava pendente quando o PlotSquared confirmou o terreno de " + playerId + ".");
                return;
            }
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "A compra de terreno " + attempt.purchaseId
                    + " foi concluída no PlotSquared, mas não pôde ser marcada no banco.", exception);
            return;
        }

        attempt.claimed = true;
        pendingPurchases.remove(playerId, attempt);
        recordPurchaseAudit("PURCHASE", playerId, attempt, "Compra concluída.");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
            player.sendMessage(ChatColor.GREEN + "Terreno adquirido por $"
                    + br.com.tw.core.utils.TWFormat.formatarDinheiro(attempt.price) + "!");
        }
    }

    private void reconcilePurchase(UUID playerId, PurchaseAttempt attempt) {
        if (pendingPurchases.get(playerId) != attempt) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && plotWorld.equalsIgnoreCase(player.getWorld().getName())
                && PlotPlayer.from(player).getPlotCount(plotWorld) > attempt.ownedPlotsBefore) {
            completePurchase(playerId, attempt);
            return;
        }
        refundIfUnclaimed(playerId, attempt);
    }

    private void refundIfUnclaimed(UUID playerId, PurchaseAttempt attempt) {
        if (!attempt.debited || attempt.claimed || !pendingPurchases.remove(playerId, attempt)) {
            return;
        }
        Double newBalance;
        try {
            newBalance = repository.refund(attempt.purchaseId);
        } catch (IllegalStateException exception) {
            pendingPurchases.put(playerId, attempt);
            plugin.getLogger().log(Level.SEVERE, "Falha ao devolver o valor da compra " + attempt.purchaseId
                    + "; ela continuará pendente para nova tentativa.", exception);
            return;
        }
        if (newBalance == null) {
            return;
        }
        attempt.user.setBalance(newBalance);
        TWCorePlugin.getInstance().getUserManager().saveUserAsync(attempt.user);
        recordPurchaseAudit("REFUND", playerId, attempt, "Compra não concluída; valor devolvido.");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(ChatColor.RED + "Não foi possível adquirir o terreno. O valor foi devolvido.");
        }
    }

    private void recoverPendingPurchases(Player player, int attemptNumber) {
        if (!player.isOnline()) {
            return;
        }
        if (TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId()) == null
                && attemptNumber < RECOVERY_ATTEMPTS) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> recoverPendingPurchases(player, attemptNumber + 1), RECOVERY_INTERVAL_TICKS);
            return;
        }
        if (TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId()) == null) {
            plugin.getLogger().warning("A recuperação de compras pendentes de " + player.getName()
                    + " foi adiada porque os dados do TW-Core não carregaram.");
            return;
        }

        try {
            for (PlotPurchaseRepository.PendingPurchase purchase : repository.findPending(player.getUniqueId())) {
                PurchaseAttempt activeAttempt = pendingPurchases.get(player.getUniqueId());
                if (plotWorld.equalsIgnoreCase(purchase.getWorld())
                        && PlotPlayer.from(player).getPlotCount(plotWorld) > purchase.getPlotsBefore()) {
                    if (activeAttempt != null && activeAttempt.purchaseId == purchase.getId()) {
                        completePurchase(player.getUniqueId(), activeAttempt);
                    } else if (repository.complete(purchase.getId())) {
                        repository.recordAudit("PURCHASE_RECOVERED", player.getUniqueId(), player.getName(),
                                null, null, plotWorld, null, "Compra #" + purchase.getId()
                                        + " confirmada após reinício.");
                        player.sendMessage(ChatColor.GREEN + "Sua compra de terreno pendente foi confirmada.");
                    }
                } else {
                    if (activeAttempt != null && activeAttempt.purchaseId == purchase.getId()) {
                        refundIfUnclaimed(player.getUniqueId(), activeAttempt);
                        continue;
                    }
                    Double newBalance = repository.refund(purchase.getId());
                    if (newBalance != null) {
                        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
                        user.setBalance(newBalance);
                        TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
                        try {
                            repository.recordAudit("REFUND_RECOVERED", player.getUniqueId(), player.getName(),
                                    null, null, purchase.getWorld(), null, "Reembolso da compra #" + purchase.getId()
                                            + " após reinício.");
                        } catch (IllegalStateException auditFailure) {
                            plugin.getLogger().log(Level.SEVERE, "O reembolso recuperado foi feito, mas não pôde ser auditado.",
                                    auditFailure);
                        }
                        player.sendMessage(ChatColor.YELLOW + "Uma compra de terreno não concluída foi reembolsada.");
                    }
                }
            }
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao recuperar compras pendentes de "
                    + player.getName() + ".", exception);
            player.sendMessage(ChatColor.RED + "Não foi possível recuperar uma compra pendente. Avise a equipe.");
        }
    }

    private void recordPurchaseAudit(String action, UUID playerId, PurchaseAttempt attempt, String details) {
        try {
            repository.recordAudit(action, playerId, attempt.user.getName(), null, null, plotWorld, null,
                    details + " Compra #" + attempt.purchaseId + " por $"
                            + br.com.tw.core.utils.TWFormat.formatarDinheiro(attempt.price));
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.SEVERE, "A compra " + attempt.purchaseId
                    + " foi processada, mas não pôde ser registrada no log de auditoria.", exception);
        }
    }

    private static final class PurchaseAttempt {
        private final TWUser user;
        private final long purchaseId;
        private final double price;
        private final int ownedPlotsBefore;
        private boolean debited = true;
        private boolean claimed;
        private boolean commandExecuting;

        private PurchaseAttempt(TWUser user, long purchaseId, double price, int ownedPlotsBefore) {
            this.user = user;
            this.purchaseId = purchaseId;
            this.price = price;
            this.ownedPlotsBefore = ownedPlotsBefore;
        }
    }
}
