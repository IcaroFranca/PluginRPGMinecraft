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

Gera `target/NexusRPG-0.28.1.jar`. Requer acesso ao repositório da PaperMC
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
- **Ranks e custo por tier**: cada habilidade agora tem seu **próprio** rank
  máximo (`CombatTreeNode`, campo `maxRank`) — nunca menor que 10, mas alguns
  nós exigem mais (12/15/20, e o capstone `APEX_WARRIOR` vai até 25) pra serem
  um grind mais longo sem mudar o quão forte a habilidade fica no rank máximo
  (mesmo valor final, só espalhado por mais ranks). O custo de cada rank
  escala tanto com o rank quanto com o *tier* do nó (quão fundo ele está na
  árvore — raiz = tier 1, calculado automaticamente a partir dos
  pré-requisitos em `CombatTreeNode`): `custo = (base + custo-por-tier·(tier-1))
  + (custo-por-rank + custo-por-rank-por-tier·(tier-1))·(rank-1)`, configurável em
  `combat-tree.*` no `config.yml`. Nós mais profundos (ex.: `APEX_WARRIOR`, tier 6)
  custam bem mais por rank que os nós-raiz. As fórmulas de efeito (dano, cura,
  cooldown, etc.) interpolam linearmente do valor de rank 1 ao de rank máximo
  de cada habilidade (`CombatTreeMath#lerp`, agora recebendo `maxRank` como
  parâmetro explícito em vez de uma constante global) — ex.: Arremesso de
  Espada (rank máximo 15) vai de 10% do dano da arma / 30s de recarga no
  rank 1 até 50% do dano / 3s de recarga no rank 15.
- **Menu**: `/skills` → "Árvore de Combate" (`CombatTreeMenuService`). Clique
  esquerdo desbloqueia/melhora; shift-clique ativa/desativa passivas
  desbloqueadas; clique direito conjura `ARCANE_SLASH`/`VITAL_TOUCH`.
  Ícone por estado: carvão = bloqueada, esmeralda = desbloqueada, diamante
  = rank máximo; variante em bloco = habilidade ativa, variante em
  minério/gema = passiva. O botão de voltar fica no canto inferior esquerdo
  e a cabeça do jogador (moeda/legenda) no canto inferior direito. O
  preenchimento dos slots vazios é vidro preto (chegou a ser trocado pra
  carvão pra imitar um "baú de carvão", mas isso escondia todo nó ainda
  bloqueado — que já usa exatamente o ícone de carvão — dentro do fundo;
  vidro preto mantém o tom escuro sem colidir com nenhum ícone de estado).
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
- **Defesa agora vem só da armadura equipada, não mais do vanilla nem do
  nível de Mineração** (era um valor incorreto herdado da descompilação —
  `GeneralSkillService#defense` na verdade retornava o nível de Mineração).
  `ArmorDefenseService` soma um valor fixo por peça/material e
  `ArmorDefenseListener` zera o atributo vanilla `ARMOR`/`ARMOR_TOUGHNESS`
  no join e a cada tick do HUD (mesmo padrão de "reaplicar sempre" já usado
  pra Swing Range/HP bônus — evita depender de qual pacote/versão o evento
  de troca de armadura do Paper usa), então só esse número conta pra redução
  de dano (mesma curva de antes: `defesa / (defesa + 100)`). Valores por
  peça (capacete/peitoral/calça/bota):

  | Material | Capacete | Peitoral | Calça | Bota | Total |
  |---|---|---|---|---|---|
  | Couro | 5 | 15 | 10 | 5 | 35 |
  | Corrente | 9 | 23 | 18 | 8 | 58 |
  | Ouro | 10 | 25 | 15 | 5 | 55 |
  | Ferro | 12 | 30 | 25 | 10 | 77 |
  | Diamante | 15 | 40 | 30 | 15 | 100 |
  | Netherite | 18 | 46 | 35 | 18 | 117 |

  Couro/Ferro/Ouro/Diamante foram os valores pedidos; Corrente e Netherite
  foram escolhidos pra manter a mesma ordem relativa do vanilla (Couro <
  Ouro ≲ Corrente < Ferro < Diamante < Netherite). Elmo de Tartaruga também
  ganha um valor pequeno (4) pra não virar defesa zero. Resistência a
  empurrão (perk do Netherite) não é mexida — só ARMOR/ARMOR_TOUGHNESS.

  **Limitação conhecida**: a tooltip do item de armadura em si (tanto no
  inventário quanto no slot equipado dentro de "Status & Equipamento") ainda
  mostra os atributos vanilla originais (`+X Armor`) como texto — são só
  cosméticos agora, sem efeito real, mas ainda aparecem escritos no item.
  Corrigir isso exigiria reescrever a lore/flags do item de verdade
  (mexendo no NBT do item equipado), o que não foi feito nesta leva.

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
