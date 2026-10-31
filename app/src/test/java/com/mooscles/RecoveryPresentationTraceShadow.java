package com.mooscles;

import android.os.Looper;
import android.os.Trace;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadows.ShadowTrace;

/** A test-only held historical presenter; essential recovery uses its separate trace scope. */
@Implements(Trace.class)
public class RecoveryPresentationTraceShadow extends ShadowTrace {
    private static volatile CountDownLatch release = new CountDownLatch(0);
    private static volatile CountDownLatch entered = new CountDownLatch(0);

    public static void holdFullPreparation() {
        release = new CountDownLatch(1);
        entered = new CountDownLatch(1);
    }

    public static boolean fullPreparationEntered() { return entered.getCount() == 0; }

    public static void releaseFullPreparation() { release.countDown(); }

    @Implementation
    protected static void beginSection(String name) {
        if ("Mooscles:present".equals(name) && release.getCount() != 0) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw new AssertionError("Full historical preparation must not block the main looper");
            }
            entered.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) {
                    throw new AssertionError("Held historical presenter was never released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Held historical presenter interrupted", interrupted);
            }
        }
        ShadowTrace.beginSection(name);
    }

    @Resetter
    public static void resetRecoveryGate() {
        release.countDown();
        release = new CountDownLatch(0);
        entered = new CountDownLatch(0);
        ShadowTrace.reset();
    }
}
