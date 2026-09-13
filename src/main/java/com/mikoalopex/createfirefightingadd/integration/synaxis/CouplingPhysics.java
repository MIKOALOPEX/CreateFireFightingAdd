package com.mikoalopex.createfirefightingadd.integration.synaxis;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;
import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.BallCouplingBlockEntity;
import com.mikoalopex.createfirefightingadd.integration.sable.SableStructureCompat;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Uses the installed Synaxis weld converter without linking native Sable constraint types. */
public final class CouplingPhysics {
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

    public static Object connect(BallCouplingBlockEntity base, BallCouplingBlockEntity top, UUID id, int mode) {
        if (!available()) return null;
        try { return api.connect(base, top, id, mode); }
        catch (ReflectiveOperationException | RuntimeException e) { report(e); return null; }
    }

    public static boolean active(Object key) {
        if (api == null || key == null) return false;
        try { return (boolean) api.active.invoke(api.access.invoke(null), key); }
        catch (ReflectiveOperationException e) { report(e); return false; }
    }

    public static void remove(Object key) {
        if (api == null || key == null) return;
        try { api.remove.invoke(api.access.invoke(null), key); }
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

        Object connect(BallCouplingBlockEntity a, BallCouplingBlockEntity b, UUID id, int mode) throws ReflectiveOperationException {
            Object bodyA = bodyId(a);
            Object bodyB = bodyId(b);
            if (bodyA.equals(bodyB)) return null;
            Object viewA = ((Optional<?>) body.invoke(null, bodyA)).orElse(null);
            Object viewB = ((Optional<?>) body.invoke(null, bodyB)).orElse(null);
            if (viewA == null && !(boolean) ground.invoke(bodyA) || viewB == null && !(boolean) ground.invoke(bodyB)) return null;
            Vector3d anchorA = vector(viewA == null ? a.worldAnchor() : a.localAnchor());
            Vector3d anchorB = vector(viewB == null ? b.worldAnchor() : b.localAnchor());
            Quaterniond qa = viewA == null ? new Quaterniond() : new Quaterniond((Quaterniondc) orientation.invoke(viewA));
            Quaterniond qb = viewB == null ? new Quaterniond() : new Quaterniond((Quaterniondc) orientation.invoke(viewB));
            Vector3d axis = vector(a.worldNormal()).normalize();
            // A free ball joint preserves its current pose. Hinge and fixed modes align the top axis to the socket.
            Quaterniond targetB = mode == 0 ? new Quaterniond(qb)
                : new Quaterniond().rotationTo(vector(b.worldNormal()), new Vector3d(axis).negate()).mul(qb).normalize();
            Quaterniond worldFrame = new Quaterniond().rotationTo(new Vector3d(1,0,0), axis);
            Quaterniond frameA = new Quaterniond(qa).conjugate().mul(worldFrame);
            Quaterniond frameB = new Quaterniond(targetB).conjugate().mul(worldFrame);
            // Ground represents the entire world: never disable all world collisions for a joint.
            boolean contacts = (boolean) ground.invoke(bodyA) || (boolean) ground.invoke(bodyB);
            List<Object> values = new ArrayList<>(List.of(id, modes[mode], bodyA, bodyB, anchorA, anchorB,
                new Quaterniond(qa).conjugate().mul(targetB), frameA, frameB,
                new Quaterniond(qa).conjugate().transform(new Vector3d(axis)),
                new Quaterniond(targetB).conjugate().transform(new Vector3d(axis)), contacts));
            if (definition.getParameterCount() == 14) { values.add(Optional.empty()); values.add(Optional.empty()); }
            Object weld = definition.newInstance(values.toArray());
            Object specification = spec.invoke(null, weld);
            ResourceLocation name = ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, "ball_coupling");
            Object constraintKey = key.newInstance(owner.newInstance(a.worldLevel().dimension(), name,
                ResourceLocation.fromNamespaceAndPath(CreateFireFightingAdd.MODID, id.toString())), name);
            Object constraints = access.invoke(null);
            String result = (configuration == null ? replace.invoke(constraints, constraintKey, specification)
                : replace.invoke(constraints, constraintKey, specification, configuration.invoke(null, weld))).toString();
            if (!result.equals("APPLIED") && !result.equals("QUEUED") && !result.equals("STORED_DESIRED")) return null;
            if (configuration == null) {
                try { command.invoke(constraints, constraintKey, contact.newInstance(contacts)); }
                catch (ReflectiveOperationException e) { remove.invoke(constraints, constraintKey); throw e; }
            }
            return constraintKey;
        }

        private Object bodyId(BallCouplingBlockEntity endpoint) throws ReflectiveOperationException {
            UUID subLevel = SableStructureCompat.containingSubLevelId(endpoint);
            UUID id = subLevel != null ? subLevel : new UUID(0, 0);
            return bodyIdConstructor.newInstance(endpoint.worldLevel().dimension(), id);
        }

        private static Vector3d vector(net.minecraft.world.phys.Vec3 v) { return new Vector3d(v.x,v.y,v.z); }
    }
}
