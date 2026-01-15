package io.quarkus.analytics.dto.segment;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import io.quarkus.analytics.util.FileUtils.JsonSerializable;
import io.quarkus.bootstrap.json.Json;

public class Track implements Serializable, JsonSerializable {
    private String userId;
    private TrackEventType event;
    private TrackProperties properties;
    private Map<String, Object> context;
    private Instant timestamp;

    public Track() {
    }

    public Track(String userId, TrackEventType event, TrackProperties properties, Map<String, Object> context,
            Instant timestamp) {
        this.userId = userId;
        this.event = event;
        this.properties = properties;
        this.context = context;
        this.timestamp = timestamp;
    }

    public static TrackBuilder builder() {
        return new TrackBuilder();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public TrackEventType getEvent() {
        return event;
    }

    public void setEvent(TrackEventType event) {
        this.event = event;
    }

    public TrackProperties getProperties() {
        return properties;
    }

    public void setProperties(TrackProperties properties) {
        this.properties = properties;
    }

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

    public static class TrackBuilder {
        private String userId;
        private TrackEventType event;
        private TrackProperties properties;
        private Map<String, Object> context;
        private Instant timestamp;

        TrackBuilder() {
        }

        public TrackBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public TrackBuilder event(TrackEventType event) {
            this.event = event;
            return this;
        }

        public TrackBuilder properties(TrackProperties properties) {
            this.properties = properties;
            return this;
        }

        public TrackBuilder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public TrackBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Track build() {
            return new Track(userId, event, properties, context, timestamp);
        }

        public String toString() {
            return "Track.TrackBuilder(userId=" + this.userId + ", event=" + this.event +
                    ", properties=" + this.properties + ", context=" + this.context +
                    ", timestamp=" + this.timestamp + ")";
        }
    }

    public static class EventPropertyNames {
        public static final String BUILD_DIAGNOSTICS = "build_diagnostics";
        public static final String APP_EXTENSIONS = "app_extensions";
    }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        try {
            Json.JsonObjectBuilder builder = Json.object()
                    .put("userId", userId);
            if (event != null) {
                builder.put("event", event.name());
            }
            if (properties != null) {
                builder.put("properties", properties.toJsonObjectBuilder());
            }
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
