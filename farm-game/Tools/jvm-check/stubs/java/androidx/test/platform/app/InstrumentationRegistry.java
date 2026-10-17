package androidx.test.platform.app;

import android.app.Instrumentation;
import android.os.Bundle;

/** Minimal stand-in for androidx.test:monitor (Google Maven is unreachable here). */
public final class InstrumentationRegistry {
    private static Instrumentation instrumentation;
    private static Bundle arguments = new Bundle();

    public static Instrumentation getInstrumentation() { return instrumentation; }
    public static Bundle getArguments() { return arguments; }
    public static void registerInstance(Instrumentation i, Bundle args) { instrumentation = i; arguments = args; }
}
