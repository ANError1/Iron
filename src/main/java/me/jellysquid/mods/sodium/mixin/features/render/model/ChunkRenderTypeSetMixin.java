package me.jellysquid.mods.sodium.mixin.features.render.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.ChunkRenderTypeSet;
import org.embeddedt.embeddium.api.ChunkRenderTypeLimits;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This mixin rewrites the ChunkRenderTypeSet and associated APIs to use a universe of objects and less indirection.
 * This reduces allocation rate and should also be slightly faster than the original implementation.
 *
 * <p>The universe cache assumes a small number of chunk render types. Mods that register additional
 * render types can raise the limit or bypass the cache via {@link ChunkRenderTypeLimits}. When the cache
 * is disabled (or the configured limit is exceeded), instances are built on demand and interned in a
 * small fallback cache, so the optimization degrades gracefully instead of crashing.</p>
 */
@Mixin(value = ChunkRenderTypeSet.class, remap = false)
public class ChunkRenderTypeSetMixin {
    @Shadow
    @Final
    private static RenderType[] CHUNK_RENDER_TYPES;
    private ImmutableList<RenderType> embeddium$containedTypes;
    private int mask;

    private static final int CHUNK_RENDER_TYPE_COUNT = CHUNK_RENDER_TYPES.length;
    private static final int POSSIBLE_RENDER_TYPE_COMBINATIONS = 1 << CHUNK_RENDER_TYPE_COUNT;
    // Guard against the 1 << N overflow when N == Integer.SIZE so that intersection's seed mask is correct.
    private static final int MASK_ALL = CHUNK_RENDER_TYPE_COUNT >= Integer.SIZE ? -1 : POSSIBLE_RENDER_TYPE_COMBINATIONS - 1;

    /**
     * Whether the pre-computed universe cache is used. It is disabled when a mod calls
     * {@link ChunkRenderTypeLimits#disableUniverseCache()} or when the number of chunk
     * render types exceeds the configured limit, in which case the fallback cache takes over.
     */
    private static final boolean USE_UNIVERSE = !ChunkRenderTypeLimits.isUniverseCacheDisabled()
            && CHUNK_RENDER_TYPE_COUNT <= ChunkRenderTypeLimits.getMaxChunkRenderTypes();

    private static final ChunkRenderTypeSet[] UNIVERSE = USE_UNIVERSE ? Util.make(new ChunkRenderTypeSet[POSSIBLE_RENDER_TYPE_COMBINATIONS], array -> {
        if (CHUNK_RENDER_TYPE_COUNT > ChunkRenderTypeLimits.getMaxChunkRenderTypes()) {
            throw new AssertionError("This code is written assuming a small universe of chunk render types. " +
                    "Use ChunkRenderTypeLimits.setMaxChunkRenderTypes() or ChunkRenderTypeLimits.disableUniverseCache() " +
                    "before ChunkRenderTypeSet is loaded.");
        }
        array[0] = ChunkRenderTypeSet.none();
        for (int i = 1; i < (array.length - 1); i++) {
            array[i] = embeddium$construct(BitSet.valueOf(new long[] { i }));
        }
        array[MASK_ALL] = ChunkRenderTypeSet.all();
    }) : null;

    /**
     * Interning cache used when the universe is disabled. Keeps instance identity stable and
     * avoids re-allocating equivalent sets on every call.
     */
    private static final Map<Integer, ChunkRenderTypeSet> FALLBACK_CACHE = USE_UNIVERSE ? null : new ConcurrentHashMap<>();

    @Invoker("<init>")
    static ChunkRenderTypeSet embeddium$construct(BitSet bitSet) {
        throw new AssertionError();
    }

    /**
     * Resolves a bitmask to a {@link ChunkRenderTypeSet}, using the universe cache when available
     * and falling back to on-demand (interned) construction otherwise.
     */
    private static ChunkRenderTypeSet embeddium$getOrCreate(int mask) {
        ChunkRenderTypeSet[] universe = UNIVERSE;
        if (universe != null) {
            return universe[mask];
        }
        return FALLBACK_CACHE.computeIfAbsent(mask, m -> embeddium$construct(BitSet.valueOf(new long[] { m })));
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(BitSet bits, CallbackInfo ci) {
        // Read the full 32-bit mask (toLongArray) instead of only the first byte, so that more
        // than 8 chunk render types are represented correctly when the limit has been raised.
        int mask = bits.length() > 0 ? (int) bits.toLongArray()[0] : 0;
        this.mask = mask;
        ImmutableList.Builder<RenderType> builder = ImmutableList.builder();
        while (mask != 0) {
            int nextId = Integer.numberOfTrailingZeros(mask);
            mask &= ~(1 << nextId);
            builder.add(CHUNK_RENDER_TYPES[nextId]);
        }
        this.embeddium$containedTypes = builder.build();
    }

    /**
     * @author embeddedt
     * @reason use the iterator for the backing list
     */
    @Overwrite
    public Iterator<RenderType> iterator() {
        return this.embeddium$containedTypes.iterator();
    }

    /**
     * @author embeddedt
     * @reason avoid BitSet
     */
    @Overwrite
    public boolean isEmpty() {
        return this.mask == 0;
    }

    /**
     * @author embeddedt
     * @reason avoid BitSet
     */
    @Overwrite
    public boolean contains(RenderType renderType) {
        int id = renderType.getChunkLayerId();
        return id >= 0 && id < Integer.SIZE && (mask & (1 << id)) != 0;
    }

    /**
     * @author embeddedt
     * @reason use cached list
     */
    @Overwrite
    public List<RenderType> asList() {
        return this.embeddium$containedTypes;
    }

    /**
     * @author embeddedt
     * @reason use universe, avoid converting to list
     */
    @Overwrite
    public static ChunkRenderTypeSet of(RenderType... types) {
        int mask = 0;
        for (RenderType renderType : types) {
            int index = renderType.getChunkLayerId();
            if (index < 0 || index >= Integer.SIZE) {
                throw new IllegalArgumentException("Attempted to create chunk render type set with an unsupported chunk render type: " + renderType);
            }
            mask |= (1 << index);
        }
        return embeddium$getOrCreate(mask);
    }

    /**
     * @author embeddedt
     * @reason use universe, avoid toString on render type when not needed
     */
    @Overwrite
    private static ChunkRenderTypeSet of(Iterable<RenderType> types) {
        int mask = 0;
        for (RenderType renderType : types) {
            int index = renderType.getChunkLayerId();
            if (index < 0 || index >= Integer.SIZE) {
                throw new IllegalArgumentException("Attempted to create chunk render type set with an unsupported chunk render type: " + renderType);
            }
            mask |= (1 << index);
        }
        return embeddium$getOrCreate(mask);
    }

    /**
     * @author embeddedt
     * @reason use universe, avoid conversion to list
     */
    @Overwrite
    public static ChunkRenderTypeSet union(ChunkRenderTypeSet... sets) {
        int mask = 0;
        for (ChunkRenderTypeSet set : sets) {
            mask |= ((ChunkRenderTypeSetMixin) (Object) set).mask;
        }
        return embeddium$getOrCreate(mask);
    }

    /**
     * @author embeddedt
     * @reason use universe
     */
    @Overwrite
    public static ChunkRenderTypeSet union(Iterable<ChunkRenderTypeSet> sets) {
        int mask = 0;
        for (ChunkRenderTypeSet set : sets) {
            mask |= ((ChunkRenderTypeSetMixin) (Object) set).mask;
        }
        return embeddium$getOrCreate(mask);
    }

    /**
     * @author embeddedt
     * @reason use universe, avoid conversion to list
     */
    @Overwrite
    public static ChunkRenderTypeSet intersection(ChunkRenderTypeSet... sets) {
        int mask = MASK_ALL;
        for (ChunkRenderTypeSet set : sets) {
            mask &= ((ChunkRenderTypeSetMixin) (Object) set).mask;
        }
        return embeddium$getOrCreate(mask);
    }

    /**
     * @author embeddedt
     * @reason use universe
     */
    @Overwrite
    public static ChunkRenderTypeSet intersection(Iterable<ChunkRenderTypeSet> sets) {
        int mask = MASK_ALL;
        for (ChunkRenderTypeSet set : sets) {
            mask &= ((ChunkRenderTypeSetMixin) (Object) set).mask;
        }
        return embeddium$getOrCreate(mask);
    }
}
