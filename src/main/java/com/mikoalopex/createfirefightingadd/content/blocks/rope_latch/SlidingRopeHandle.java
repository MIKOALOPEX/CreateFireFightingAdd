package com.mikoalopex.createfirefightingadd.content.blocks.rope_latch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.api.rope.RopeHookMode;

import com.mikoalopex.createfirefightingadd.mixin.RopeLatchPhysicsAccess;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Anchors both solver starts at the hook while preserving the native strand's packet direction. */
public final class SlidingRopeHandle extends PinnedRopeHandle {
    private final RopeSlideState lengths;
    private Anchor outerStart;
    private Anchor outerEnd;
    private int logicalCount;
    private double sourceExtension;
    private double appliedHead;
    private double appliedTail;
    private long lastSlideTick = Long.MIN_VALUE;
    private boolean failed;
    private long slideSteps;
    private double headTautness;
    private double tailTautness;
    private double transferred;
    private volatile RopeHookMode mode = RopeHookMode.SLIDING;
    private volatile Vector3dc requestedAnchor;

    /** Queues an immutable local-space position for the next native pre-solver update. */
    public void moveAnchor(Vector3dc anchor) {
        requestedAnchor = new Vector3d(anchor);
    }

    public void setMode(RopeHookMode mode) { this.mode = java.util.Objects.requireNonNull(mode); }

    private SlidingRopeHandle(RopeHandle first, RopeHandle second, List<Vector3d> a, List<Vector3d> b,
            Vector3dc anchor, ServerSubLevel body, Anchor start, Anchor end,
            int count, double extension, RopeSlideState lengths) {
        super(first, second, a, b, anchor, body);
        outerStart = start;
        outerEnd = end;
        logicalCount = count;
        sourceExtension = extension;
        this.lengths = lengths;
        appliedHead = lengths.head();
        appliedTail = lengths.tail();
    }

    public static SlidingRopeHandle attach(ServerLevel level, ServerRopeStrand rope, int node,
            Vector3dc anchor, ServerSubLevel body, double savedTail) {
        if (!canCapture(level, rope) || node < 1 || node >= rope.getPoints().size() - 1) return null;
        double total = rope.getPoints().size() - 2 + rope.getExtension();
        double tail = savedTail >= RopeSlideState.MIN_SIDE ? savedTail : rope.getPoints().size() - 1 - node;
        if (total < 2 * RopeSlideState.MIN_SIDE || total - tail < RopeSlideState.MIN_SIDE - 1e-9) return null;
        Anchor start = endpoint(level, rope.getAttachment(RopeAttachmentPoint.START));
        Anchor end = endpoint(level, rope.getAttachment(RopeAttachmentPoint.END));
        if (start == null || end == null) return null;
        rope.updatePose();
        List<Vector3d> rawHead = copy(rope.getPoints().subList(0, node + 1));
        Collections.reverse(rawHead);
        RopeSlideState lengths = new RopeSlideState(total, tail);
        List<Vector3d> a = remesh(rawHead, lengths.head());
        List<Vector3d> b = remesh(rope.getPoints().subList(node, rope.getPoints().size()), lengths.tail());
        var pipeline = SubLevelPhysicsSystem.get(level).getPipeline();
        RopeHandle first = null, second = null;
        try {
            first = pipeline.addRope(new RopePhysicsObject(a, rope.getCollisionRadius()));
            second = pipeline.addRope(new RopePhysicsObject(b, rope.getCollisionRadius()));
            first.setFirstSegmentLength(RopeSlideState.firstSegment(lengths.head()));
            second.setFirstSegmentLength(RopeSlideState.firstSegment(lengths.tail()));
            first.setAttachment(AttachmentPoint.START, anchor, body);
            second.setAttachment(AttachmentPoint.START, anchor, body);
            start.apply(first, AttachmentPoint.END);
            end.apply(second, AttachmentPoint.END);
        } catch (RuntimeException exception) {
            if (first != null) first.remove();
            if (second != null) second.remove();
            throw exception;
        }
        SlidingRopeHandle result = new SlidingRopeHandle(first, second, a, b, anchor, body, start, end,
            rope.getPoints().size(), rope.getExtension(), lengths);
        RopeHandle old = handle(rope);
        ((RopeLatchPhysicsAccess) rope).firefighting$setHandle(result);
        old.remove();
        result.wakeUp();
        return result;
    }

    /** Runs after native extension interpolation, before the next solver step. */
    public void physicsStep(long tick) {
        if (isRemoved() || failed) return;
        // Apply local anchor changes at the same pre-solver boundary as rope length changes.
        Vector3dc nextAnchor = requestedAnchor;
        if (nextAnchor != null && !nextAnchor.equals(pinAnchor)) {
            first.setAttachment(AttachmentPoint.START, nextAnchor, pinBody);
            second.setAttachment(AttachmentPoint.START, nextAnchor, pinBody);
            pinAnchor = nextAnchor;
            wakeUp();
        }
        lengths.configure(Config.ropeLatchSlideSettings);
        lengths.setMode(mode);
        lengths.setTotal(logicalCount - 2 + sourceExtension);
        if (lastSlideTick != tick) {
            lastSlideTick = tick;
            first.readPose(firstPoints);
            second.readPose(secondPoints);
            headTautness = firstPoints.getFirst().distance(firstPoints.getLast()) / lengths.head();
            tailTautness = secondPoints.getFirst().distance(secondPoints.getLast()) / lengths.tail();
            double before = lengths.tail();
            lengths.step(pathLength(firstPoints), pathLength(secondPoints),
                firstPoints.getFirst().distance(firstPoints.getLast()),
                secondPoints.getFirst().distance(secondPoints.getLast()), gravitySlope());
            transferred += Math.abs(lengths.tail() - before);
            slideSteps++;
        }
        if (Math.abs(appliedHead - lengths.head()) > 1e-9) {
            resize(first, firstPoints, lengths.head(), outerStart);
            appliedHead = lengths.head();
        }
        if (Math.abs(appliedTail - lengths.tail()) > 1e-9) {
            resize(second, secondPoints, lengths.tail(), outerEnd);
            appliedTail = lengths.tail();
        }
    }

    public double tailLength() { return lengths.tail(); }

    private double gravitySlope() {
        // World-space solver tangents: positive travel gives length to the head side.
        // A latch sharing either endpoint's body cannot move independently along this path.
        if (pinBody == null || pinBody == outerStart.body() || pinBody == outerEnd.body()) return 0;
        return (outwardSlope(firstPoints) - outwardSlope(secondPoints)) * 0.5;
    }

    private static double outwardSlope(List<Vector3d> points) {
        Vector3d anchor = points.getFirst();
        for (int i = 1; i < points.size(); i++) {
            double distance = anchor.distance(points.get(i));
            if (distance > 1e-6) return (points.get(i).y - anchor.y) / distance;
        }
        return 0;
    }
    public String debugState() {
        return "steps=" + slideSteps + ", head=" + lengths.head() + ", tail=" + lengths.tail()
            + ", tautness=" + headTautness + "/" + tailTautness + ", transferred=" + transferred;
    }
    public boolean hasFailed() { return failed; }
    public void markFailed() { failed = true; }

    @Override public int pinnedNode() {
        return Math.clamp(1 + (int) Math.round(lengths.head() - sourceExtension), 1, logicalCount - 2);
    }
    @Override public int tailSegments() { return logicalCount - 1 - pinnedNode(); }
    @Override public float limitWinchSpeed(float speed, double totalLength) {
        // Native packets need an interior sample for the latch bend as well as both endpoints.
        double minimum = mode.transfersLength() ? 1.001 : Math.max(1.001, lengths.tail() + RopeSlideState.MIN_SIDE);
        return Math.max(speed, (float) Math.min(0, minimum - totalLength));
    }

    // These are changes to the original rope's total length, not transfers through the latch.
    @Override public void addPoint(Vector3dc point) { logicalCount++; }
    @Override public void removeFirstPoint() { logicalCount--; }
    @Override public void setFirstSegmentLength(double length) { sourceExtension = length; }

    @Override
    public void setAttachment(AttachmentPoint point, Vector3dc position, ServerSubLevel body) {
        Anchor anchor = new Anchor(new Vector3d(position), body);
        if (point == AttachmentPoint.START) {
            outerStart = anchor;
            anchor.apply(first, AttachmentPoint.END);
        } else {
            outerEnd = anchor;
            anchor.apply(second, AttachmentPoint.END);
        }
    }

    @Override
    public void readPose(List<Vector3d> points) {
        first.readPose(firstPoints);
        second.readPose(secondPoints);
        while (points.size() < logicalCount) points.add(new Vector3d());
        while (points.size() > logicalCount) points.removeLast();
        // A second fractional segment must not be counted as extra rope by the native winch or codec.
        double total = appliedHead + appliedTail;
        double logicalTotal = logicalCount - 2 + sourceExtension;
        for (int i = 0; i < logicalCount; i++) {
            double distance = i == 0 ? 0 : sourceExtension + i - 1;
            distance = logicalTotal > 0 ? distance * total / logicalTotal : 0;
            if (distance <= appliedHead)
                sample(firstPoints, appliedHead, appliedHead - distance, points.get(i));
            else sample(secondPoints, appliedTail, distance - appliedHead, points.get(i));
        }
        // Keep the sharp bend visible even when it falls between the native packet's sample positions.
        if (logicalCount >= 3) points.get(pinnedNode()).set(firstPoints.getFirst());
    }

    private void resize(RopeHandle handle, List<Vector3d> points, double length, Anchor outer) {
        int count = RopeSlideState.points(length);
        while (points.size() < count) {
            Vector3d point = new Vector3d(points.getFirst());
            handle.addPoint(point);
            points.addFirst(point);
        }
        while (points.size() > count) {
            handle.removeFirstPoint();
            points.removeFirst();
        }
        handle.setFirstSegmentLength(RopeSlideState.firstSegment(length));
        handle.setAttachment(AttachmentPoint.START, pinAnchor, pinBody);
        outer.apply(handle, AttachmentPoint.END);
        handle.wakeUp();
    }

    private static void sample(List<Vector3d> points, double length, double distance, Vector3d target) {
        distance = Math.clamp(distance, 0, length);
        double fraction = length - (points.size() - 2);
        if (distance <= fraction) {
            target.set(points.get(0)).lerp(points.get(1), fraction > 0 ? distance / fraction : 0);
            return;
        }
        double remaining = distance - fraction;
        int index = Math.min(points.size() - 2, 1 + (int) Math.floor(remaining));
        target.set(points.get(index)).lerp(points.get(index + 1), Math.clamp(remaining - (index - 1), 0, 1));
    }

    private static List<Vector3d> remesh(List<Vector3d> path, double length) {
        double[] distances = new double[path.size()];
        for (int i = 1; i < path.size(); i++) distances[i] = distances[i - 1] + path.get(i).distance(path.get(i - 1));
        List<Vector3d> result = new ArrayList<>();
        int segment = 0, count = RopeSlideState.points(length);
        double fraction = RopeSlideState.firstSegment(length);
        for (int i = 0; i < count; i++) {
            double distance = (i == 0 ? 0 : fraction + i - 1) / length * distances[path.size() - 1];
            while (segment < path.size() - 2 && distances[segment + 1] < distance) segment++;
            double span = distances[segment + 1] - distances[segment];
            double alpha = span > 1e-9 ? Math.clamp((distance - distances[segment]) / span, 0, 1) : 0;
            result.add(new Vector3d(path.get(segment)).lerp(path.get(segment + 1), alpha));
        }
        return result;
    }

    private static double pathLength(List<Vector3d> points) {
        double length = 0;
        for (int i = 1; i < points.size(); i++) length += points.get(i).distance(points.get(i - 1));
        return length;
    }
}
