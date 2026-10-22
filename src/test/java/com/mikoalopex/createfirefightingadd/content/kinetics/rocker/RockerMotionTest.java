package com.mikoalopex.createfirefightingadd.content.kinetics.rocker;

/** Stroke endpoints and network rotation must agree even when a tick ends at a fractional step. */
public final class RockerMotionTest {
    public static void main(String[] args) {
        int checks = 0;
        double[] expectedSpeeds = {32, 64.0/3, 16, 12.8, 32.0/3, 64.0/7, 8};
        double[] expectedCapacities = {256, 384, 512, 640, 768, 896, 1024};
        for (int step=0;step<7;step++) for (boolean reverse:new boolean[]{false,true}) {
            RockerMotion motion = new RockerMotion(); motion.step=step;
            double speed = motion.outputSpeed(reverse ? -32 : 32);
            if (Math.abs(speed-(reverse ? -1 : 1)*expectedSpeeds[step])>1e-8)
                throw new AssertionError("Incorrect rotary output at step " + step);
            // The native network multiplies the source coefficient by absolute generated speed.
            for (double base:new double[]{0, 8, 13}) {
                double capacity=motion.capacityCoefficient(base)*Math.abs(speed);
                if (Math.abs(capacity-expectedCapacities[step]*base/8)>1e-8)
                    throw new AssertionError("Incorrect source capacity at step " + step);
            }
            for (int mode:new int[]{RockerMotion.RECIPROCATING,RockerMotion.RATCHET}) {
                motion.mode=mode; motion.start(reverse);
                if (Math.abs(motion.speed()-speed)>1e-8)
                    throw new AssertionError("Stroke and rotary output disagree");
                motion.travel=90;
                if (Math.abs(motion.speed()-(mode==RockerMotion.RATCHET ? 0 : -speed))>1e-8)
                    throw new AssertionError("Incorrect return stroke output");
            }
            checks++;
        }
        for (int from=0;from<7;from++) for (int to=0;to<7;to++) {
            RockerSliderAnimation slider=new RockerSliderAnimation();
            slider.tick(from);
            if(slider.value(1)!=from) throw new AssertionError("Initial slider position");
            slider.tick(to);
            if(slider.value(0)!=from) throw new AssertionError("Slider jumped on retarget");
            float previous=from;
            for(int tick=0;tick<10;tick++) {
                for(int frame=0;frame<=10;frame++) {
                    float value=slider.value(frame/10f);
                    if(value<Math.min(from,to)-1e-5 || value>Math.max(from,to)+1e-5
                        || (value-previous)*Math.signum(to-from)<-1e-5)
                        throw new AssertionError("Slider overshoot or reversal");
                    previous=value;
                }
                slider.tick(to);
            }
            if(slider.value(1)!=to) throw new AssertionError("Slider did not arrive");
            slider.tick(from);
            float before=slider.value(1);
            slider.tick(to);
            if(Math.abs(slider.value(0)-before)>1e-5) throw new AssertionError("Interrupted slide jumped");
        }
        float early=RockerSliderAnimation.progress(.1f)-RockerSliderAnimation.progress(0);
        float middle=RockerSliderAnimation.progress(.5f)-RockerSliderAnimation.progress(.4f);
        float late=RockerSliderAnimation.progress(1)-RockerSliderAnimation.progress(.9f);
        if(early>=middle || late>=middle || Math.abs(early-late)>1e-5)
            throw new AssertionError("Slider must accelerate, cruise and decelerate");
        var bounds = RockerGeometry.INTERACTION_BOX;
        if (bounds.getXsize()!=48 || bounds.getYsize()!=6 || bounds.getZsize()!=3)
            throw new AssertionError("Rod interaction dimensions");
        for (var facing : net.minecraft.core.Direction.values()) {
            // Native crank envelope (14 x 14 x 8), supplied without starting the mod loader.
            var nativeBounds = RockerGeometry.box(19,1,8,33,15,16,facing,0);
            net.minecraft.world.phys.AABB combined = null;
            for (int side : new int[]{-1,1}) {
                var half = RockerGeometry.coreHalf(facing,side,nativeBounds);
                var transformed = RockerGeometry.box(half.minX,half.minY,half.minZ,
                    half.maxX,half.maxY,half.maxZ,facing,0);
                combined = combined == null ? transformed : combined.minmax(transformed);
                var outer = RockerGeometry.point(new net.minecraft.world.phys.Vec3(
                    half.minX, (half.minY+half.maxY)/2, half.minZ),facing,0);
                if (RockerGeometry.selectionSide(outer,facing,nativeBounds)!=side)
                    throw new AssertionError("Native core edge must remain selectable");
            }
            if (Math.abs(combined.minX-nativeBounds.minX)>1e-8
                || Math.abs(combined.minY-nativeBounds.minY)>1e-8
                || Math.abs(combined.minZ-nativeBounds.minZ)>1e-8
                || Math.abs(combined.maxX-nativeBounds.maxX)>1e-8
                || Math.abs(combined.maxY-nativeBounds.maxY)>1e-8
                || Math.abs(combined.maxZ-nativeBounds.maxZ)>1e-8)
                throw new AssertionError("Core halves must cover the complete native bounds");
            for (int side : new int[]{-1,1}) {
                var local = RockerGeometry.point(new net.minecraft.world.phys.Vec3(26,8+side*2,10),facing,0);
                if (RockerGeometry.selectionSide(local,facing,nativeBounds)!=side)
                    throw new AssertionError("Fixed core selection disagrees across facings");
            }
            for (int angle = -180; angle <= 180; angle += 15) {
                var point = new net.minecraft.world.phys.Vec3(-11, 8, 8);
                var result = RockerGeometry.inverse(RockerGeometry.point(point, facing, angle), facing, angle);
                if (result.distanceToSqr(point) > 1e-8) throw new AssertionError("Model and hit transforms disagree");
                var start = RockerGeometry.point(new net.minecraft.world.phys.Vec3(-10,8,-10),facing,angle);
                var end = RockerGeometry.point(new net.minecraft.world.phys.Vec3(-10,8,20),facing,angle);
                var hit = RockerGeometry.clip(start,end,facing,angle);
                var expectedHit = RockerGeometry.point(new net.minecraft.world.phys.Vec3(-10,8,10),facing,angle);
                if (hit == null || hit.distanceToSqr(expectedHit)>1e-8) throw new AssertionError("Extended rod ray missed");
                var missStart = RockerGeometry.point(new net.minecraft.world.phys.Vec3(-10,20,-10),facing,angle);
                var missEnd = RockerGeometry.point(new net.minecraft.world.phys.Vec3(-10,20,20),facing,angle);
                if (RockerGeometry.clip(missStart,missEnd,facing,angle)!=null) throw new AssertionError("Ray outside rod hit");
                checks++;
            }
            var axis = RockerGeometry.point(new net.minecraft.world.phys.Vec3(26, 8, 16), facing, 0);
            var expected = new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5)
                .subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
            if (axis.distanceToSqr(expected)>1e-8) throw new AssertionError("Axis must meet back shaft at block face");
        }
        for (int mode=1;mode<=2;mode++) for(int step=0;step<7;step++) for(boolean reverse:new boolean[]{false,true}) {
            RockerMotion motion = new RockerMotion(); motion.mode=mode; motion.step=step;
            motion.start(reverse);
            double accumulated=0; int ticks=0;
            while(motion.running) {
                var saved = new net.minecraft.nbt.CompoundTag();
                motion.write(saved);
                RockerMotion restored = new RockerMotion();
                restored.read(saved);
                if (restored.speed()!=motion.speed() || restored.angle()!=motion.angle()
                    || restored.running!=motion.running || restored.reverse!=motion.reverse
                    || restored.step!=motion.step || restored.mode!=motion.mode)
                    throw new AssertionError("Reload changed active source state");
                // Continue from the reloaded state at every tick, including the return stroke.
                motion = restored;
                accumulated += motion.speed()*0.3;
                motion.tick();
                if(++ticks>100) throw new AssertionError("Stroke never completes");
                if(Math.abs(motion.angle()-45)>90.0001) throw new AssertionError("Stroke overshoot");
                checks++;
            }
            double expected=mode==1?0:reverse?-90:90;
            if(Math.abs(accumulated-expected)>1e-6) throw new AssertionError("Output angle differs from stroke: "+accumulated);
            if(motion.angle()!=45 || motion.speed()!=0) throw new AssertionError("Stroke did not return to idle");
        }
        RockerMotion selection=new RockerMotion();
        selection.select(-1);
        if(selection.mode!=0 || selection.half!=-1) throw new AssertionError("Other half must not cycle mode");
        for(int expected:new int[]{1,2,0}) { selection.select(-1); if(selection.mode!=expected) throw new AssertionError("Mode cycle"); }
        for (int step=0;step<7;step++) for (boolean backwards:new boolean[]{false,true}) {
            var saved = new net.minecraft.nbt.CompoundTag();
            saved.putInt("InUse",6);
            saved.putBoolean("Backwards",backwards);
            saved.putFloat("Speed",(backwards?-32:32)*(1+step*0.5f));
            RockerMotion rotary=new RockerMotion(); rotary.step=step;
            rotary.write(saved);
            RockerMotion restored=new RockerMotion(); restored.read(saved);
            restored.write(saved);
            if(saved.getInt("InUse")!=6 || saved.getBoolean("Backwards")!=backwards
                || saved.getFloat("Speed")!=(backwards?-32:32)*(1+step*0.5f)
                || restored.mode!=RockerMotion.ROTARY || restored.step!=step)
                throw new AssertionError("Rocker persistence must preserve native crank fields");
        }
        var malformed=new net.minecraft.nbt.CompoundTag();
        malformed.putInt("RockerMode",RockerMotion.RECIPROCATING);
        malformed.putBoolean("Stroke",true);
        for(double travel:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,180,181}) {
            malformed.putDouble("Travel",travel);
            selection.read(malformed);
            if(selection.running || selection.travel!=0 || selection.speed()!=0)
                throw new AssertionError("Invalid stroke must stop safely");
        }
        malformed.remove("Stroke");
        malformed.remove("Travel");
        selection.read(malformed);
        if(selection.running) throw new AssertionError("Legacy save must not invent a stroke");
        System.out.println("RockerMotion: "+checks+" checks, seven ratios, per-tick persistence, native fields, rod ray picking and modes passed");
    }
}
