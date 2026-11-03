package br.com.tw.minas.managers;

import br.com.tw.core.TWCorePlugin;
import br.com.tw.minas.TWMinasPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PvpKitManager implements CommandExecutor, Listener {
    private static final String MENU_TITLE = ChatColor.DARK_GRAY + "Kits PvP";

    private final TWMinasPlugin plugin;
    private final NamespacedKey kitItemKey;
    private final NamespacedKey kitIdKey;
    private final List<KitDefinition> kits;

    public PvpKitManager(TWMinasPlugin plugin) {
        this.plugin = plugin;
        this.kitItemKey = new NamespacedKey(plugin, "pvp-kit-item");
        this.kitIdKey = new NamespacedKey(plugin, "pvp-kit-id");
        this.kits = loadKits();
        createCooldownTable();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando só pode ser usado por um jogador.");
            return true;
        }

        Player player = (Player) sender;
        if (!plugin.getPvpArenaManager().isAllowedKitWorld(player)) {
            player.sendMessage("§cOs kits PvP só podem ser resgatados no lobby, plots ou na arena.");
            return true;
        }
        openMenu(player);
        return true;
    }

    public boolean isPvpItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(kitItemKey, PersistentDataType.BYTE);
    }

    public void markPvpProjectile(org.bukkit.entity.Entity projectile) {
        projectile.getPersistentDataContainer().set(kitItemKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isPvpProjectile(org.bukkit.entity.Entity projectile) {
        return projectile.getPersistentDataContainer().has(kitItemKey, PersistentDataType.BYTE);
    }

    public String getKitId(ItemStack item) {
        if (!isPvpItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(kitIdKey, PersistentDataType.STRING);
    }

    private void openMenu(Player player) {
        KitMenuHolder holder = new KitMenuHolder();
        Inventory inventory = holder.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, namedItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int[] slots = {11, 13, 15};
        for (int index = 0; index < kits.size() && index < slots.length; index++) {
            KitDefinition kit = kits.get(index);
            ItemStack icon = namedItem(kit.icon, kit.displayName,
                    "§7Clique para resgatar este kit.",
                    kit.permission.isEmpty() ? "§aDisponível para todos."
                            : "§7Permissão: §f" + kit.permission,
                    "§eCooldown: §f" + formatDuration(kit.cooldownSeconds));
            inventory.setItem(slots[index], icon);
        }
        player.openInventory(inventory);
    }

    private void onKitClick(Player player, int slot) {
        int index;
        if (slot == 11) index = 0;
        else if (slot == 13) index = 1;
        else if (slot == 15) index = 2;
        else return;

        if (index >= kits.size()) return;
        KitDefinition kit = kits.get(index);
        if (!kit.permission.isEmpty() && !player.hasPermission(kit.permission)) {
            player.sendMessage("§cVocê não tem permissão para resgatar este kit.");
            return;
        }

        List<ItemStack> items = createKitItems(kit);
        Inventory preview = Bukkit.createInventory(null, 36);
        preview.setStorageContents(player.getInventory().getStorageContents());
        if (!preview.addItem(items.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty()) {
            player.sendMessage("§cLibere espaço no inventário antes de resgatar o kit.");
            return;
        }

        ClaimResult result = claimCooldown(player.getUniqueId(), kit);
        if (result.error != null) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao registrar o cooldown do kit PvP "
                    + kit.id + " para " + player.getUniqueId() + ".", result.error);
            player.sendMessage("§cNão foi possível registrar o resgate agora. Tente novamente.");
            return;
        }
        if (!result.claimed) {
            player.sendMessage("§cVocê poderá resgatar este kit novamente em "
                    + formatDuration(result.remainingSeconds) + ".");
            return;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            plugin.getLogger().severe("O inventário mudou durante o resgate do kit " + kit.id
                    + " de " + player.getUniqueId() + "; os itens restantes foram deixados no local.");
            for (ItemStack item : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        player.sendMessage("§aKit " + kit.displayName + " resgatado. §7O cooldown deste kit é de "
                + formatDuration(kit.cooldownSeconds) + ".");
        player.closeInventory();
    }

    private List<ItemStack> createKitItems(KitDefinition kit) {
        List<ItemStack> items = new ArrayList<>();
        for (KitItem item : kit.items) {
            ItemStack stack = new ItemStack(item.material, item.amount);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.displayName);
                meta.setLore(item.lore);
                meta.getPersistentDataContainer().set(kitItemKey, PersistentDataType.BYTE, (byte) 1);
                meta.getPersistentDataContainer().set(kitIdKey, PersistentDataType.STRING, kit.id);
                for (Map.Entry<String, Integer> enchant : item.enchantments.entrySet()) {
                    Enchantment type = Registry.ENCHANTMENT.get(
                            NamespacedKey.minecraft(enchant.getKey().toLowerCase(Locale.ROOT)));
                    if (type != null) meta.addEnchant(type, enchant.getValue(), true);
                }
                stack.setItemMeta(meta);
            }
            items.add(stack);
        }
        return items;
    }

    private List<KitDefinition> loadKits() {
        List<KitDefinition> result = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection kitSection =
                plugin.getConfig().getConfigurationSection("pvp.kits");
        if (kitSection == null) {
            throw new IllegalStateException("pvp.kits deve definir os kits basic, vip e prestige.");
        }
        for (String id : kitSection.getKeys(false)) {
            String path = "pvp.kits." + id + ".";
            String displayName = color(plugin.getConfig().getString(path + "display-name", id));
            String iconName = plugin.getConfig().getString(path + "icon", "IRON_SWORD");
            Material icon = Material.matchMaterial(iconName);
            long cooldown = plugin.getConfig().getLong(path + "cooldown-seconds", -1L);
            if (icon == null || cooldown < 0L) {
                throw new IllegalStateException("Configuração inválida para " + path + ".");
            }

            List<KitItem> items = new ArrayList<>();
            for (Map<?, ?> raw : plugin.getConfig().getMapList(path + "items")) {
                Object materialValue = raw.get("material");
                Material material = materialValue instanceof String
                        ? Material.matchMaterial((String) materialValue) : null;
                int amount = raw.get("amount") instanceof Number
                        ? ((Number) raw.get("amount")).intValue() : 1;
                if (material == null || amount < 1 || amount > material.getMaxStackSize()) {
                    throw new IllegalStateException("Item inválido em " + path + "items.");
                }

                String name = raw.get("name") instanceof String
                        ? color((String) raw.get("name")) : displayName;
                List<String> lore = new ArrayList<>();
                Object loreValue = raw.get("lore");
                if (loreValue instanceof List<?>) {
                    for (Object line : (List<?>) loreValue) {
                        if (line instanceof String) lore.add(color((String) line));
                    }
                }
                java.util.Map<String, Integer> enchantments = new java.util.HashMap<>();
                Object enchantValue = raw.get("enchantments");
                if (enchantValue instanceof Map<?, ?>) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) enchantValue).entrySet()) {
                        if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                            enchantments.put((String) entry.getKey(),
                                    ((Number) entry.getValue()).intValue());
                        }
                    }
                }
                items.add(new KitItem(material, amount, name, lore, enchantments));
            }
            if (items.isEmpty()) throw new IllegalStateException(path + "items não pode estar vazio.");

            result.add(new KitDefinition(id.toLowerCase(Locale.ROOT), displayName,
                    plugin.getConfig().getString(path + "permission", ""), icon, cooldown, items));
        }
        if (result.size() != 3) {
            throw new IllegalStateException("pvp.kits deve definir exatamente os kits basic, vip e prestige.");
        }
        return List.copyOf(result);
    }

    private void createCooldownTable() {
        String sql = "CREATE TABLE IF NOT EXISTS tw_pvp_kit_cooldowns ("
                + "player_uuid UUID NOT NULL, kit_id VARCHAR(32) NOT NULL, "
                + "claimed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, kit_id))";
        try (Connection connection = TWCorePlugin.getInstance().getDatabaseManager().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível criar a tabela de cooldowns dos kits PvP.", e);
        }
    }

    private ClaimResult claimCooldown(UUID playerId, KitDefinition kit) {
        String upsert = "INSERT INTO tw_pvp_kit_cooldowns (player_uuid, kit_id, claimed_at) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT (player_uuid, kit_id) "
                + "DO UPDATE SET claimed_at = CURRENT_TIMESTAMP "
                + "WHERE tw_pvp_kit_cooldowns.claimed_at <= CURRENT_TIMESTAMP "
                + "- make_interval(secs => ?::double precision) "
                + "RETURNING claimed_at";
        try (Connection connection = TWCorePlugin.getInstance().getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setObject(1, playerId);
            statement.setString(2, kit.id);
            statement.setDouble(3, kit.cooldownSeconds);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return ClaimResult.claimed();
            }
            return ClaimResult.cooldown(getRemainingCooldown(connection, playerId, kit));
        } catch (SQLException e) {
            return ClaimResult.error(e);
        }
    }

    private long getRemainingCooldown(Connection connection, UUID playerId, KitDefinition kit)
            throws SQLException {
        String query = "SELECT GREATEST(0, CEIL(EXTRACT(EPOCH FROM "
                + "(claimed_at + make_interval(secs => ?::double precision) "
                + "- CURRENT_TIMESTAMP)))::bigint) "
                + "FROM tw_pvp_kit_cooldowns WHERE player_uuid = ? AND kit_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setDouble(1, kit.cooldownSeconds);
            statement.setObject(2, playerId);
            statement.setString(3, kit.id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private ItemStack namedItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "min";
        if (minutes > 0) return minutes + "min " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    private boolean isPvpWorld(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(
                plugin.getConfig().getString("pvp.world", "mina_pvp"));
    }

    private void blockOutsideArena(Player player, ItemStack item,
                                   org.bukkit.event.Cancellable event) {
        if (!isPvpItem(item) || isPvpWorld(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTopInventory().getHolder() instanceof KitMenuHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                onKitClick(player, event.getSlot());
            }
            return;
        }
        if (isPvpWorld(player)) return;
        if (isPvpItem(event.getCurrentItem()) || isPvpItem(event.getCursor())) {
            event.setCancelled(true);
            player.sendMessage("§cOs itens dos kits só podem ser movidos ou equipados na Mina PvP.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isPvpWorld(player) && isPvpItem(event.getOldCursor())) {
            event.setCancelled(true);
            player.sendMessage("§cOs itens dos kits só podem ser movidos ou equipados na Mina PvP.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        blockOutsideArena(event.getPlayer(), event.getItem(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        blockOutsideArena(event.getPlayer(), event.getItemDrop().getItemStack(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isPvpWorld(player) && isPvpItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        blockOutsideArena(event.getPlayer(), event.getItem(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!isPvpWorld(player) && isPvpItem(event.getBow())) {
                event.setCancelled(true);
            } else if (isPvpItem(event.getBow())) {
                markPvpProjectile(event.getProjectile());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player attacker = getAttackingPlayer(event.getDamager());
        if (attacker != null && !isPvpWorld(attacker)
                && (isPvpItem(attacker.getInventory().getItemInMainHand())
                || isPvpProjectile(event.getDamager()))) {
            event.setCancelled(true);
            attacker.sendMessage("§cOs itens dos kits só podem ser usados na Mina PvP.");
        }
    }

    private Player getAttackingPlayer(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (!(damager instanceof Projectile)) return null;
        ProjectileSource source = ((Projectile) damager).getShooter();
        return source instanceof Player ? (Player) source : null;
    }

    private static final class KitDefinition {
        private final String id;
        private final String displayName;
        private final String permission;
        private final Material icon;
        private final long cooldownSeconds;
        private final List<KitItem> items;

        private KitDefinition(String id, String displayName, String permission, Material icon,
                              long cooldownSeconds, List<KitItem> items) {
            this.id = id;
            this.displayName = displayName;
            this.permission = permission;
            this.icon = icon;
            this.cooldownSeconds = cooldownSeconds;
            this.items = List.copyOf(items);
        }
    }

    private static final class KitItem {
        private final Material material;
        private final int amount;
        private final String displayName;
        private final List<String> lore;
        private final Map<String, Integer> enchantments;

        private KitItem(Material material, int amount, String displayName, List<String> lore,
                        Map<String, Integer> enchantments) {
            this.material = material;
            this.amount = amount;
            this.displayName = displayName;
            this.lore = List.copyOf(lore);
            this.enchantments = Map.copyOf(enchantments);
        }
    }

    private static final class ClaimResult {
        private final boolean claimed;
        private final long remainingSeconds;
        private final SQLException error;

        private ClaimResult(boolean claimed, long remainingSeconds, SQLException error) {
            this.claimed = claimed;
            this.remainingSeconds = remainingSeconds;
            this.error = error;
        }

        private static ClaimResult claimed() {
            return new ClaimResult(true, 0L, null);
        }

        private static ClaimResult cooldown(long remainingSeconds) {
            return new ClaimResult(false, remainingSeconds, null);
        }

        private static ClaimResult error(SQLException error) {
            return new ClaimResult(false, 0L, error);
        }
    }

    private static final class KitMenuHolder implements InventoryHolder {
        private final Inventory inventory = Bukkit.createInventory(this, 27, MENU_TITLE);

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
