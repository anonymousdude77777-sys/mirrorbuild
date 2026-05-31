package com.mirrorbuild.mod;

import net.minecraft.util.math.BlockPos;

/**
 * Holds all persistent game state: arena corners, axis, enabled flag.
 * All fields are validated before being stored.
 */
public class MirrorArena {

    public enum Axis { X, Z }

    // Raw selection positions (before setarea is called)
    private BlockPos selPos1 = null;
    private BlockPos selPos2 = null;

    // Confirmed arena corners
    private BlockPos arenaMin = null;
    private BlockPos arenaMax = null;

    private Axis axis = Axis.Z;
    private boolean enabled = false;

    // -------------------------------------------------------
    // Selection wand positions
    // -------------------------------------------------------

    public void setSelPos1(BlockPos pos) {
        this.selPos1 = pos;
    }

    public void setSelPos2(BlockPos pos) {
        this.selPos2 = pos;
    }

    public BlockPos getSelPos1() { return selPos1; }
    public BlockPos getSelPos2() { return selPos2; }

    public boolean hasSelection() {
        return selPos1 != null && selPos2 != null;
    }

    // -------------------------------------------------------
    // Confirm area from current selection
    // Returns false if selection is invalid (<3 wide in both horiz. axes)
    // -------------------------------------------------------
    public boolean applySelection() {
        if (selPos1 == null || selPos2 == null) return false;

        int minX = Math.min(selPos1.getX(), selPos2.getX());
        int minY = Math.min(selPos1.getY(), selPos2.getY());
        int minZ = Math.min(selPos1.getZ(), selPos2.getZ());
        int maxX = Math.max(selPos1.getX(), selPos2.getX());
        int maxY = Math.max(selPos1.getY(), selPos2.getY());
        int maxZ = Math.max(selPos1.getZ(), selPos2.getZ());

        // Must be at least 3 wide on BOTH horizontal axes
        if ((maxX - minX + 1) < 3 || (maxZ - minZ + 1) < 3) {
            return false;
        }

        arenaMin = new BlockPos(minX, minY, minZ);
        arenaMax = new BlockPos(maxX, maxY, maxZ);
        return true;
    }

    public void clearSelection() {
        selPos1 = null;
        selPos2 = null;
        arenaMin = null;
        arenaMax = null;
        enabled = false;
    }

    // -------------------------------------------------------
    // Arena queries
    // -------------------------------------------------------

    public boolean hasArena() {
        return arenaMin != null && arenaMax != null;
    }

    public BlockPos getArenaMin() { return arenaMin; }
    public BlockPos getArenaMax() { return arenaMax; }

    /**
     * True if the given position is inside the confirmed arena volume.
     */
    public boolean contains(BlockPos pos) {
        if (!hasArena() || pos == null) return false;
        return pos.getX() >= arenaMin.getX() && pos.getX() <= arenaMax.getX()
            && pos.getY() >= arenaMin.getY() && pos.getY() <= arenaMax.getY()
            && pos.getZ() >= arenaMin.getZ() && pos.getZ() <= arenaMax.getZ();
    }

    /**
     * Mirror a position across the current axis through the centre of the arena.
     * Returns null if the position is outside the arena or mirroring would put
     * the result outside the arena.
     */
    public BlockPos mirror(BlockPos pos) {
        if (!hasArena() || pos == null) return null;
        if (!contains(pos)) return null;

        BlockPos mirrored;
        if (axis == Axis.X) {
            // Mirror across X axis → flip Z
            int midZ2 = arenaMin.getZ() + arenaMax.getZ(); // *2 to avoid float
            int mirroredZ = midZ2 - pos.getZ();
            mirrored = new BlockPos(pos.getX(), pos.getY(), mirroredZ);
        } else {
            // Mirror across Z axis → flip X
            int midX2 = arenaMin.getX() + arenaMax.getX();
            int mirroredX = midX2 - pos.getX();
            mirrored = new BlockPos(mirroredX, pos.getY(), pos.getZ());
        }

        // Sanity: result must be in arena
        if (!contains(mirrored)) return null;
        // Must not mirror onto itself (exact centre column)
        if (mirrored.equals(pos)) return null;

        return mirrored;
    }

    // -------------------------------------------------------
    // Axis / enabled
    // -------------------------------------------------------

    public Axis getAxis() { return axis; }
    public void setAxis(Axis axis) { this.axis = axis; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // -------------------------------------------------------
    // Status string for /mirror status
    // -------------------------------------------------------

    public String getStatusText() {
        StringBuilder sb = new StringBuilder();
        sb.append("§e=== Mirror Build Status ===\n");
        sb.append("§7Mirroring: ").append(enabled ? "§aENABLED" : "§cDISABLED").append("\n");
        sb.append("§7Axis: §b").append(axis.name()).append("\n");

        if (hasArena()) {
            sb.append("§7Arena Min: §f").append(posStr(arenaMin)).append("\n");
            sb.append("§7Arena Max: §f").append(posStr(arenaMax)).append("\n");
            int sx = arenaMax.getX() - arenaMin.getX() + 1;
            int sy = arenaMax.getY() - arenaMin.getY() + 1;
            int sz = arenaMax.getZ() - arenaMin.getZ() + 1;
            sb.append("§7Size: §f").append(sx).append("x").append(sy).append("x").append(sz);
        } else {
            sb.append("§7Arena: §cNot set");
            if (hasSelection()) {
                sb.append("\n§7Selection: §f").append(posStr(selPos1))
                  .append(" §7-> §f").append(posStr(selPos2))
                  .append(" §7(run §e/mirror setarea§7 to confirm)");
            }
        }
        return sb.toString();
    }

    private static String posStr(BlockPos p) {
        return p == null ? "null" : p.getX() + ", " + p.getY() + ", " + p.getZ();
    }
}
