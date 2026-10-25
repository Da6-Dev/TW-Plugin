# Operação dos plugins TW

## Build e deploy

Execute `build-and-deploy.bat` com o servidor parado. O script compila e testa o reactor com atualização forçada do cache Maven, copia os dez JARs que acabou de gerar para uma pasta temporária, faz backup dos JARs atuais em `TW-Network/backups` e instala os artefatos. O staging copia diretamente os artefatos locais, sem uma segunda execução Maven isolada que tente resolver os módulos internos em repositórios remotos. Se a cópia falhar, tenta restaurar o backup.

Para compilar apenas `tw-caixas` com os módulos internos de que depende, execute a partir da raiz deste repositório `mvn -pl tw-caixas -am clean package`. Se uma falha anterior deixou uma resolução negativa em cache, repita com `mvn -U -pl tw-caixas -am clean package` para forçar a atualização. Não use `-rf :tw-caixas` para esse caso: `-rf` retoma a partir de `tw-caixas` e exclui `tw-farms` e os outros módulos anteriores do reactor, fazendo o Maven procurá-los nos repositórios remotos.

## PostgreSQL

Configure `TW_DB_HOST`, `TW_DB_PORT`, `TW_DB_DATABASE`, `TW_DB_USERNAME` e `TW_DB_PASSWORD`, ou preencha os campos equivalentes em `plugins/tw-core/config.yml`. Não coloque credenciais reais em arquivos versionados.

O Core registra as versões instaladas em `tw_schema_version`. As alterações de esquema atuais são idempotentes; mantenha backup do banco antes de atualizar os JARs.

## Backup e restauração

Faça backup com o servidor parado ou com uma política consistente do PostgreSQL:

```powershell
pg_dump --format=custom --file="C:\backups\tw-network.dump" --host=localhost --port=5432 --username=tw_user tw_network_db
```

Restaure em uma base vazia ou de recuperação, não sobre a base ativa sem plano de rollback:

```powershell
createdb --host=localhost --port=5432 --username=tw_user tw_network_restore
pg_restore --clean --if-exists --no-owner --host=localhost --port=5432 --username=tw_user --dbname=tw_network_restore "C:\backups\tw-network.dump"
```

Valide a base restaurada e as tabelas `tw_users`, `tw_machines`, `tw_spawners`, `tw_farms`, `tw_farm_crops`, `tw_farm_storage`, `tw_cheques`, `tw_crate_wallet`, `tw_crate_pity`, `tw_crate_opening`, `tw_crate_mail`, `tw_crate_player_effects`, `tw_physical_crates` e `tw_schema_version` antes de direcionar o servidor para ela. Armazene credenciais fora do histórico do terminal e proteja os arquivos de backup com permissões adequadas.

## Terrenos

O `tw-terrenos` usa o PlotSquared 7.6.0. Instale a distribuição Bukkit oficial do PlotSquared; `plotsquared-core` sozinho não é um plugin Bukkit e não deve ficar em `plugins`. O FAWE já satisfaz o requisito de WorldEdit. Faça backup e pare o servidor antes de trocar JARs.

Crie o mundo separado, sem alterar o `world` do lobby:

```text
/mv create terrenos normal -g PlotSquared
```

Confirme que o PlotSquared gerou `plugins/PlotSquared/config/worlds.yml` para `terrenos` e que o mundo aparece em `/mv list`. O TW-Core já identifica `terrenos` como mundo de plots. O grupo `terrenos` do Multiverse-Inventories deve permanecer separado dos grupos `default` e `minas`.

No PlotSquared, mantenha `titles.display-titles: false` em `plugins/PlotSquared/config/settings.yml` e `worlds.terrenos.flags.titles: false` em `plugins/PlotSquared/config/worlds.yml`. O callback de título pode tentar enviar um componente nulo quando a resolução assíncrona do proprietário falha, desconectando o jogador; não troque essa notificação por action bar, pois ela usa o mesmo conteúdo.

O menu da agência é `/terreno` (alias `/terrenos`); `/plot` e `/p` pertencem ao PlotSquared. O preço inicial é $50.000 e cada compra seguinte dobra; os limites por rank e o bônus de prestígio ficam em `tw-terrenos/config.yml`. O débito e o registro `PENDING` são gravados juntos no PostgreSQL. Um claim confirmado conclui a compra; se o claim falhar ou não existir após o prazo, o reembolso é transacional e idempotente. Compras pendentes também são reconciliadas quando o jogador volta após um reinício. Não conceda `plots.auto` nem `plots.claim` ao grupo padrão. Para acesso básico, conceda `plots.use`, `plots.info`, `plots.home`, `plots.visit`, `plots.visit.owned` e `plots.plot.1`; para acesso ao mundo pelo Multiverse, conceda `mv.access.terrenos`. Com `use-finer-teleport-permissions: true`, `multiverse.teleport.self.w.terrenos` libera `/mvtp terrenos`.

O limite global de membros (membros além do dono) é configurado em `limits.members-per-plot`; trusts e membros são limitados pelo TW-Terrenos. O plugin concede aos jogadores apenas as permissões nativas necessárias para gerir o próprio lote durante a sessão; claim e auto-claim continuam restritos ao menu pago. A equipe com `tw-terrenos.admin` pode consultar compras, reembolsos, alterações de membros, trocas de dono, claims e merges por `/terrenosadmin auditoria <jogador>`. Conceda essa permissão por LuckPerms apenas aos grupos de equipe (por exemplo, `/lp group <equipe> permission set tw-terrenos.admin true`). Os registros ficam nas tabelas PostgreSQL `tw_plot_purchases` e `tw_plot_audit_log`; mantenha-as nos backups regulares.

O menu permite buscar um jogador pelo nome para visitar o terreno. O botão de gerenciamento aponta para os comandos nativos do PlotSquared para trust/add/remove, merge, alias, descrição e status `done`. A expansão é automática e sem teto configurado; mantenha `world.border: false` no mundo PlotSquared e não habilite limpeza automática de terrenos inativos. A configuração PlotSquared atual mantém `auto-clear.task1.threshold: -1`.

No mundo de terrenos, o TW-Terrenos mantém o horário em dia, impede chuva, fome, dano de queda/fogo/lava, dano de mobs hostis e uso de portais/explosões. O voo fica liberado apenas dentro de plot próprio ou compartilhado; jogadores com `tw-terrenos.admin` são exceção. Multiverse mantém PvP desligado, fome/clima/portais e surgimento natural desligados, com dificuldade fácil para spawners permitidos. No PlotSquared, spawn natural fica desligado e spawners ficam permitidos; preserve as flags globais `pvp`, `explosion`, `block-burn`, `block-ignition`, `liquid-flow`, `deny-portals` e `deny-portal-travel`. As ruas eliminam mobs, itens e veículos. Preserve `titles.display-titles: false` e `worlds.terrenos.flags.titles: false`.

Antes de abrir o mundo, teste com uma conta comum: preços de $50.000/$100.000/$200.000, limite por rank/prestígio, compra concluída e cobrança única, falta de saldo/limite sem claim, reembolso e recuperação de compra pendente após reinício, voo dentro e fora de plots, até 10 membros, visita, proteção entre plots e isolamento de inventário. O PlotSquared protege os lotes; não aplique uma flag global do WorldGuard que impeça construção dentro deles.

## Máquinas

`tw-maquinas` exige `tw-core`, `tw-economia`, PlotSquared 7.6.0 e WorldEdit; instale as distribuições Bukkit dos dois últimos plugins. Por padrão, só registra até 25 máquinas agrupadas por lote no mundo `terrenos`; confira `machine-rules.plot-world`, `max-machines-per-plot`, `max-stack-size` e `max-fuel-cycles` em `plugins/tw-maquinas/config.yml`. Máquinas agrupadas contam pelo total do stack, não apenas pelo número de blocos. Não há produção offline: o chunk precisa estar carregado. O autosave de estados alterados ocorre a cada 60 segundos; o desligamento aguarda a fila de persistência.

O menu `/maquinas` possui páginas para os tipos configurados; preços e quantidades de combustível usam as opções `combustivel.preco-money` e `combustivel.unidades-por-compra`. No painel da máquina, upgrades têm níveis máximos configurados em `machine-rules.max-upgrades`. Os lucros só podem ser recolhidos pelo dono; a atualização do saldo e a limpeza do armazenamento são feitas na mesma transação PostgreSQL. O desmonte exige dono, confirmação, inventário com espaço e saldo armazenado igual a zero.

Para diagnóstico administrativo, conceda `tw-maquinas.admin` somente à equipe via LuckPerms; `/maquinas status` mostra quantidade de máquinas registradas, ativas e produção armazenada, e `/maquinas jogador <nome>` resume os dados associados ao nome do proprietário. Proteções cobrem explosões de entidades/blocos, fogo, mudanças de bloco por entidades e pistões.

## Spawners

`tw-spawners` requer `tw-core`, `tw-economia` e PlotSquared 7.6.0. Mantenha também a instalação Bukkit de WorldEdit ou FAWE compatível com a versão do PlotSquared; o plugin reconhece ambos sem exigir o nome específico de um deles. Os spawners são persistidos em `tw_spawners` no PostgreSQL. O limite padrão por ciclo é 10.000 unidades representadas pelo stack (não são 10.000 entidades Bukkit); o valor continua configurável até esse teto em `limits.max-spawn-per-cycle`. Na primeira inicialização com uma configuração anterior à versão 2, o plugin migra o antigo padrão de 1.000 para 10.000 e completa as novas opções ausentes sem substituir as opções já definidas.

Os ciclos só avançam em chunks carregados. No modo físico, a geração exige um jogador num raio de 16 blocos; a produção que não puder ser materializada fica em `pending_physical`, até `limits.max-pending-physical`. As entidades empilhadas não desaparecem por distância e guardam os dados do stack em Persistent Data Container para sobreviver a unload/restart. No modo virtual, dinheiro, tokens, XP e almas são acumulados no cofre. O autosave de alterações ocorre conforme `limits.autosave-seconds` (padrão 60); o desligamento aguarda a fila de persistência.

Os limites de stack por item/spawner, spawners por jogador/lote, níveis de upgrades, produção física pendente e tempo de autosave ficam em `plugins/tw-spawners/config.yml`. Em `limits.max-entities-per-spawner`, o teto é de stacks de entidades, não de unidades dentro de um stack. O mundo de terrenos usa PlotSquared para validar proprietário/membros e `limits.max-spawners-per-plot`; fora desse mundo, o limite por jogador continua ativo.

O menu `/spawners` permite comprar geradores, alternar modo físico/virtual, ativar Auto-Sell, evoluir e recolher recursos. Apenas o proprietário pode recolher o cofre ou desmontar o spawner. Dinheiro/tokens são creditados e removidos do cofre numa transação PostgreSQL; XP e almas permanecem armazenados se não puderem ser entregues. Para diagnóstico, conceda `tw-spawners.admin` apenas à equipe: `/spawners status` mostra totais, chunks ativos e unidades físicas pendentes; `/spawners jogador <nome>` consulta os stacks desse dono.

Antes de atualizar a produção, faça backup do PostgreSQL e teste em uma cópia: stack de 10.000 no modo virtual e físico, jogador ausente com buffer pendente, inventário cheio ao recolher almas, coleta de dinheiro/tokens, limites de compra/colocação, upgrades máximos e persistência após reinício. Use `mvn -pl tw-spawners -am test` para validar o módulo.

## Farms

`tw-farms` depende de `tw-core`, `tw-economia` e `tw-spawners` e usa o PostgreSQL compartilhado. A produção vem de plantações físicas construídas no terreno: as culturas crescem com as regras normais do Minecraft, e os drops de produtos associados a uma cultura registada são encaminhados ao armazém `/farm` em vez de permanecerem no chão. O detalhe de cada cultura no armazém permite vender, recolher, ligar/desligar Auto-Sell e devolver ao silo os itens identificados que foram recolhidos. Os boosters aplicam-se às plantações do mesmo tipo; a duração não acumula, e usar um booster mais forte melhora a potência sem reiniciar o temporizador ativo. Cada ativação nova é limitada por `farm.growth-booster-max-duration-seconds` (predefinição de 60 minutos). `/farm loja` vende os itens de plantio e boosters e o botão do armazém volta a `/farm`; `/farm` e `/farm silo` abrem o armazém. As farms-bloco antigas não produzem mais, mas continuam acessíveis para venda/recolha do stock e desmontagem. A associação das culturas é ao jogador que as plantou nos mundos definidos como plots em `tw-core`.

## Caixas

`tw-caixas` mantém as chaves e o pity como dados virtuais no PostgreSQL; eles não são itens de inventário. `/key` abre o painel, `/key enviar <jogador> <tipo> <quantidade>` transfere chaves, `/key forja` abre a forja e `/key correio` recolhe itens excedentes que não couberam no inventário. Cada chave sorteia uma recompensa ponderada; a abertura manual mostra uma animação sem escolha de prémio, e cada chave em abertura em massa também rende exatamente uma recompensa. O pity na abertura em massa avança à metade da velocidade. O Selo da Fortuna Divina no inventário garante um prémio lendário na próxima abertura, manual ou em massa. Os limites operacionais e as tabelas de recompensas são configuráveis em `plugins/tw-caixas/config.yml`. Administradores usam `/caixa dar`, `/caixa setar`, `/caixa remover`, `/caixa daritem` e `/caixa reload`.

Os consumíveis de mineração são ativados com clique direito no ar ou num bloco: o item é consumido e as cargas ficam associadas ao UUID do jogador em `tw_crate_player_effects`, portanto funcionam após a troca do lobby/spawn para o mundo de minas e não dependem do inventário daquela dimensão. O Contrato duplica o pagamento por bloco; a Carga Sísmica amplia a área de uma picaretada por carga; a Broca aplica +20% ao pagamento por bloco. O Íman de Almas também é carregado na conta e concede uma alma extra por carga consumida em abates elegíveis. Usa `/key efeitos` para consultar os saldos. As alterações são guardadas em lote a cada 40 ticks, ao sair e no encerramento normal do plugin.

Os restantes consumíveis são aplicados diretamente no alvo indicado pela lore: cápsula e talismã em spawners registados, bateria/lubrificante/overclock em máquinas registadas, booster/foice/semente em plantações registadas e orbe no Rank MAX. O Fragmento da Natureza é um item colecionável empilhável, não um consumível sem ação associada. `divine_seal` é aplicado automaticamente na próxima abertura. `/caixa daritem` só aceita efeitos com utilização implementada e cria os itens nativos de farm com as tags necessárias.

## Meteorito do TW-Minas

O meteorito pode surgir com probabilidade `events.meteorite-chance` por bloco elegível minerado. O bloco recebe um holograma nativo (Text Display) com vida restante, barra de progresso, participantes e golpes; cada acerto concede Tokens conforme `events.meteorite-hit-reward-min` e `events.meteorite-hit-reward-max`. Ao ser destruído, `events.meteorite-completion-reward` é dividido proporcionalmente aos golpes de cada participante. A quota de quem estiver offline ou ainda não tiver os dados carregados é guardada em `plugins/tw-minas/meteorite-rewards.yml` e entregue na próxima entrada. Os valores iniciais são 0,005% de chance, 300 pontos de vida, 50–100 Tokens por golpe e 5.000 Tokens no prémio final.

## Compatibilidade

Os artefatos são compilados com Java 25 e destinados ao Purpur 26.3. Teste os JARs em uma cópia desse servidor antes de atualizar produção; os `plugin.yml` exigem API 26.3.
