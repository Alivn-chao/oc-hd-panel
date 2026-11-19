package dev.bec.hdpanel.adapter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.tileentity.TileEntity;

/** GTNH 2.9 BEC access on OC's synchronized server thread. Keeps GT optional. */
final class BecNodeAccess implements BecBatch.Node {
    private static final String NODE = "tectech.thing.metaTileEntity.multi.bec.MTEBECIONode";

    private final TileEntity tile;
    private final Object machine;
    private final Class<?> nodeType;

    private BecNodeAccess(TileEntity tile, Object machine, Class<?> nodeType) {
        this.tile = tile;
        this.machine = machine;
        this.nodeType = nodeType;
    }

    static BecNodeAccess open(TileEntity tile) throws ReflectiveOperationException {
        Object machine = tile.getClass().getMethod("getMetaTileEntity").invoke(tile);
        for (Class<?> type = machine == null ? null : machine.getClass(); type != null; type = type.getSuperclass()) {
            if (NODE.equals(type.getName())) return new BecNodeAccess(tile, machine, type);
        }
        throw new IllegalStateException("target is not a supported GTNH BEC I/O node");
    }

    @Override public Object identity() { return tile; }

    private int number(Object target, String method) throws ReflectiveOperationException {
        return ((Number) target.getClass().getMethod(method).invoke(target)).intValue();
    }

    private String state() throws ReflectiveOperationException {
        return (String) nodeType.getMethod("getState").invoke(machine);
    }

    private boolean allowed() throws ReflectiveOperationException {
        return (Boolean) tile.getClass().getMethod("isAllowedToWork").invoke(tile);
    }

    @Override public int min() throws ReflectiveOperationException { return number(machine, "getMinParallel"); }
    @Override public int max() throws ReflectiveOperationException { return number(machine, "getMaxParallel"); }

    @Override public void requireIdleClosed() throws ReflectiveOperationException {
        if (allowed() || !"idle".equals(state()) || number(tile, "getMaxProgress") > 0) {
            throw new IllegalStateException("node must be idle and closed to new recipes");
        }
    }

    @Override public void limits(int min, int max) throws ReflectiveOperationException {
        Method setMin = nodeType.getMethod("setMinParallel", int.class);
        Method setMax = nodeType.getMethod("setMaxParallel", int.class);
        int oldMin = min();
        int oldMax = max();
        if (oldMin == min && oldMax == max) return;
        tile.markDirty();
        if (oldMin > max) {
            setMin.invoke(machine, min);
            oldMin = min;
        }
        if (oldMax != max) setMax.invoke(machine, max);
        if (oldMin != min) setMin.invoke(machine, min);
    }

    Map<String, Object> snapshot() throws ReflectiveOperationException {
        String state = state();
        int active = number(machine, "getParallelRecipesInProgress");
        int largest = number(tile, "getMaxProgress");
        boolean allowed = allowed();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("state", state);
        row.put("min", min());
        row.put("max", max());
        row.put("parallel", active);
        row.put("progress", number(tile, "getProgress"));
        row.put("maxProgress", largest);
        row.put("workAllowed", allowed);
        row.put("admitted", !allowed && largest > 0 && active > 0
                && ("paused-immediate".equals(state) || "nanite-tier-too-low".equals(state)));
        return row;
    }

    void openAdmission(int quota) throws ReflectiveOperationException {
        requireIdleClosed();
        if (quota < 1 || min() != quota || max() != quota) {
            throw new IllegalStateException("readback limits do not match the requested quota");
        }
        if (!instantPauseSignal()) throw new IllegalStateException("an immediate-pause hatch must receive redstone before admission");
        tile.markDirty();
        tile.getClass().getMethod("enableWorking").invoke(tile);
        if (!allowed()) throw new IllegalStateException("could not confirm admission opened");
    }

    void closeAdmission() throws ReflectiveOperationException {
        if (allowed()) {
            tile.markDirty();
            tile.getClass().getMethod("disableWorking").invoke(tile);
        }
        if (allowed()) throw new IllegalStateException("could not confirm admission closed");
    }

    private boolean instantPauseSignal() throws ReflectiveOperationException {
        Field field = nodeType.getDeclaredField("controllerHatches");
        field.setAccessible(true);
        for (Object hatch : (Iterable<?>) field.get(machine)) {
            if (hatch == null) continue;
            Object mode = hatch.getClass().getMethod("getMode").invoke(hatch);
            if (mode instanceof Enum && "PAUSE_INSTANT".equals(((Enum<?>) mode).name())
                    && Boolean.TRUE.equals(hatch.getClass().getMethod("receivingSignal").invoke(hatch))) return true;
        }
        return false;
    }

    static String message(Throwable failure) {
        if (failure instanceof InvocationTargetException) failure = ((InvocationTargetException) failure).getTargetException();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
