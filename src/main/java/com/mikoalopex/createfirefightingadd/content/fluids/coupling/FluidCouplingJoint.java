package com.mikoalopex.createfirefightingadd.content.fluids.coupling;

import java.util.UUID;
import java.util.function.UnaryOperator;

import com.mikoalopex.createfirefightingadd.integration.synaxis.CouplingPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Builds immutable joint requests; callers resolve loaded endpoints and perform ownership checks first. */
public final class FluidCouplingJoint {
    public static final ResourceLocation FEATURE = ResourceLocation.fromNamespaceAndPath(
        "createfirefightingadd", "fluid_coupling");

    private FluidCouplingJoint() {}

    public static CouplingPhysics.Endpoint endpoint(ResourceKey<Level> dimension, UUID body, BlockPos pos,
            Direction facing, FluidCouplingSettings settings, UnaryOperator<Vec3> toWorldDirection) {
        Vec3 anchor = FluidCouplingGeometry.modelPoint(settings.type().jointPivot(settings.height()), facing)
            .add(Vec3.atLowerCornerOf(pos));
        boolean hard = settings.type().locksConstraint();
        boolean passive = settings.type() == FluidCouplingInterfaces.BOOM_NOZZLE;
        Vec3 normal = hard ? new Vec3(passive ? -1 : 1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 tangent = hard ? new Vec3(0, passive ? -1 : 1, 0) : new Vec3(1, 0, 0);
        normal = toWorldDirection.apply(FluidCouplingGeometry.orient(normal, facing)).normalize();
        tangent = toWorldDirection.apply(FluidCouplingGeometry.orient(tangent, facing)).normalize();
        return new CouplingPhysics.Endpoint(dimension, body, vector(anchor), vector(normal), vector(tangent));
    }

    public static CouplingPhysics.Request request(UUID pair, FluidCouplingSettings controller,
            FluidCouplingSettings other, CouplingPhysics.Endpoint a, CouplingPhysics.Endpoint b) {
        if (pair == null || !controller.type().accepts(other.type()) || !other.type().accepts(controller.type())
                || controller.type().activation() == FluidCouplingInterface.Activation.PASSIVE
                || !a.dimension().equals(b.dimension()))
            throw new IllegalArgumentException("Invalid fluid coupling pair");
        var rule = controller.constraint();
        return new CouplingPhysics.Request(FEATURE, pair, a, b, rule.mode(), rule.lower(), rule.upper(),
            rule.flipRange(), rule.modelZero());
    }

    private static Vector3d vector(Vec3 value) { return new Vector3d(value.x, value.y, value.z); }
}
