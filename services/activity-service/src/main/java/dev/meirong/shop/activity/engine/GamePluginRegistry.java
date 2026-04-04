package dev.meirong.shop.activity.engine;

import dev.meirong.shop.activity.domain.GameType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GamePluginRegistry {

    private final Map<GameType, GamePlugin> plugins;

    public GamePluginRegistry(List<GamePlugin> pluginList) {
        this.plugins = pluginList.stream()
                .collect(Collectors.toMap(GamePlugin::supportedType, Function.identity()));
    }

    public Optional<GamePlugin> getPlugin(GameType type) {
        return Optional.ofNullable(plugins.get(type));
    }

    public GamePlugin requirePlugin(GameType type) {
        return getPlugin(type)
                .orElseThrow(() -> new IllegalArgumentException("No plugin registered for game type: " + type));
    }
}
