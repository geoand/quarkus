package io.quarkus.analytics.dto.config;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import io.quarkus.analytics.dto.segment.SegmentContext;
import io.quarkus.analytics.util.FileUtils.JsonSerializable;
import io.quarkus.bootstrap.json.Json;

/**
 * Identity of the user at the upstream collection tool.
 */
public class Identity implements Serializable, SegmentContext, JsonSerializable {
    private String userId;
    private Map<String, Object> context;
    private Instant timestamp;

    public Identity(String userId, Map<String, Object> context, Instant timestamp) {
        this.userId = userId;
        this.context = context;
        this.timestamp = timestamp;
    }

    public static IdentityBuilder builder() {
        return new IdentityBuilder();
    }

    /**
     * The UUID of the user.
     *
     * @return
     */
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * The context of the user. See: AnalyticsService.createContextMap() (package friendly) for details.
     *
     * @return
     */
    @Override
    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public static class IdentityBuilder {
        private String userId;
        private Map<String, Object> context;
        private Instant timestamp;

        IdentityBuilder() {
        }

        public IdentityBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public IdentityBuilder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public IdentityBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Identity build() {
            return new Identity(userId, context, timestamp);
        }

        public String toString() {
            return "Identity.IdentityBuilder(userId=" + this.userId + ", context="
                    + this.context + ", timestamp=" + this.timestamp + ")";
        }
    }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        try {
            Json.JsonObjectBuilder builder = Json.object()
                    .put("userId", userId);
            if (context != null) {
                builder.put("context", mapToJsonObjectBuilder(context));
            }
            if (timestamp != null) {
                builder.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(timestamp));
            }
            builder.appendTo(sb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Json.JsonObjectBuilder mapToJsonObjectBuilder(Map<String, Object> map) {
        Json.JsonObjectBuilder builder = Json.object();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                builder.put(entry.getKey(), s);
            } else if (value instanceof Boolean b) {
                builder.put(entry.getKey(), b);
            } else if (value instanceof Integer i) {
                builder.put(entry.getKey(), i);
            } else if (value instanceof Long l) {
                builder.put(entry.getKey(), l);
            } else if (value instanceof Map<?, ?>) {
                builder.put(entry.getKey(), mapToJsonObjectBuilder((Map<String, Object>) value));
            } else if (value instanceof List<?> list) {
                Json.JsonArrayBuilder arr = Json.array();
                for (Object item : list) {
                    if (item instanceof String s) {
                        arr.add(s);
                    } else if (item instanceof Boolean b) {
                        arr.add(b);
                    } else if (item instanceof Integer i) {
                        arr.add(i);
                    } else if (item instanceof Long l) {
                        arr.add(l);
                    } else if (item instanceof Map<?, ?>) {
                        arr.add(mapToJsonObjectBuilder((Map<String, Object>) item));
                    }
                }
                builder.put(entry.getKey(), arr);
            }
        }
        return builder;
    }
}
