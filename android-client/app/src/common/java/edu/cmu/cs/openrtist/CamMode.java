package edu.cmu.cs.openrtist;

import android.app.AlertDialog;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;

import java.util.EnumMap;
import java.util.Map;

public class CamMode extends AbstractModeManager {

    public CamMode(GabrielClientActivity gabrielClientActivity, Map<ViewID, View> views) {
        super(gabrielClientActivity, views, new EnumMap<ViewID, Integer>(ViewID.class) {
            {
                put(ViewID.ARROW_UP, View.VISIBLE);
                put(ViewID.ARROW_DOWN, View.VISIBLE);
                put(ViewID.ARROW_LEFT, View.VISIBLE);
                put(ViewID.ARROW_RIGHT, View.VISIBLE);
                put(ViewID.FULL_SCREEN, View.VISIBLE);
                put(ViewID.CAM_CONTROL, View.VISIBLE);
                put(ViewID.AR_VIEW, View.VISIBLE);
                put(ViewID.ALIGN_CENTER, View.VISIBLE);
                put(ViewID.MENU, View.GONE);
                put(ViewID.SCENE_LIST, View.INVISIBLE);
                put(ViewID.RESET, View.INVISIBLE);
                put(ViewID.PLAY_PAUSE, View.INVISIBLE);
                put(ViewID.PARTICLE, View.INVISIBLE);
                put(ViewID.AUTO_PLAY, View.INVISIBLE);
                put(ViewID.ROTATE, View.INVISIBLE);
                put(ViewID.INFO, View.INVISIBLE);
                put(ViewID.HELP, View.VISIBLE);
                put(ViewID.IMAGE, View.VISIBLE);
            }});
    }

    @Override
    public void init() {
        super.init();
        ((ImageView)this.views.get(ViewID.CAM_CONTROL)).setImageResource(R.drawable.baseline_arrow_back_ios_new_24);

    }

    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    @Override
    protected View.OnTouchListener getOnTouchListener(ViewID key) {

        if (key == ViewID.IMAGE) {
            return new SceneScaleGestures(gabrielClientActivity, gabrielClientActivity);
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
        View.OnClickListener defaultListener = (view) -> {
        };

        switch(key) {
            case FULL_SCREEN:

                return (view -> {
                    ((ImageView)this.views.get(ViewID.CAM_CONTROL)).setImageResource(R.drawable.baseline_control_camera_24);
                    gabrielClientActivity.switchMode(GabrielClientActivity.AppMode.FULLSCREEN);
                });
            case CAM_CONTROL:

                return (view -> {
                    ((ImageView)this.views.get(ViewID.CAM_CONTROL)).setImageResource(R.drawable.baseline_control_camera_24);
                    gabrielClientActivity.switchMode(GabrielClientActivity.AppMode.MAIN);
                });

            case ALIGN_CENTER:
                return (view -> {
                    gabrielClientActivity.setAlignCenter(true);
                });

            case AR_VIEW:
                return (view -> {
                    gabrielClientActivity.setARView(true);
                });

            case HELP:
                return (view -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(gabrielClientActivity);
                    builder.setTitle("HELPER")
                            .setMessage(R.string.help_cam);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                });
        }

        return null;
    }



}
