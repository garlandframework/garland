package dev.garlandframework.base;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable, stringly-typed state bag that flows through all steps of a single pipeline
 * execution. Not thread-safe — pipelines execute sequentially by design.
 *
 * <p>Keys are plain strings. There is no schema enforcement; steps must agree on key
 * names by convention. Prefer returning data through step output types over writing
 * to the context — context is for values needed several steps downstream that would
 * otherwise need to be threaded through intermediate steps.
 *
 * @see Step#saveToContext(String)
 */
public class PipelineContext {

    private final Map<String, Object> data = new HashMap<>();

    public <T> void put(String key, T value) {
        data.put(key, value);
    }

    /**
     * Returns the value stored under {@code key}, cast to {@code T}. Returns {@code null}
     * if the key is absent. Throws {@link ClassCastException} if the stored type does not
     * match {@code T}.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    /** Read-only view of all context entries. Intended for logging and diagnostics. */
    public Map<String, Object> raw() {
        return Collections.unmodifiableMap(data);
    }
}