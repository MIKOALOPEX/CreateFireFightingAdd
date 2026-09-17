package com.mikoalopex.createfirefightingadd.integration.synaxis;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mikoalopex.createfirefightingadd.content.kinetics.coupling.StressCouplingCompatibility;
import com.mojang.logging.LogUtils;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Applies short-lived docking forces before {@link CouplingPhysics} creates the final constraint. */
public final class CouplingAlignment {
    private static final Set<Session> SESSIONS = new HashSet<>();
    private static Api api;
    private static boolean inspected;
    private static boolean reported;

    private CouplingAlignment() {}

    public static boolean available() {
        if (!StressCouplingCompatibility.supportedVersions())
            return false;
        if (!inspected) {
            inspected = true;
            try { api = new Api(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) { report(error); }
        }
        return api != null;
    }

    public static Session begin(CouplingPhysics.Endpoint a, CouplingPhysics.Endpoint b, int mode, boolean free) {
        if (!StressCouplingCompatibility.enabled())
            return null;
        try {
            return api == null ? null : api.begin(a, b, mode, free);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            report(e);
            return null;
        }
    }

    private static void report(Throwable error) {
        if (reported) return;
        reported = true;
        LogUtils.getLogger().warn("Coupling attraction unavailable; check the installed Synaxis version. {}", error.toString());
        LogUtils.getLogger().debug("Coupling attraction failure", error);
    }

    public static void clear() {
        for (Session session : List.copyOf(SESSIONS)) session.close();
    }

    private record Cluster(Set<Object> bodies, int constraints, double mass, boolean grounded) {}

    public static final class Session implements AutoCloseable {
        private final Api bridge;
        private final Object moving, fixed, runtime;
        private final Set<Object> bodies;
        private final Vector3d movingAnchor, fixedAnchor, movingNormal, fixedNormal;
        private final Quaterniond relativeOrientation;
        private final int mode;
        private final Vector3d integral = new Vector3d();
        private boolean closed;
        private double elapsed;
        private volatile boolean failed;

        private Session(Api bridge, Object moving, Object fixed, Object runtime, Set<Object> bodies,
                CouplingPhysics.Endpoint m, CouplingPhysics.Endpoint f, Object mv, Object fv, int mode)
                throws ReflectiveOperationException {
            this.bridge = bridge;
            this.moving = moving;
            this.fixed = fixed;
            this.runtime = runtime;
            this.bodies = bodies;
            this.mode = mode;
            movingAnchor = new Vector3d(m.anchor());
            fixedAnchor = new Vector3d(f.anchor());
            Quaterniond mq = bridge.rotation(mv), fq = bridge.rotation(fv);
            movingNormal = new Quaterniond(mq).conjugate().transform(new Vector3d(m.normal()));
            fixedNormal = new Quaterniond(fq).conjugate().transform(new Vector3d(f.normal()));
            Quaterniond aligned = mode == 2
                ? CouplingPhysics.snappedFixedTarget(mq, m.normal(), f.normal(), m.tangent(), f.tangent())
                : new Quaterniond().rotationTo(m.normal(), new Vector3d(f.normal()).negate()).mul(mq);
            relativeOrientation = new Quaterniond(fq).conjugate().mul(aligned).normalize();
        }

        public boolean failed() { return failed; }

        // Synaxis invokes controls on the physics thread. No block entities or world state are read here.
        private synchronized void step(Object context) {
            if (closed || failed) return;
            try {
                double dt = (double) bridge.timeStep.invoke(context);
                if (!Double.isFinite(dt) || dt <= 0 || dt > 0.25) { failed = true; return; }
                elapsed += dt;
                Object mv = bridge.self.invoke(context);
                Object fv = ((Optional<?>) bridge.lookup.invoke(context, fixed)).orElse(null);
                if (fv == null && !(boolean) bridge.ground.invoke(fixed) || elapsed > 3) {
                    failed = true;
                    return;
                }
                Vector3d mp = bridge.position(mv, movingAnchor), fp = bridge.position(fv, fixedAnchor);
                Vector3d error = fp.sub(mp);
                if (!error.isFinite() || Math.abs(error.x) > 1.5 || Math.abs(error.y) > 1.5 || Math.abs(error.z) > 1.5) {
                    failed = true;
                    return;
                }
                Vector3d relativeVelocity = bridge.pointVelocity(mv, mp)
                    .sub(bridge.pointVelocity(fv, bridge.position(fv, fixedAnchor)));
                double mass = (double) bridge.mass.invoke(mv);
                if (!Double.isFinite(mass) || mass <= 0) { failed = true; return; }

                // A bounded integral cancels sustained loads such as gravity without assuming world gravity.
                integral.fma(dt * 80, error);
                limit(integral, 20);
                Vector3d desiredVelocity = limit(new Vector3d(error).mul(5), 1.5);
                Vector3d acceleration = limit(desiredVelocity.sub(relativeVelocity).mul(20).add(integral), 40);
                Object forces = bridge.forces.invoke(context);
                bridge.force.invoke(forces, acceleration.mul(mass));

                if (mode != 0) {
                    Quaterniond mq = bridge.rotation(mv), fq = bridge.rotation(fv);
                    Vector3d rotationError;
                    Vector3d angularVelocity = bridge.angularVelocity(mv).sub(bridge.angularVelocity(fv));
                    if (mode == 1) {
                        Vector3d mn = mq.transform(new Vector3d(movingNormal));
                        Vector3d target = fq.transform(new Vector3d(fixedNormal)).negate().normalize();
                        rotationError = rotationVector(new Quaterniond().rotationTo(mn, target));
                        angularVelocity.sub(new Vector3d(target).mul(angularVelocity.dot(target)));
                    } else {
                        rotationError = rotationVector(new Quaterniond(fq).mul(relativeOrientation)
                            .mul(new Quaterniond(mq).conjugate()).normalize());
                    }
                    Vector3d angularAcceleration = limit(limit(rotationError.mul(5), 2)
                        .sub(angularVelocity).mul(16), 30);
                    Vector3d torque = new Quaterniond(mq).conjugate().transform(angularAcceleration);
                    ((Matrix3dc) bridge.inertia.invoke(mv)).transform(torque);
                    mq.transform(torque);
                    if (!torque.isFinite()) { failed = true; return; }
                    bridge.torque.invoke(forces, torque);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                report(e);
            }
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            try { bridge.removeControl.invoke(runtime, moving, this); }
            catch (ReflectiveOperationException e) { report(e); }
            SESSIONS.remove(this);
        }
    }

    private static Vector3d limit(Vector3d v, double max) {
        if (v.lengthSquared() > max * max) v.normalize(max);
        return v;
    }

    private static Vector3d rotationVector(Quaterniond q) {
        if (q.w < 0) q.set(-q.x, -q.y, -q.z, -q.w);
        Vector3d axis = new Vector3d(q.x, q.y, q.z);
        double length = axis.length();
        return length < 1e-9 ? axis.zero() : axis.mul(2 * Math.atan2(length, q.w) / length);
    }

    /** Reflective boundary keeps optional Synaxis classes out of common class loading. */
    private static final class Api {
        private static final String ROOT = "com.verr1.synaxis.foundation.physics.";
        final Constructor<?> bodyId;
        final Class<?> control;
        final Method body, ground, runtime, submitControl, removeControl, wake;
        final Method graph, edgesOf, edgeA, edgeB, edgeKey, edgeKind, edgeActive;
        final Method self, lookup, forces, timeStep, force, torque;
        final Method orientation, modelToWorld, velocity, omega, center, mass, inertia;

        Api() throws ReflectiveOperationException {
            Class<?> physics = Class.forName(ROOT + "SynaxisPhysics");
            Class<?> id = Class.forName(ROOT + "BodyId");
            bodyId = id.getConstructor(net.minecraft.resources.ResourceKey.class, java.util.UUID.class);
            ground = id.getMethod("isGround");
            body = physics.getMethod("body", id);
            runtime = physics.getMethod("runtime");
            wake = physics.getMethod("wakeUp", id);
            control = Class.forName(ROOT + "PhysicsControl");
            Class<?> rt = Class.forName(ROOT + "PhysicsRuntime");
            submitControl = rt.getMethod("submitControl", id, Object.class, control);
            removeControl = rt.getMethod("removeControl", id, Object.class);
            graph = physics.getMethod("constraintGraph");
            edgesOf = Class.forName(ROOT + "constraint.graph.ConstraintGraphAccess").getMethod("edgesOf", id);
            Class<?> edge = Class.forName(ROOT + "constraint.graph.ConstraintEdge");
            edgeA = edge.getMethod("bodyA"); edgeB = edge.getMethod("bodyB");
            edgeKey = edge.getMethod("key"); edgeKind = edge.getMethod("kind"); edgeActive = edge.getMethod("active");
            Class<?> step = Class.forName(ROOT + "PhysicsStepContext");
            self = step.getMethod("self"); lookup = step.getMethod("lookup", id);
            forces = step.getMethod("forces"); timeStep = step.getMethod("timeStep");
            Class<?> sink = Class.forName(ROOT + "PhysicsForceSink");
            force = sink.getMethod("applyWorldForce", Vector3dc.class);
            torque = sink.getMethod("applyWorldTorque", Vector3dc.class);
            Class<?> view = Class.forName(ROOT + "PhysicsBodyView");
            orientation = view.getMethod("orientation");
            modelToWorld = view.getMethod("modelToWorldPosition", Vector3dc.class);
            velocity = view.getMethod("velocity"); omega = view.getMethod("omega");
            center = view.getMethod("centerOfMassModel"); mass = view.getMethod("mass");
            inertia = view.getMethod("inertiaTensor");
        }

        Session begin(CouplingPhysics.Endpoint a, CouplingPhysics.Endpoint b, int mode, boolean free)
                throws ReflectiveOperationException {
            if (!a.dimension().equals(b.dimension())) return null;
            Object aid = id(a), bid = id(b);
            if (aid.equals(bid)) return null;
            Cluster ac = cluster(aid), bc = cluster(bid);
            if (ac == null || bc == null) return null;
            // Existing physical links must not turn one-sided attraction into a tug on both endpoints.
            for (Object member : ac.bodies())
                if (!(boolean) ground.invoke(member) && bc.bodies().contains(member)) return null;
            Set<Object> members = new HashSet<>(ac.bodies());
            members.addAll(bc.bodies());
            for (Session session : SESSIONS)
                for (Object member : members)
                    if (!(boolean) ground.invoke(member) && session.bodies.contains(member)) return null;

            boolean moveA = false;
            if ((boolean) ground.invoke(bid)) moveA = true;
            else if (!(boolean) ground.invoke(aid) && free) {
                if (ac.grounded() != bc.grounded()) moveA = bc.grounded();
                else if (ac.constraints() != bc.constraints()) moveA = ac.constraints() < bc.constraints();
                else if (Double.isFinite(ac.mass()) && Double.isFinite(bc.mass()))
                    moveA = ac.mass() < bc.mass() * 0.95;
                // Near ties keep the UUID-selected owner (endpoint A) as the reference.
            }
            Object moving = moveA ? aid : bid, fixed = moveA ? bid : aid;
            if ((boolean) ground.invoke(moving)) return null;
            Object mv = ((Optional<?>) body.invoke(null, moving)).orElse(null);
            Object fv = ((Optional<?>) body.invoke(null, fixed)).orElse(null);
            if (mv == null || fv == null && !(boolean) ground.invoke(fixed)) return null;
            Object rt = runtime.invoke(null);
            Session session = new Session(this, moving, fixed, rt, members, moveA ? a : b, moveA ? b : a, mv, fv, mode);
            Object callback = Proxy.newProxyInstance(control.getClassLoader(), new Class<?>[] {control}, (proxy, method, args) -> {
                if (method.getName().equals("tick")) { session.step(args[0]); return null; }
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return "Coupling alignment control";
            });
            SESSIONS.add(session);
            try {
                submitControl.invoke(rt, moving, session, callback);
                wake.invoke(null, moving);
            } catch (ReflectiveOperationException e) { session.close(); throw e; }
            return session;
        }

        private Object id(CouplingPhysics.Endpoint endpoint) throws ReflectiveOperationException {
            return bodyId.newInstance(endpoint.dimension(), endpoint.body() == null ? new java.util.UUID(0, 0) : endpoint.body());
        }

        // Inspect each active cluster once per attempt, never traverse the ground into unrelated structures.
        private Cluster cluster(Object root) throws ReflectiveOperationException {
            Object access = graph.invoke(null);
            Set<Object> members = new HashSet<>(), edges = new HashSet<>();
            ArrayDeque<Object> queue = new ArrayDeque<>();
            queue.add(root); members.add(root);
            double total = 0;
            boolean grounded = false;
            while (!queue.isEmpty()) {
                Object current = queue.remove();
                if ((boolean) ground.invoke(current)) { grounded = true; continue; }
                Object view = ((Optional<?>) body.invoke(null, current)).orElse(null);
                if (view == null) return null;
                total += (double) mass.invoke(view);
                for (Object edge : (List<?>) edgesOf.invoke(access, current)) {
                    if (!(boolean) edgeActive.invoke(edge) || edgeKind.invoke(edge).toString().equals("NO_CONTACT")) continue;
                    edges.add(edgeKey.invoke(edge));
                    Object a = edgeA.invoke(edge), b = edgeB.invoke(edge);
                    Object other = current.equals(a) ? b : a;
                    if (members.add(other)) queue.add(other);
                    if (members.size() > 128 || edges.size() > 256) return null;
                }
            }
            return new Cluster(members, edges.size(), total, grounded);
        }

        Quaterniond rotation(Object view) throws ReflectiveOperationException {
            return view == null ? new Quaterniond() : new Quaterniond((Quaterniondc) orientation.invoke(view));
        }

        Vector3d position(Object view, Vector3dc anchor) throws ReflectiveOperationException {
            return view == null ? new Vector3d(anchor) : new Vector3d((Vector3dc) modelToWorld.invoke(view, anchor));
        }

        Vector3d angularVelocity(Object view) throws ReflectiveOperationException {
            return view == null ? new Vector3d() : new Vector3d((Vector3dc) omega.invoke(view));
        }

        Vector3d pointVelocity(Object view, Vector3dc point) throws ReflectiveOperationException {
            if (view == null) return new Vector3d();
            Vector3d offset = new Vector3d(point).sub(position(view, (Vector3dc) center.invoke(view)));
            return angularVelocity(view).cross(offset).add((Vector3dc) velocity.invoke(view));
        }
    }
}
