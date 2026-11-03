package br.com.tw.caixas.model;

import br.com.tw.core.utils.TWItemTags;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateReward {
    private final double weight;
    private final Rarity rarity;
    private final RewardKind kind;
    private final long minimum;
    private final long maximum;
    private final Material material;
    private final String displayName;
    private final String effect;
    private final int charges;

    public CrateReward(double weight, Rarity rarity, RewardKind kind, long minimum, long maximum,
                       Material material, String displayName, String effect, int charges) {
        if (!Double.isFinite(weight) || weight <= 0.0 || minimum < 0 || maximum < minimum || charges < 0) {
            throw new IllegalArgumentException("Configuração de recompensa inválida.");
        }
        if (kind == RewardKind.ITEM && material == null) {
            throw new IllegalArgumentException("Recompensa de item exige um material.");
        }
        if (kind == RewardKind.ITEM && effect != null && effect.trim().isEmpty()) {
            throw new IllegalArgumentException("O efeito de item não pode estar vazio.");
        }
        this.weight = weight;
        this.rarity = rarity;
        this.kind = kind;
        this.minimum = minimum;
        this.maximum = maximum;
        this.material = material;
        this.displayName = displayName;
        this.effect = effect;
        this.charges = charges;
    }

    public double getWeight() { return weight; }
    public Rarity getRarity() { return rarity; }
    public RewardKind getKind() { return kind; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public String getEffect() { return effect; }
    public String getAmountRangeText() {
        if (kind == RewardKind.ITEM) return charges > 0 ? "1 item (" + charges + " cargas)" : "1 item";
        return minimum == maximum ? Long.toString(minimum) : minimum + "-" + maximum;
    }

    public long rollAmount() {
        if (kind == RewardKind.ITEM) return 1L;
        if (maximum == minimum) return minimum;
        return ThreadLocalRandom.current().nextLong(minimum, Math.addExact(maximum, 1L));
    }

    public ItemStack createItem(long amount) {
        if (kind != RewardKind.ITEM) throw new IllegalStateException("Somente recompensas de item criam itens.");
        ItemStack item = new ItemStack(material, (int) Math.max(1L, Math.min(64L, amount)));
        ItemMeta meta = item.getItemMeta();
        if (displayName != null && !displayName.trim().isEmpty()) meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Recompensa de caixa");
        if (effect != null && !effect.trim().isEmpty()) {
            lore.add("§7Cargas: §e" + charges);
            lore.add("§e" + getUsageHint(effect));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        if (effect != null && !effect.trim().isEmpty()) {
            Map<String, Object> tags = new HashMap<>();
            tags.put("tw_crate_effect", effect);
            tags.put("tw_crate_charges", (long) charges);
            tags.put("tw_crate_instance", java.util.UUID.randomUUID().toString());
            return TWItemTags.set(item, tags);
        }
        return item;
    }

    private String getUsageHint(String effectId) {
        CrateEffect memoryEffect = CrateEffect.fromId(effectId);
        if (memoryEffect != null) return "Clique direito para ativar na tua conta.";
        switch (effectId) {
            case "kill_capsule": return "Clique direito num spawner teu para usar.";
            case "machine_battery":
            case "machine_lubricant":
            case "machine_overclock": return "Clique direito numa máquina tua para usar.";
            case "farm_growth": return "Clique direito numa plantação registada para aplicar.";
            case "golden_scythe": return "Clique direito numa plantação madura para colher.";
            case "mutant_seed": return "Clique direito numa plantação registada para mutá-la.";
            case "free_spawner_upgrade": return "Clique direito num spawner teu para evoluí-lo.";
            case "free_prestige": return "Clique direito após alcançar o Rank MAX.";
            case "divine_seal": return "Ativado automaticamente na próxima abertura.";
            default: return "Efeito especial da caixa.";
        }
    }

    public static List<CrateReward> immutableCopy(List<CrateReward> rewards) {
        return Collections.unmodifiableList(new ArrayList<>(rewards));
    }

    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC,
        LEGENDARY
    }
}
