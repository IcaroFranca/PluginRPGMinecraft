package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.LevelColorTheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

public final class LevelBadgeRenderer {
    private static final TextColor BRACKET = TextColor.color((int)0x555555);
    private final Map<Key, List<Component>> cache = new ConcurrentHashMap<Key, List<Component>>();
    private final int smoothness;

    public LevelBadgeRenderer() {
        this(1);
    }

    public LevelBadgeRenderer(int smoothness) {
        this.smoothness = Math.max(1, smoothness);
    }

    public Component frame(long level, LevelColorTheme theme, long tick) {
        List<Component> frames = this.frames(level, theme);
        return frames.get(theme.animated() ? Math.floorMod(tick, frames.size()) : 0);
    }

    public List<Component> frames(long level, LevelColorTheme theme) {
        return this.cache.computeIfAbsent(new Key(level, theme.id()), key -> this.build(level, theme));
    }

    public void clear() {
        this.cache.clear();
    }

    private List<Component> build(long level, LevelColorTheme theme) {
        String digits = Long.toString(Math.max(0L, level));
        int[] colors = theme.palette();
        if (!theme.animated()) {
            return List.of(((TextComponent)Component.text((String)"[", (TextColor)BRACKET).append((Component)Component.text((String)digits, (TextColor)TextColor.color((int)colors[0])))).append((Component)Component.text((String)"] ", (TextColor)BRACKET)));
        }
        ArrayList<Integer> unique = new ArrayList<Integer>(new LinkedHashSet<Integer>(Arrays.stream(colors).boxed().toList()));
        ArrayList<Integer> bounce = new ArrayList<Integer>(unique);
        for (int i = unique.size() - 2; i > 0; --i) {
            bounce.add((Integer)unique.get(i));
        }
        ArrayList<TextComponent> frames = new ArrayList<TextComponent>();
        int steps = Math.max(1, theme.frameInterval() * this.smoothness);
        for (int i = 0; i < bounce.size(); ++i) {
            int from = (Integer)bounce.get(i);
            int to = (Integer)bounce.get((i + 1) % bounce.size());
            for (int step = 0; step < steps; ++step) {
                TextColor active = TextColor.color((int)this.interpolate(from, to, (double)step / (double)steps));
                TextComponent frame = theme.colorsWholeBadge() ? Component.text((String)("[" + digits + "] "), (TextColor)active) : ((TextComponent)Component.text((String)"[", (TextColor)BRACKET).append((Component)Component.text((String)digits, (TextColor)active))).append((Component)Component.text((String)"] ", (TextColor)BRACKET));
                frames.add(frame);
            }
        }
        return List.copyOf(frames);
    }

    private int interpolate(int from, int to, double ratio) {
        int r = (int)Math.round((double)(from >> 16 & 0xFF) * (1.0 - ratio) + (double)(to >> 16 & 0xFF) * ratio);
        int g = (int)Math.round((double)(from >> 8 & 0xFF) * (1.0 - ratio) + (double)(to >> 8 & 0xFF) * ratio);
        int b = (int)Math.round((double)(from & 0xFF) * (1.0 - ratio) + (double)(to & 0xFF) * ratio);
        return r << 16 | g << 8 | b;
    }

    private record Key(long level, String theme) {
    }
}

