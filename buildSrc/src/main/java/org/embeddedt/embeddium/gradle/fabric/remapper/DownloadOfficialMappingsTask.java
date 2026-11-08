package org.embeddedt.embeddium.gradle.fabric.remapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class DownloadOfficialMappingsTask {
    private static final URL VERSION_MANIFEST_URL = createUrl("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
    private static final int MAX_MANIFEST_SIZE = 16 * 1024 * 1024;
    private static final int MAX_MAPPINGS_SIZE = 64 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    public static void run(String targetVersion, File outputFile) throws IOException {
        JsonObject versionManifest = parseJson(download(VERSION_MANIFEST_URL, MAX_MANIFEST_SIZE), "version manifest");
        System.out.println("Downloaded launcher manifest.");
        var versionArray = versionManifest.getAsJsonArray("versions");
        URL targetURL = null;
        for(var element : versionArray) {
            if(element.getAsJsonObject().get("id").getAsJsonPrimitive().getAsString().equals(targetVersion)) {
                targetURL = new URL(element.getAsJsonObject().get("url").getAsJsonPrimitive().getAsString());
                break;
            }
        }
        if(targetURL == null) {
            throw new IllegalArgumentException("No version info found for " + targetVersion);
        }
        JsonObject targetManifest = parseJson(download(targetURL, MAX_MANIFEST_SIZE), "manifest for " + targetVersion);
        if (!targetVersion.equals(targetManifest.get("id").getAsString())) {
            throw new IOException("Downloaded manifest does not match Minecraft version " + targetVersion);
        }
        System.out.println("Downloaded " + targetVersion + " manifest.");

        JsonObject mappingsMetadata = targetManifest.getAsJsonObject("downloads").getAsJsonObject("client_mappings");
        URL mappingsURL = new URL(mappingsMetadata.getAsJsonPrimitive("url").getAsString());
        String expectedSha1 = mappingsMetadata.getAsJsonPrimitive("sha1").getAsString().toLowerCase(Locale.ROOT);
        long expectedSize = mappingsMetadata.getAsJsonPrimitive("size").getAsLong();
        if (!expectedSha1.matches("[0-9a-f]{40}")) {
            throw new IOException("Invalid SHA-1 in mappings metadata for " + targetVersion);
        }
        if (expectedSize <= 0 || expectedSize > MAX_MAPPINGS_SIZE) {
            throw new IOException("Unexpected mappings size for " + targetVersion + ": " + expectedSize);
        }

        byte[] mappings = download(mappingsURL, MAX_MAPPINGS_SIZE);
        if (mappings.length != expectedSize) {
            throw new IOException("Mappings size mismatch for " + targetVersion);
        }
        if (!expectedSha1.equals(sha1(mappings))) {
            throw new IOException("Mappings SHA-1 mismatch for " + targetVersion);
        }

        Path destination = outputFile.toPath().toAbsolutePath();
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, destination.getFileName().toString(), ".tmp");
        try {
            Files.write(temporaryFile, mappings);
            try {
                Files.move(temporaryFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
        System.out.println("Downloaded and verified " + targetVersion + " mappings.");
    }

    private static byte[] download(URL initialUrl, int maximumSize) throws IOException {
        URL url = initialUrl;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validateUrl(url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Embeddium-Build");

            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirect == MAX_REDIRECTS) {
                        throw new IOException("Too many or invalid redirects while downloading " + url);
                    }
                    url = new URL(url, location);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("Unexpected HTTP status " + status + " while downloading " + url);
                }

                long contentLength = connection.getContentLengthLong();
                if (contentLength > maximumSize) {
                    throw new IOException("Download exceeds size limit: " + url);
                }
                try (InputStream input = connection.getInputStream()) {
                    return readLimited(input, maximumSize, url);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects while downloading " + initialUrl);
    }

    private static byte[] readLimited(InputStream input, int maximumSize, URL url) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total > maximumSize - read) {
                throw new IOException("Download exceeds size limit: " + url);
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private static JsonObject parseJson(byte[] data, String description) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                throw new IOException("Invalid JSON object in " + description);
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Could not parse " + description, e);
        }
    }

    private static void validateUrl(URL url) throws IOException {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(url.getProtocol())
                || url.getUserInfo() != null
                || (url.getPort() != -1 && url.getPort() != 443)
                || !(host.equals("mojang.com") || host.endsWith(".mojang.com"))) {
            throw new IOException("Refusing untrusted mappings download URL: " + url);
        }
    }

    private static String sha1(byte[] data) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 is unavailable", e);
        }
    }

    private static URL createUrl(String value) {
        try {
            return new URL(value);
        } catch (java.net.MalformedURLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
