package git.artdeell.dnbootstrap;

import android.content.res.AssetManager;
import android.system.Os;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class GraphicsProfileManager {

    private static final String TAG = "PSX-GraphicsProfile";

    private static final String PROFILE_ASSET =
            "graphics-profile-absolute-minimum.json";

    private static final String PROFILE_MARKER =
            "psx-graphics-profile-absolute-minimum-v1.applied";

    private static final String TEMP_SUFFIX = ".psx.tmp";
    private static final String BACKUP_SUFFIX =
            ".psx-absolute-minimum.bak";

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private GraphicsProfileManager() {
    }

    public static synchronized void applyIfNeeded(
            File appBaseDir,
            File homeDir,
            AssetManager assets
    ) {
        File marker = new File(appBaseDir, PROFILE_MARKER);

        if (marker.exists()) {
            return;
        }

        try {
            JsonObject profile = loadAsset(assets, PROFILE_ASSET);
            validateProfile(profile);

            File settingsFile = new File(
                    homeDir,
                    ".config/VintagestoryData/clientsettings.json"
            );

            JsonObject settings = settingsFile.exists()
                    ? loadFile(settingsFile)
                    : new JsonObject();

            mergeSection(settings, profile, "intSettings");
            mergeSection(settings, profile, "boolSettings");
            mergeSection(settings, profile, "floatSettings");

            writeSettingsSafely(settingsFile, settings);
            writeMarkerSafely(marker);

            Log.i(
                    TAG,
                    "Applied absolute minimum graphics profile."
            );

        } catch (Exception e) {
            Log.w(
                    TAG,
                    "Graphics profile was not applied; keeping existing/default settings.",
                    e
            );
        }
    }

    private static JsonObject loadAsset(
            AssetManager assets,
            String assetName
    ) throws IOException {

        if (assets == null) {
            throw new IOException("AssetManager is null");
        }

        try (Reader reader = new InputStreamReader(
                assets.open(assetName),
                StandardCharsets.UTF_8
        )) {
            JsonElement element = JsonParser.parseReader(reader);

            if (!element.isJsonObject()) {
                throw new IOException(
                        "Graphics profile root is not a JSON object"
                );
            }

            return element.getAsJsonObject();
        }
    }

    private static JsonObject loadFile(File file)
            throws IOException {

        try (Reader reader = new InputStreamReader(
                new FileInputStream(file),
                StandardCharsets.UTF_8
        )) {
            JsonElement element = JsonParser.parseReader(reader);

            if (!element.isJsonObject()) {
                throw new IOException(
                        "Existing clientsettings.json is not a JSON object"
                );
            }

            return element.getAsJsonObject();
        }
    }

    private static void validateProfile(JsonObject profile)
            throws IOException {

        validateSection(profile, "intSettings");
        validateSection(profile, "boolSettings");
        validateSection(profile, "floatSettings");
    }

    private static void validateSection(
            JsonObject profile,
            String sectionName
    ) throws IOException {

        JsonElement section = profile.get(sectionName);

        if (section == null) {
            return;
        }

        if (!section.isJsonObject()) {
            throw new IOException(
                    "Graphics profile section is not an object: "
                            + sectionName
            );
        }

        for (String key :
                section.getAsJsonObject().keySet()) {

            JsonElement value =
                    section.getAsJsonObject().get(key);

            if (value == null || !value.isJsonPrimitive()) {
                throw new IOException(
                        "Graphics profile contains invalid value: "
                                + sectionName + "." + key
                );
            }
        }
    }

    private static void mergeSection(
            JsonObject target,
            JsonObject source,
            String sectionName
    ) {

        JsonElement sourceElement =
                source.get(sectionName);

        if (sourceElement == null
                || !sourceElement.isJsonObject()) {
            return;
        }

        JsonObject targetSection;

        JsonElement targetElement =
                target.get(sectionName);

        if (targetElement != null
                && targetElement.isJsonObject()) {

            targetSection =
                    targetElement.getAsJsonObject();

        } else {

            targetSection = new JsonObject();
            target.add(sectionName, targetSection);
        }

        JsonObject sourceSection =
                sourceElement.getAsJsonObject();

        for (String key : sourceSection.keySet()) {
            targetSection.add(
                    key,
                    sourceSection.get(key)
            );
        }
    }

    private static void writeSettingsSafely(
            File settingsFile,
            JsonObject contents
    ) throws IOException {

        File parent = settingsFile.getParentFile();

        if (!parent.exists()
                && !parent.mkdirs()
                && !parent.exists()) {

            throw new IOException(
                    "Could not create settings directory: "
                            + parent
            );
        }

        File tempFile = new File(
                parent,
                settingsFile.getName() + TEMP_SUFFIX
        );

        File backupFile = new File(
                parent,
                settingsFile.getName() + BACKUP_SUFFIX
        );

        if (tempFile.exists()
                && !tempFile.delete()) {

            throw new IOException(
                    "Could not remove stale temporary settings file: "
                            + tempFile
            );
        }

        /*
         * Preserve the first known-good copy of the original settings.
         * Never overwrite an existing backup.
         */
        if (settingsFile.exists()
                && !backupFile.exists()) {

            copyFile(settingsFile, backupFile);

            Log.i(
                    TAG,
                    "Preserved original clientsettings.json at: "
                            + backupFile.getAbsolutePath()
            );
        }

        writeJsonFile(tempFile, contents);

        try {
            /*
             * tempFile and settingsFile are deliberately in the same
             * directory so Android can perform a same-filesystem rename.
             */
            Os.rename(
                    tempFile.getAbsolutePath(),
                    settingsFile.getAbsolutePath()
            );

        } catch (Exception e) {

            tempFile.delete();

            throw new IOException(
                    "Could not atomically install graphics settings file",
                    e
            );
        }

        Log.i(
                TAG,
                "Installed graphics settings: "
                        + settingsFile.getAbsolutePath()
        );

        if (backupFile.exists()) {
            Log.i(
                    TAG,
                    "Recovery backup retained at: "
                            + backupFile.getAbsolutePath()
            );
        }
    }

    private static void writeJsonFile(
            File file,
            JsonObject contents
    ) throws IOException {

        try (FileOutputStream output =
                     new FileOutputStream(file);
             Writer writer =
                     new OutputStreamWriter(
                             output,
                             StandardCharsets.UTF_8
                     )) {

            GSON.toJson(contents, writer);
            writer.flush();

            /*
             * Flush the completed JSON to storage before replacement.
             */
            output.getFD().sync();
        }
    }

    private static void copyFile(
            File source,
            File destination
    ) throws IOException {

        /*
         * The caller only invokes this when the destination does not
         * already exist, so the first original backup is preserved.
         */
        try (FileInputStream input =
                     new FileInputStream(source);
             FileOutputStream output =
                     new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            output.flush();
            output.getFD().sync();
        }
    }

    private static void writeMarkerSafely(File marker)
            throws IOException {

        File parent = marker.getParentFile();

        if (!parent.exists()
                && !parent.mkdirs()
                && !parent.exists()) {

            throw new IOException(
                    "Could not create marker directory: "
                            + parent
            );
        }

        File tempMarker = new File(
                parent,
                marker.getName() + TEMP_SUFFIX
        );

        if (tempMarker.exists()
                && !tempMarker.delete()) {

            throw new IOException(
                    "Could not remove stale marker temp file: "
                            + tempMarker
            );
        }

        try (FileOutputStream output =
                     new FileOutputStream(tempMarker);
             Writer writer =
                     new OutputStreamWriter(
                             output,
                             StandardCharsets.UTF_8
                     )) {

            writer.write("absolute-minimum-v1\n");
            writer.flush();
            output.getFD().sync();
        }

        try {

            Os.rename(
                    tempMarker.getAbsolutePath(),
                    marker.getAbsolutePath()
            );

        } catch (Exception e) {

            tempMarker.delete();

            throw new IOException(
                    "Could not install graphics profile marker",
                    e
            );
        }

        Log.i(
                TAG,
                "Graphics profile marker installed: "
                        + marker.getAbsolutePath()
        );
    }
}
