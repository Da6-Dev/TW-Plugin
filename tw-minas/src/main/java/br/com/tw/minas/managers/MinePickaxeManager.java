package br.com.tw.minas.managers;

import br.com.tw.core.data.TWUser;
import br.com.tw.minas.TWMinasPlugin;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class MinePickaxeManager {
    private final NamespacedKey pickaxeKey;

    public MinePickaxeManager(TWMinasPlugin plugin) {
        pickaxeKey = new NamespacedKey(plugin, "mine-pickaxe");
    }

    public boolean isMinePickaxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR
                || !item.getType().name().endsWith("_PICKAXE")) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(pickaxeKey, PersistentDataType.BYTE);
    }

    public boolean ensurePickaxe(Player player, TWUser user) {
        PlayerInventory inventory = player.getInventory();
        ItemStack pickaxe = null;
        int pickaxeSlot = -1;
        boolean pickaxeInOffHand = false;
        ItemStack[] contents = inventory.getStorageContents();

        for (int slot = 0; slot < contents.length; slot++) {
            if (!isMinePickaxe(contents[slot])) continue;
            if (pickaxe == null) {
                pickaxe = contents[slot];
                pickaxeSlot = slot;
            } else {
                inventory.setItem(slot, null);
            }
        }

        if (isMinePickaxe(inventory.getItemInOffHand())) {
            if (pickaxe == null) {
                pickaxe = inventory.getItemInOffHand();
                pickaxeInOffHand = true;
            } else {
                inventory.setItemInOffHand(null);
            }
        }

        if (pickaxe == null) {
            pickaxe = createPickaxe();
            updatePickaxe(pickaxe, user);
            int emptySlot = inventory.firstEmpty();
            if (emptySlot >= 0) {
                inventory.setItem(emptySlot, pickaxe);
            } else if (inventory.getItemInOffHand().getType() == Material.AIR) {
                inventory.setItemInOffHand(pickaxe);
            } else {
                player.sendMessage("§cLibera um espaço no inventário para receber a picareta da mina.");
                return false;
            }
            return true;
        }

        updatePickaxe(pickaxe, user);
        if (pickaxeInOffHand) {
            inventory.setItemInOffHand(pickaxe);
        } else if (pickaxeSlot >= 0) {
            inventory.setItem(pickaxeSlot, pickaxe);
        }
        return true;
    }

    public void updatePickaxe(ItemStack item, TWUser user) {
        if (!isMinePickaxe(item)) return;

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lPicareta de Natal §8[Lv. " + user.getPickaxeLevel() + "]");
        meta.addEnchant(Enchantment.EFFICIENCY, 1000, true);

        double xpNeeded = user.getPickaxeLevel() * 1000.0;
        double xp = user.getPickaxeXp();
        int progress = Math.min(10, (int) ((xp / xpNeeded) * 10));
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < 10; i++) {
            if (i == progress) bar.append("§7");
            bar.append("■");
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7Nível: §f" + user.getPickaxeLevel());
        lore.add("§7XP: §f" + (int) xp + " / " + (int) xpNeeded);
        lore.add("§7Progresso: " + bar);
        lore.add("");
        lore.add("§e§lHabilidades:");

        String[] skills = {"fortuna", "cacador", "sabedoria", "sorteiro", "jackpot",
                "explosao", "broca", "laser", "trator", "avalanche", "bateria"};
        boolean hasSkill = false;
        for (String skill : skills) {
            int level = user.getSkillLevel(skill);
            if (level > 0) {
                lore.add(" §7• " + skill.substring(0, 1).toUpperCase() + skill.substring(1)
                        + " Lv. " + level);
                hasSkill = true;
            }
        }
        if (!hasSkill) lore.add(" §cSem habilidades.");

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private ItemStack createPickaxe() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(pickaxeKey, PersistentDataType.BYTE, (byte) 1);
        pickaxe.setItemMeta(meta);
        return pickaxe;
    }
}
