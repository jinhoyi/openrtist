package edu.cmu.cs.openrtist;

import android.app.AlertDialog;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MenuMode extends AbstractModeManager {

    public MenuMode(GabrielClientActivity gabrielClientActivity, Map<ViewID, View> views) {
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
                put(ViewID.SCENE_LIST, View.VISIBLE);
                put(ViewID.RESET, View.VISIBLE);
                put(ViewID.PLAY_PAUSE, View.VISIBLE);
                put(ViewID.PARTICLE, View.VISIBLE);
                put(ViewID.AUTO_PLAY, View.VISIBLE);
                put(ViewID.ROTATE, View.VISIBLE);
                put(ViewID.INFO, View.VISIBLE);
                put(ViewID.HELP, View.VISIBLE);
                put(ViewID.MAIN, View.VISIBLE);
            }});
    }

    @Override
    public void init() {
        super.init();
        ((ImageView)this.views.get(ViewID.MENU)).setImageResource(R.drawable.outline_menu_open_24);
        ((ImageView)this.views.get(ViewID.PLAY_PAUSE)).setImageResource(
                clientActivity.getPause() ? R.drawable.outline_play_arrow_24 : R.drawable.outline_pause_24);
        ((ImageView)this.views.get(ViewID.PARTICLE)).setImageResource(
                clientActivity.getParticle() ? R.drawable.baseline_blur_off_24 : R.drawable.outline_blur_on_24);
        ((ImageView)this.views.get(ViewID.AUTO_PLAY)).setImageResource(
                clientActivity.getAutoPlay() ? R.drawable.autostop_fill0xml : R.drawable.autoplay_fill0);
        this.views.get(ViewID.ROTATE).setRotation(
                clientActivity.getLandscapeMode() ? 45.0f : -45.0f);
        this.views.get(ViewID.ROTATE).setRotationX(
                clientActivity.getLandscapeMode() ? 180.0f : 0.0f);
        ((ImageView)this.views.get(ViewID.INFO)).setImageResource(
                clientActivity.getInfo() ? R.drawable.baseline_close_24 : R.drawable.outline_info_24);
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
                    ((ImageView)this.views.get(ViewID.MENU)).setImageResource(R.drawable.baseline_menu_24);
                    clientActivity.switchMode(GabrielClientActivity.AppMode.FULLSCREEN);
                });
            case CAM_CONTROL:
                return (view -> {
                    ((ImageView)this.views.get(ViewID.MENU)).setImageResource(R.drawable.baseline_menu_24);
                    clientActivity.switchMode(GabrielClientActivity.AppMode.CAM);
                });

            case MAIN:
            case MENU:
                return (view -> {
                    ((ImageView)this.views.get(ViewID.MENU)).setImageResource(R.drawable.baseline_menu_24);
                    clientActivity.switchMode(GabrielClientActivity.AppMode.MAIN);
                });

            case SCENE_LIST:
                ArrayAdapter<String> sceneAdapter = clientActivity.getSceneAdapter();
                return (v -> {
                    // Create an ArrayAdapter
                    ArrayAdapter<String> adapter = sceneAdapter;
                    List<String> styleIds = clientActivity.getSceneIDs();

                    AlertDialog.Builder builder = new AlertDialog.Builder(clientActivity);
                    builder.setTitle("Choose a Scene")
                            .setAdapter(adapter, (dialog, position) -> clientActivity.setSceneType(styleIds.get(position)));

                    AlertDialog dialog = builder.create();
                    dialog.show();
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    clientActivity.switchMode(GabrielClientActivity.AppMode.MAIN);
                    ((ImageView)this.views.get(ViewID.MENU)).setImageResource(R.drawable.baseline_menu_24);
                });
            case PLAY_PAUSE:
                return (view -> {
                    clientActivity.setPause(true);
                    this.init();
                });

            case RESET:
                return (view -> {
                    clientActivity.setReset(true);
                    this.init();
                });

            case PARTICLE:
                return (view -> {
                    clientActivity.setParticle(true);
                    this.init();
                });

            case AUTO_PLAY:
                return (view -> {
                    clientActivity.setAutoPlay(true);
                    this.init();
                });

            case ROTATE:
                return (view -> {
                    clientActivity.setLandscapeMode(true);
                    this.init();
                });

            case INFO:
                return (view -> {
                    clientActivity.setInfo(true);
                    this.init();
                });



            case HELP:
                return (view -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(clientActivity);
                    builder.setTitle("HELPER")
                            .setMessage(R.string.help_cam);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                });
        }

        return null;
    }



}
