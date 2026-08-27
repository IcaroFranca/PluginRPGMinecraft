package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.LevelBadgeRenderer;
import dev.icaro.foodtooltips.global.LevelColorCatalog;
import dev.icaro.foodtooltips.global.LevelColorService;
import dev.icaro.foodtooltips.global.LevelColorTheme;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class GlobalPresentationService
implements Listener {
    private static final BadgeState DEFAULT_STATE = new BadgeState(0L, LevelColorCatalog.white());
    private final Plugin plugin;
    private final GlobalLevelService global;
    private final LevelColorService colors;
    private final LevelBadgeRenderer renderer;
    private volatile long tick;
    private final Map<UUID, BadgeState> badgeCache = new ConcurrentHashMap<UUID, BadgeState>();

    public GlobalPresentationService(Plugin plugin, GlobalLevelService global, LevelColorService colors, LevelBadgeRenderer renderer) {
        this.plugin = plugin;
        this.global = global;
        this.colors = colors;
        this.renderer = renderer;
        long animationPeriod = Math.max(1L, plugin.getConfig().getLong("global-level.badge-animation-ticks", 1L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::animate, 1L, animationPeriod);
    }

    public void refreshAll() {
        for (Player subject : Bukkit.getOnlinePlayers()) {
            this.refresh(subject);
        }
    }

    public void refresh(Player subject) {
        Component badge = this.badge(subject);
        subject.playerListName(badge.append((Component)Component.text((String)subject.getName(), (TextColor)NamedTextColor.WHITE)));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.syncTeam(viewer, subject, badge);
        }
    }

    public Component badge(Player player) {
        BadgeState state = new BadgeState(this.global.snapshot(player).level(), this.colors.effective(player));
        this.badgeCache.put(player.getUniqueId(), state);
        return this.renderer.frame(state.level(), state.theme(), this.tick);
    }

    private Component cachedBadge(UUID id) {
        BadgeState state = this.badgeCache.getOrDefault(id, DEFAULT_STATE);
        return this.renderer.frame(state.level(), state.theme(), this.tick);
    }

    private String teamId(Player subject) {
        return "gl_" + subject.getUniqueId().toString().replace("-", "").substring(0, 12);
    }

    private void syncTeam(Player viewer, Player subject, Component prefix) {
        String id;
        Scoreboard board = viewer.getScoreboard();
        Team team = board.getTeam(id = this.teamId(subject));
        if (team == null) {
            team = board.registerNewTeam(id);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }
        if (!team.hasEntry(subject.getName())) {
            team.addEntry(subject.getName());
        }
        team.prefix(prefix);
    }

    private void clearTeam(Player subject) {
        String id = this.teamId(subject);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Team team = viewer.getScoreboard().getTeam(id);
            if (team == null) continue;
            team.unregister();
        }
    }

    private void animate() {
        ++this.tick;
        for (Player p : Bukkit.getOnlinePlayers()) {
            BadgeState state = this.badgeCache.get(p.getUniqueId());
            if (state == null || !state.theme().animated()) continue;
            this.refresh(p);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.badge(e.getPlayer());
        Bukkit.getScheduler().runTask(this.plugin, this::refreshAll);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.badgeCache.remove(e.getPlayer().getUniqueId());
        this.clearTeam(e.getPlayer());
        Bukkit.getScheduler().runTask(this.plugin, this::refreshAll);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void chat(AsyncChatEvent e) {
        Component badge = this.cachedBadge(e.getPlayer().getUniqueId());
        e.renderer((source, sourceDisplayName, message, viewer) -> badge.append((Component)Component.text((String)source.getName(), (TextColor)NamedTextColor.WHITE)).append((Component)Component.text((String)": ", (TextColor)NamedTextColor.GRAY)).append(message));
    }

    private record BadgeState(long level, LevelColorTheme theme) {
    }
}

