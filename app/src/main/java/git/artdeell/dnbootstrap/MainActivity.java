package git.artdeell.dnbootstrap;

import static android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import java.io.File;

import git.artdeell.dnbootstrap.assets.AppDirs;
import git.artdeell.dnbootstrap.glfw.GLFW;
import git.artdeell.dnbootstrap.glfw.KeyCodes;
import git.artdeell.dnbootstrap.input.ControlLayout;
import git.artdeell.dnbootstrap.input.SoftInputCallback;
import git.artdeell.dnbootstrap.input.TouchCharInput;
import git.artdeell.dnbootstrap.input.editor.ControlEditorLayout;
import git.artdeell.dnbootstrap.input.editor.LayoutEditorHost;
import git.artdeell.dnbootstrap.utils.InsetUtils;
import git.artdeell.dnbootstrap.utils.Utils;

public class MainActivity extends Activity implements SoftInputCallback, LayoutEditorHost {
    static {
        System.loadLibrary("glfw");
        GLFW.initialize();
        System.loadLibrary("dnbootstrap");
    }

    private TouchCharInput touchCharInput;
    private ControlLayout controlLayout;
    private View layoutEditor;

    private static boolean isRunning = false;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SurfaceView surfaceView = findViewById(R.id.surface_view);
        touchCharInput = findViewById(R.id.touch_char_input);
        controlLayout = findViewById(R.id.control_layout);
        InsetUtils.setInsetsMode(this, true, false);
        surfaceView.getHolder().addCallback(new NativeSurfaceListener());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerOnBackInvoked();
        }
        if(!isRunning) {
            isRunning = true;
            new Thread(this::kickstart).start();
        }
    }

    @Override
    public void requestSoftInput() {
        touchCharInput.requestKeyboard();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void registerOnBackInvoked() {
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(PRIORITY_DEFAULT, this::onBackPressed);
    }

    @Override
    public void openLayoutEditor() {
        ViewGroup controlLayoutParent = (ViewGroup) controlLayout.getParent();
        controlLayoutParent.removeView(controlLayout);
        layoutEditor = LayoutInflater.from(this).inflate(R.layout.controls_editor, controlLayoutParent, false);
        ControlEditorLayout editorLayout = layoutEditor.findViewById(R.id.control_layout_editor);
        editorLayout.setEditorHost(this);
        controlLayoutParent.addView(layoutEditor);
    }

    @Override
    public void exitLayoutEditor() {
        if(layoutEditor == null) return;
        ViewGroup editorParent = (ViewGroup) layoutEditor.getParent();
        editorParent.removeView(layoutEditor);
        editorParent.addView(controlLayout);
        controlLayout.loadAsync();
        layoutEditor = null;
    }


    public void kickstart() {
        try {
            DotnetStarter.kickstart(
                    new AppDirs(getFilesDir()),
                    new File(getApplicationInfo().nativeLibraryDir),
                    getAssets()
            );
        }catch (Throwable t) {
            Utils.showErrorDialog(this, t, true);
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        GLFW.sendKeyEvent(KeyCodes.GLFW_KEY_ESCAPE, 1, 0);
        GLFW.sendKeyEvent(KeyCodes.GLFW_KEY_ESCAPE, 0, 0);
    }

    public static native void runDotnet(String dotnetRoot, String vsDir, String nativeLibDir);
}