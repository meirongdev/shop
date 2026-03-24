package dev.meirong.shop.common.feature;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.features")
public class FeatureToggleProperties {

    private Map<String, Boolean> flags = new LinkedHashMap<>();

    public Map<String, Boolean> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, Boolean> flags) {
        this.flags = flags == null ? new LinkedHashMap<>() : new LinkedHashMap<>(flags);
    }

    public boolean contains(String key) {
        return flags.containsKey(key);
    }

    public Boolean get(String key) {
        return flags.get(key);
    }
}
