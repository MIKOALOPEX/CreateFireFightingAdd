package com.mikoalopex.createfirefightingadd.integration.synaxis;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Uses the installed Synaxis weld converter without linking native Sable constraint types. */
public final class CouplingPhysics {
    /** Anchors use body model coordinates; normals use world coordinates. */
    public record Endpoint(ResourceKey<net.minecraft.world.level.Level> dimension, UUID body,
            Vector3dc anchor, Vector3dc normal) {}
    public record Request(ResourceLocation feature, UUID id, Endpoint a, Endpoint b,
            int mode, double lowerDegrees, double upperDegrees) {}
    private record Connection(Object key, Quaterniond orientationA, Quaterniond orientationB,
            Vector3d normalA, Vector3d normalB) {}
    private static Api api;
    private static boolean inspected;
    private static boolean reported;
    private CouplingPhysics() {}

    public static boolean available() {
        if (!inspected) {
            inspected = true;
            if (ModList.get().isLoaded("synaxis") && ModList.get().isLoaded("sable_schematic_api")) {
                try { api = new Api(); }
                catch (ReflectiveOperationException | LinkageError e) { report(e); }
            }
        }
        return api != null;
    }

    private static void report(Throwable error) {
        if (!reported) {
            reported = true;
            LogUtils.getLogger().warn("Ball coupling physics unavailable: check Synaxis/Sable versions. {}", error.toString());
            LogUtils.getLogger().debug("Ball coupling physics failure", error);
        }
    }

    public static Object connect(Request request) {
        if (!available()) return null;
        try { return api.connect(request, null); }
        catch (ReflectiveOperationException | RuntimeException e) { report(e); return null; }
    }

    /** Rebuilds the limit frames against the original connection pose, not the current angle. */
    public static Object update(Object connection, Request request) {
        if (!available()) return null;
        try { return api.connect(request, connection instanceof Connection c ? c : null); }
        catch (ReflectiveOperationException | RuntimeException e) { report(e); return null; }
    }

    public static CompoundTag saveReference(Object connection) {
        CompoundTag tag = new CompoundTag();
        if (!(connection instanceof Connection c)) return tag;
        double[] values = {c.orientationA.x, c.orientationA.y, c.orientationA.z, c.orientationA.w,
            c.orientationB.x, c.orientationB.y, c.orientationB.z, c.orientationB.w,
            c.normalA.x, c.normalA.y, c.normalA.z, c.normalB.x, c.normalB.y, c.normalB.z};
        for (int i = 0; i < values.length; i++) tag.putDouble("V" + i, values[i]);
        return tag;
    }

    public static Object restoreReference(CompoundTag tag) {
        double[] v = new double[14];
        for (int i = 0; i < v.length; i++) {
            if (!tag.contains("V" + i, net.minecraft.nbt.Tag.TAG_DOUBLE)) return null;
            v[i] = tag.getDouble("V" + i);
            if (!Double.isFinite(v[i])) return null;
        }
        Quaterniond a = new Quaterniond(v[0], v[1], v[2], v[3]), b = new Quaterniond(v[4], v[5], v[6], v[7]);
        Vector3d na = new Vector3d(v[8], v[9], v[10]), nb = new Vector3d(v[11], v[12], v[13]);
        if (a.lengthSquared() < 1e-10 || b.lengthSquared() < 1e-10
                || na.lengthSquared() < 1e-10 || nb.lengthSquared() < 1e-10) return null;
        return new Connection(null, a.normalize(), b.normalize(), na.normalize(), nb.normalize());
    }

    public static boolean active(Object key) {
        if (api == null || key == null) return false;
        try { return (boolean) api.active.invoke(api.access.invoke(null), ((Connection) key).key()); }
        catch (ReflectiveOperationException e) { report(e); return false; }
    }

    public static void remove(Object key) {
        if (api == null || key == null) return;
        try { api.remove.invoke(api.access.invoke(null), ((Connection) key).key()); }
        catch (ReflectiveOperationException e) { report(e); }
    }

    private static final class Api {
        private static final String PHYSICS = "com.verr1.synaxis.foundation.physics.";
        private static final String WELD = "com.verr1.synaxis.compat.sableblueprint.weld.";
        final Method body, access, replace, remove, active, spec, orientation, ground, command, configuration;
        final Constructor<?> bodyIdConstructor, definition, owner, key, contact;
        final Object[] modes;

        Api() throws ReflectiveOperationException {
            Class<?> physics = Class.forName(PHYSICS + "SynaxisPhysics");
            Class<?> bodyIdClass = Class.forName(PHYSICS + "BodyId");
            Class<?> view = Class.forName(PHYSICS + "PhysicsBodyView");
            Class<?> accessClass = Class.forName(PHYSICS + "constraint.ConstraintAccess");
            Class<?> keyClass = Class.forName(PHYSICS + "constraint.ConstraintKey");
            Class<?> ownerClass = Class.forName(PHYSICS + "constraint.owner.ConstraintOwner");
            Class<?> def = Class.forName(WELD + "WeldConstraintDefinition");
            Class<?> mode = Class.forName(WELD + "WeldConstraintMode");
            List<Class<?>> parameters = new ArrayList<>(List.of(UUID.class, mode, bodyIdClass, bodyIdClass,
                Vector3dc.class, Vector3dc.class, Quaterniondc.class, Quaterniondc.class, Quaterniondc.class,
                Vector3dc.class, Vector3dc.class, boolean.class));
            if (def.getRecordComponents().length == 14) { parameters.add(Optional.class); parameters.add(Optional.class); }
            definition = def.getConstructor(parameters.toArray(Class<?>[]::new));
            spec = Class.forName(WELD + "WeldConstraintService").getMethod("spec", def);
            modes = new Object[] {mode.getField("GIMBAL").get(null), mode.getField("HINGE").get(null), mode.getField("FIXED").get(null)};
            bodyIdConstructor = bodyIdClass.getConstructor(ResourceKey.class, UUID.class);
            body = physics.getMethod("body", bodyIdClass);
            ground = bodyIdClass.getMethod("isGround");
            access = physics.getMethod("constraints");
            Class<?> specClass = Class.forName(PHYSICS + "constraint.spec.ConstraintSpec");
            remove = accessClass.getMethod("remove", keyClass);
            active = accessClass.getMethod("isActive", keyClass);
            Method newConfiguration;
            try { newConfiguration = Class.forName(WELD + "WeldConstraintService").getMethod("configuration", def); }
            catch (NoSuchMethodException oldApi) { newConfiguration = null; }
            configuration = newConfiguration;
            if (configuration != null) {
                replace = accessClass.getMethod("replace", keyClass, specClass, configuration.getReturnType());
                command = null;
                contact = null;
            } else {
                replace = accessClass.getMethod("replace", keyClass, specClass);
                command = accessClass.getMethod("command", keyClass, Class.forName(PHYSICS + "constraint.command.ConstraintCommand"));
                contact = Class.forName(PHYSICS + "constraint.command.ContactCommand").getConstructor(boolean.class);
            }
            orientation = view.getMethod("orientation");
            owner = Class.forName(PHYSICS + "constraint.owner.SystemConstraintOwner")
                .getConstructor(ResourceKey.class, ResourceLocation.class, ResourceLocation.class);
            key = keyClass.getConstructor(ownerClass, ResourceLocation.class);
        }

        Object connect(Request request, Connection previous) throws ReflectiveOperationException {
            Endpoint a = request.a(), b = request.b();
            int mode = request.mode();
            if (mode < 0 || mode > 2 || !Double.isFinite(request.lowerDegrees())
                    || !Double.isFinite(request.upperDegrees()) || request.lowerDegrees() < -180
                    || request.upperDegrees() > 180 || request.lowerDegrees() > request.upperDegrees()) return null;
            if (mode == 1 && definition.getParameterCount() != 14) return null;
            UUID id = request.id();
            if (!a.dimension().equals(b.dimension())) return null;
            Object bodyA = bodyId(a);
            Object bodyB = bodyId(b);
            if (bodyA.equals(bodyB)) return null;
            Object viewA = ((Optional<?>) body.invoke(null, bodyA)).orElse(null);
            Object viewB = ((Optional<?>) body.invoke(null, bodyB)).orElse(null);
            if (viewA == null && !(boolean) ground.invoke(bodyA) || viewB == null && !(boolean) ground.invoke(bodyB)) return null;
            Vector3d anchorA = new Vector3d(a.anchor());
            Vector3d anchorB = new Vector3d(b.anchor());
            Quaterniond qa = viewA == null ? new Quaterniond() : new Quaterniond((Quaterniondc) orientation.invoke(viewA));
            Quaterniond qb = viewB == null ? new Quaterniond() : new Quaterniond((Quaterniondc) orientation.invoke(viewB));
            if (previous != null) {
                qa.set(previous.orientationA());
                qb.set(previous.orientationB());
            }
            Vector3d axis = new Vector3d(previous == null ? a.normal() : previous.normalA()).normalize();
            Vector3d otherAxis = new Vector3d(previous == null ? b.normal() : previous.normalB()).normalize();
            // A free ball joint preserves its current pose. Hinge and fixed modes align the top axis to the socket.
            Quaterniond targetB = mode == 0 ? new Quaterniond(qb)
                : new Quaterniond().rotationTo(otherAxis, new Vector3d(axis).negate()).mul(qb).normalize();
            Object limit = null;
            if (mode == 1 && definition.getParameterCount() == 14) {
                Class<?> arc = Class.forName(WELD + "WeldAngularLimit$ArcSelection");
                Class<?> limitClass = Class.forName(WELD + "WeldAngularLimit");
                double lower = Math.toRadians(request.lowerDegrees()), upper = Math.toRadians(request.upperDegrees());
                double offset = (lower + upper) / 2;
                // Preserve the signed -180 endpoint instead of wrapping it onto +180.
                limit = limitClass.getConstructor(double.class, double.class, arc, double.class, double.class, double.class)
                    .newInstance(lower, upper, arc.getField("BETWEEN_ENDPOINTS").get(null), offset,
                        lower - offset, upper - offset);
                targetB = new Quaterniond().rotationAxis(offset, axis.x, axis.y, axis.z).mul(targetB).normalize();
            }
            Quaterniond worldFrame = new Quaterniond().rotationTo(new Vector3d(1,0,0), axis);
            Quaterniond frameA = new Quaterniond(qa).conjugate().mul(worldFrame);
            Quaterniond frameB = new Quaterniond(targetB).conjugate().mul(worldFrame);
            // Ground represents the entire world: never disable all world collisions for a joint.
            boolean contacts = (boolean) ground.invoke(bodyA) || (boolean) ground.invoke(bodyB);
            List<Object> values = new ArrayList<>(List.of(id, modes[mode], bodyA, bodyB, anchorA, anchorB,
                new Quaterniond(qa).conjugate().mul(targetB), frameA, frameB,
                new Quaterniond(qa).conjugate().transform(new Vector3d(axis)),
                new Quaterniond(targetB).conjugate().transform(new Vector3d(axis)), contacts));
            if (definition.getParameterCount() == 14) { values.add(Optional.ofNullable(limit)); values.add(Optional.empty()); }
            Object weld = definition.newInstance(values.toArray());
            Object specification = spec.invoke(null, weld);
            ResourceLocation name = request.feature();
            Object constraintKey = key.newInstance(owner.newInstance(a.dimension(), name,
                ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, id.toString())), name);
            Object constraints = access.invoke(null);
            Object config = configuration == null ? null : configuration.invoke(null, weld);
            if (config != null && contacts)
                config = config.getClass().getMethod("withContactsEnabled", boolean.class).invoke(config, true);
            String result = (configuration == null ? replace.invoke(constraints, constraintKey, specification)
                : replace.invoke(constraints, constraintKey, specification, config)).toString();
            if (!result.equals("APPLIED") && !result.equals("QUEUED") && !result.equals("STORED_DESIRED")) return null;
            if (configuration == null) {
                try { command.invoke(constraints, constraintKey, contact.newInstance(contacts)); }
                catch (ReflectiveOperationException e) { remove.invoke(constraints, constraintKey); throw e; }
            }
            return new Connection(constraintKey, qa, qb, axis, otherAxis);
        }

        private Object bodyId(Endpoint endpoint) throws ReflectiveOperationException {
            UUID subLevel = endpoint.body();
            UUID id = subLevel != null ? subLevel : new UUID(0, 0);
            return bodyIdConstructor.newInstance(endpoint.dimension(), id);
        }

    }
}
