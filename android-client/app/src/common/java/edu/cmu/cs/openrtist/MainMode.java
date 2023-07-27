package edu.cmu.cs.openrtist;

import android.app.AlertDialog;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.RequiresApi;

import java.util.EnumMap;
import java.util.Map;

public class MainMode extends AbstractModeManager {

    public MainMode(GabrielClientActivity gabrielClientActivity, Map<ViewID, View> views) {
        super(gabrielClientActivity, views, new EnumMap<ViewID, Integer>(ViewID.class) {
            {
                put(ViewID.ARROW_UP, View.INVISIBLE);
                put(ViewID.ARROW_DOWN, View.INVISIBLE);
                put(ViewID.ARROW_LEFT, View.INVISIBLE);
                put(ViewID.ARROW_RIGHT, View.INVISIBLE);
                put(ViewID.FULL_SCREEN, View.VISIBLE);
                put(ViewID.CAM_CONTROL, View.VISIBLE);
                put(ViewID.AR_VIEW, View.INVISIBLE);
                put(ViewID.ALIGN_CENTER, View.INVISIBLE);
                put(ViewID.MENU, View.VISIBLE);
                put(ViewID.SCENE_LIST, View.INVISIBLE);
                put(ViewID.RESET, View.INVISIBLE);
                put(ViewID.PLAY_PAUSE, View.INVISIBLE);
                put(ViewID.PARTICLE, View.INVISIBLE);
                put(ViewID.AUTO_PLAY, View.INVISIBLE);
                put(ViewID.ROTATE, View.INVISIBLE);
                put(ViewID.INFO, View.INVISIBLE);
                put(ViewID.HELP, View.VISIBLE);
                put(ViewID.MAIN, View.VISIBLE);
            }});
    }

    public void init() {
        super.init();
    }

    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    @Override
    protected View.OnTouchListener getOnTouchListener(ViewID key) {
        if (key == ViewID.MAIN) {
            return null;
        }

        return (view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    break;
                case MotionEvent.ACTION_UP:
                    view.setPressed(false);
                    view.performClick();
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    break;
            }
            return true;
        };
    }

    @Override
    protected View.OnClickListener getOnClickListener(ViewID key) {

        switch(key) {
            case FULL_SCREEN:
                return (view -> {
                    clientActivity.switchMode(GabrielClientActivity.AppMode.FULLSCREEN);
                });
            case CAM_CONTROL:
                return (view -> {
                    clientActivity.switchMode(GabrielClientActivity.AppMode.CAM);
                });

            case MENU:
                return (view -> {
                    clientActivity.switchMode(GabrielClientActivity.AppMode.MENU);
                });

            case HELP:
                return (view -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(clientActivity);
                    builder.setTitle("HELPER")
                            .setMessage(R.string.help_info);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                });
        }

        return null;
    }
}
