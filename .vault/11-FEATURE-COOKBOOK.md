---
title: "📖 Cookbook de Desenvolvimento (Receitas e Templates)"
aliases:
  - "Cookbook"
  - "Receitas"
  - "Templates"
  - "11-FEATURE-COOKBOOK"
tags:
  - tw/cookbook
  - tw/templates
  - tw/code-snippets
  - type/guide
module: "tw-network"
version: "1.0.0"
status: "producao"
updated: 2026-11-05
---

> [!INFO] **Navegação Rápida do Vault**
> ⬅️ **Anterior:** [[10-CAIXAS-MODULE|10. Módulo TW-Caixas]] • 🏠 **MOC:** [[00-ROUTER-AND-TAXONOMY|Dashboard]] • ➡️ **Início:** [[00-ROUTER-AND-TAXONOMY|00. Hub Central & MOC]]

# 📖 Cookbook de Desenvolvimento (Receitas & Templates)

> [!SUMMARY] **Visão Geral**
> Este cookbook fornece templates de código prontos para copiar e estender, em conformidade total com as 8 regras inquebráveis de desenvolvimento (Purpur 26.3, Java 25, Adventure MiniMessage, PersistentDataContainer e assincronismo estrito).

---

## 🍳 Receita 1: Novo Menu GUI Interativo (`TWMenu`)

> [!EXAMPLE] **Template de Menu com Proteção de Cliques Automática**
> Menus nunca devem registrar listeners manuais soltos de `InventoryClickEvent`. Devem implementar `TWMenu`:

```java
package br.com.tw.exemplo.menus;

import br.com.tw.core.menus.TWMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ExemploPainelMenu implements TWMenu {

    private final Inventory inventory;
    private final Player player;

    public ExemploPainelMenu(Player player) {
        this.player = player;
        Component title = MiniMessage.miniMessage().deserialize("<gradient:#ffaa00:#ff5500>Painel Customizado</gradient>");
        this.inventory = Bukkit.createInventory(this, 27, title);
        build();
    }

    private void build() {
        ItemStack botaoAcao = new ItemStack(Material.EMERALD);
        ItemMeta meta = botaoAcao.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<green><bold>CONFIRMAR OPERAÇÃO</bold></green>"));
        meta.lore(List.of(
            MiniMessage.miniMessage().deserialize("<gray>Clique para executar esta ação com segurança.</gray>")
        ));
        botaoAcao.setItemMeta(meta);

        inventory.setItem(13, botaoAcao);
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // O evento já vem cancelado automaticamente pelo MenuListener do tw-core!
        int slot = event.getRawSlot();
        if (slot == 13) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Ação executada com sucesso!</green>"));
            player.closeInventory();
        }
    }
}
```

---

## 🏷️ Receita 2: Criando Item Especial via PDC (`TWItemTags`)

> [!EXAMPLE] **Template de Item com Tags Persistentes**
> Nenhuma informação de gameplay deve ser lida da lore. Use o PDC:

```java
package br.com.tw.exemplo.items;

import br.com.tw.core.utils.TWItemTags;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExemploItemFactory {

    public static ItemStack criarFerramentaEspecial(int nivel, double multiplicador) {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<gradient:#e65c00:#f9d423>Picareta Sísmica Lendária</gradient>"));
        meta.lore(List.of(
            MiniMessage.miniMessage().deserialize("<gray>Nível: <yellow>" + nivel + "</yellow></gray>"),
            MiniMessage.miniMessage().deserialize("<gray>Multiplicador: <gold>" + multiplicador + "x</gold></gray>")
        ));
        item.setItemMeta(meta);

        // Grava dados estruturados no PersistentDataContainer
        Map<String, Object> tags = new HashMap<>();
        tags.put("item_id", "sismic_pickaxe");
        tags.put("pickaxe_level", nivel);
        tags.put("bonus_mult", multiplicador);
        tags.put("item_uuid", UUID.randomUUID().toString());

        return TWItemTags.set(item, tags);
    }

    public static boolean ehFerramentaEspecial(ItemStack item) {
        return "sismic_pickaxe".equals(TWItemTags.getString(item, "item_id"));
    }
}
```

---

## 🐘 Receita 3: Tabela PostgreSQL com Transação Assíncrona

> [!EXAMPLE] **Template de Repository Assíncrono com Versionamento**

```java
package br.com.tw.exemplo.data;

import br.com.tw.core.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.function.Consumer;

public class ExemploRepository {

    private final DatabaseManager db;
    private final JavaPlugin plugin;

    public ExemploRepository(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        initTable();
    }

    private void initTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS tw_exemplo_recursos (
                player_uuid VARCHAR(36) PRIMARY KEY,
                points BIGINT NOT NULL DEFAULT 0,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;
        try (Connection con = db.getConnection(); Statement stmt = con.createStatement()) {
            stmt.execute(sql);
            db.recordSchemaVersion("tw-exemplo", 1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Falha ao inicializar tabela tw_exemplo_recursos: " + e.getMessage());
        }
    }

    public void adicionarPontosAsync(UUID uuid, long pontos, Consumer<Long> callbackSync) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = """
                INSERT INTO tw_exemplo_recursos (player_uuid, points) VALUES (?, ?)
                ON CONFLICT (player_uuid) DO UPDATE SET
                    points = tw_exemplo_recursos.points + EXCLUDED.points,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING points
                """;
            try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, pontos);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long totalAtualizado = rs.getLong("points");
                        // Retorna para a Main Thread com segurança
                        Bukkit.getScheduler().runTask(plugin, () -> callbackSync.accept(totalAtualizado));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao salvar pontos: " + e.getMessage());
            }
        });
    }
}
```

---

> [!INFO] **Navegação de Rodapé**
> ⬅️ **Anterior:** [[10-CAIXAS-MODULE|10. Módulo TW-Caixas]] • 🏠 **MOC:** [[00-ROUTER-AND-TAXONOMY|Dashboard]] • ➡️ **Início:** [[00-ROUTER-AND-TAXONOMY|00. Hub Central & MOC]]
