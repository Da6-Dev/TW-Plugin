package br.com.tw.spawners.listeners;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.menus.TWConfirmationMenu;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.core.utils.TWItemTags;
import br.com.tw.economia.TWEconomiaPlugin;
import br.com.tw.spawners.TWSpawnersPlugin;
import br.com.tw.spawners.managers.SpawnerManager;
import br.com.tw.spawners.menus.SpawnerPanelMenu;
import br.com.tw.spawners.models.PlacedSpawner;
import br.com.tw.spawners.models.SpawnerType;
import br.com.tw.spawners.utils.SpawnerActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerListener implements Listener {

    private final SpawnerManager manager;

    // Mapa para o sistema de compra com chat
    public static final Map<UUID, SpawnerType> pendingPurchases = new ConcurrentHashMap<>();

    public SpawnerListener(SpawnerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatPurchase(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingPurchases.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            SpawnerType type = pendingPurchases.remove(player.getUniqueId());

            if (event.getMessage().equalsIgnoreCase("cancelar")) {
                player.sendMessage("§cCompra cancelada.");
                return;
            }

            try {
                long amount = Long.parseLong(event.getMessage());
                if (amount <= 0) throw new NumberFormatException();

                // Volta para a thread principal para dar os itens e remover saldo com segurança
                Bukkit.getScheduler().runTask(TWSpawnersPlugin.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    if (player.getInventory().firstEmpty() == -1) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        player.sendMessage(ChatColor.RED + "O teu inventário está cheio!");
                        return;
                    }

                    TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(player.getUniqueId());
                    double totalCost = type.getBuyCost() * amount;
                    if (!Double.isFinite(totalCost)) {
                        player.sendMessage(ChatColor.RED + "O valor total da compra excede o limite permitido.");
                        return;
                    }
                    if (user == null) {
                        player.sendMessage(ChatColor.RED + "Os teus dados ainda estão a ser carregados.");
                        return;
                    }
                    Runnable purchase = () -> completeSpawnerPurchase(player, user, type, amount, totalCost);
                    double threshold = TWSpawnersPlugin.getInstance().getConfig()
                            .getDouble("confirmation.price-threshold", 250000.0);
                    if (!Double.isFinite(threshold) || threshold < 0.0) {
                        throw new IllegalStateException("Valor inválido em confirmation.price-threshold.");
                    }
                    if (totalCost >= threshold) {
                        new TWConfirmationMenu(player,
                                "Comprar " + amount + "x " + type.getDisplayName() + " por $"
                                        + TWFormat.formatarDinheiro(totalCost) + "?",
                                purchase).open();
                    } else {
                        purchase.run();
                    }
                });

            } catch (NumberFormatException e) {
                player.sendMessage("§cQuantidade inválida. Digite um inteiro positivo ou 'cancelar'.");
            }
        }
    }

    private void completeSpawnerPurchase(Player player, TWUser user, SpawnerType type,
                                         long amount, double totalCost) {
        if (!player.isOnline()) return;
        if (amount < 1L || !Double.isFinite(totalCost) || totalCost < 0.0) {
            player.sendMessage(ChatColor.RED + "Os dados da compra expiraram ou são inválidos.");
            return;
        }
        if (!manager.hasRequiredRank(user.getRankId(), user.getPrestigeId(), type.getRequiredRank())) {
            player.sendMessage(ChatColor.RED + "O teu rank já não permite comprar este spawner.");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "O teu inventário está cheio.");
            return;
        }
        if (!user.removeBalance(totalCost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(ChatColor.RED + "Faltam $" + TWFormat.formatarDinheiro(totalCost - user.getBalance()) + ".");
            return;
        }
        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
            refund(player, user, totalCost, "compra de spawner");
            player.sendMessage(ChatColor.RED + "Compra cancelada: o saldo não pôde ser persistido.");
            return;
        }
        ItemStack[] previousContents = snapshotInventoryContents(player);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(manager.createSpawnerItem(type, amount));
        if (!leftovers.isEmpty()) {
            player.getInventory().setContents(previousContents);
            refund(player, user, totalCost, "compra de spawner com inventário cheio");
            player.sendMessage(ChatColor.RED + "Inventário cheio; a compra foi cancelada e o saldo devolvido.");
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.sendMessage(ChatColor.GREEN + "Compraste " + amount + "x " + type.getDisplayName()
                + ChatColor.GREEN + " por $" + TWFormat.formatarDinheiro(totalCost) + "!");
    }

    private void refund(Player player, TWUser user, double amount, String reason) {
        user.addBalance(amount);
        if (!TWCorePlugin.getInstance().getUserManager().flushUserSync(user)) {
            TWSpawnersPlugin.getInstance().getLogger().severe(
                    "Falha ao salvar o reembolso por " + reason + " para " + player.getUniqueId() + ".");
            player.sendMessage(ChatColor.RED + "O reembolso precisa de assistência da equipe; não saias do servidor.");
        }
    }

    private ItemStack[] snapshotInventoryContents(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null) contents[slot] = contents[slot].clone();
        }
        return contents;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingPurchases.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (hand == null) return;

        String typeId;
        long itemStack;
        int speedLvl;
        int lootLvl;
        int amtLvl;
        boolean autoSell;
        if (TWItemTags.has(hand, "tw_spawner_type")) {
            typeId = TWItemTags.getString(hand, "tw_spawner_type");
            itemStack = TWItemTags.getLong(hand, "tw_spawner_stack", 0L);
            speedLvl = TWItemTags.getInt(hand, "tw_spawner_speed", 1);
            lootLvl = TWItemTags.getInt(hand, "tw_spawner_loot", 1);
            amtLvl = TWItemTags.getInt(hand, "tw_spawner_amount", 1);
            autoSell = TWItemTags.getBoolean(hand, "tw_spawner_auto_sell", false);
        } else {
            String legacyData = getLegacyData(hand, "TWSPAWNER:");
            if (legacyData == null) return;

            String[] split = legacyData.split(":");
            if (split.length < 3) return;
            typeId = split[1];
            itemStack = parseLong(split[2], "stack do spawner");
            speedLvl = split.length >= 7 ? parseInt(split[3], "velocidade do spawner", 1) : 1;
            lootLvl = split.length >= 7 ? parseInt(split[4], "fortuna do spawner", 1) : 1;
            amtLvl = split.length >= 7 ? parseInt(split[5], "quantidade do spawner", 1) : 1;
            autoSell = split.length >= 7 && Boolean.parseBoolean(split[6]);
        }
        Player player = event.getPlayer();
        if (typeId == null || typeId.isEmpty() || itemStack <= 0 || speedLvl < 1 || lootLvl < 1
                || amtLvl < 1 || speedLvl > manager.getMaxSpeedLevel()
                || lootLvl > manager.getMaxLootLevel() || amtLvl > manager.getMaxAmountLevel()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Os dados deste item de spawner são inválidos.");
            return;
        }
        long stackToPlace = itemStack;

        SpawnerType type = manager.getSpawnerTypes().get(typeId);
        if (type == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Este tipo de spawner não está configurado.");
            return;
        }

        Block block = event.getBlockPlaced();
        PlacedSpawner nearby = manager.findNearbySameSpawner(block.getLocation(), player.getUniqueId(), typeId, 3);
        if (!manager.canPlaceStack(player, block.getLocation(), stackToPlace, nearby != null)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Não podes colocar esse stack aqui: verifica os limites de spawners e o acesso ao terreno.");
            return;
        }

        if (nearby != null) {
            event.setCancelled(true);
            try {
                nearby.addStack(stackToPlace);
            } catch (ArithmeticException e) {
                player.sendMessage(ChatColor.RED + "O stack atingiria o limite numérico permitido.");
                return;
            }

            // Fusão Inteligente: Mantém sempre o maior nível entre o chão e a mão!
            if (speedLvl > nearby.getSpeedLevel()) nearby.setSpeedLevel(speedLvl);
            if (lootLvl > nearby.getLootLevel()) nearby.setLootLevel(lootLvl);
            if (amtLvl > nearby.getAmountLevel()) nearby.setAmountLevel(amtLvl);
            if (autoSell) nearby.setAutoSell(true);

            manager.saveSpawnerAsync(nearby);
            TWSpawnersPlugin.getInstance().getHologramManager().updateOrCreateHologram(nearby, type);

            consumeOne(player, event.getHand());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            player.sendMessage(ChatColor.GREEN + "+" + stackToPlace + "x " + type.getDisplayName() +
                    ChatColor.GREEN + " agrupado! Total: " + ChatColor.YELLOW + nearby.getStackAmount() + "x");
            return;
        }

        PlacedSpawner placed = new PlacedSpawner(
                PlacedSpawner.toKey(block.getLocation()), player.getUniqueId(), player.getName(), typeId, stackToPlace
        );

        // Aplica os atributos recuperados à nova máquina recém-colocada
        placed.setSpeedLevel(speedLvl);
        placed.setLootLevel(lootLvl);
        placed.setAmountLevel(amtLvl);
        placed.setAutoSell(autoSell);

        manager.registerSpawner(placed);

        TWSpawnersPlugin.getInstance().getHologramManager().updateOrCreateHologram(placed, type);
        player.playSound(block.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
        player.sendMessage(ChatColor.GREEN + "Colocaste " + stackToPlace + "x " + type.getDisplayName() + ChatColor.GREEN + "!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;

        PlacedSpawner spawner = manager.getSpawnerAt(b.getLocation());
        if (spawner == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (TWItemTags.has(hand, "tw_crate_effect")
                && !"free_spawner_upgrade".equals(TWItemTags.getString(hand, "tw_crate_effect"))) return;
        if (hand != null) {
            if ("free_spawner_upgrade".equals(TWItemTags.getString(hand, "tw_crate_effect"))) {
                if (!spawner.getOwnerUuid().equals(player.getUniqueId())
                        && !player.hasPermission("tw-spawners.admin")) {
                    player.sendMessage("§cSó o dono pode usar este talismã.");
                    return;
                }
                long charges = TWItemTags.getLong(hand, "tw_crate_charges", 0L);
                if (charges < 1) {
                    player.sendMessage("§cEste talismã já não tem cargas.");
                    return;
                }
                if (player.isSneaking()) spawner.upgradeLootLevel();
                else spawner.upgradeSpeedLevel();
                manager.saveSpawnerAsync(spawner);
                if (charges == 1) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    java.util.Map<String, Object> tags = new java.util.HashMap<>();
                    tags.put("tw_crate_charges", charges - 1);
                    player.getInventory().setItemInMainHand(TWItemTags.set(hand, tags));
                }
                player.sendMessage(player.isSneaking()
                        ? "§aTalismã aplicado: melhoria de loot grátis."
                        : "§aTalismã aplicado: melhoria de velocidade grátis.");
                return;
            }
            double multiplier;
            int minutes;
            if (TWItemTags.has(hand, "tw_spawner_booster")) {
                multiplier = TWItemTags.getDouble(hand, "tw_booster_multiplier", 0.0);
                minutes = TWItemTags.getInt(hand, "tw_booster_minutes", 0);
            } else {
                String legacyData = getLegacyData(hand, "TWBOOSTER:");
                if (legacyData == null) {
                    new SpawnerPanelMenu(player, spawner).open();
                    return;
                }
                String[] parts = legacyData.split(":");
                if (parts.length < 3) return;
                multiplier = parseDouble(parts[1], "multiplicador do booster");
                minutes = parseInt(parts[2], "duração do booster", 0);
            }
            if (multiplier > 0.0 && minutes > 0) {
                if (!spawner.getOwnerUuid().equals(player.getUniqueId())
                        && !player.hasPermission("tw-spawners.admin")) {
                    player.sendMessage(ChatColor.RED + "Apenas o dono pode aplicar boosters!");
                    return;
                }

                spawner.setBoosterMultiplier(multiplier);
                spawner.setBoosterEndTime((System.currentTimeMillis() / 1000L) + (minutes * 60L));
                manager.saveSpawnerAsync(spawner);

                SpawnerType type = manager.getSpawnerTypes().get(spawner.getTypeId());
                if (type != null) TWSpawnersPlugin.getInstance().getHologramManager().updateOrCreateHologram(spawner, type);

                if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
                else player.getInventory().setItemInMainHand(null);

                player.updateInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2.0f);
                player.sendMessage("§b🔥 Aplicaste um Booster de " + multiplier + "x por " + minutes + " Minutos no spawner!");
                return;
            }
        }

        new SpawnerPanelMenu(player, spawner).open();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;
        if (manager.getSpawnerAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "§lAtenção! §cPara desmontares o spawner, abre o painel dele e clica no botão §4'Desmontar'§c.");
        }
    }

    @EventHandler
    public void onVanillaSpawnerSpawn(SpawnerSpawnEvent event) {
        if (manager.getSpawnerAt(event.getSpawner().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            if (manager.hasMobStack(entity)) {
                if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUFFOCATION) {
                    event.setCancelled(true);
                    return;
                }
                Location anchor = manager.getMobOrigin(entity);
                Location spawnPoint = manager.getMobSpawnLocation(anchor);
                if (spawnPoint == null) return;
                Bukkit.getScheduler().runTaskLater(TWSpawnersPlugin.getInstance(), () -> {
                    if (entity.isValid() && !entity.isDead()) {
                        entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                        entity.teleport(spawnPoint);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStackedEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!manager.hasMobStack(entity)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        long currentStack = manager.getMobStack(entity);
        String typeId = manager.getMobType(entity);
        int lootLevel = manager.getMobLoot(entity);

        Location anchor = manager.getMobOrigin(entity);
        SpawnerType type = manager.getSpawnerTypes().get(typeId);
        if (type == null) return;

        Player killer = entity.getKiller();
        TWUser killerUser = killer == null ? null
                : TWCorePlugin.getInstance().getUserManager().getUser(killer.getUniqueId());
        long killedAmount = killerUser == null ? 0L
                : (killer.isSneaking() ? currentStack : 1L);
        double totalEarned = 0.0;
        if (killedAmount > 0L) {
            int lootingEnchantLvl = killer.getInventory().getItemInMainHand() == null
                    ? 0 : killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
            double swordLootingBonus = 1.0 + (lootingEnchantLvl * 0.15);
            double spawnerLootBonus = 1.0 + ((lootLevel - 1) * 0.10);
            double prestigeMult = manager.getPrestigeMultiplier(killerUser.getPrestigeId());
            double globalBooster = TWEconomiaPlugin.getInstance().getGlobalBooster();
            totalEarned = type.getValuePerKill() * killedAmount * swordLootingBonus
                    * spawnerLootBonus * prestigeMult * globalBooster;
            if (!Double.isFinite(totalEarned) || totalEarned < 0.0
                    || !Double.isFinite(killerUser.getBalance() + totalEarned)) {
                TWSpawnersPlugin.getInstance().getLogger().severe(
                        "Pagamento inválido; stack preservado no spawner " + anchor + ".");
                killedAmount = 0L;
                totalEarned = 0.0;
            }
        }
        br.com.tw.core.events.TWSpawnerKillEvent crateEvent =
                new br.com.tw.core.events.TWSpawnerKillEvent(killer, entity, killedAmount);
        Bukkit.getPluginManager().callEvent(crateEvent);
        long remaining = currentStack - killedAmount;

        if (killer != null && killerUser != null && killedAmount > 0L) {
            TWUser user = killerUser;
            user.addBalance(totalEarned);

            double tokenChance = Math.min(1.0,
                    type.getTokenChance() * (1.0 + ((lootLevel - 1) * 0.05)));
            java.util.Random random = new java.util.Random();
            long tokensWon = br.com.tw.spawners.managers.SpawnerSpawnMath.sampleBinomial(
                    killedAmount, tokenChance, random);
            long soulsWon = br.com.tw.spawners.managers.SpawnerSpawnMath.sampleBinomial(
                    killedAmount, 0.001, random);

            soulsWon = Math.addExact(soulsWon, crateEvent.getBonusSouls());
            if (tokensWon > Long.MAX_VALUE - user.getTokens()) {
                TWSpawnersPlugin.getInstance().getLogger().severe(
                        "Recompensa de tokens excedeu o limite do jogador " + killer.getUniqueId() + ".");
                tokensWon = Long.MAX_VALUE - user.getTokens();
            }
            if (tokensWon > 0) user.addTokens(tokensWon);
            TWCorePlugin.getInstance().getUserManager().saveUserAsync(user);
            if (soulsWon > 0) {
                long soulsRemaining;
                try {
                    soulsRemaining = manager.giveSoulItems(killer, soulsWon);
                } catch (ArithmeticException e) {
                    TWSpawnersPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                            "Overflow ao combinar almas antigas com a recompensa de " + killer.getName() + ".", e);
                    soulsRemaining = soulsWon;
                }
                if (soulsRemaining > 0L) {
                    PlacedSpawner placedSpawner = manager.getSpawnerAt(anchor);
                    if (placedSpawner == null) {
                        TWSpawnersPlugin.getInstance().getLogger().severe(
                                "O inventário ficou sem espaço para " + soulsRemaining + " almas de "
                                        + killer.getName() + "; spawner não registado em " + anchor + ".");
                        killer.getWorld().dropItemNaturally(killer.getLocation(),
                                manager.createSoulItem(soulsRemaining));
                        killer.sendMessage("§eO inventário está cheio; as almas restantes foram deixadas no chão.");
                    } else {
                        try {
                            placedSpawner.setStoredSouls(Math.addExact(
                                    placedSpawner.getStoredSouls(), soulsRemaining));
                            manager.saveSpawnerAsync(placedSpawner);
                            killer.sendMessage("§dO inventário está cheio; §e" + soulsRemaining
                                    + " almas §dforam guardadas no cofre do spawner.");
                        } catch (ArithmeticException e) {
                            TWSpawnersPlugin.getInstance().getLogger().severe(
                                    "O cofre de almas excedeu o limite numérico no spawner "
                                            + placedSpawner.getLocationKey() + ".");
                            killer.getWorld().dropItemNaturally(killer.getLocation(),
                                    manager.createSoulItem(soulsRemaining));
                            killer.sendMessage("§eO cofre atingiu o limite; as almas restantes foram deixadas no chão.");
                        }
                    }
                }
            }

            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.8f);

            SpawnerActionBarUtil.send(killer, "§c⚔ Abate: §a+$" + TWFormat.formatarDinheiro(totalEarned)
                    + (tokensWon > 0 ? " §8| §b+" + tokensWon + " T" : "")
                    + (soulsWon > 0 ? " §8| §d+" + soulsWon + " Almas" : ""));
        }

        if (remaining > 0) {
            Location spawnPoint = manager.getMobSpawnLocation(anchor);
            if (spawnPoint == null) return;
            LivingEntity next = (LivingEntity) entity.getWorld().spawnEntity(spawnPoint, type.getEntityType());
            next.setRemoveWhenFarAway(false); next.setCanPickupItems(false);
            next.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 250, false, false));
            next.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 250, false, false));
            manager.updateEntityStack(next, type, remaining, lootLevel, anchor);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) if (manager.getSpawnerAt(block.getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) if (manager.getSpawnerAt(block.getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.getSpawnerAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> manager.getSpawnerAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBurn(BlockBurnEvent event) {
        if (manager.getSpawnerAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeSpawnerBlock(EntityChangeBlockEvent event) {
        if (manager.getSpawnerAt(event.getBlock().getLocation()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.onChunkLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        manager.onChunkUnload(event.getChunk());
        TWSpawnersPlugin.getInstance().getHologramManager().onChunkUnload(event.getChunk());
    }

    private void consumeOne(Player player, org.bukkit.inventory.EquipmentSlot hand) {
        ItemStack held = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private String getLegacyData(ItemStack item, String prefix) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        List<String> lore = item.getItemMeta().getLore();
        if (lore.isEmpty()) return null;

        String lastLine = ChatColor.stripColor(lore.get(lore.size() - 1));
        return lastLine.startsWith(prefix) ? lastLine : null;
    }

    private long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            TWSpawnersPlugin.getInstance().getLogger()
                    .warning("Dados antigos inválidos no item (" + field + "): " + value);
            return 0L;
        }
    }

    private int parseInt(String value, String field, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            TWSpawnersPlugin.getInstance().getLogger()
                    .warning("Dados antigos inválidos no item (" + field + "): " + value);
            return fallback;
        }
    }

    private double parseDouble(String value, String field) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            TWSpawnersPlugin.getInstance().getLogger()
                    .warning("Dados antigos inválidos no item (" + field + "): " + value);
            return 0.0;
        }
    }
}