package io.quarkus.analytics.dto.config;

import java.io.IOException;
import java.io.Serializable;

import io.quarkus.analytics.util.FileUtils.JsonSerializable;
import io.quarkus.bootstrap.json.Json;
import io.quarkus.bootstrap.json.JsonBoolean;
import io.quarkus.bootstrap.json.JsonObject;

public class LocalConfig implements AnalyticsLocalConfig, Serializable, JsonSerializable {
    private boolean disabled;

    public LocalConfig(boolean disabled) {
        this.disabled = disabled;
    }

    public LocalConfig() {
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        try {
            Json.object()
                    .put("disabled", disabled)
                    .appendTo(sb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    public static LocalConfig fromJson(JsonObject json) {
        JsonBoolean disabledValue = json.get("disabled");
        LocalConfig config = new LocalConfig();
        config.setDisabled(disabledValue != null && disabledValue.value());
        return config;
    }
}
