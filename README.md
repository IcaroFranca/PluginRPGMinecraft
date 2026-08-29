# NexusRPG

Plugin de RPG para servidores Paper/Spigot (`dev.icaro.foodtooltips.FoodTooltipsPlugin`,
registrado como `NexusRPG` no `plugin.yml`).

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

Gera `target/NexusRPG-0.31.0.jar`. Requer acesso ao repositório da PaperMC
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
- `combat` — listener de combate e visuais de mob (labels/HP acima da cabeça).
- `economy` — saldo de moedas dos jogadores.
- `food` — tooltips de comida.
- `global` — nível global, XP, cores de badge/tema.
- `i18n` — idiomas.
- `mining` — baú do tesouro, gemas, menu de mineração.
- `protect` — hooks de proteção (WorldGuard / GriefPrevention).
- `shop` — loja, itens, portais.
- `skills` — habilidades de combate, mochilas, skills gerais.
- `stats` — status do jogador e HUD.

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
próprio — Loja e Cores do Nível têm `/shop` e `/levelcolor`).

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
