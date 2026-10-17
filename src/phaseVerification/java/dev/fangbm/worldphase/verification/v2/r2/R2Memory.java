package dev.fangbm.worldphase.verification.v2.r2;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.*;
/** Measured reachable owned graph. Borrowed reference/owner/clock and static JVM state excluded.
 * Shared immutable metadata reached through owned graph is deliberately included (conservative).
 */
public final class R2Memory {
    private R2Memory(){}
    public static long measure(Object context,Runnable checkpoint){
        try{
            Class<?> agent=Class.forName("org.worldphase.measurement.Agent",true,ClassLoader.getSystemClassLoader());
            Instrumentation inst=(Instrumentation)agent.getMethod("instrumentation").invoke(null);
            // NeoForge verification classes are a named module, not ALL-UNNAMED.
            // Dev agent opens only JDK containers to this measurement module.
            inst.redefineModule(Object.class.getModule(),Set.of(),Map.of(),Map.of("java.util",Set.of(R2Memory.class.getModule()),"java.lang",Set.of(R2Memory.class.getModule())),Set.of(),Map.of());
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Object> queue=new ArrayDeque<>();queue.add(context);long bytes=0;
            while(!queue.isEmpty()){
                Object v=queue.removeFirst();if(!seen.add(v))continue;bytes+=inst.getObjectSize(v);
                if((seen.size()&255)==0)checkpoint.run();
                Class<?> type=v.getClass();if(type.isEnum()||v instanceof Class<?>)continue;
                if(type.isArray()){if(!type.getComponentType().isPrimitive())for(Object x:(Object[])v)if(x!=null)queue.add(x);continue;}
                for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
                    if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
                    if(v==context&&Set.of("reference","owner","clock").contains(f.getName()))continue;
                    f.setAccessible(true);Object x=f.get(v);if(x!=null)queue.add(x);
                }
            }
            return bytes;
        }catch(ReflectiveOperationException|RuntimeException e){throw new IllegalStateException("RETAINED_CONTEXT_UNPROVEN",e);}
    }
}
