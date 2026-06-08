package dev.garlandframework.base.tracker;

import dev.garlandframework.base.Step;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResourceTracker<ID> {

    private final List<ID> tracked = new ArrayList<>();
    private final Consumer<ID> cleanup;

    public ResourceTracker(Consumer<ID> cleanup) {
        this.cleanup = cleanup;
    }

    public <T> Step<T, T> track(Function<T, ID> extractor) {
        return (dto, ctx) -> {
            tracked.add(extractor.apply(dto));
            return dto;
        };
    }

    public void cleanupAll(Logger log) {
        for (ID id : new ArrayList<>(tracked)) {
            try {
                cleanup.accept(id);
            } catch (Throwable e) {
                log.warn("Cleanup failed for {}: {}", id, e.getMessage());
            }
        }
        tracked.clear();
    }
}
