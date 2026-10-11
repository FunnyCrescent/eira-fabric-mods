package com.larrymod;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Custom AI goal that keeps Larrys in the same group together.
 *
 * Each Larry checks the distance to other Larrys in its group. If any
 * Larry is more than 5 blocks away from the group center, it moves
 * toward the center.
 *
 * This ensures the group stays cohesive — they attack together and
 * never spread out.
 */
public class FollowGroupGoal extends Goal {

    private final LarryEntity larry;
    private int cooldown = 0;

    private static final double MAX_DISTANCE = 5.0;
    private static final double TELEPORT_DISTANCE = 20.0;

    public FollowGroupGoal(LarryEntity larry) {
        this.larry = larry;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = 5; // Check every 5 ticks (4 times per second)

        if (!(larry.getEntityWorld() instanceof ServerWorld world)) return false;

        // Find other Larrys in the same group
        UUID groupId = larry.getGroupId();
        List<? extends LarryEntity> groupMembers = world.getEntitiesByType(LarryMod.LARRY,
                l -> l != this.larry && l.isAlive() && l.getGroupId().equals(groupId));

        if (groupMembers.isEmpty()) return false;

        // Check if any member is too far away
        for (LarryEntity member : groupMembers) {
            double dist = larry.squaredDistanceTo(member);
            if (dist > MAX_DISTANCE * MAX_DISTANCE) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void start() {
        if (!(larry.getEntityWorld() instanceof ServerWorld world)) return;

        UUID groupId = larry.getGroupId();
        List<? extends LarryEntity> groupMembers = world.getEntitiesByType(LarryMod.LARRY,
                l -> l != this.larry && l.isAlive() && l.getGroupId().equals(groupId));

        if (groupMembers.isEmpty()) return;

        // Calculate group center
        double cx = larry.getX();
        double cy = larry.getY();
        double cz = larry.getZ();
        int count = 1;
        for (LarryEntity member : groupMembers) {
            cx += member.getX();
            cy += member.getY();
            cz += member.getZ();
            count++;
        }
        cx /= count;
        cy /= count;
        cz /= count;

        // Move toward group center
        double distToCenter = larry.squaredDistanceTo(cx, cy, cz);
        if (distToCenter > MAX_DISTANCE * MAX_DISTANCE) {
            // If too far, teleport closer instead of walking (prevents getting stuck)
            if (distToCenter > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                // Teleport to nearest group member
                LarryEntity nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (LarryEntity member : groupMembers) {
                    double d = larry.squaredDistanceTo(member);
                    if (d < nearestDist) {
                        nearestDist = d;
                        nearest = member;
                    }
                }
                if (nearest != null) {
                    double dx = nearest.getX() - larry.getX();
                    double dz = nearest.getZ() - larry.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0) {
                        dx = dx / len * 3.0;
                        dz = dz / len * 3.0;
                    }
                    // Find safe Y — use nearest member's Y but check for solid ground
                    double targetX = nearest.getX() - dx;
                    double targetZ = nearest.getZ() - dz;
                    double targetY = findSafeY(nearest.getEntityWorld(),
                            (int) targetX, (int) nearest.getY(), (int) targetZ);
                    larry.refreshPositionAndAngles(targetX, targetY, targetZ,
                            larry.getYaw(), larry.getPitch());
                }
            } else {
                // Walk toward center
                larry.getNavigation().startMovingTo(cx, cy, cz, 1.2);
            }
        }
    }

    @Override
    public void tick() {
        // Keep moving toward group center
        start();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    /**
     * Find a safe Y position — scan from the target Y downward for the first
     * solid block with 2 air blocks above it. Prevents teleporting into/below
     * ground.
     */
    private double findSafeY(net.minecraft.world.World world, int x, int startY, int z) {
        for (int y = startY + 2; y >= world.getBottomY(); y--) {
            var ground = world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z));
            var above1 = world.getBlockState(new net.minecraft.util.math.BlockPos(x, y + 1, z));
            var above2 = world.getBlockState(new net.minecraft.util.math.BlockPos(x, y + 2, z));
            if (!ground.isAir() && above1.isAir() && above2.isAir()) {
                return y + 1.0;
            }
        }
        return startY;
    }
}
