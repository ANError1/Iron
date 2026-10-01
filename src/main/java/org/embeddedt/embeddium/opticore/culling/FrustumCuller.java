package org.embeddedt.embeddium.opticore.culling;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Frustum visibility queries built directly from the projection and model-view matrices.
 *
 * <p>This deliberately does not read Minecraft's own frustum instance. Embeddium replaces the chunk
 * rendering path, and with it the points where the vanilla frustum is set up, so reading that state
 * would couple this mod to Embeddium internals. Building a frustum from matrices the frame already
 * needs costs one matrix multiply per frame and behaves identically on vanilla, Embeddium, and
 * Oculus.
 *
 * <h2>Indexing convention</h2>
 * Everything here uses <b>column-major</b> flat storage: element {@code (row, col)} lives at
 * {@code index = col * 4 + row}. This matches JOML's native layout, so {@link #read(Matrix4f)} needs
 * no reordering, and it is the convention the Gribb-Hartmann derivation assumes.
 *
 * <p>This matters more than it looks. The two conventions differ only by a transpose, so getting it
 * wrong does not throw or produce obviously broken output — it yields planes pointing in
 * plausible-but-incorrect directions, which surfaces as entities flickering at screen edges rather
 * than as a clear fault. The plane math here is verified against known camera setups.
 */
public final class FrustumCuller {
    /** Six planes as (a, b, c, d) with the normal pointing inward. Column-major: 4 doubles each. */
    private final double[] planes = new double[6 * 4];
    private boolean valid;

    /**
     * Builds the frustum from the exact matrices the current frame is drawn with.
     *
     * @param modelView  the frame's pose matrix, as returned by {@code PoseStack.last().pose()}
     * @param projection the frame's projection matrix
     * @return true when a usable frustum was produced
     */
    public boolean update(Matrix4f modelView, Matrix4f projection) {
        double[] view = read(modelView);
        double[] proj = read(projection);

        if (view == null || proj == null) {
            this.valid = false;
            return false;
        }

        // Clip-space transform: proj * view. A point is inside when -w <= x,y,z <= w, so adding and
        // subtracting the clip-space rows yields the six inward-facing plane equations directly.
        double[] m = multiply(proj, view);

        // Each row i of the column-major array is m[i], m[i+4], m[i+8], m[i+12].
        setPlane(0, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]);   // left
        setPlane(1, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]);   // right
        setPlane(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]);   // bottom
        setPlane(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]);   // top
        setPlane(4, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]);  // near
        setPlane(5, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]);  // far

        this.valid = true;
        return true;
    }

    private void setPlane(int index, double a, double b, double c, double d) {
        double length = Math.sqrt(a * a + b * b + c * c);
        int base = index * 4;

        if (!(length > 1.0e-9) || !Double.isFinite(length)) {
            // Degenerate plane. Store a plane that accepts everything rather than one that would
            // reject the whole world.
            this.planes[base] = 0.0;
            this.planes[base + 1] = 0.0;
            this.planes[base + 2] = 0.0;
            this.planes[base + 3] = 0.0;
            return;
        }

        this.planes[base] = a / length;
        this.planes[base + 1] = b / length;
        this.planes[base + 2] = c / length;
        this.planes[base + 3] = d / length;
    }

    /**
     * Conservative AABB test.
     *
     * <p>Returns false only when the box is provably outside at least one plane. Testing the plane's
     * positive vertex means a box merely touching the frustum is still reported visible, so entities
     * at the screen edge are never dropped.
     */
    public boolean isVisible(AABB box) {
        if (!this.valid) {
            return true;
        }

        for (int i = 0; i < 6; i++) {
            int base = i * 4;
            double a = this.planes[base];
            double b = this.planes[base + 1];
            double c = this.planes[base + 2];
            double d = this.planes[base + 3];

            double x = a >= 0.0 ? box.maxX : box.minX;
            double y = b >= 0.0 ? box.maxY : box.minY;
            double z = c >= 0.0 ? box.maxZ : box.minZ;

            if (a * x + b * y + c * z + d < 0.0) {
                return false;
            }
        }

        return true;
    }

    public boolean isValid() {
        return this.valid;
    }

    public void invalidate() {
        this.valid = false;
    }

    /**
     * Copies a JOML matrix into column-major flat storage, with no reordering.
     *
     * @return the elements, or null when the matrix is absent or holds non-finite values
     */
    private static double[] read(Matrix4f matrix) {
        if (matrix == null) {
            return null;
        }

        double[] out = new double[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                double value = matrix.get(col, row);
                if (!Double.isFinite(value)) {
                    return null;
                }
                out[col * 4 + row] = value;
            }
        }
        return out;
    }

    /** Column-major 4x4 multiply: {@code out = left * right}. */
    private static double[] multiply(double[] left, double[] right) {
        double[] out = new double[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                double sum = 0.0;
                for (int k = 0; k < 4; k++) {
                    sum += left[k * 4 + row] * right[col * 4 + k];
                }
                out[col * 4 + row] = sum;
            }
        }
        return out;
    }
}
