package me.jellysquid.mods.sodium.client.render.vertex.serializers;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import me.jellysquid.mods.sodium.client.render.vertex.serializers.generated.VertexSerializerFactory;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.StampedLock;

public class VertexSerializerRegistryImpl implements VertexSerializerRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertexSerializerRegistryImpl.class);

    private static final Path CLASS_DUMP_PATH;

    static {
        var classDumpPath = System.getProperty("sodium.codegen.dump", null);

        Path validatedPath = null;
        if (classDumpPath != null) {
            // Validate the dump path to prevent malicious configurations
            try {
                Path path = Path.of(classDumpPath).toAbsolutePath().normalize();
                
                // Ensure it's not a system-critical directory
                String pathStr = path.toString().toLowerCase();
                if (pathStr.contains("system32") || pathStr.contains("windows") || 
                    pathStr.equals("/") || pathStr.equals("c:\\") || pathStr.equals("c:/")) {
                    LOGGER.error("Rejecting unsafe class dump path: {}", classDumpPath);
                } else {
                    validatedPath = path;
                    LOGGER.info("Class dump path set to: {}", validatedPath);
                }
            } catch (Exception e) {
                LOGGER.error("Invalid class dump path: {}", classDumpPath, e);
            }
        }
        CLASS_DUMP_PATH = validatedPath;
    }

    private final Long2ReferenceMap<VertexSerializer> cache = new Long2ReferenceOpenHashMap<>();
    private final StampedLock lock = new StampedLock();

    @Override
    public VertexSerializer get(VertexFormatDescription srcFormat, VertexFormatDescription dstFormat) {
        var identifier = createKey(srcFormat, dstFormat);
        var serializer = this.find(identifier);

        if (serializer == null) {
            serializer = this.create(identifier, srcFormat, dstFormat);
        }

        return serializer;
    }

    private VertexSerializer create(long identifier, VertexFormatDescription srcFormat, VertexFormatDescription dstFormat) {
        var stamp = this.lock.writeLock();

        try {
            // Additional lookup to avoid calling createSerializer twice
            // Necessary because 'find' only acquires a non-exclusive lock
            var cached = this.cache.get(identifier);
            if (cached != null) {
                return cached;
            }

            // Create serializer
            var serializer = createSerializer(srcFormat, dstFormat);
            this.cache.put(identifier, serializer);
            return serializer;
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    private VertexSerializer find(long identifier) {
        var stamp = this.lock.readLock();

        try {
            return this.cache.get(identifier);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    private static VertexSerializer createSerializer(VertexFormatDescription srcVertexFormat, VertexFormatDescription dstVertexFormat) {
        var identifier = String.format("%04X$%04X", srcVertexFormat.id(), dstVertexFormat.id());

        var bytecode = VertexSerializerFactory.generate(srcVertexFormat, dstVertexFormat, identifier);

        if (CLASS_DUMP_PATH != null) {
            dumpClass(identifier, bytecode);
        }

        Class<?> clazz = VertexSerializerFactory.define(bytecode);
        Constructor<?> constructor;

        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to find constructor of generated class", e);
        }

        Object instance;

        try {
            instance = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to instantiate generated class", e);
        }

        VertexSerializer serializer;

        try {
            serializer = (VertexSerializer) instance;
        } catch (ClassCastException e) {
            throw new RuntimeException("Failed to cast generated class to interface type", e);
        }

        return serializer;
    }

    private static void dumpClass(String id, VertexSerializerFactory.Bytecode bytecode) {
        // Validate id to prevent path traversal
        if (id == null || id.contains("..") || id.contains("/") || id.contains("\\")) {
            LOGGER.error("Invalid class identifier for dump: {}", id);
            return;
        }
        
        try {
            // Ensure dump directory exists and is writable
            if (!Files.exists(CLASS_DUMP_PATH)) {
                Files.createDirectories(CLASS_DUMP_PATH);
            }
            
            // Validate that CLASS_DUMP_PATH is a directory
            if (!Files.isDirectory(CLASS_DUMP_PATH)) {
                LOGGER.error("Class dump path is not a directory: {}", CLASS_DUMP_PATH);
                return;
            }
            
            var fileName = "VertexSerializer$Impl$%s.class".formatted(id);
            var path = CLASS_DUMP_PATH.resolve(fileName);
            
            // Ensure resolved path is still within CLASS_DUMP_PATH (prevent symlink attacks)
            if (!path.normalize().startsWith(CLASS_DUMP_PATH.normalize())) {
                LOGGER.error("Attempted path traversal detected: {}", path);
                return;
            }
            
            Files.write(path, bytecode.copy());
        } catch (IOException e) {
            LOGGER.warn("Could not dump bytecode to location", e);
        }
    }

    private static long createKey(VertexFormatDescription a, VertexFormatDescription b) {
        return (long) a.id() & 0xffffffffL | ((long) b.id() & 0xffffffffL) << 32;
    }
}
