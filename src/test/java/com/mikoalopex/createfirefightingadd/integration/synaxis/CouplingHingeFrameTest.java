package com.mikoalopex.createfirefightingadd.integration.synaxis;

import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Opt-in model-zero hinges must not inherit the contact angle as their neutral pose. */
public final class CouplingHingeFrameTest {
    public static void main(String[] args) {
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            Quaterniond fixed = new Quaterniond().rotationXYZ(random.nextDouble() * 6,
                random.nextDouble() * 6, random.nextDouble() * 6);
            Quaterniond moving = new Quaterniond().rotationXYZ(random.nextDouble() * 6,
                random.nextDouble() * 6, random.nextDouble() * 6);
            Vector3d fixedAxis = fixed.transform(new Vector3d(1, 0, 0));
            Vector3d fixedTangent = fixed.transform(new Vector3d(0, 1, 0));
            Vector3d movingAxis = moving.transform(new Vector3d(-1, 0, 0));
            Vector3d movingTangent = moving.transform(new Vector3d(0, 1, 0));
            Quaterniond target = CouplingPhysics.alignedHingeTarget(moving, movingAxis,
                fixedAxis, movingTangent, fixedTangent);
            check(target.transform(new Vector3d(-1, 0, 0)).distance(new Vector3d(fixedAxis).negate()) < 1e-8, "Pitch axis");
            check(target.transform(new Vector3d(0, 1, 0)).distance(fixedTangent) < 1e-8, "Model zero");
        }
        CompoundTag legacy = new CompoundTag();
        for (int i = 0; i < 14; i++) legacy.putDouble("V" + i, 0);
        legacy.putDouble("V3", 1); legacy.putDouble("V7", 1);
        legacy.putDouble("V8", 1); legacy.putDouble("V11", -1);
        Object reference = CouplingPhysics.restoreReference(legacy);
        check(reference != null, "Legacy stress reference remains readable");
        check(CouplingPhysics.saveReference(reference).equals(legacy), "Legacy reference unchanged");
        legacy.putDouble("HingeX", 0); legacy.putDouble("HingeY", 0);
        legacy.putDouble("HingeZ", 0); legacy.putDouble("HingeW", 1);
        check(CouplingPhysics.saveReference(CouplingPhysics.restoreReference(legacy)).equals(legacy), "Model zero survives reload");
        legacy.putDouble("HingeW", Double.NaN);
        check(CouplingPhysics.restoreReference(legacy) == null, "Reject invalid saved frame");
        System.out.println("CouplingHingeFrame: 2004 orientation and persistence checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
