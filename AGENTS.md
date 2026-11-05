# AGENTS.md - Diretrizes Mandatórias para Agentes de IA

> **ATENÇÃO IA / ASSISTENTE AGÊNTICO:**
> Este arquivo contém instruções obrigatórias e inegociáveis para o desenvolvimento e manutenção do monorepo **TW-Plugin**.
> Você **DEVE** seguir o protocolo abaixo em TODA interação antes de sugerir, editar ou planejar qualquer alteração no código.

---

## 1. Protocolo Obrigatório de RAG (Consulta ao Vault de Memórias)

Antes de criar qualquer nova feature, alterar arquivos existentes, debugar erros ou responder dúvidas arquiteturais sobre este repositório, você **DEVE consultar os documentos correspondentes no diretório `.vault/`**:

```text
.vault/
├── 00-ROUTER-AND-TAXONOMY.md          <- [[00-ROUTER-AND-TAXONOMY]] (Hub Central, Roteamento MOC e Glossário)
├── 01-ARCHITECTURE-AND-CORE-RULES.md  <- [[01-ARCHITECTURE-AND-CORE-RULES]] (Regras Inquebráveis, Thread Safety, Java 25)
├── 02-CORE-MODULE.md                  <- [[02-CORE-MODULE]] (tw-core: PostgreSQL, UserManager, TWMenu, TWItemTags)
├── 03-ECONOMIA-MODULE.md              <- [[03-ECONOMIA-MODULE]] (tw-economia: Saldos, Boosters, Cheques com assinatura digital)
├── 04-RANKS-MODULE.md                 <- [[04-RANKS-MODULE]] (tw-ranks: Progressão, LuckPerms Sync, PAPI Expansion)
├── 05-MINAS-MODULE.md                 <- [[05-MINAS-MODULE]] (tw-minas: Mineração, Oficina, Cartinhas, Meteorito TextDisplay)
├── 06-TERRENOS-MODULE.md              <- [[06-TERRENOS-MODULE]] (tw-terrenos: PlotSquared 7.6.0, Agência, Compras Atômicas)
├── 07-MAQUINAS-MODULE.md              <- [[07-MAQUINAS-MODULE]] (tw-maquinas: Geração passiva, Upgrades, Combustível, Hologramas)
├── 08-SPAWNERS-MODULE.md              <- [[08-SPAWNERS-MODULE]] (tw-spawners: Spawners até 10k, Modo Físico vs Virtual, Drops)
├── 09-FARMS-MODULE.md                 <- [[09-FARMS-MODULE]] (tw-farms: Plantações físicas, Silos virtuais, Enxadas em área)
├── 10-CAIXAS-MODULE.md                <- [[10-CAIXAS-MODULE]] (tw-caixas: Chaves virtuais, Pity, Roleta, Consumíveis na conta)
└── 11-FEATURE-COOKBOOK.md             <- [[11-FEATURE-COOKBOOK]] (Receitas passo a passo para criar menus, itens e tabelas)
```

> [!TIP]
> Caso abra o repositório como um Vault no **Obsidian**, o arquivo principal de entrada é o [[00-ROUTER-AND-TAXONOMY|00-ROUTER-AND-TAXONOMY (MOC)]], com suporte nativo a Graph View, Canvas, Callouts e Breadcrumbs em todas as notas.

### Passo a Passo de Ação para a IA:
1. **Identificar os módulos afetados**: Consulte `00-ROUTER-AND-TAXONOMY.md` para entender as dependências e o fluxo de dados.
2. **Carregar as especificações do módulo**: Leia o arquivo correspondente em `.vault/` (ex: `07-MAQUINAS-MODULE.md` se for trabalhar em máquinas).
3. **Verificar as regras inquebráveis**: Releia `01-ARCHITECTURE-AND-CORE-RULES.md` para garantir que nenhuma premissa técnica seja violada.
4. **Seguir os padrões do Cookbook**: Utilize os templates de `11-FEATURE-COOKBOOK.md` para criar menus `TWMenu`, itens com `TWItemTags` ou tabelas no banco.
5. **Validar com Maven**: Ao final de qualquer alteração de código, execute `mvn test` para assegurar que nada foi quebrado.

---

## 2. As 8 Regras Invioláveis de Desenvolvimento

1. **MAIN THREAD SAFETY (I/O ASSÍNCRONO ESTRITO)**:
   - **NUNCA** execute queries SQL (`getConnection()`, `prepareStatement()`), I/O de disco ou requisições web na Main Thread do Bukkit.
   - Use `TWAsyncTaskQueue` ou `runTaskAsynchronously`.
   - Sempre retorne para a thread principal (`runTask`) antes de tocar em APIs não thread-safe do Bukkit (modificar blocos, inventários, teleportar).

2. **PREVENÇÃO DE MEMORY LEAKS**:
   - **NUNCA** armazene instâncias da classe `Player` em mapas estáticos ou coleções de managers. Armazene estritamente `UUID`.
   - Limpe caches, tarefas ativas e hologramas no método `onDisable()` de cada plugin.

3. **ADVENTURE API E MINIMESSAGE (PROIBIDO CHATCOLOR LEGADO)**:
   - **NUNCA** use caracteres de cor `§` ou `org.bukkit.ChatColor`.
   - Use exclusivamente `Component` e `MiniMessage.miniMessage().deserialize("<color>...")`.
   - Envie mensagens e action bars com os métodos nativos: `player.sendMessage(Component)` e `player.sendActionBar(Component)`.

4. **METADADOS VIA PERSISTENT DATA CONTAINER (PROIBIDO LER DADOS DA LORE)**:
   - **NUNCA** utilize o display name ou lore de itens como fonte de dados para regras de negócio (IDs, níveis, quantias, multiplicadores).
   - Use o utilitário nativo `br.com.tw.core.utils.TWItemTags` (PDC). A lore é estritamente cosmética.

5. **POSTGRESQL COMO BACKEND OFICIAL**:
   - O projeto utiliza **PostgreSQL** através do HikariCP no `tw-core`. Não assuma SQLite ou MySQL.
   - Sempre crie tabelas com `CREATE TABLE IF NOT EXISTS` e use `ON CONFLICT (...) DO UPDATE`.
   - Registre versões de schema via `DatabaseManager.recordSchemaVersion(modulo, versao)`.

6. **SISTEMA DE MENUS NATIVO (`TWMenu`)**:
   - Todo inventário interativo DEVE implementar `br.com.tw.core.menus.TWMenu`.
   - Não crie listeners soltos de `InventoryClickEvent`; o `MenuListener` do Core gerencia o cancelamento automático de cliques e redireciona para `menu.onClick(event)`.

7. **HOLOGRAMAS NATIVOS VIA TEXT DISPLAY**:
   - Não adicione dependências de plugins externos de hologramas (como DecentHolograms ou HolographicDisplays).
   - Use o utilitário `br.com.tw.core.utils.TWHologram` baseado na entidade nativa `Display.TextDisplay`.

8. **COMPATIBILIDADE PURPUR 26.3 E JAVA 25**:
   - O código é compilado com release Java 25 contra a API Purpur 26.3.
   - Não introduza referências a internals NMS versionados (`net.minecraft.server.v1_...`).

---

## 3. Checklist Obrigatório Pré-Entrega para IA

Antes de considerar qualquer tarefa finalizada, confirme mentalmente ou via terminal:
- [ ] Consultei os arquivos relevantes no `.vault/` antes de codificar?
- [ ] O código compila sem erros com `mvn clean test`?
- [ ] Toda query SQL é executada em thread assíncrona?
- [ ] O retorno para a Main Thread foi feito antes de mexer em inventários/jogadores?
- [ ] Itens especiais gravam suas propriedades via `TWItemTags` e não dependem de lore?
- [ ] Todas as mensagens visuais usam MiniMessage ou Component da Adventure API?
- [ ] Nenhuma instância de `Player` está retida em coleções persistentes?
