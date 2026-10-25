package br.com.tw.minas.listeners;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.core.data.TWUser;
import br.com.tw.core.utils.TWFormat;
import br.com.tw.economia.TWEconomiaPlugin;
import br.com.tw.minas.TWMinasPlugin;
import br.com.tw.minas.managers.CartinhasManager;
import br.com.tw.minas.managers.ComboManager;
import br.com.tw.minas.managers.MineEventManager;
import br.com.tw.minas.managers.MinePickaxeManager;
import br.com.tw.minas.menus.OficinaMenu;
import br.com.tw.minas.utils.ActionBarUtil;
import br.com.tw.minas.integrations.RealMinesBridge;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MineracaoListener implements Listener {

    private final ComboManager comboManager;
    private final Map<UUID, Long> skillCooldowns = new ConcurrentHashMap<>();

    public MineracaoListener() {
        this.comboManager = new ComboManager();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        skillCooldowns.remove(uuid);
        comboManager.clearPlayer(uuid);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!TWCorePlugin.getInstance().isMineWorld(player.getWorld().getName())) return;

        // Interação com Presente Oculto dos Duendes
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.ENDER_CHEST) {
            if (TWMinasPlugin.getInstance().getMineEventManager().handleGiftInteract(player, event.getClickedBlock())) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (TWMinasPlugin.getInstance().getMinePickaxeManager().isMinePickaxe(item)) {
                new OficinaMenu(player).open();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!TWCorePlugin.getInstance().isMineWorld(player.getWorld().getName())) return;

        Block mainBlock = event.getBlock();

        // Valida se o jogador está realmente com a Picareta na mão
        ItemStack hand = player.getInventory().getItemInMainHand();
        MinePickaxeManager pickaxeManager = TWMinasPlugin.getInstance().getMinePickaxeManager();
        if (!pickaxeManager.isMinePickaxe(hand)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Precisas de usar a picareta TW-Minas para minerar!");
            return;
        }

        MineEventManager eventManager = TWMinasPlugin.getInstance().getMineEventManager();

        // Verifica se é um Presente Oculto ou Meteorito Estelar
        if (mainBlock.getType() == Material.ENDER_CHEST && eventManager.handleGiftInteract(player, mainBlock)) {
            event.setCancelled(true);
            return;
        }
        if (mainBlock.getType() == Material.SEA_LANTERN && eventManager.handleMeteoriteHit(player, mainBlock)) {
            event.setCancelled(true);
            return;
        }

        TWEconomiaPlugin eco = TWEconomiaPlugin.getInstance();
        if (eco.getBlockPrice(mainBlock.getType()) <= 0) {
            event.setCancelled(true);
            return;
        }

        RealMinesBridge realMines = TWMinasPlugin.getInstance().getRealMinesBridge();
        if (realMines == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "O sistema de minas está indisponível. Contacta a equipa.");
            return;
        }
        boolean trackedByRealMines;
        try {
            if (!realMines.isMineBlock(mainBlock)) {
                event.setCancelled(true);
                return;
            }
            trackedByRealMines = realMines.trackBlockBreak(player, event, mainBlock);
        } catch (IllegalStateException e) {
            event.setCancelled(true);
            TWMinasPlugin.getInstance().getLogger().log(
                    java.util.logging.Level.SEVERE, "Não foi possível atualizar o reset da mina RealMines.", e);
            player.sendMessage(ChatColor.RED + "A mina está temporariamente indisponível. Contacta a equipa.");
            return;
        }
        if (!trackedByRealMines) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) return;
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        TWUser user = TWCorePlugin.getInstance().getUserManager().getUser(uuid);
        if (user == null) return;

        List<Block> blocksToBreak = new ArrayList<>();
        blocksToBreak.add(mainBlock);

        boolean canTriggerSkill = !skillCooldowns.containsKey(uuid) || (System.currentTimeMillis() - skillCooldowns.get(uuid) >= 2000L);
        boolean skillTriggered = false;

        if (canTriggerSkill) {
            if (checkSkill(user, "avalanche", 0.001)) {
                skillTriggered = true;
                blocksToBreak = getBlocksInRadius(mainBlock, 2, 2, 2);
                player.sendMessage(ChatColor.YELLOW + "§lAVALANCHE!");
            } else if (checkSkill(user, "trator", 0.002)) {
                skillTriggered = true;
                blocksToBreak = getBlocksInRadius(mainBlock, 2, 0, 2);
                player.playSound(player.getLocation(), Sound.ENTITY_MINECART_RIDING, 1f, 1f);
            } else if (checkSkill(user, "explosao", 0.003)) {
                skillTriggered = true;
                blocksToBreak = getBlocksInRadius(mainBlock, 1, 1, 1);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2f);
            } else if (checkSkill(user, "broca", 0.004)) {
                skillTriggered = true;
                blocksToBreak = getBlocksInRadius(mainBlock, 0, -4, 0);
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 2f);
            } else if (checkSkill(user, "laser", 0.005)) {
                skillTriggered = true;
                blocksToBreak = getLaserBlocks(player, mainBlock, 5);
                player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.5f, 2f);
            }
        }

        CartinhasManager cartinhas = TWMinasPlugin.getInstance().getCartinhasManager();

        if (skillTriggered) {
            skillCooldowns.put(uuid, System.currentTimeMillis());
            cartinhas.incrementProgress(player, user, CartinhasManager.MissionType.SKILLS, 1, false);
        }

        int bateriaLvl = user.getSkillLevel("bateria");
        int combo = comboManager.addCombo(player, bateriaLvl);
        double tokenBonusMultiplier = (combo >= 200) ? 1.5 : 1.0;

        double totalMoney = 0;
        long totalTokens = 0;
        int blocksCount = 0;

        int lvlFortuna = user.getSkillLevel("fortuna");
        int lvlCacador = user.getSkillLevel("cacador");
        int lvlJackpot = user.getSkillLevel("jackpot");
        int lvlSorteiro = user.getSkillLevel("sorteiro");

        double prestigeMult = getPrestigeMultiplier(user.getPrestigeId());
        double globalBooster = eco.getGlobalBooster() * eventManager.getEventMultiplier();

        org.bukkit.Bukkit.getPluginManager().callEvent(
                new br.com.tw.core.events.TWMiningPrepareEvent(event.getPlayer(), mainBlock, blocksToBreak));
        for (Block b : blocksToBreak) {
            if (b.getType() == Material.AIR || eventManager.isSpecialBlock(b.getLocation())) continue;
            if (!realMines.isMineBlock(b)) continue;
            if (!b.getLocation().equals(mainBlock.getLocation())) {
                try {
                    realMines.trackBlockBreak(player, b);
                } catch (IllegalStateException e) {
                    TWMinasPlugin.getInstance().getLogger().log(
                            java.util.logging.Level.SEVERE, "Falha ao atualizar um bloco adicional no reset RealMines.", e);
                    continue;
                }
            }
            double basePrice = eco.getBlockPrice(b.getType());
            if (basePrice > 0) {
                double blockPrice = basePrice + (basePrice * (lvlFortuna * 0.05));
                if (lvlJackpot > 0 && Math.random() < (lvlJackpot * 0.001)) {
                    blockPrice *= 10;
                }
                br.com.tw.core.events.TWBlockMinedEvent minedEvent =
                        new br.com.tw.core.events.TWBlockMinedEvent(event.getPlayer(), b,
                                blockPrice * prestigeMult * globalBooster);
                org.bukkit.Bukkit.getPluginManager().callEvent(minedEvent);
                totalMoney += minedEvent.getPayout();

                // Remove o bloco SEM disparar eventos de física nos blocos adjacentes (Zero Lag)
                b.setType(Material.AIR, false);
                blocksCount++;

                long blockTokens = 1;
                if (lvlCacador > 0 && Math.random() < (lvlCacador * 0.01)) {
                    blockTokens++;
                }
                totalTokens += (long) (blockTokens * tokenBonusMultiplier * eventManager.getEventMultiplier());
            }
        }

        if (blocksCount > 0) {
            user.addBalance(totalMoney);
            user.addTokens(totalTokens);
            user.addBlocosQuebrados(blocksCount);
            user.addDinheiroMina(totalMoney);

            // Habilidade Sorteiro: Chance de achar Bónus de Tokens/Chave
            if (lvlSorteiro > 0 && Math.random() < (lvlSorteiro * 0.0003)) {
                long bonusSorte = 100L * lvlSorteiro;
                user.addTokens(bonusSorte);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 2.0f);
                player.sendMessage(ChatColor.GOLD + "§lSORTEIRO! §eEncontraste um saco extra com §b+" + bonusSorte + " Tokens§e!");
            }

            // Atualiza progresso das Cartinhas de Missão e Meta Comunitária
            cartinhas.incrementProgress(player, user, CartinhasManager.MissionType.BLOCOS, blocksCount, false);
            cartinhas.incrementProgress(player, user, CartinhasManager.MissionType.COMBO, combo, true);
            eventManager.addCommunityBlocks(player, mainBlock, blocksCount);

            int lvlSabedoria = user.getSkillLevel("sabedoria");
            double xpGanho = blocksCount * (1.0 + (lvlSabedoria * 0.5));
            boolean leveledUp = user.addPickaxeXp(xpGanho);

            if (leveledUp) {
                celebrateLevelUp(player, user.getPickaxeLevel());
                updatePickaxeLore(player, user);
            } else if (user.getBlocosQuebrados() % 20 == 0) {
                // Atualiza a lore da picareta a cada 20 blocos para poupar pacotes de rede
                updatePickaxeLore(player, user);
            }

            if (combo >= 100) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 2), true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1), true);
            } else if (combo >= 50) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 1), true);
            }

            String comboColor = (combo >= 200) ? "§c§l" : (combo >= 100) ? "§6§l" : "§e";
            String hhTag = eventManager.isHappyHourActive() ? " §d§l[2x HAPPY HOUR] " : "";
            String actionMsg = hhTag + ChatColor.GREEN + "+ $" + TWFormat.formatarDinheiro(totalMoney) +
                    ChatColor.AQUA + " | + " + TWFormat.formatarDinheiro(totalTokens) + " Tokens" +
                    " | " + comboColor + "Combo x" + combo;

            ActionBarUtil.send(player, actionMsg);
        }
    }

    private void celebrateLevelUp(Player player, int novoNivel) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        player.sendMessage("");
        player.sendMessage("§b§lPICARETA EVOLUÍDA!");
        player.sendMessage("§7A tua picareta atingiu o nível §e§l" + novoNivel + "§7!");
        player.sendMessage("");

        Firework fw = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.addEffect(FireworkEffect.builder().withColor(Color.AQUA, Color.YELLOW).with(FireworkEffect.Type.BALL_LARGE).withTrail().build());
        fwm.setPower(1);
        fw.setFireworkMeta(fwm);
    }

    private void updatePickaxeLore(Player player, TWUser user) {
        TWMinasPlugin.getInstance().getMinePickaxeManager()
                .updatePickaxe(player.getInventory().getItemInMainHand(), user);
    }

    private boolean checkSkill(TWUser user, String skill, double chanceMultiplier) {
        int lvl = user.getSkillLevel(skill);
        return lvl > 0 && Math.random() < (lvl * chanceMultiplier);
    }

    private List<Block> getBlocksInRadius(Block center, int radiusX, int radiusY, int radiusZ) {
        List<Block> blocks = new ArrayList<>();
        int minY = radiusY < 0 ? radiusY : -radiusY;
        int maxY = radiusY < 0 ? 0 : radiusY;
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    blocks.add(center.getRelative(x, y, z));
                }
            }
        }
        return blocks;
    }

    private List<Block> getLaserBlocks(Player player, Block start, int length) {
        List<Block> blocks = new ArrayList<>();
        Vector dir = player.getLocation().getDirection().normalize();
        for (int i = 0; i < length; i++) {
            blocks.add(start.getLocation().add(dir.clone().multiply(i)).getBlock());
        }
        return blocks;
    }

    private double getPrestigeMultiplier(String prestigeId) {
        if (prestigeId == null) return 1.0;
        switch (prestigeId.toUpperCase()) {
            case "KRAMPUS": return 3.0;
            case "YETI": return 2.0;
            case "GRINCH": return 1.5;
            default: return 1.0;
        }
    }
}