# IcarusRPG

Plugin de RPG para servidores Paper/Spigot (`dev.icaro.foodtooltips.FoodTooltipsPlugin`,
registrado como `IcarusRPG` no `plugin.yml`).

> **Renomeado de NexusRPG pra IcarusRPG na v0.39.0.** O pacote Java
> (`dev.icaro.foodtooltips`) não mudou, mas o `name:` do `plugin.yml` mudou —
> e é esse campo que o Bukkit usa pra nomear a pasta de dados do plugin. Ao
> atualizar um servidor existente, mova `plugins/NexusRPG/` pra
> `plugins/IcarusRPG/` (config, dados de jogadores etc.) antes de subir o jar
> novo, ou o plugin sobe com uma pasta vazia e perde as configurações salvas.

## Estado deste repositório

Este repositório estava vazio; o único artefato disponível era o `.jar` compilado
da versão `0.21.0` (sem código-fonte). O código em `src/main/java` foi
**reconstruído por descompilação** (CFR) desse jar e reorganizado como projeto
Maven. Funcionalmente deve corresponder ao jar original, mas:

- Comentários originais e nomes de variáveis locais foram perdidos (o
  descompilador gera nomes genéricos em alguns trechos).
- Ainda não foi possível compilar dentro deste ambiente porque o repositório
  Maven da PaperMC (`repo.papermc.io`) está bloqueado pela política de rede
  desta sandbox. Compile localmente ou em CI com acesso normal à internet.
- Vale revisar o código reconstruído com calma antes de considerá-lo
  equivalente linha a linha ao original do Codex.

## Build

```
mvn package
```

Gera `target/IcarusRPG-0.39.0.jar`. Requer acesso ao repositório da PaperMC
(`https://repo.papermc.io/repository/maven-public/`) e, para o hook de
WorldGuard, ao repositório da EngineHub (`https://maven.enginehub.org/repo/`).

**Versionamento**: a cada mudança publicada, suba o número da versão em
`pom.xml` (`<version>`) e `src/main/resources/plugin.yml` (`version:`) —
os dois precisam bater. Updates menores só mexem no terceiro número (ex.:
`0.26.1` → `0.26.2`) — correções, ajustes de config, pequenos retoques de
texto/UI. Updates maiores (features novas, mudanças de sistema, como a
árvore de combate) sobem o segundo número e **zeram** o terceiro (ex.:
`0.26.2` → `0.27.0`, nunca `0.26.3`).

## Módulos

- `bestiary` — catálogo de mobs e marcos (milestones) de progresso.
- `builder` — Varinha do Construtor (estende um bloco em linha/coluna).
- `combat` — listener de combate e visuais de mob (labels/HP acima da cabeça).
- `destroyer` — Mão do Destruidor (limpa um bloco em linha/coluna, espelho da Varinha).
- `economy` — saldo de moedas dos jogadores.
- `food` — tooltips de comida.
- `global` — nível global, XP, cores de badge/tema.
- `i18n` — idiomas.
- `item` — sistema de raridade por Tiers (`ItemTierService`).
- `mining` — baú do tesouro, gemas, menu de mineração.
- `protect` — hooks de proteção (WorldGuard / GriefPrevention).
- `skills` — habilidades de combate, mochilas, skills gerais.
- `stats` — status do jogador e HUD.

O pacote `shop` (loja, itens, portais) foi removido — ver "Loja removida
(por enquanto)" mais abaixo.

## Árvore de Habilidades de Combate

As 15 habilidades de combate originais restantes (a 16ª, `TELEKINESIS`, saiu da
árvore — ver abaixo) deixaram de ser liberadas automaticamente por nível. Agora
elas (mais 4 novas: `RUTHLESS_STRIKES` e `UNDYING_WILL` passivas, `ARCANE_SLASH`
e `VITAL_TOUCH` ativas) vivem em uma árvore de 19 nós (`CombatTreeNode`),
organizada em 3 ramos temáticos (Fúria, Sangue, Precisão) que convergem em nós
de sinergia e no capstone `APEX_WARRIOR`.

**Telekinesis é universal**: em vez de fazer parte de uma árvore de skill
específica, `TELEKINESIS` agora é um perk liberado automaticamente para todo
jogador que atingir o **Nível Global** configurado (`global-level.telekinesis-level`,
padrão 3) — sem custo, sem depender de Combate ou Mineração. Uma vez liberado
(`GlobalLevelService#telekinesisUnlocked`), drops de abates hostis e de blocos
minerados vão direto pro inventário, e itens soltos próximos também são sugados
num raio configurável (`global-level.telekinesis-radius`, padrão 3 blocos). O
status aparece no menu "Seus status" (`/skills`).

- **Moeda**: **Pontos de Sangue** 🩸 (`CombatValorService`). Desbloquear e melhorar um
  nó é gated *somente* por Pontos de Sangue e pelo(s) nó(s) anterior(es) da árvore
  (rank ≥ 1) — não existe mais requisito de nível de Combate. Cada mob hostil
  dropa exatamente a quantia mostrada no seu card do Bestiário (`awardedCombatXp()`,
  arredondado); mobs fora do catálogo caem num fallback baseado em vida máxima.
  Subir de nível de Combate também dá um bônus fixo.
- **Ranks e custo por tier**: o rank máximo agora é puramente função do
  *tier* do nó (`CombatTreeNode`, campo `maxRank`) — tier 1 (raízes) vai até
  10, e cada tier seguinte sobe: 14/18/22/26, com o capstone `APEX_WARRIOR`
  (tier 6) no maior de todos, 32. Antes o rank máximo variava até dentro de
  um mesmo tier (10 a 15 lado a lado); agora todo nó no mesmo tier tem o
  mesmo teto, um progressão mais longa e mais previsível. O custo de cada
  rank escala tanto com o rank quanto com o tier (raiz = tier 1, calculado
  automaticamente a partir dos pré-requisitos em `CombatTreeNode`): `custo =
  (base + custo-por-tier·(tier-1)) + (custo-por-rank + custo-por-rank-por-tier·(tier-1))·(rank-1)`,
  configurável em `combat-tree.*` no `config.yml`. Nós mais profundos (ex.:
  `APEX_WARRIOR`, tier 6) custam bem mais por rank que os nós-raiz. As
  fórmulas de efeito (dano, cura, cooldown, etc.) interpolam linearmente do
  valor de rank 1 ao de rank máximo de cada habilidade (`CombatTreeMath#lerp`,
  recebendo `maxRank` como parâmetro explícito).
- **Nível de Combate mínimo por tier**: além de Blood Points e pré-requisitos,
  cada tier da árvore agora também exige um Nível de Combate mínimo pra
  desbloquear/melhorar um nó (`combat-tree.tier-level-requirements` no
  config.yml — padrão `[0, 15, 35, 60, 90, 130]` pros tiers 1-6;
  `CombatAbilityService#levelRequirement`, checado em `purchaseRank`). Isso
  volta um gate de nível que tinha sido removido numa leva anterior (então
  só Blood Points/pré-requisitos importavam) — dessa vez escalando por tier
  em vez de ser um valor único pra árvore toda, então subir de verdade no
  Combate também é necessário pra chegar no topo, não só farmar moeda. A
  tooltip de cada nó mostra o requisito (✔/✖) junto dos pré-requisitos.
- **Menu**: `/skills` → "Árvore de Combate" (`CombatTreeMenuService`). Clique
  esquerdo desbloqueia/melhora; shift-clique ativa/desativa passivas
  desbloqueadas; clique direito conjura `ARCANE_SLASH`/`VITAL_TOUCH`.
  Ícone por estado: carvão = bloqueada, esmeralda = desbloqueada, diamante
  = rank máximo; variante em bloco = habilidade ativa, variante em
  minério/gema = passiva. O botão de voltar fica no canto inferior esquerdo
  e a cabeça do jogador (moeda/legenda) no canto inferior direito. O
  preenchimento dos slots vazios é vidro preto (não carvão — nós bloqueados
  já usam esse ícone, então um filler de carvão os esconderia no fundo).
  **Layout inspirado em Heart of the Mountain/Heart of the Forest (Hypixel
  SkyBlock)**: em vez das 3 colunas retas de antes convergindo num só ponto,
  cada nó agora fica deslocado (esquerda/direita) em relação à coluna do seu
  pré-requisito — RUTHLESS_STRIKES/`VAMPIRISM`/`HUNTERS_INSTINCT` (as raízes)
  ficam nas mesmas colunas de sempre, mas os filhos delas zigzagueiam a
  partir daí, e VAMPIRISM sozinha se abre em 3 caminhos (`BLOOD_LUST`,
  `TREASURE_HUNTER`, `VITAL_TOUCH`). A silhueta ainda afunila conforme sobe
  (linha das raízes ocupa 5 colunas, o meio da árvore chega a ocupar 7-8,
  depois estreita de novo até `APEX_WARRIOR` sozinho no topo) — a mesma
  leitura de "montanha" de antes, só que agora são os próprios ícones das
  habilidades que desenham o formato, não o fundo. Só o `slot` de cada
  `register()` em `CombatTreeNode` mudou — ramos, pré-requisitos, custos e
  ranks continuam exatamente os mesmos.
  **Coluna 0 é um medidor de Nível de Combate**: como a coluna mais à
  esquerda ficou livre em toda linha (exceto a do botão de voltar), cada
  linha 0-4 ganha um vidro colorido mostrando o requisito de Nível de
  Combate daquele tier (`CombatTreeMenuService#placeLevelIndicators`) —
  verde se já alcançado, amarelo pro próximo nível que falta alcançar,
  vermelho pros mais distantes. O tier 1 (requisito 0, sempre cumprido) não
  tem vidro próprio, já que sua linha é onde fica o botão de voltar.
- **Tooltip detalhado**: cada nó mostra, além da descrição, uma leitura numérica
  "nível atual → próximo nível" de cada stat que ele concede
  (`CombatAbilityService#statPreview`), ex.: "Dano: 22.2% → 26.7%",
  "Recarga: 21.0s → 18.0s". Com a habilidade ainda bloqueada, mostra uma prévia
  do nível 1; já no nível máximo, mostra só o valor final.
- **Ordem de desbloqueio**: dentro de cada ramo, os nós estão ordenados para que
  o ganho no rank máximo nunca diminua conforme o tier sobe (ex.: no ramo Fúria,
  `ARMOR_PIERCER` agora vem antes de `BERSERKER`, já que davam a mesma coisa "fora
  de ordem" antes). O nó raiz de cada ramo agora é sempre uma passiva simples —
  `SWORD_THROW` (ativa) deixou de ser a raiz do ramo Precisão, com
  `HUNTERS_INSTINCT` em seu lugar.
- **Bestiário**: cada entrada mostra quantos Pontos de Sangue 🩸 aquele mob dropa
  (`BestiaryMenuService`), ao lado de moedas, XP de combate e drops.
- **Novas stats** (inspiradas em Hypixel SkyBlock, base configurável em
  `stats.*` no `config.yml`): Ferocity (chance de acerto extra em mobs),
  Swing Range (alcance de interação, quando o servidor expõe o atributo
  vanilla correspondente), Intelligence (Mana máxima + dano mágico),
  Ability Damage (multiplicador de dano mágico), Health Regen
  (regeneração natural), Vitality (novo recurso, separado de Mana/Vida,
  usado por `VITAL_TOUCH`) e Mending (multiplica cura aplicada a
  *outros* jogadores).
- **Toda stat de combate agora é upável pela árvore, exceto as 3 primeiras**
  (Vida, Defesa e Defesa Verdadeira ficam fora de propósito — vêm só de
  atributo vanilla/gear/config, sem fonte na árvore). As outras 8 ganham um
  bônus de uma habilidade específica, empilhado em cima da base do
  `config.yml` (`PlayerStatsService#stats`, ver o javadoc de
  `CombatAbilityService` pra lista completa nó → stat):
  `COMBAT_MASTERY` → Strength, `CLEAVE` → Ferocity (temático, já que Ferocity
  *é* chance de acerto extra e Cleave já acerta múltiplos alvos),
  `SWORD_THROW` → Swing Range, `ARCANE_SLASH` → Intelligence, `APEX_WARRIOR`
  → Ability Damage (o payoff mais amplo de fim de jogo, no capstone),
  `SOUL_HARVEST` → Health Regen, `UNDYING_WILL` → Vitalidade máxima,
  `SECOND_WIND` → Mending. Cada nó afetado mostra a linha extra no tooltip
  (`statPreview`) junto dos bônus que já tinha.
- **Resetar a árvore**: novo botão de TNT no menu (`CombatTreeMenuService`,
  ao lado do botão de voltar) — clique uma vez pra armar, clique de novo
  em até 10s pra confirmar. Zera o nível de toda habilidade e devolve
  **integralmente** os Pontos de Sangue gastos (mesma fórmula por tier de
  `nextRankCost`, somada por `CombatAbilityService#resetTree`), sem custo
  extra. Existe principalmente porque toda vez que uma leva desta rebalanceia
  fórmulas/ranks máximos, quem já tinha investido ficava preso na build
  antiga sem jeito de reorganizar.
- **Arremesso de Espada gira pra frente, não mais de lado**: o `ItemDisplay`
  usado no voo da espada rodava em torno do eixo Z (`Quaternionf#rotateZ`),
  o que parecia um giro "de disco" (plano, de lado). Trocado por
  `rotateX`, que faz a espada tombar pra frente (cambalhota) como um
  arremesso de faca de verdade (`SwordThrowListener`).
- **Arremesso de Espada corrigido no Bedrock**: `BedrockSwordThrowListener`
  detectava jogador de Bedrock só via `FloodgateApi` por reflexão — se o
  jar do Floodgate não estiver instalado *neste* servidor (por exemplo,
  Geyser só no proxy, ou um setup sem Floodgate) a detecção sempre falhava
  silenciosamente e o arremesso nunca disparava, mesmo com os controles
  certos. Agora tenta 3 sinais em cascata: (1) a assinatura de UUID que o
  Floodgate sempre gera (deriva o UUID só do XUID de 64 bits, zerando os
  64 bits superiores — funciona mesmo sem o jar do Floodgate no classpath,
  cobre o setup mais comum), (2) `FloodgateApi#isFloodgatePlayer` se o
  plugin Floodgate estiver instalado aqui, (3) `GeyserApi#connectionByUuid`
  se for o Geyser-Spigot (funciona mesmo sem Floodgate). Todos por reflexão
  (sem dependência de compilação), então nenhum deles precisa estar
  presente pro plugin compilar ou rodar.
- **Arremesso de Espada agora só atinge um inimigo por vez**: o dano do
  arremesso ia direto por `LivingEntity#damage()`, o que fazia o mesmo
  `CombatListener#damage` genérico das espadadas normais processar o hit —
  incluindo o respingo do Golpe em Arco (Cleave) pros inimigos próximos, se
  o jogador tivesse a passiva ativa. `CombatAbilityService#dealAbilityDamage`
  (mesmo mecanismo de flag que já protegia Corte Arcano) agora cobre o
  Arremesso de Espada também, sinalizando o hit pra `CombatListener` pular
  toda a pilha de multiplicadores de espadada — nível, crítico, Strength,
  Ferocity e o respingo do Cleave — então o arremesso aplica só a própria
  fórmula de dano (fração da arma) no único alvo que realmente acertou.
- **Números de dano flutuantes não são mais negrito**: o número de dano
  crítico (o arco-íris com ✦) usava `TextDecoration.BOLD` em cada caractere;
  removido (`MobVisualService#criticalNumber`). O número normal (não
  crítico) já não era negrito.

### Bugs pré-existentes corrigidos nesta mudança

Ao tocar nesses arquivos, dois problemas de descompilação que **não
compilariam** foram corrigidos (não relacionados ao pedido, mas bloqueavam o
build inteiro): `SwordThrowListener` e `BuriedTreasureService` tinham
`new BukkitRunnable(this){...}` — sintaxe inválida, já que `BukkitRunnable`
não tem construtor com argumento; o CFR decompilou de forma incorreta a
captura implícita da instância externa. Um terceiro, em
`SkillsMenuService.bagReward()`, tinha uma variável `slots` nunca atribuída
fora do caso `default` do switch — corrigido reescrevendo como switch
expression.

### Compilação

Esta sandbox não tem acesso ao repositório da PaperMC, então o build real
roda no GitHub Actions (`.github/workflows/build.yml`), disparado a cada
push — é lá que o `.jar` pronto pra baixar é gerado (aba **Actions** do
repositório → run mais recente → seção **Artifacts**). Foi assim que se
descobriu, entre outras coisas, que o Minecraft/Paper passou a usar
versionamento por data (`26.2`, exigindo JDK 25) e uma leva de bugs da
descompilação original que só o compilador real pegava.

Além disso, `CombatTreeMath` (a matemática da árvore — curva de custo,
Ferocity, fórmulas de escala por rank) é puro Java sem dependência do
Bukkit e roda com testes próprios (84 checks) direto nesta sandbox.

## Vida e Defesa: base de 100 HP, mobs 5× mais tanques, Defesa vem da armadura

Primeiro passo do "mob level scaling" planejado para depois que todas as
skills estiverem prontas:

- **HP padrão do jogador agora é 100** (`stats.base-health` no config.yml,
  antes o padrão vanilla de 20) — `PlayerStatsService#applyBaseHealth`, chamado
  no join. Os bônus que já existiam (milestones do Bestiário, HP por Nível
  Global) continuam somando em cima normalmente, sem mudança de comportamento
  ali.
- **Todo mob tem a Vida Máxima multiplicada por 5** (`mob-visuals.health-multiplier`
  no config.yml) — `CombatListener#scaleMobHealth`, aplicado uma vez por mob
  (guardado por uma flag na PDC do mob, então recarregar o plugin nunca
  multiplica de novo) tanto em spawns novos quanto nos mobs já existentes no
  mundo. Só a vida sobe — XP e Pontos de Sangue dropados por mobs sem entrada
  no Bestiário também sobem proporcionalmente (dependem da vida máxima), mas
  os valores fixos do Bestiário **não** mudam nesta leva.
- **Defesa agora vem só da armadura equipada — de jogadores e mobs — não
  mais do vanilla nem do nível de Mineração** (era um valor incorreto
  herdado da descompilação — `GeneralSkillService#defense` na verdade
  retornava o nível de Mineração). `ArmorDefenseService#defense` soma um
  valor fixo por peça/material a partir do `EntityEquipment` de qualquer
  `LivingEntity` (funciona igual pra jogador ou mob), e `neutralizeVanillaArmor`
  zera o atributo vanilla `ARMOR`/`ARMOR_TOUGHNESS` — em jogadores no join e a
  cada tick do HUD (mesmo padrão de "reaplicar sempre" já usado pra Swing
  Range/HP bônus — evita depender de qual pacote/versão o evento de troca de
  armadura do Paper usa), em mobs uma vez no spawn (`CombatListener#spawn`,
  mobs raramente trocam de equipamento depois de nascer). Só esse número
  conta pra redução de dano de qualquer `LivingEntity` (mesma curva de
  antes: `defesa / (defesa + 100)`, aplicada em `ArmorDefenseListener#defense`
  pra qualquer alvo, não só jogadores). Valores por peça (capacete/peitoral/calça/bota):

  | Material | Capacete | Peitoral | Calça | Bota | Total |
  |---|---|---|---|---|---|
  | Couro | 5 | 15 | 10 | 5 | 35 |
  | Cobre | 6 | 18 | 13 | 6 | 43 |
  | Corrente | 9 | 23 | 18 | 8 | 58 |
  | Ouro | 10 | 25 | 15 | 5 | 55 |
  | Ferro | 12 | 30 | 25 | 10 | 77 |
  | Diamante | 15 | 40 | 30 | 15 | 100 |
  | Netherite | 18 | 46 | 35 | 18 | 117 |

  Couro/Ferro/Ouro/Diamante foram os valores pedidos; Cobre, Corrente e
  Netherite foram escolhidos pra manter a mesma ordem relativa do vanilla
  (Couro < Cobre < Ouro ≲ Corrente < Ferro < Diamante < Netherite). Elmo de
  Tartaruga também ganha um valor pequeno (4) pra não virar defesa zero.
  Resistência a empurrão (perk do Netherite) não é mexida — só
  ARMOR/ARMOR_TOUGHNESS.

  **A tooltip do item mostra a Defesa de verdade**: `ArmorDefenseService#applyDefenseTooltip`
  reescreve a peça de armadura em si (equipada, na mochila, ou na mão
  secundária — em qualquer slot do inventário do jogador) pra esconder os
  atributos vanilla (`ItemFlag.HIDE_ATTRIBUTES`) e mostrar em vez disso uma
  linha "Defesa: +N" em verde, igual ao número que realmente conta. Roda no
  join e a cada tick do HUD, uma vez por item (guardado por uma flag na PDC
  do próprio item, então não sobrescreve encantos/renomes feitos depois).

  **Corrige Perfurador de Armadura no PvP e em mobs**: a habilidade checava
  se o alvo tinha `Attribute.ARMOR` vanilla > 0 pra decidir se dava o bônus
  de dano — como a Defesa vanilla é zerada de propósito, a habilidade nunca
  mais disparava contra outros jogadores (só contra os poucos mobs que já
  vinham com armadura vanilla). `CombatAbilityService#hasDefense` agora só
  checa `ArmorDefenseService#defense`, que funciona igual pra jogador ou
  mob.

## Nível Global

`/skills` → "Nível Global" (`SkillsMenuService#openGlobal`) abre uma tela no
mesmo formato paginado de 25-níveis-por-página das telas de Combate/skills
gerais, mostrando o que cada nível concede: +HP máximo (todo nível), +Strength
(a cada `global-level.levels-per-strength` níveis) e o desbloqueio da
Telecinese no nível configurado. Nível Global é baseado em XP linear e não tem
teto real, mas a tela só precisa ir até o nível 100 pra mostrar todo padrão de
recompensa pelo menos uma vez.

**Strength é um stat só**: o menu "Seus status" (`/skills`) mostrava
"Strength: 32" seguido de "Bônus do Nível Global: +32" — dois rótulos pro
mesmo número, já que Strength só vem do Nível Global (não existe fonte
adicional). Removida a linha duplicada (e o campo `globalStrength` redundante
em `PlayerStats`); agora é só "Strength: 32".

## Status & Equipamento

A cabeça no menu principal (`/skills`) foi renomeada para "Status &
Equipamento" e agora mostra, ao passar o mouse, só um resumo curto (estilo
Hypixel SkyBlock): Velocidade, Strength, Defesa, Dano Crítico, Chance
Crítica, Vida e Inteligência — cada um com seu próprio ícone (a Inteligência
ganhou o mesmo ícone ✎ já usado pela Mana, já que uma alimenta a outra).
"Velocidade" e "Dano Crítico" são novos na UI: Velocidade lê o atributo
vanilla `MOVEMENT_SPEED` do jogador convertido pra porcentagem (100 = andar
normal); Dano Crítico usa o novo `CombatAbilityService#criticalDamageMultiplier`
(config `combat.critical-damage-multiplier` ou o bônus de `CRITICAL_MASTERY`,
o que estiver ativo).

Clicar na cabeça abre uma tela nova ("Status & Equipamento",
`SkillsMenuService#openStats`) com o restante dos status, agrupados por ícone
temático (Combate/espada, Fortune de Mineração/Agricultura/Coleta com os
mesmos ícones do menu principal), e as 4 peças de armadura que o jogador tem
equipadas (capacete, peitoral, calças, botas) mostradas como os itens reais —
nome, encantos e tudo — lidas direto de `Player#getInventory()`; um slot
vazio mostra "Nada equipado."

**"Status de Combate" virou uma lista única e completa** (estilo Hypixel
SkyBlock): Vida, Defesa, Defesa Verdadeira, Strength, Chance Crítica, Dano
Crítico, Ferocity, Alcance de Ataque, Inteligência, Dano de Habilidade,
Regen. de Vida, Vitality e Mending, tudo no mesmo item (`combatStatsItem`,
antes dividido em 4 itens separados — Combate/Vitalidade/Magia/Defesa — que
saíram do menu). Como quase todas essas stats agora vêm parcialmente da
árvore de combate, os números aqui já refletem qualquer bônus de habilidade
ativa.

## Reorganização do menu de Skills

O menu principal (`/skills`, `SkillsMenuService#openMain`) agora mostra
*só* a cabeça de status (slot 4) e os ícones de skill (Combate + as 6 gerais
+ Nível Global): Bestiário e Árvore de Combate deixaram de ter botão aqui —
só são acessíveis pela tela de Combate (`openCombat`, que já os tinha nos
slots 39/41); Bestiário continua alcançável também via `/bestiary`. Mochilas,
Cores do Nível e Loja continuam com botão no menu principal (removê-los
deixaria Mochilas sem nenhuma forma de acesso, já que não tem comando
próprio — Loja e Cores do Nível têm `/shop` e `/levelcolor`). *(A Loja e o
`/shop` foram removidos numa leva posterior — ver "Loja removida (por
enquanto)" mais abaixo; o botão dela some junto do menu principal.)*

**Nível Global virou um ícone de skill**: em vez do botão separado que
tinha, agora fica no slot 13 (centralizado, logo abaixo da cabeça de status),
como se fosse mais uma skill. O ícone é uma cabeça customizada configurável
em `global-level.icon-texture` (cole o "Value" base64 de um custom head, por
exemplo do minecraft-heads.com); sem essa config, cai no ícone padrão (frasco
de experiência). A cabeça de overview dentro de "Status & Equipamento"
também foi enxugada — mostra só o Nível Global, sem Progresso/Nível de
Combate/Telecinese (essas informações já vivem na tela de Combate e na
própria tela de Nível Global).

**Tela de Nível Global mostra até o nível máximo real**: em vez de um limite
fixo arbitrário, `GlobalLevelService#maxAchievableLevel()` calcula o Nível
Global mais alto realmente alcançável — Combate e as 6 skills gerais todas no
nível 200, mais toda milestone de Bestiário e de Mineração reivindicada — e
usa esse número pra paginar a tela até lá.

## Sistema de raridade por Tiers (`dev.icaro.foodtooltips.item`)

Todo item do jogo agora tem uma raridade, expressa como **Tier** em vez das
palavras clássicas do Hypixel: `ItemTier` tem 6 valores, `S` > `A` > `B` > `C`
> `D` > `E`, cada um com a cor que a palavra Hypixel equivalente teria —
`S`=dourado (Legendary), `A`=roxo (Epic), `B`=azul (Rare), `C`=verde
(Uncommon), `D`=branco (Common) — mais um tier novo abaixo do Common, `E`,
cinza, pros blocos/itens mais "crus" e comuns do jogo (terra, pedregulho,
graveto, cascalho...). O rótulo mostrado no item é `TIER {letra}` (ex.:
`TIER C`) — mantido em inglês nas duas línguas, igual ao termo "Tier" que o
próprio pedido já usava em português.

**`ItemTierService#tierOf(Material)`** resolve o tier de qualquer `Material`:
primeiro checa um override de config (`item-tiers` no `config.yml`, vazio por
padrão), depois — se for uma ferramenta/arma/armadura — o tier vem da família
do material (Netherite=S, Diamond=A, Iron/Golden/Copper/Chainmail=B,
Stone=C, Wooden/Leather=D, mais alguns casos sem prefixo como Arco/Tridente
julgados à parte), depois um conjunto curado de itens notáveis (lingotes,
blocos de minério, drops raros) em S/A/B/C, depois um conjunto de "blocos
crus" em E (terra, pedra, cascalho, graveto...) — e cai em `D` (Common) como
padrão pra tudo que não foi listado, garantindo que **nenhum item fica sem
tier**. Nada disso precisa recompilar pra ajustar: qualquer `Material`
individual pode ser sobrescrito em `item-tiers:` no `config.yml`.

**Exibição no tooltip** segue o padrão das imagens de referência do Hypixel:
`ItemTierService#applyItemTiers(Player)` reescreve o lore de cada item do
inventário do jogador (armazenamento + armadura + offhand) uma única vez
(idempotente via flag na `PersistentDataContainer` do próprio `ItemMeta`,
mesmo padrão de `ArmorDefenseService#applyDefenseTooltip`) — pra ferramentas,
armas e armaduras, adiciona no fim do lore uma linha em negrito, cor do tier,
maiúscula, `TIER {letra} {TIPO}` (ex.: `TIER C PICKAXE`); pra qualquer outro
item (blocos, comida, ingredientes...) adiciona só o rótulo puro (`TIER D`),
sem sufixo — igual ao exemplo de "Dirt" mostrando só a raridade, sem mais
nenhuma linha. É aplicado no join do jogador (`ItemTierListener`) e
recarregado a cada tick do HUD (mesmo ciclo que já reaplica a tooltip de
Defesa), então cobre qualquer item que o jogador ganhe depois — compra na
loja, minério minerado, drop de mob, dado por comando — sem precisar
instrumentar cada sistema que entrega itens individualmente.

**Contorno da caixa do tooltip não é customizável sem resource pack**: no
vanilla, a moldura ao redor do tooltip é sempre a mesma textura fixa — só
muda de cor via o data component `tooltip_style`, que aponta pra uma sprite
definida num resource pack (o único estilo alternativo que o cliente já traz
pronto é o roxo "ominous" dos itens de Trial Chambers, não dá pra ter 6 cores
diferentes sem enviar texturas customizadas). Como alternativa sem essa
dependência, o próprio **nome do item** agora fica colorido e em negrito na
cor do tier (em vez de branco/padrão) — reaproveitando `Component.translatable`
já usado em `GemService`/`MiningMenuService` pra manter o nome original do
item quando ele não tem nome customizado.

**Bug corrigido: itens não empilhavam entre si.** A tooltip de tier só é
aplicada no tick seguinte a um item entrar no inventário (compra, minério
minerado, drop de mob...); nesse intervalo, o item novo ainda não tem a
tag "TIER X" enquanto uma pilha já existente do mesmo item já tem — o jogo
vê metadados diferentes e não junta as pilhas, e elas ficavam separadas
mesmo depois de as duas ganharem a mesma tag. `ItemTierService#applyItemTiers`
agora reagrupa as pilhas iguais do inventário (`coalesce`, respeitando o
stack size máximo) logo depois de aplicar a tag, todo tick — cura tanto essa
fragmentação causada pelo sistema de tiers quanto qualquer outra pilha
partida por acidente.

## Loja removida (por enquanto)

Todo o pacote `dev.icaro.foodtooltips.shop` (`ShopService`, `ShopItem`,
`ShopMenuListener`, `ShopItemListener`, `PortalService`) foi removido, junto
com o comando `/shop`, o registro dos seus listeners e o botão "Loja" (slot
51) do menu principal de `/skills`. `ProtectionService` (usado só pelo
`ShopItemListener`) ficou pra trás sem uso — é inofensivo mantê-lo (não
depende de nada que foi removido), então não foi apagado, pra facilitar
reviver a loja depois se for o caso. `EconomyService` (moedas, `/coins`)
continua existindo normalmente — só perdeu o bônus de Caçador de Tesouros
(ver seção seguinte), não a loja em si.

## Árvore de Combate reduzida e reordenada; Mochila de Combate migrou pra árvore

**12 habilidades removidas**: Vampirismo, Execução, Caçador de Tesouros,
Instinto do Caçador, Vontade Inabalável, Toque Vital, Maestria de Combate,
Corte Arcano, Golpe em Arco (Cleave), Perfurador de Armadura, Implacável e
Guerreiro Supremo. Sobraram 7: Golpes Implacáveis, Arremesso de Espada, Sede
de Sangue, Berserker, Colheita de Almas, Maestria Crítica e Segundo Fôlego —
cada bônus de stat que vinha de uma habilidade removida (Strength, Ferocity,
Inteligência, Dano de Habilidade, Vitalidade máxima) saiu de
`PlayerStatsService`/`EconomyService` junto com ela; o resto (Swing Range,
Regen. de Vida, Mending) continua vindo de Arremesso de Espada/Colheita de
Almas/Segundo Fôlego, que ficaram.

**Berserker agora ativa abaixo de 10% HP** (era 30%) — um bônus de
last-stand de verdade, não "a maior parte da luta com HP reduzido".

**Árvore reordenada**: Fúria e Sangue viraram cadeias de 3 níveis só
(Golpes Implacáveis → Berserker → Maestria Crítica; Sede de Sangue →
Colheita de Almas → Segundo Fôlego, cada uma agora raiz da própria cadeia
já que seus antigos pré-requisitos foram removidos). **Arremesso de Espada
subiu pro topo da árvore**: agora exige Maestria Crítica *e* Segundo Fôlego
(o topo das duas cadeias) em vez de ser raiz de um galho próprio atrás de
uma passiva descartável — é a habilidade de pico da árvore agora, não mais
enterrada Vidência.

**Mochila de Combate virou parte da árvore**: os 6 níveis de capacidade
(9/18/27/36/45/54 slots) que antes desbloqueavam automaticamente pelo nível
da skill de Combate (1/10/20/30/40/50) agora são 6 nós próprios na árvore
(`CombatAbility.BACKPACK_1`..`BACKPACK_6`, ramo novo `CombatBranch.STORAGE`,
maxRank 1 cada — desbloqueio único, não uma habilidade que sobe de nível),
comprados com Pontos de Sangue como qualquer outro nó, em cadeia linear até
o topo da árvore (BACKPACK_6 no capstone, slot 4). `BackpackService` agora
lê `CombatAbilityService#backpackRank` (quantos nós desbloqueados, 0-6) em
vez do nível da skill pra calcular a capacidade da mochila de Combate — as
outras 6 mochilas (Mineração, Pesca etc.) continuam do jeito que estavam,
level-based. Só conta nós *desbloqueados* (`unlocked`), não *ativados*
(`enabled`): diferente de toda outra passiva, desativar um nó de mochila via
shift-clique encolheria a capacidade visível e prenderia itens já guardados
além do novo limite menor — por isso o menu da árvore recusa esse
shift-clique nesses nós com uma mensagem explicando o motivo.

## Varinha do Construtor (`dev.icaro.foodtooltips.builder`)

Item novo, só disponível via comando por enquanto (`/builderwand [player]`,
permissão `foodtooltips.admin`): clique direito num bloco já colocado estende
esse bloco em toda a linha ou coluna a partir dele — a direção depende da
face clicada (topo/base = coluna vertical, qualquer face lateral = linha
horizontal). Segue substituindo blocos de ar na direção escolhida até achar
um bloco que não seja ar, até o limite configurável
(`builder-wand.max-length`, padrão 64), ou — só na Sobrevivência — até
acabar aquele bloco no inventário do jogador. No Criativo não gasta nada
(mesma regra do próprio modo Criativo). `BuilderWandService#extend` copia o
`BlockData` inteiro do bloco clicado (não só o `Material`), então escadas,
troncos e outros blocos com orientação saem virados do jeito certo, não só
no padrão.

O item em si é um `Stick` identificado por uma flag na
`PersistentDataContainer` (não pelo nome, então renomear não quebra o
reconhecimento) — clicar com ele sempre cancela a interação padrão do bloco
(não abre baú/porta por engano), já que segurando a varinha a intenção é
sempre construir.

**Shift + clique esquerdo desfaz a última extensão**: `BuilderWandService`
guarda só a ação mais recente por jogador (bloco, lista dos blocos
colocados e se ela cobrou algo do inventário) — desfazer bota ar de volta
nesses blocos e, só se a extensão original tiver sido na Sobrevivência,
devolve a mesma quantidade daquele material (dropando no chão o que não
couber no inventário). É desfazer de 1 nível só, não uma pilha de
histórico. Shift + clique esquerdo com a varinha na mão também cancela a
quebra do bloco embaixo da mira, então não tem risco de minerar por
engano ao tentar desfazer.

A varinha é um `Stick` puro — que por padrão cairia em `JUNK_ITEMS` (Tier E)
no `ItemTierService`, já que `tierOf(Material)` não distingue "um Stick
comum" de "a varinha". Como ela é uma ferramenta única, não faz sentido
mexer na tabela de `Material` (isso rebaixaria todo Stick do jogo junto).
Em vez disso, `ItemTierService#forceTier(ItemMeta, ItemTier)` grava a tier
direto na `PersistentDataContainer` do item específico (chave separada da
usada pelo override de `Material` em `item-tiers`), e `BuilderWandService`
chama isso com `ItemTier.S` ao criar a varinha em `create()`. Na leitura,
`tierOf(ItemMeta, Material)` checa primeiro esse valor gravado no item antes
de cair no `tierOf(Material)` de sempre — é assim que o mesmo Stick de
sempre pode ter uma tier diferente sem afetar mais nenhum item no jogo.

## Mão do Destruidor (`dev.icaro.foodtooltips.destroyer`)

O espelho da Varinha do Construtor: item novo, também só disponível via
comando por enquanto (`/destroyerhand [player]`, permissão
`foodtooltips.admin`). Clique direito num bloco já colocado limpa esse
bloco e todo bloco do mesmo `Material` contíguo a ele na mesma direção
implícita na face clicada — a mesma convenção da varinha (topo/base =
coluna vertical, qualquer face lateral = linha horizontal), só que ao
invés de construir a partir do bloco clicado ela apaga a partir dele,
parando no primeiro bloco diferente (ar incluso) ou no limite configurável
(`destroyer-hand.max-length`, padrão 64). `DestroyerHandService#clear`
guarda o `BlockData` original de cada bloco removido antes de apagar, pelo
mesmo motivo que a varinha copia o `BlockData` ao construir: escadas,
troncos e outros blocos com orientação voltam do jeito certo se a ação for
desfeita.

Não mexe em drop table de verdade (sem olhar ferramenta, encantamento ou
loot table) — é uma troca crua material-por-material, espelhando a
simplificação que a própria varinha já faz pro lado de construir: no
Criativo não devolve nada (mesma regra do próprio modo Criativo), na
Sobrevivência devolve um item daquele `Material` pra cada bloco limpo
(dropando no chão o que não couber no inventário).

**Shift + clique esquerdo desfaz a última limpeza**, do mesmo jeito que na
varinha: bota os blocos de volta exatamente como estavam (mesmo
`BlockData`) e, só se aquela limpeza tiver devolvido itens (Sobrevivência),
tira de volta do inventário a mesma quantidade daquele material — melhor
esforço, se o jogador já não tiver mais o suficiente ele tira o que
sobrar. Também é desfazer de 1 nível só, e cancela a quebra do bloco
embaixo da mira do mesmo jeito.

O item em si é um `Bone` identificado por uma flag própria na
`PersistentDataContainer` (não pelo nome), e — assim como a varinha —
tem sua tier forçada pra `S` via `ItemTierService#forceTier` ao ser
criado, já que um `Bone` puro cairia em `C` por padrão (está em
`C_ITEMS` no `ItemTierService`).

## Arremesso de Espada: recarga menor, agora custa Mana; nível de desbloqueio

**Recarga reduzida** (`CombatTreeMath#swordThrowBaseCooldownMillis`): a curva
que ia de 30s (rank 1) a 3s (rank máximo) baixou pra 22s → 2s — um corte de
~27% em toda a escala.

**Novo custo de Mana** (`CombatTreeMath#swordThrowManaCost`): 35 no rank 1,
caindo até 15 no rank máximo (dominar a habilidade barateia o cast, mesmo
tema da própria recarga caindo por rank). `CombatAbilityService#spendSwordThrowMana`
saca de `PlayerStatsService#withdrawMana` — se não tiver Mana suficiente, o
arremesso simplesmente não sai (`SwordThrowListener#attemptThrow` mostra
"Mana insuficiente" na action bar) e, importante, **não gasta a recarga** por
um lançamento que nunca aconteceu. É a troca clássica de rebalanceamento:
antes só a recarga limitava o quanto você conseguia spammar a habilidade,
agora a Mana (um recurso de verdade, com regeneração própria) também entra
na conta. O tooltip da árvore mostra a nova linha "Custo de Mana" junto de
Dano/Recarga/Alcance.

**Nível de desbloqueio: 35 em vez de 60.** Arremesso de Espada fica no tier 4
da árvore (exige os dois "finalizadores" de ramo, Maestria Crítica e Segundo
Fôlego), então pelo requisito padrão por tier (`tier-level-requirements`)
precisaria de Nível de Combate 60 — dois tiers de grind depois de já ter os
pré-requisitos prontos. Como isso não fazia sentido pra uma habilidade que
deveria abrir *junto* com o topo dos dois ramos, `CombatAbilityService`
ganhou um mecanismo de override por habilidade (`LEVEL_REQUIREMENT_OVERRIDES`),
separado do requisito genérico por tier — só o Arremesso de Espada usa isso
por enquanto (fixo em 35 no código, não é uma opção de `config.yml`, já que é
uma decisão de design específica dessa habilidade, não um ajuste fino que
faça sentido variar por servidor). O nó Mochila do próprio tier 4
(`BACKPACK_4`) continua usando o requisito padrão de 60 normalmente — o
override é por habilidade, não por tier inteiro.

**A posição na grade também mudou**, não só o número: o slot do Arremesso de
Espada saiu de 22 (linha do tier 4, ao lado do medidor "60") pra 31 (linha do
tier 3, ao lado do medidor "35"), ficando entre Maestria Crítica (coluna 2) e
Segundo Fôlego (coluna 5) — o ponto exato onde as duas correntes se
encontram. Sem isso, o ícone continuava desenhado na linha errada mesmo
depois do requisito numérico mudar, o que é enganoso: o jogador vê o medidor
de Nível de Combate daquela linha mostrando 60, mas a habilidade ao lado só
precisa de 35 de verdade.

## Varinha e Mão do Destruidor: modo Linha/Coluna ou Face inteira

As duas ferramentas ganharam um **menu de configuração**: clique esquerdo
(sem agachar) nelas agora abre um inventário de 1 linha com duas opções —
**Linha/Coluna** (o comportamento de sempre, estende/limpa só na direção da
face clicada) e **Face inteira (parede/chão)** (novo: preenche/limpa toda a
área conectada da parede ou chão que você está olhando). A opção marcada
com ✔ e brilho é o modo atual; clicar na outra troca na hora. O modo fica
gravado no próprio item (`BuilderWandService.FillMode`/
`DestroyerHandService.FillMode`, uma segunda chave na
`PersistentDataContainer` separada da que identifica a ferramenta), não no
jogador — cada varinha/mão guarda sua própria configuração.

**Modo Face inteira** é um flood-fill (busca em largura) na malha
perpendicular à face clicada: pra Varinha, começa um bloco além do clicado
(mesmo ponto de partida do modo Linha) e espalha por ar contíguo,
preenchendo cada bloco com o mesmo `BlockData` do bloco original; pra Mão do
Destruidor, começa no próprio bloco clicado e espalha por blocos contíguos
do mesmo `Material`, limpando cada um. Os dois modos reusam o mesmo limite
`max-length` do `config.yml` como teto de blocos processados (achatado, não
elevado ao quadrado — uma parede de 64×64 seria grande demais pra processar
de uma vez), então uma parede/chão muito grande só preenche/limpa até esse
teto e para, sem estourar performance. Undo (shift + clique esquerdo)
funciona igual nos dois modos, já que ele só depende da lista de blocos
afetados guardada na última ação — não importa se ela veio de uma linha ou
de um flood-fill.

Com essa mudança, clique esquerdo *sem* agachar (que antes simplesmente não
fazia nada, deixando a quebra normal do bloco vazar por baixo do cancelamento
de evento) agora sempre abre o menu e cancela a interação — fechando de
quebra uma pequena inconsistência onde seria possível minerar um bloco por
engano segurando a ferramenta sem querer usá-la.

## Correção do flood-fill (modo Face) e menu ampliado

**Bug corrigido: o modo Face não preenchia/limpava a área direito.** O
flood-fill guardava os blocos já visitados num `HashSet<Block>` — mas
`Block#getRelative` devolve uma instância nova a cada chamada, e depender do
`equals`/`hashCode` dela pra deduplicar é uma pegadinha conhecida da API do
Bukkit (não é garantido comparar por coordenada em toda versão). Na prática
isso fazia o algoritmo ficar "quicando" entre um punhado de blocos vizinhos
sem nunca se espalhar de verdade pela parede/chão. Trocado por um record
interno `Pos(x, y, z)` como chave do `HashSet` — comparação por valor
garantida, sem depender de nenhum comportamento específico do `Block`.

**Novo controle de Alcance no menu**: além de Linha/Face, o menu de
configuração (clique esquerdo) ganhou um terceiro item — uma luneta
mostrando o alcance atual, clique esquerdo aumenta e clique direito diminui,
ciclando entre potências de 2 (8, 16, 32...) até o teto configurado em
`max-length`. Isso fica gravado por item, igual ao modo — cada varinha/mão
pode ter seu próprio alcance, sem precisar mexer no `config.yml` nem
reiniciar o servidor. O valor nunca passa do `max-length` do servidor, só
pra baixo dele.

**O menu não fecha mais sozinho.** Antes, escolher Linha ou Face fechava o
inventário na hora; agora qualquer clique (modo ou alcance) só atualiza os
itens do próprio menu, que continua aberto — dá pra ajustar várias
configurações na mesma sessão sem precisar reabrir o menu a cada mudança.
Fecha normalmente com ESC ou clicando fora, como qualquer inventário.

**Vidro cinza em vez de preto** nos três menus que usavam
`BLACK_STAINED_GLASS_PANE` como preenchimento (o menu de configuração da
Varinha, o da Mão do Destruidor, e a Árvore de Combate) — alinhando com o
`GRAY_STAINED_GLASS_PANE` que todo o resto dos menus do plugin já usava
como padrão.

## Menu de configuração: grade de 3 linhas, clique no ar confiável

**Menu ampliado pra 3 linhas (27 slots)**, com os três controles (Linha,
Face, Alcance) centralizados na linha do meio, em vez do inventário de 1
linha só de antes.

**Bug corrigido: clique no ar não abria o menu.** O gatilho do menu/desfazer
usava `PlayerInteractEvent` com `Action.LEFT_CLICK_AIR` — mas esse evento no
Bukkit é jogado de forma "melhor esforço"/limitada pro caso de clicar no ar
(diferente do clique num bloco, que é confiável), então nem todo balançar de
braço sem alvo chegava a disparar o listener. Trocado pelo
`PlayerAnimationEvent` (o evento de "balançar o braço" puro) como gatilho
principal — esse dispara em todo clique esquerdo, com ou sem bloco na mira,
sem exceção. O `PlayerInteractEvent` continua sendo usado, mas só pra
cancelar a quebra do bloco quando o clique acontece em cima de um (a
ação de desfazer/abrir menu em si já rodou pelo evento de animação).

## Modo Face da Varinha corrigido: agora copia a parede existente

**Bug corrigido: o modo Face inundava o ar aberto sem limite natural.**
Antes, `extendFace` fazia flood-fill direto na camada de AR a ser
preenchida — mas ar sem nada atrás não tem beirada natural pra parar, então
clicar num bloco isolado (sem parede real por trás) fazia o preenchimento
crescer feito um losango (o formato clássico de flood-fill BFS em espaço
aberto) até bater no limite de Alcance, sem guardar nenhuma relação com uma
parede de verdade.

A correção muda a ordem das coisas: primeiro `extendFace` rastreia a forma
**real** da parede/chão existente (flood-fill pelo mesmo `Material` do
bloco clicado, contíguo — o mesmo algoritmo que `DestroyerHandService`
já usa pra decidir o que limpar), *depois* pinta uma cópia dessa forma na
camada imediatamente além dela, só onde tiver ar. Isso naturalmente limita
o preenchimento ao tamanho real da parede (que sempre tem uma borda física
concreta), em vez de inundar o vazio sem nenhuma referência.

## Modo Linha: Mão do Destruidor anda ao longo da parede, Varinha continua na direção da face

**Bug corrigido (só na Mão do Destruidor): clicar numa face lateral só
limpava 1 bloco.** A direção do modo Linha sempre foi literalmente a face
clicada (clicar na face leste = anda pra leste) — o que faz sentido pra uma
coluna vertical (clicar topo/base) ou uma linha "pra fora" de um ponto
isolado (um pilar, uma ponte), mas numa parede plana de 1 bloco de
espessura, andar "pra fora" da face lateral significa furar através dela —
e como ela só tem 1 bloco de espessura, a linha parava imediatamente depois
desse único bloco.

Pra Mão do Destruidor, clicar numa face lateral (não topo/base) agora faz o
modo Linha andar **ao longo do plano da própria parede** em vez de furar por
ela. `DestroyerHandService#lineDirection` escolhe entre as duas direções
possíveis nesse plano (ex.: Norte ou Sul, pra uma parede virada
Leste/Oeste) checando qual delas **continua de verdade com o mesmo
`Material`** do bloco clicado — é limpeza, então dá pra olhar o estado real
do mundo em vez de adivinhar. Só cai pro critério de produto escalar entre a
direção do olhar do jogador e o vetor de cada `BlockFace` candidata quando
isso é ambíguo (as duas direções continuam com o mesmo material, ex.: no
meio de uma parede comprida) ou indiferente (nenhuma das duas continua,
ex.: um bloco isolado — a limpeza só alcançaria 1 bloco de qualquer jeito).
Clicar topo/base continua sem mudança — ainda uma coluna vertical simples.

**A Varinha do Construtor não ganhou essa mudança.** Uma primeira versão
aplicou o mesmo critério (olhar + produto escalar) simetricamente às duas
ferramentas, mas pra construir isso não faz sentido: o modo Linha da Varinha
estende pra dentro do **ar**, então não existe "material real que continua"
pra checar — só o palpite do olhar, que adivinhava errado com frequência e
fazia a Varinha construir pro lado errado do bloco clicado. A Varinha
manteve (voltou a) o comportamento original: o modo Linha sempre estende
literalmente na direção da face clicada, sem heurística nenhuma. As duas
ferramentas resolvem o mesmo problema de formas diferentes porque uma
enxerga o mundo real (limpar) e a outra não (construir no vazio).

## Novo preset de Alcance: Ilimitado

O menu de Alcance ganhou uma última opção acima do teto configurado em
`max-length` (padrão 64): **Ilimitado** (ícone de olho de ender, com um
aviso em vermelho no lore). Ela pula o clamp normal de `[1, max-length]` —
é a única forma de passar do teto do servidor sem editar o `config.yml`.
Continua sendo uma ferramenta administrativa (permissão `foodtooltips.admin`
nos comandos `/builderwand` e `/destroyerhand`), então é um opt-in
deliberado, não um buraco de segurança.

Internamente `UNLIMITED` não é literalmente infinito. Além do risco óbvio
de loop sem fim (`Integer.MAX_VALUE`: o modo Linha da Varinha só para ao
encontrar um bloco não-ar, então apontar pro céu aberto em modo Criativo
giraria o loop até estourar o limite de verdade), tem um segundo risco mais
sutil: colocar/remover cada bloco é síncrono, na mesma tick, na thread
principal do servidor — cada `setBlockData`/quebra de bloco pode disparar
recálculo de física e luz, então mesmo um número "grande" como 10.000
blocos numa ação só já é suficiente pra travar o servidor por um instante
perceptível, independente de virar loop infinito ou não. Por isso o valor
final é bem mais conservador: **1.000 blocos** por ação — generoso (várias
construções/paredes inteiras de uma vez), mas curto o bastante pra não
gerar uma trava sentida pelos jogadores.
