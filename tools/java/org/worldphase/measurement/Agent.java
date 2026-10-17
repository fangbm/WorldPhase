package org.worldphase.measurement;
import java.lang.instrument.Instrumentation;
/** Optional dev measurement agent, packaged separately; never the gameplay artifact. */
public final class Agent {
    private static Instrumentation value;
    public static void premain(String ignored,Instrumentation instrumentation){value=instrumentation;}
    public static Instrumentation instrumentation(){if(value==null)throw new IllegalStateException("RETAINED_INSTRUMENTATION_UNAVAILABLE");return value;}
}
