package org.modulartestorchestrator.testng;

import org.modulartestorchestrator.base.tracker.ResourceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractMtoBaseTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractMtoBaseTest.class);

    private final List<ResourceTracker<?>> trackers = new ArrayList<>();

    protected final void registerTrackers(ResourceTracker<?>... trackers) {
        Collections.addAll(this.trackers, trackers);
    }

    @AfterMethod(alwaysRun = true)
    public void mtoCleanupResources() {
        trackers.forEach(t -> t.cleanupAll(log));
    }
}
