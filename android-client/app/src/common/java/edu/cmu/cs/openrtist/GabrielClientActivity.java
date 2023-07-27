// Copyright 2018 Carnegie Mellon University
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package edu.cmu.cs.openrtist;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.RenderScript;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.view.Choreographer;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

//import android.hardware.SensorManager;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.camera.CameraCapture;
import edu.cmu.cs.gabriel.camera.ImageViewUpdater;
import edu.cmu.cs.gabriel.camera.YuvToNV21Converter;
import edu.cmu.cs.gabriel.camera.YuvToJPEGConverter;
import edu.cmu.cs.gabriel.network.OpenrtistComm;
import edu.cmu.cs.gabriel.protocol.Protos.InputFrame;
import edu.cmu.cs.gabriel.protocol.Protos.PayloadType;
import edu.cmu.cs.localtransfer.LocalTransfer;
import edu.cmu.cs.openrtist.Protos.Extras;

public class GabrielClientActivity extends AppCompatActivity implements
        AdapterView.OnItemSelectedListener, SensorEventListener {

    public enum AppMode {
        MAIN, MENU, CAM, FULLSCREEN
    }
    private static boolean running = false;
    private static final String LOG_TAG = "GabrielClientActivity";
    private static final int DISPLAY_WIDTH = 480;
    private static final int DISPLAY_HEIGHT = 640;
    private static final int BITRATE = 1024 * 1024;
    private static final int MEDIA_TYPE_IMAGE = 1;
    private static final int MEDIA_TYPE_VIDEO = 2;

    // major components for streaming sensor data and receiving information
    String serverIP = null;
    private String styleType = "?"; // "?" when style is not retrieved from the server, "none" if style is retrieved but not selected

    private OpenrtistComm openrtistComm;

    private MediaController mediaController = null;
    private int mScreenDensity;
    private int mScreenHeight = 640;
    private int mScreenWidth = 480;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private boolean capturingScreen = false;
    private boolean recordingInitiated = false;
    private String mOutputPath = null;

    private boolean help = false;

    // views
    private ImageView imgView;
    private ImageView iconView;
    private ImageButton buttonLeft;
    private ImageButton buttonRight;
    private ImageButton buttonUp;
    private ImageButton buttonDown;
    private ImageView viewFullScreen;
    private ImageView viewCamControl;
    private ImageView viewARView;
    private ImageView viewAlignCenter;
    private ImageView viewMenu;
    private ImageView viewSceneList;
    private ImageView viewReset;
    private ImageView viewPlayPause;
    private ImageView viewParticle;
    private ImageView viewAutoPlay;
    private ImageView viewRotate;
    private ImageView viewInfo;
    private ImageView viewHelp;

    private final Map<ViewID, View> views = new EnumMap<ViewID, View>(ViewID.class);
    private final Map<AppMode, AbstractModeManager> modeList = new EnumMap<AppMode, AbstractModeManager>(AppMode.class);

    private AppMode currMode = AppMode.MAIN;
    private Handler iterationHandler;
    private Handler frameHandler;
    private Handler fpsHandler;
    private TextView fpsLabel;
    private PreviewView preview;

    private TextView accelLabel;

    // Stereo views

    private boolean cleared = false;

    private int framesProcessed = 0;
    private YuvToNV21Converter yuvToNV21Converter;
    private YuvToJPEGConverter yuvToJPEGConverter;
    private CameraCapture cameraCapture;

    private final List<String> styleDescriptions = new ArrayList<>(Collections.singletonList("Choose Scene"));
    private final List<String> styleIds = new ArrayList<>(Collections.singletonList("none"));

    public class Pair implements Comparable<Pair> {
        String key;
        String value;

        public Pair(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int compareTo(Pair other) {
            return Integer.parseInt(this.getKey()) - Integer.parseInt(other.getKey());
        }
    }

    public void sortPairedArray(List<String> keyList, List<String> valueList) {
        ArrayList<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < keyList.size(); i++) {
            pairs.add(new Pair(keyList.get(i), valueList.get(i)));
        }

        Collections.sort(pairs);

        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            keyList.set(i, pair.getKey());
            valueList.set(i, pair.getValue());
        }
    }

    public void addStyles(Set<Map.Entry<String, String>> entrySet) {
        for (Map.Entry<String, String> entry : entrySet) {
            Log.v(LOG_TAG, "style: " + entry.getKey() + ", desc: " + entry.getValue());
            styleDescriptions.add(entry.getValue());
            styleIds.add(entry.getKey());
        }
        styleDescriptions.remove(0);
        styleIds.remove(0);

        // Sort the list of Pairs
        sortPairedArray(styleIds, styleDescriptions);

        this.styleType = styleIds.get(0);
    }

    // SensorListener
    private SensorManager sensorManager;
    private Sensor mSensor;
    private float imu_x = 0;
    private float imu_y = 0;
    private float imu_z = 0;

//    private ScaleGestureDetector SGD;
//    private float scaleFactor = 1;
//    private boolean inScale = false;
    private float sceneScaleFactor = 1.0f;
    private float sceneX = 0.0f;
    private float sceneY= 0.0f;

    private boolean alignCenter = false;
    private boolean arView = false;

    public void setAlignCenter(boolean b){
        alignCenter = b;
        Toast.makeText(this,
                "setAlignCenter - Incomplete",
                Toast.LENGTH_SHORT).show();
    }

    public void setARView(boolean b){
        arView = b;
        Toast.makeText(this,
                "setARView - Incomplete",
                Toast.LENGTH_SHORT).show();
    }

    public void setScaleFactor(float factor){
        sceneScaleFactor = factor;
    }

    public void setXY(float x, float y){
        sceneX = x;
        sceneY = y;
    }

    public void switchMode(AppMode mode) {
        currMode = mode;
        switch(mode) {
            case MAIN:
                Toast.makeText(this,
                        "MAIN!!",
                        Toast.LENGTH_LONG).show();
                modeList.get(mode).init();
                return;
            case FULLSCREEN:
                Toast.makeText(this,
                        "FULLSCREEN!!",
                        Toast.LENGTH_LONG).show();
                return;
            case CAM:
                Toast.makeText(this,
                        "CAM!!",
                        Toast.LENGTH_LONG).show();
                modeList.get(mode).init();
                return;
            case MENU:
                Toast.makeText(this,
                        "MENU!!",
                        Toast.LENGTH_LONG).show();
                return;
        }
        return;
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        imu_x = event.values[0];
        imu_y = event.values[1];
        imu_z = event.values[2];

    }

    // local execution
    private boolean runLocally = false;
    private LocalTransfer localRunner = null;
    private HandlerThread localRunnerThread = null;
    private Handler localRunnerThreadHandler = null;
    private volatile boolean localRunnerBusy = false;
    private RenderScript rs = null;
    private Bitmap bitmapCache;
//    private SensorManager sensorManager = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(LOG_TAG, "++onCreate");
        super.onCreate(savedInstanceState);
        Const.STYLES_RETRIEVED = false;
        Const.ITERATION_STARTED = false;

        if (Const.STEREO_ENABLED) {
            setContentView(R.layout.activity_stereo);
        } else {
            setContentView(R.layout.activity_main);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                + WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                + WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        imgView = findViewById(R.id.guidance_image);
        iconView = findViewById(R.id.style_image);
        fpsLabel = findViewById(R.id.fpsLabel);
        accelLabel = findViewById(R.id.accelLabel);

//        imgView.setOnTouchListener(new SceneScaleGestures(this, this));

        buttonLeft = findViewById(R.id.button_left);
        buttonRight = findViewById(R.id.button_right);
        buttonUp = findViewById(R.id.button_up);
        buttonDown = findViewById(R.id.button_down);

        viewFullScreen = findViewById(R.id.imgFullScreen);
        viewCamControl = findViewById(R.id.imgCamControl);
        viewARView = findViewById(R.id.imgARView);
        viewAlignCenter = findViewById(R.id.imgAlignCenter);
        viewMenu = findViewById(R.id.imgMenu);
        viewSceneList = findViewById(R.id.imgSceneList);
        viewReset = findViewById(R.id.imgReset);
        viewPlayPause = findViewById(R.id.imgPlayPause);
        viewParticle = findViewById(R.id.imgParticle);
        viewAutoPlay = findViewById(R.id.imgAutoPlay);
        viewRotate = findViewById(R.id.imgRotate);
        viewInfo = findViewById(R.id.imgInfo);
        viewHelp = findViewById(R.id.imgHelp);

        views.put(ViewID.ARROW_UP, buttonUp);
        views.put(ViewID.ARROW_DOWN, buttonDown);
        views.put(ViewID.ARROW_LEFT, buttonLeft);
        views.put(ViewID.ARROW_RIGHT, buttonRight);
        views.put(ViewID.FULL_SCREEN, viewFullScreen);
        views.put(ViewID.CAM_CONTROL, viewCamControl);
        views.put(ViewID.AR_VIEW, viewARView);
        views.put(ViewID.ALIGN_CENTER, viewAlignCenter);
        views.put(ViewID.MENU, viewMenu);
        views.put(ViewID.SCENE_LIST, viewSceneList);
        views.put(ViewID.RESET, viewReset);
        views.put(ViewID.PLAY_PAUSE, viewPlayPause);
        views.put(ViewID.PARTICLE, viewParticle);
        views.put(ViewID.AUTO_PLAY, viewAutoPlay);
        views.put(ViewID.ROTATE, viewRotate);
        views.put(ViewID.INFO, viewInfo);
        views.put(ViewID.HELP, viewHelp);
        views.put(ViewID.IMAGE, imgView);

//        views.put(ViewID.ARROW_UP, findViewById(R.id.button_up));
//        views.put(ViewID.ARROW_DOWN, findViewById(R.id.button_down));
//        views.put(ViewID.ARROW_LEFT, findViewById(R.id.button_left));
//        views.put(ViewID.ARROW_RIGHT, findViewById(R.id.button_right));
//        views.put(ViewID.FULL_SCREEN, findViewById(R.id.imgFullScreen));
//        views.put(ViewID.CAM_CONTROL, findViewById(R.id.imgCamControl));
//        views.put(ViewID.AR_VIEW, findViewById(R.id.imgARView));
//        views.put(ViewID.ALIGN_CENTER, findViewById(R.id.imgAlignCenter));
//        views.put(ViewID.MENU, findViewById(R.id.imgMenu));
//        views.put(ViewID.SCENE_LIST, findViewById(R.id.imgSceneList));
//        views.put(ViewID.RESET, findViewById(R.id.imgReset));
//        views.put(ViewID.PLAY_PAUSE, findViewById(R.id.imgPlayPause));
//        views.put(ViewID.PARTICLE, findViewById(R.id.imgParticle));
//        views.put(ViewID.AUTO_PLAY, findViewById(R.id.imgAutoPlay));
//        views.put(ViewID.ROTATE, findViewById(R.id.imgRotate));
//        views.put(ViewID.INFO, findViewById(R.id.imgInfo));
//        views.put(ViewID.HELP, findViewById(R.id.imgHelp));
//        views.put(ViewID.IMAGE, findViewById(R.id.guidance_image));

//        AbstractModeManager mainMode = new MainMode(this, views);
        modeList.put(AppMode.MAIN, new MainMode(this, views));
        modeList.put(AppMode.CAM, new CamMode(this, views));
        switchMode(AppMode.MAIN);


        String[] menuItems = {"Menu Item 1", "Menu Item 2"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an option")
                .setItems(menuItems, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        switch (which) {
                            case 0: // Menu Item 1 selected
                                // Your action here
                                break;
                            case 1: // Menu Item 2 selected
                                // Your action here
                                break;
                        }

                    }
                });


        ImageView imgRecord =  findViewById(R.id.imgRecord);
//        ImageView screenshotButton = findViewById(R.id.imgSceneList);

        // Sensor Registration
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> gravSensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        mSensor = gravSensors.get(0);

        ArrayAdapter<String> spinner_adapter = new ArrayAdapter<>(
                this, R.layout.mylist, styleDescriptions);

        if (Const.SHOW_RECORDER) {
            imgRecord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (capturingScreen) {
                        imgRecord.setImageResource(R.drawable.ic_baseline_videocam_24px);
                        stopRecording();
                        MediaActionSound m = new MediaActionSound();
                        m.play(MediaActionSound.STOP_VIDEO_RECORDING);
                    } else {
                        recordingInitiated = true;
                        MediaActionSound m = new MediaActionSound();
                        m.play(MediaActionSound.START_VIDEO_RECORDING);

                        imgRecord.setImageResource(R.drawable.ic_baseline_videocam_off_24px);
                        initRecorder();
                        shareScreen();
                    }
                    imgRecord.performHapticFeedback(
                            android.view.HapticFeedbackConstants.LONG_PRESS);
                }
            });

//            viewSceneList.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    Bitmap b = Screenshot.takescreenshotOfRootView(imgView);
//                    storeScreenshot(b,getOutputMediaFile(MEDIA_TYPE_IMAGE).getPath());
//                    viewSceneList.performHapticFeedback(
//                            android.view.HapticFeedbackConstants.LONG_PRESS);
//                    }
//            });
        } else if (!Const.STEREO_ENABLED){
            //this view doesn't exist when stereo is enabled (activity_stereo.xml)
            imgRecord.setVisibility(View.GONE);
            findViewById(R.id.imgSceneList).setVisibility(View.GONE);
        }

        if (Const.STEREO_ENABLED) {
            if (Const.ITERATE_STYLES) {
                // artificially start iteration since we don't display
                // any buttons in stereo view
                Const.ITERATION_STARTED = true;

                iterationHandler = new Handler();
                iterationHandler.postDelayed(styleIterator, 100);
            }
        } else {
            Spinner spinner = findViewById(R.id.spinner);
//            ImageView playPauseButton = findViewById(R.id.imgPlayPause);
//            ImageView camButton = findViewById(R.id.imgHelp);

            if (Const.ITERATE_STYLES) {
                viewPlayPause.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!Const.ITERATION_STARTED) {
                            Const.ITERATION_STARTED = true;
                            viewPlayPause.setImageResource(R.drawable.ic_pause);

                            Toast.makeText(
                                    viewPlayPause.getContext(),
                                    getString(R.string.iteration_started),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Const.ITERATION_STARTED = false;
                            viewPlayPause.setImageResource(R.drawable.ic_play);
                            Toast.makeText(
                                    viewPlayPause.getContext(),
                                    getString(R.string.iteration_stopped),
                                    Toast.LENGTH_LONG).show();
                        }
                        viewPlayPause.performHapticFeedback(
                                android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                });

                spinner.setVisibility(View.GONE);
                iterationHandler = new Handler();
                iterationHandler.postDelayed(styleIterator, 100);
            } else {
                viewPlayPause.setVisibility(View.GONE);


                // Spinner click listener
                spinner.setAdapter(spinner_adapter);
                spinner.setOnItemSelectedListener(this);

            }

            viewHelp.setVisibility(View.VISIBLE);
//            viewHelp.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    viewHelp.performHapticFeedback(
//                            android.view.HapticFeedbackConstants.LONG_PRESS);
//                    if (Const.USING_FRONT_CAMERA) {
//                        viewHelp.setImageResource(R.drawable.outline_info_24);
//                        help = true;
////                        cameraCapture = new CameraCapture(
////                                GabrielClientActivity.this, analyzer, Const.IMAGE_WIDTH,
////                                Const.IMAGE_HEIGHT, preview, CameraSelector.DEFAULT_BACK_CAMERA);
//
//                        Const.USING_FRONT_CAMERA = false;
//                    } else {
//                        viewHelp.setImageResource(R.drawable.baseline_close_24);
//                        help = true;
//
////                        cameraCapture = new CameraCapture(
////                                GabrielClientActivity.this, analyzer, Const.IMAGE_WIDTH,
////                                Const.IMAGE_HEIGHT, preview, CameraSelector.DEFAULT_FRONT_CAMERA);
//
//                        Const.USING_FRONT_CAMERA = true;
//                    }
//                }
//            });
        }

        viewSceneList.setHapticFeedbackEnabled(true);
        viewSceneList.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                builder.show();
//            }
            @Override
            public void onClick(View v) {
                // Create an ArrayAdapter
                ArrayAdapter<String> adapter = spinner_adapter;

                AlertDialog.Builder builder = new AlertDialog.Builder(GabrielClientActivity.this);
                builder.setTitle("Choose an option")
                        .setAdapter(adapter, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int position) {
                                if (styleIds.get(position).equals("none")) {
                                    if (!Const.STYLES_RETRIEVED) {
                                        styleType = "?";
                                    } else {
                                        styleType = "none";
                                    }
                                }
                                else {
                                    styleType = styleIds.get(position);
                                }
                            }
                        });

                AlertDialog dialog = builder.create();
                dialog.show();
//                viewSceneList.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING );
            }


        });


//        viewSceneList.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View view, MotionEvent event) {
//                switch (event.getAction()) {
//                    case MotionEvent.ACTION_DOWN:
//                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
//                        break;
//                    case MotionEvent.ACTION_UP:
//                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
//                        break;
//                }
//                return true;
//            }
//        });


        if (Const.SHOW_FPS) {
            findViewById(R.id.fpsLabel).setVisibility(View.VISIBLE);
            fpsHandler = new Handler();
            fpsHandler.postDelayed(fpsCalculator, 1000);
        }

        accelLabel.setVisibility(View.INVISIBLE);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mScreenHeight = metrics.heightPixels;
        mScreenWidth = metrics.widthPixels;



        mMediaRecorder = new MediaRecorder();

        mProjectionManager = (MediaProjectionManager) getSystemService
                (Context.MEDIA_PROJECTION_SERVICE);

//        FrameUpdateThread frameUpdateThread = new FrameUpdateThread();
//        frameUpdateThread.start();

//        running = true;
        frameHandler = new Handler();
//        frameHandler.post(frameIterator);
    }


    private final Runnable frameIterator = new Runnable() {
        @Override
        public void run() {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }
    };

    private Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            // This code will run every time a new frame is drawn
//            framesProcessed++;
            sendIMUCloudlet();
            // Repost frame callback for the next frame
            if (running) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    public void addFrameProcessed() {
        framesProcessed++;
        // Log.e("FRAME!!!!", "Frame is  " + framesProcessed);
    }

    private final Runnable fpsCalculator = new Runnable() {
        @Override
        public void run() {
            if (fpsLabel.getVisibility() == View.INVISIBLE) {
                fpsLabel.setVisibility(View.VISIBLE);

            }
            String msg= "FPS: " + framesProcessed;
            fpsLabel.setText( msg );

            framesProcessed = 0;
            fpsHandler.postDelayed(this, 1000);
        }
    };

    private void storeScreenshot(Bitmap bitmap, String path) {
        File imageFile = new File(path);

        try {
            MediaActionSound m = new MediaActionSound();
            m.play(MediaActionSound.SHUTTER_CLICK);
            OutputStream out = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(imageFile)));
            Toast.makeText(this, getString(R.string.screenshot_taken, path), Toast.LENGTH_LONG).show();
            out.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException when attempting to store screenshot", e);
        }
    }

    //FLAG FOR LATER


    private final Runnable styleIterator = new Runnable() {
        private int position = 1;

        @Override
        public void run() {
            if(Const.STYLES_RETRIEVED && Const.ITERATION_STARTED) {
                // wait until styles are retrieved before iterating
                if (++position == styleIds.size()) {
                    position = 1;
                }
                styleType = styleIds.get(position);

                if (runLocally) {
                    localRunnerThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                localRunner.load(getApplicationContext(),
                                        String.format("%s.pt", styleType));
                            } catch (FileNotFoundException e) {
                                styleType = "none";
                                AlertDialog.Builder builder = new AlertDialog.Builder(
                                        GabrielClientActivity.this,
                                        android.R.style.Theme_Material_Light_Dialog_Alert);
                                builder.setMessage("Style Not Found Locally")
                                        .setTitle("Failed to Load Style");
                                AlertDialog dialog = builder.create();
                                dialog.show();
                            }
                        }
                    });
                }
                Toast.makeText(getApplicationContext(), styleDescriptions.get(position),
                        Toast.LENGTH_SHORT).show();


                if (Const.DISPLAY_REFERENCE) {
                    iconView.setVisibility(View.VISIBLE);
                }
                if (imgView.getVisibility() == View.INVISIBLE) {
                    imgView.setVisibility(View.VISIBLE);
                }


                iterationHandler.postDelayed(this, 1000 * Const.ITERATE_INTERVAL);
            } else {
                iterationHandler.postDelayed(this, 100);
            }
        }
    };

    private boolean imuOn = false;
    void setIMUSensor(boolean on) {
        if (on) {
            if (!imuOn) {
                imuOn = true;
                sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else {
            if (imuOn) {
                imuOn = false;
                sensorManager.unregisterListener(this);
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.v(LOG_TAG, "++onResume");
        setIMUSensor(true);

        initOnce();
        Intent intent = getIntent();
        serverIP = intent.getStringExtra("SERVER_IP");
        initPerRun(serverIP);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(LOG_TAG, "++onPause");
        setIMUSensor(false);

        if(iterationHandler != null) {
            iterationHandler.removeCallbacks(styleIterator);
        }

        if(frameHandler != null) {
            running = false;
            frameHandler.removeCallbacks(frameIterator);
        }

        if(capturingScreen) {
            stopRecording();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(LOG_TAG, "++onDestroy");

        if (iterationHandler != null) {
            iterationHandler.removeCallbacks(styleIterator);
        }

        if(frameHandler != null) {
            running = false;
            frameHandler.removeCallbacks(frameIterator);
        }

        if (capturingScreen) {
            stopRecording();
        }

        if ((localRunnerThread != null) && (localRunnerThread.isAlive())) {
            localRunnerThread.quitSafely();
            localRunnerThread.interrupt();
            localRunnerThread = null;
            localRunnerThreadHandler = null;
        }
        if (rs != null) {
            rs.destroy();
        }

        if (this.openrtistComm != null) {
            this.openrtistComm.stop();
            this.openrtistComm = null;
        }
//        cameraCapture.shutdown();
    }

    /**
     * Creates a media file in the {@code Environment.DIRECTORY_PICTURES} directory. The directory
     * is persistent and available to other applications like gallery.
     *
     * @param type Media type. Can be video or image.
     * @return A file object pointing to the newly created file.
     */
    public  static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        if (!Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "OpenRTiST");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()){
            if (!mediaStorageDir.mkdirs()) {
                Log.d(LOG_TAG, "failed to create media directory");
                return null;
            }
        }

        // Create a media file name
        String pattern = "yyyyMMdd_HHmmss";
        String timeStamp = new SimpleDateFormat(pattern, Locale.US).format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath(), "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath(), "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() != RESULT_OK) {
                        Toast.makeText(GabrielClientActivity.this,
                                "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();

                        return;
                    }

                    mMediaProjection = mProjectionManager.getMediaProjection(
                            result.getResultCode(), result.getData());

                    mVirtualDisplay = createVirtualDisplay();
                    mMediaRecorder.start();
                    capturingScreen = true;
                    if (Const.ITERATE_STYLES) {
                        iterationHandler.postDelayed(styleIterator, 100 * Const.ITERATE_INTERVAL);
                    }
                }
            });

    private void shareScreen() {
        if (mMediaProjection == null) {
            activityResultLauncher.launch(mProjectionManager.createScreenCaptureIntent());
        }
    }

    private VirtualDisplay createVirtualDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        return mMediaProjection.createVirtualDisplay("MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(),
                null /* Callbacks */,
                null /* Handler */);
    }

    private void initRecorder() {
        try {
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mOutputPath = getOutputMediaFile(MEDIA_TYPE_VIDEO).getPath();
            mMediaRecorder.setOutputFile(mOutputPath);
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setVideoEncodingBitRate(BITRATE);
            mMediaRecorder.setVideoFrameRate(24);
            mMediaRecorder.prepare();

        } catch (IOException e) {
            Log.e(LOG_TAG, "Problem with recorder", e);
        }
    }

    private void stopRecording() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        Log.v(LOG_TAG, "Recording Stopped");
        Toast.makeText(this,
                getString(R.string.recording_complete, mOutputPath), Toast.LENGTH_LONG).show();
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(new File(mOutputPath))));
        mMediaProjection = null;
        stopScreenSharing();
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release(); //If used: mMediaRecorder object cannot
        // be reused again
        destroyMediaProjection();
        capturingScreen = false;
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {

            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(LOG_TAG, "MediaProjection Stopped");
    }

    /**
     * Does initialization for the entire application.
     */
    private void initOnce() {
        Log.v(LOG_TAG, "++initOnce");

        // Media controller
        if (mediaController == null) {
            mediaController = new MediaController(this);
        }
    }

    /**
     * Does initialization before each run (connecting to a specific server).
     */
    private void initPerRun(String serverIP) {
        Log.v(LOG_TAG, "++initPerRun");

        switchMode(currMode);

        // don't connect to cloudlet if running locally
        // if a mobile only run is specified
        if (runLocally) {
            if ((localRunnerThread != null) && (localRunnerThread.isAlive())) {
                localRunnerThread.quitSafely();
                localRunnerThread.interrupt();
            }
            localRunnerThread = new HandlerThread("LocalTransferThread");
            localRunnerThread.start();
            localRunnerThreadHandler = new Handler(localRunnerThread.getLooper());
            localRunnerBusy = false;
            return;
        }

        if (serverIP == null) return;

        this.setupComm();
        if (Const.STEREO_ENABLED) {
            preview = findViewById(R.id.camera_preview1);
        } else {
            preview = findViewById(R.id.camera_preview);
        }


        yuvToNV21Converter = new YuvToNV21Converter();
        yuvToJPEGConverter = new YuvToJPEGConverter(this);

        running = true;
        frameHandler.post(frameIterator);

//        cameraCapture = new CameraCapture(
//                this, analyzer, Const.IMAGE_WIDTH, Const.IMAGE_HEIGHT,
//                preview, CameraSelector.DEFAULT_BACK_CAMERA);
    }

    // Based on
    // https://github.com/protocolbuffers/protobuf/blob/2f6a7546e4539499bc08abc6900dc929782f5dcd/src/google/protobuf/compiler/java/java_message.cc#L1374
    private static Any pack(Extras extras) {
        return Any.newBuilder()
                .setTypeUrl("type.googleapis.com/openrtist.Extras")
                .setValue(extras.toByteString())
                .build();
    }

    int counter = 0;
    private void sendIMUCloudlet() {
        openrtistComm.sendSupplier(() -> {
            Extras.ScreenValue screenValue = Extras.ScreenValue.newBuilder()
                    .setHeight(mScreenHeight)
                    .setWidth(mScreenWidth)
                    .build();
//            counter++;
            Extras.IMUValue imuValue = Extras.IMUValue.newBuilder()
                    .setX(imu_x)
                    .setY(imu_y)
                    .setZ(imu_z)
                    .build();
            Extras.TouchInput touchValue = Extras.TouchInput.newBuilder()
                    .setX(sceneX / mScreenDensity)
                    .setY(sceneY / mScreenDensity)
                    .setScale(sceneScaleFactor)
                    .build();

            Extras.ArrowKey arrowKey = Extras.ArrowKey.newBuilder()
                    .setLeft(buttonLeft.isPressed())
                    .setRight(buttonRight.isPressed())
                    .setUp(buttonUp.isPressed())
                    .setDown(buttonDown.isPressed())
                    .build();

            int scene = 0;
            if (!(styleType.equals("?") || styleType.equals("none"))) {
                scene = Integer.parseInt(styleType);
            }
            scene = scene + 1;
            if (help) {
                scene = -scene;
                help = !help;
            }
            sceneX = 0;
            sceneY = 0;
//            sceneScaleFactor = 1.0f;

//            Extras extras = Extras.newBuilder().setStyle(styleType)
//                    .setScreenValue(screenValue)
//                    .setImuValue(imuValue)
//                    .build();

            Extras extras = Extras.newBuilder().setDepthThreshold(scene)
                    .setStyle(styleType)
                    .setScreenValue(screenValue)
                    .setImuValue(imuValue)
                    .setTouchValue(touchValue)
                    .setArrowKey(arrowKey)
                    .build();
            return InputFrame.newBuilder()
                    .setPayloadType(PayloadType.IMAGE)
                    // .addPayloads(yuvToJPEGConverter.convert(image))
                    .setExtras(GabrielClientActivity.pack(extras))
                    .build();
        });
    }

//    private void sendIMUCloudlet() {
//        Log.v(LOG_TAG, "++sendIMUCloudlet " + counter);
//        openrtistComm.sendSupplier(() -> {
//            Extras.IMUValue imuValue = Extras.IMUValue.newBuilder()
//                    .setX(imu_x)
//                    .setY(imu_y)
//                    .setZ(imu_z)
//                    .build();
//            counter++;
//            Extras extras = Extras.newBuilder().setStyle(styleType)
//                    .setImuValue(imuValue)
//                    .build();
//
//            return InputFrame.newBuilder()
//                    .setPayloadType(PayloadType.IMAGE)
//                    // .addPayloads(yuvToJPEGConverter.convert(image))
//                    .setExtras(GabrielClientActivity.pack(extras))
//                    .build();
//        });
//    }

//    private void sendFrameCloudlet(@NonNull ImageProxy image) {
//        openrtistComm.sendSupplier(() -> {
//            Extras.IMUValue imuValue = Extras.IMUValue.newBuilder()
//                    .setX(imu_x)
//                    .setY(imu_y)
//                    .setZ(imu_z)
//                    .build();
//
//            Extras extras = Extras.newBuilder().setStyle(styleType)
//                    .setImuValue(imuValue)
//                    .build();
//
//            return InputFrame.newBuilder()
//                    .setPayloadType(PayloadType.IMAGE)
//                    // .addPayloads(yuvToJPEGConverter.convert(image))
//                    .setExtras(GabrielClientActivity.pack(extras))
//                    .build();
//        });
//    }

//    frameCallback = new Choreographer.FrameCallback() {
//        @Override
//        public void doFrame(long frameTimeNanos) {
//            // This code will run every time a new frame is drawn
//
//            // Repost frame callback for the next frame
//            if (isRunning) {
//                Choreographer.getInstance().postFrameCallback(this);
//            }
//        }
//    };

    final private ImageAnalysis.Analyzer analyzer = new ImageAnalysis.Analyzer() {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            if (styleType.equals("?") || !styleType.equals("none")) {
                if (runLocally && !styleType.equals("?")) {

                } else if (GabrielClientActivity.this.openrtistComm != null) {
//                    sendFrameCloudlet(image);
                } else {
                    runOnUiThread(() -> imgView.setVisibility(View.VISIBLE));
                }
            } else if (!cleared) {
                Log.v(LOG_TAG, "Display Cleared");
                runOnUiThread(() -> imgView.setVisibility(View.INVISIBLE));
                cleared = true;
            }
            image.close();
        }
    };

    int getPort() {
        Log.d(LOG_TAG, this.serverIP);
        int port = URI.create(this.serverIP).getPort();
        if (port == -1) {
            return Const.PORT;
        }
        return port;
    }

    void setupComm() {
        int port = getPort();
        Consumer<ByteString> imageViewUpdater = new ImageViewUpdater(this.imgView);
        this.openrtistComm = OpenrtistComm.createOpenrtistComm(
                this.serverIP, port, this, this.iconView, imageViewUpdater, Const.TOKEN_LIMIT);
    }

    // Used by measurement build variant
    void setOpenrtistComm(OpenrtistComm openrtistComm) {
        this.openrtistComm = openrtistComm;
    }

    public void showNetworkErrorMessage(String message) {
        // suppress this error when screen recording as we have to temporarily leave this
        // activity causing a network disruption
        if (!recordingInitiated) {
            this.runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        this, android.R.style.Theme_Material_Light_Dialog_Alert);
                builder.setMessage(message)
                        .setTitle(R.string.connection_error)
                        .setNegativeButton(R.string.back_button,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        GabrielClientActivity.this.finish();
                                    }
                                }).setCancelable(false);
                AlertDialog dialog = builder.create();
                dialog.show();
            });
        }
    }

    // **************** onItemSelected ***********************

    // Performing action onItemSelected and onNothing selected
    @Override
    public void onItemSelected(AdapterView<?> arg0, View arg1, int position,long id) {
        if (styleIds.get(position).equals("none")) {
            if (!Const.STYLES_RETRIEVED) {
                styleType = "?";
            } else {
                styleType = "none";
            }
        }
        else {
            styleType = styleIds.get(position);
        }
        if (imgView.getVisibility() == View.INVISIBLE) {
            imgView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }

    // **************** End of onItemSelected ****************
}
