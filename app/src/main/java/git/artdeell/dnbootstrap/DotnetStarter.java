package git.artdeell.dnbootstrap;

import android.content.res.AssetManager;
import android.system.Os;

import java.io.File;
import java.io.IOException;

import git.artdeell.dnbootstrap.assets.AppDirs;
import git.artdeell.dnbootstrap.utils.SymlinkUtil;

public class DotnetStarter {

    private static File findCertsDir() {
        File certsDir = new File("/apex/com.android.conscrypt/cacerts/");
        if(certsDir.exists()) return certsDir;
        certsDir = new File("/system/etc/security/cacerts");
        if(certsDir.exists()) return certsDir;
        return null;
    }

    public static void kickstart(
            AppDirs appDirs,
            File appNativeDir,
            AssetManager assets
    ) throws IOException {

        File homeDir = new File(appDirs.base, "home");
        File certsDir = findCertsDir();

        if(certsDir == null) throw new IOException("Cannot start: can't find HTTPS certificate directory");
        File trueVsDir = new File(appDirs.vs, "vintagestory");

        try {
            Os.setenv("HOME", homeDir.getAbsolutePath(), true);
            Os.setenv("FONTCONFIG_PATH", appDirs.fontconfig.getAbsolutePath(), true);
            Os.setenv("SSL_CERT_DIR", certsDir.getAbsolutePath(), true);
            Os.setenv("LIBGL_NOERROR", "1", true);
            //Os.setenv("LIBGL_EGL", "libEGL_angle.so", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SymlinkUtil symlinkUtil = new SymlinkUtil(trueVsDir, appNativeDir);

        symlinkUtil.symlinkLibrary("libopenal.so", "libopenal.so.1");
        symlinkUtil.symlinkLibrary("libcairo.so", "libcairo.so.2");

        GraphicsProfileManager.applyIfNeeded(
                appDirs.base,
                homeDir,
                assets
        );

        MainActivity.runDotnet(appDirs.runtime.getAbsolutePath(), trueVsDir.getAbsolutePath(), appNativeDir.getAbsolutePath());
        System.exit(0);
    }
}
