package me.jellysquid.mods.sodium.client.render.chunk.vertex.builder;

import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int stride;

    private final int initialCapacity;
    private final TranslucentQuadAnalyzer analyzer;

    private ByteBuffer buffer;
    private int count;
    private int capacity;
    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity, boolean collectSortState) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial vertex capacity must be positive");
        }

        this.encoder = vertexType.getEncoder();
        this.stride = vertexType.getVertexFormat().getStride();

        this.buffer = null;

        this.capacity = initialCapacity;
        this.initialCapacity = initialCapacity;

        this.analyzer = collectSortState ? new TranslucentQuadAnalyzer() : null;
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        var vertexCount = vertices.length;

        if (vertexCount == 0) {
            return;
        }

        int requiredCapacity = Math.addExact(this.count, vertexCount);

        if (requiredCapacity > this.capacity) {
            this.grow(requiredCapacity);
        }

        long ptr = MemoryUtil.memAddress(this.buffer, bytesForVertices(this.count));

        if (this.analyzer != null) {
            for (ChunkVertexEncoder.Vertex vertex : vertices) {
                this.analyzer.capture(vertex);
            }
        }

        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            ptr = this.encoder.write(ptr, material, vertex, this.sectionIndex);
        }

        this.count += vertexCount;
    }

    private void grow(int requiredCapacity) {
        // Capacity is measured in vertices. Keeping byte conversion in one place prevents
        // integer overflow from producing an undersized native allocation.
        int doubledCapacity = this.capacity > Integer.MAX_VALUE / 2
                ? Integer.MAX_VALUE
                : this.capacity * 2;
        int newCapacity = Math.max(doubledCapacity, requiredCapacity);

        this.setBufferSize(newCapacity);
    }

    private void setBufferSize(int vertexCapacity) {
        this.buffer = MemoryUtil.memRealloc(this.buffer, bytesForVertices(vertexCapacity));
        this.capacity = vertexCapacity;
    }

    private int bytesForVertices(int vertexCount) {
        return Math.multiplyExact(vertexCount, this.stride);
    }

    public void start(int sectionIndex) {
        this.count = 0;
        this.sectionIndex = sectionIndex;
        if(this.analyzer != null) {
            this.analyzer.clear();
        }

        this.setBufferSize(this.initialCapacity);
    }

    @Nullable
    public TranslucentQuadAnalyzer.SortState getSortState() {
        return this.analyzer != null ? this.analyzer.getSortState() : null;
    }

    public void destroy() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }

        this.buffer = null;
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memSlice(this.buffer, 0, bytesForVertices(this.count));
    }

    public int count() {
        return this.count;
    }
}
