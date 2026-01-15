package io.quarkus.analytics.dto.config;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.quarkus.analytics.util.FileUtils.JsonSerializable;
import io.quarkus.bootstrap.json.Json;
import io.quarkus.bootstrap.json.JsonArray;
import io.quarkus.bootstrap.json.JsonBoolean;
import io.quarkus.bootstrap.json.JsonDouble;
import io.quarkus.bootstrap.json.JsonInteger;
import io.quarkus.bootstrap.json.JsonObject;
import io.quarkus.bootstrap.json.JsonString;
import io.quarkus.bootstrap.json.JsonValue;

/**
 * Allow to configure build analytics behaviour by downloading a remote configuration file from a public location.
 */
public class RemoteConfig implements AnalyticsRemoteConfig, Serializable, JsonSerializable {

    private boolean active;
    private List<String> denyAnonymousIds;
    private List<String> denyQuarkusVersions;
    private Duration refreshInterval;

    public RemoteConfig() {
    }

    RemoteConfig(boolean active, List<String> denyUserIds, List<String> denyQuarkusVersions, Duration refreshInterval) {
        this.active = active;
        this.denyAnonymousIds = denyUserIds;
        this.denyQuarkusVersions = denyQuarkusVersions;
        this.refreshInterval = refreshInterval;
    }

    public static RemoteConfigBuilder builder() {
        return new RemoteConfigBuilder();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getDenyAnonymousIds() {
        return denyAnonymousIds;
    }

    public void setDenyAnonymousIds(List<String> denyAnonymousIds) {
        this.denyAnonymousIds = denyAnonymousIds;
    }

    public List<String> getDenyQuarkusVersions() {
        return denyQuarkusVersions;
    }

    public void setDenyQuarkusVersions(List<String> denyQuarkusVersions) {
        this.denyQuarkusVersions = denyQuarkusVersions;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RemoteConfig that = (RemoteConfig) o;
        return active == that.active &&
                Objects.equals(denyAnonymousIds, that.denyAnonymousIds) &&
                Objects.equals(denyQuarkusVersions, that.denyQuarkusVersions) &&
                Objects.equals(refreshInterval, that.refreshInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(active, denyAnonymousIds, denyQuarkusVersions, refreshInterval);
    }

    public static class RemoteConfigBuilder {
        private boolean active;
        private List<String> denyUserIds;
        private List<String> denyQuarkusVersions;
        private Duration refreshInterval;

        RemoteConfigBuilder() {
        }

        public RemoteConfigBuilder active(boolean active) {
            this.active = active;
            return this;
        }

        public RemoteConfigBuilder denyUserIds(List<String> denyUserIds) {
            this.denyUserIds = denyUserIds;
            return this;
        }

        public RemoteConfigBuilder denyQuarkusVersions(List<String> denyQuarkusVersions) {
            this.denyQuarkusVersions = denyQuarkusVersions;
            return this;
        }

        public RemoteConfigBuilder refreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
            return this;
        }

        public RemoteConfig build() {
            return new RemoteConfig(active, denyUserIds, denyQuarkusVersions, refreshInterval);
        }

        public String toString() {
            return "RemoteConfig.RemoteConfigBuilder(active=" + this.active + ", denyUserIds=" + this.denyUserIds +
                    ", denyQuarkusVersions=" + this.denyQuarkusVersions + ", refreshInterval=" + this.refreshInterval + ")";
        }
    }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        try {
            Json.JsonObjectBuilder builder = Json.object()
                    .put("active", active);
            if (denyAnonymousIds != null) {
                Json.JsonArrayBuilder arr = Json.array();
                for (String id : denyAnonymousIds) {
                    arr.add(id);
                }
                builder.put("deny_anonymous_ids", arr);
            }
            if (denyQuarkusVersions != null) {
                Json.JsonArrayBuilder arr = Json.array();
                for (String v : denyQuarkusVersions) {
                    arr.add(v);
                }
                builder.put("deny_quarkus_versions", arr);
            }
            if (refreshInterval != null) {
                builder.put("refresh_interval", refreshInterval.toString());
            }
            builder.appendTo(sb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    public static RemoteConfig fromJson(JsonObject json) {
        RemoteConfig config = new RemoteConfig();
        JsonBoolean activeValue = json.get("active");
        config.setActive(activeValue != null && activeValue.value());

        JsonArray denyIdsArray = json.get("deny_anonymous_ids");
        if (denyIdsArray != null) {
            List<String> ids = new ArrayList<>();
            for (JsonValue v : denyIdsArray.value()) {
                if (v instanceof JsonString s) {
                    ids.add(s.value());
                }
            }
            config.setDenyAnonymousIds(ids);
        }

        JsonArray denyVersionsArray = json.get("deny_quarkus_versions");
        if (denyVersionsArray != null) {
            List<String> versions = new ArrayList<>();
            for (JsonValue v : denyVersionsArray.value()) {
                if (v instanceof JsonString s) {
                    versions.add(s.value());
                }
            }
            config.setDenyQuarkusVersions(versions);
        }

        JsonValue intervalValue = json.get("refresh_interval");
        if (intervalValue != null) {
            if (intervalValue instanceof JsonString s) {
                config.setRefreshInterval(Duration.parse(s.value()));
            } else if (intervalValue instanceof JsonDouble d) {
                config.setRefreshInterval(Duration.ofSeconds((long) d.value()));
            } else if (intervalValue instanceof JsonInteger i) {
                config.setRefreshInterval(Duration.ofSeconds(i.longValue()));
            }
        }

        return config;
    }
}
