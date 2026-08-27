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

Gera `target/NexusRPG-0.21.0.jar`. Requer acesso ao repositório da PaperMC
(`https://repo.papermc.io/repository/maven-public/`) e, para o hook de
WorldGuard, ao repositório da EngineHub (`https://maven.enginehub.org/repo/`).

**Versionamento**: a cada mudança publicada, suba o número da versão em
`pom.xml` (`<version>`) e `src/main/resources/plugin.yml` (`version:`) —
os dois precisam bater. Patch (`0.21.0` → `0.21.1`) para correções e
ajustes pequenos; minor (`0.21.0` → `0.22.0`) para features novas como a
árvore de combate.

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

As 16 habilidades de combate originais deixaram de ser liberadas automaticamente
por nível. Agora elas (mais 4 novas: `RUTHLESS_STRIKES` e `UNDYING_WILL` passivas,
`ARCANE_SLASH` e `VITAL_TOUCH` ativas) vivem em uma árvore de 20 nós
(`CombatTreeNode`), organizada em 3 ramos temáticos (Fúria, Sangue, Precisão) que
convergem em nós de sinergia e no capstone `APEX_WARRIOR`.

- **Moeda**: **Cristais de Combate** (`CombatValorService`), obtida matando mobs hostis e ao
  subir de nível de Combate. Gasta para desbloquear (rank 1) e melhorar
  (ranks seguintes, até 5 — ou 3 para nós de sinergia/capstone) cada nó,
  reduzindo cooldowns e aumentando dano/cura conforme o rank.
- **Pré-requisitos**: cada nó exige nível mínimo de Combate (reaproveitando os
  antigos thresholds, agora reordenados por ramo) e o(s) nó(s) anterior(es)
  com rank ≥ 1.
- **Menu**: `/skills` → "Árvore de Combate" (`CombatTreeMenuService`). Clique
  esquerdo desbloqueia/melhora; shift-clique ativa/desativa passivas
  desbloqueadas; clique direito conjura `ARCANE_SLASH`/`VITAL_TOUCH`.
  Ícone por estado: carvão = bloqueada, esmeralda = desbloqueada, diamante
  = rank máximo; variante em bloco = habilidade ativa, variante em
  minério/gema = passiva.
- **Novas stats** (inspiradas em Hypixel SkyBlock, configuráveis em
  `stats.*` no `config.yml`): Ferocity (chance de acerto extra em mobs),
  Swing Range (alcance de interação, quando o servidor expõe o atributo
  vanilla correspondente), Intelligence (Mana máxima + dano mágico),
  Ability Damage (multiplicador de dano mágico), Health Regen
  (regeneração natural), Vitality (novo recurso, separado de Mana/Vida,
  usado por `VITAL_TOUCH`) e Mending (multiplica cura aplicada a
  *outros* jogadores).

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
Bukkit e roda com testes próprios (80 checks) direto nesta sandbox.
