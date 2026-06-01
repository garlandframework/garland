package org.modulartestorchestrator.base;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PipelineContext {

    private final Map<String, Object> data = new HashMap<>();

    public <T> void put(String key, T value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public Map<String, Object> raw() {
        return Collections.unmodifiableMap(data);
    }
}