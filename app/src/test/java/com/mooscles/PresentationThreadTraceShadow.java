package com.mooscles;

import android.os.Looper;
import android.os.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadows.ShadowTrace;

/** Test-only observation of the existing preparation scope; tracing behavior is preserved. */
@Implements(Trace.class)
public class PresentationThreadTraceShadow extends ShadowTrace {
    private static final ConcurrentLinkedQueue<Boolean> PREPARATION_ON_MAIN = new ConcurrentLinkedQueue<>();

    @Implementation
    protected static void beginSection(String name) {
        if ("Mooscles:present".equals(name)) {
            PREPARATION_ON_MAIN.add(Looper.myLooper() == Looper.getMainLooper());
        }
        ShadowTrace.beginSection(name);
    }

    public static List<Boolean> preparationThreads() {
        return new ArrayList<>(PREPARATION_ON_MAIN);
    }

    @Resetter
    public static void resetThreadRecords() {
        PREPARATION_ON_MAIN.clear();
        ShadowTrace.reset();
    }
}
