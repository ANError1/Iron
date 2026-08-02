package org.embeddedt.embeddium.api;

/**
 * Configures the maximum number of chunk render types accepted by Embeddium's
 * optimized {@code ChunkRenderTypeSet}.
 *
 * <p>Embeddium pre-computes a "universe" cache containing every possible
 * combination of chunk render types so that lookups stay allocation-free.
 * By default this cache only accommodates {@value #DEFAULT_MAX} render types
 * (vanilla uses 4). Mods that register additional chunk render types through
 * Forge will hit an {@link AssertionError} once the count exceeds the limit;
 * this class lets them raise the limit, or bypass the cache entirely.</p>
 *
 * <p><b>Timing:</b> {@link #setMaxChunkRenderTypes(int)} and
 * {@link #disableUniverseCache()} only take effect if they are called
 * <em>before</em> {@code ChunkRenderTypeSet} is class-loaded &mdash; i.e. before
 * the first chunk is rendered. The recommended place to call them is a mod
 * constructor or an early FML setup event. Calling them afterwards is silently
 * ignored, because the universe cache has already been built.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // In your mod constructor, before any rendering happens:
 * ChunkRenderTypeLimits.setMaxChunkRenderTypes(12);
 * // ...or, if you need more than ABSOLUTE_MAX types:
 * ChunkRenderTypeLimits.disableUniverseCache();
 * }</pre>
 */
public final class ChunkRenderTypeLimits {
    /** Default and minimum number of chunk render types the universe cache will accept. */
    public static final int DEFAULT_MAX = 8;

    /**
     * Highest limit that may be passed to {@link #setMaxChunkRenderTypes(int)}.
     * The universe cache stores {@code 2^N} entries, so the memory cost grows
     * exponentially (16 types &asymp; 65K entries). Use
     * {@link #disableUniverseCache()} when more are needed &mdash; the on-demand
     * fallback supports up to 32 render types.
     */
    public static final int ABSOLUTE_MAX = 16;

    private static int maxChunkRenderTypes = DEFAULT_MAX;
    private static boolean universeCacheDisabled = false;

    private ChunkRenderTypeLimits() {
    }

    /**
     * Raise the number of chunk render types the universe cache will accept.
     * <p>The cache pre-allocates {@code 2^N} entries, so prefer the smallest
     * value that accommodates your mod.</p>
     *
     * @param limit the new limit, in [{@value #DEFAULT_MAX}, {@value #ABSOLUTE_MAX}]
     * @throws IllegalArgumentException if {@code limit} is outside the allowed range
     */
    public static void setMaxChunkRenderTypes(int limit) {
        if (limit < DEFAULT_MAX || limit > ABSOLUTE_MAX) {
            throw new IllegalArgumentException(
                    "Chunk render type limit must be between " + DEFAULT_MAX + " and " + ABSOLUTE_MAX +
                    " (inclusive), got " + limit);
        }
        maxChunkRenderTypes = limit;
    }

    /** @return the currently configured limit (defaults to {@value #DEFAULT_MAX}). */
    public static int getMaxChunkRenderTypes() {
        return maxChunkRenderTypes;
    }

    /**
     * Disable the universe cache. Every {@code ChunkRenderTypeSet} will then be
     * constructed on demand (and interned in a small cache), with no hard limit
     * on the number of render types (up to the 32-bit bitmask maximum). Use this
     * when raising the limit is not enough or the cache's memory cost is unwanted.
     */
    public static void disableUniverseCache() {
        universeCacheDisabled = true;
    }

    /** @return whether {@link #disableUniverseCache()} has been called. */
    public static boolean isUniverseCacheDisabled() {
        return universeCacheDisabled;
    }
}
