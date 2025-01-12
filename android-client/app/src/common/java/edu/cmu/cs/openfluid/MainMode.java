package edu.cmu.cs.openfluid;

import android.app.AlertDialog;
import android.os.Build;
import android.util.Pair;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
                List<Pair<Integer, String>> items = new ArrayList<>();
                items.add(new Pair<>(R.drawable.outline_help_outline_24,
                        clientActivity.getString(R.string.help_info)));
                items.add(new Pair<>(R.drawable.baseline_menu_24,
                        clientActivity.getString(R.string.help_menu)));
                items.add(new Pair<>(R.drawable.baseline_control_camera_24,
                        clientActivity.getString(R.string.help_cam_mode)));
                items.add(new Pair<>(R.drawable.baseline_fullscreen_24,
                        clientActivity.getString(R.string.help_fullscreen)));

                CustomListAdapter adapter = new CustomListAdapter(clientActivity, R.layout.my_help_list, items);
                AlertDialog.Builder builder = new AlertDialog.Builder(clientActivity);
                builder.setTitle("Help")
                        .setAdapter(adapter, (dialog, which) -> {});
                AlertDialog dialog = builder.create();

                return (view -> {
                    dialog.show();
                });
        }

        return null;
    }
}
