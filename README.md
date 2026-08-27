# NexusRPG

Plugin de RPG para servidores Paper/Spigot (`dev.icaro.foodtooltips.FoodTooltipsPlugin`,
registrado como `NexusRPG` no `plugin.yml`).

## Estado deste repositório

Este repositório estava vazio; o único artefato disponível era o `.jar` compilado
da versão `0.20.9` (sem código-fonte). O código em `src/main/java` foi
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

Gera `target/NexusRPG-0.20.9.jar`. Requer acesso ao repositório da PaperMC
(`https://repo.papermc.io/repository/maven-public/`) e, para o hook de
WorldGuard, ao repositório da EngineHub (`https://maven.enginehub.org/repo/`).

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
