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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.renderscript.RenderScript;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Looper;
import android.view.Choreographer;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

//import android.hardware.SensorManager;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

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
import edu.cmu.cs.gabriel.client.comm.ServerComm;
import edu.cmu.cs.gabriel.client.results.ErrorType;
import edu.cmu.cs.gabriel.network.OpenrtistComm;
import edu.cmu.cs.gabriel.network.StereoViewUpdater;
import edu.cmu.cs.gabriel.protocol.Protos.InputFrame;
import edu.cmu.cs.gabriel.protocol.Protos.PayloadType;
import edu.cmu.cs.gabriel.util.Screenshot;
import edu.cmu.cs.localtransfer.LocalTransfer;
import edu.cmu.cs.localtransfer.Utils;
import edu.cmu.cs.openrtist.Protos.Extras;
import edu.cmu.cs.openrtist.R;

public class GabrielClientActivity extends AppCompatActivity implements
        AdapterView.OnItemSelectedListener, SensorEventListener {

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
    private Handler iterationHandler;
    private Handler frameHandler;
    private Handler fpsHandler;
    private TextView fpsLabel;
    private PreviewView preview;

    private TextView accelLabel;

    // Stereo views
    private ImageView stereoView1;
    private ImageView stereoView2;

    private boolean cleared = false;

    private int framesProcessed = 0;
    private YuvToNV21Converter yuvToNV21Converter;
    private YuvToJPEGConverter yuvToJPEGConverter;
    private CameraCapture cameraCapture;

    private final List<String> styleDescriptions = new ArrayList<>(
            Collections.singletonList("Clear Display"));


    private final List<String> styleIds = new ArrayList<>(Collections.singletonList("none"));

    public void addStyles(Set<Map.Entry<String, String>> entrySet) {
        this.styleType = "none";
        for (Map.Entry<String, String> entry : entrySet) {
            Log.v(LOG_TAG, "style: " + entry.getKey() + ", desc: " + entry.getValue());
            styleDescriptions.add(entry.getValue());
            styleIds.add(entry.getKey());
        }
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

    public void setScaleFactor(float factor){
        sceneScaleFactor = factor;
    }

    public void setXY(float x, float y){
        sceneX = x;
        sceneY = y;
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

        imgView.setOnTouchListener(new SceneScaleGestures(this, this));

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

        stereoView1 = findViewById(R.id.guidance_image1);
        stereoView2 = findViewById(R.id.guidance_image2);

        ImageView imgRecord =  findViewById(R.id.imgRecord);
        ImageView screenshotButton = findViewById(R.id.imgScreenshot);

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

//            screenshotButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    Bitmap b = Screenshot.takescreenshotOfRootView(imgView);
//                    storeScreenshot(b,getOutputMediaFile(MEDIA_TYPE_IMAGE).getPath());
//                    screenshotButton.performHapticFeedback(
//                            android.view.HapticFeedbackConstants.LONG_PRESS);
//                    }
//            });
        } else if (!Const.STEREO_ENABLED){
            //this view doesn't exist when stereo is enabled (activity_stereo.xml)
            imgRecord.setVisibility(View.GONE);
            findViewById(R.id.imgScreenshot).setVisibility(View.GONE);
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
            ImageView playPauseButton = findViewById(R.id.imgPlayPause);
            ImageView camButton = findViewById(R.id.imgSwitchCam);

            if (Const.ITERATE_STYLES) {
                playPauseButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!Const.ITERATION_STARTED) {
                            Const.ITERATION_STARTED = true;
                            playPauseButton.setImageResource(R.drawable.ic_pause);

                            Toast.makeText(
                                    playPauseButton.getContext(),
                                    getString(R.string.iteration_started),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Const.ITERATION_STARTED = false;
                            playPauseButton.setImageResource(R.drawable.ic_play);
                            Toast.makeText(
                                    playPauseButton.getContext(),
                                    getString(R.string.iteration_stopped),
                                    Toast.LENGTH_LONG).show();
                        }
                        playPauseButton.performHapticFeedback(
                                android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                });

                spinner.setVisibility(View.GONE);
                iterationHandler = new Handler();
                iterationHandler.postDelayed(styleIterator, 100);
            } else {
                playPauseButton.setVisibility(View.GONE);


                // Spinner click listener
                spinner.setOnItemSelectedListener(this);
                spinner.setAdapter(spinner_adapter);
            }

            camButton.setVisibility(View.VISIBLE);
            camButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    camButton.performHapticFeedback(
                            android.view.HapticFeedbackConstants.LONG_PRESS);
                    if (Const.USING_FRONT_CAMERA) {
                        camButton.setImageResource(R.drawable.ic_baseline_camera_front_24px);
                        help = true;
//                        cameraCapture = new CameraCapture(
//                                GabrielClientActivity.this, analyzer, Const.IMAGE_WIDTH,
//                                Const.IMAGE_HEIGHT, preview, CameraSelector.DEFAULT_BACK_CAMERA);

                        Const.USING_FRONT_CAMERA = false;
                    } else {
                        camButton.setImageResource(R.drawable.ic_baseline_camera_rear_24px);
                        help = true;

//                        cameraCapture = new CameraCapture(
//                                GabrielClientActivity.this, analyzer, Const.IMAGE_WIDTH,
//                                Const.IMAGE_HEIGHT, preview, CameraSelector.DEFAULT_FRONT_CAMERA);

                        Const.USING_FRONT_CAMERA = true;
                    }
                }
            });
        }

        screenshotButton.setOnClickListener(new View.OnClickListener() {
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
                            public void onClick(DialogInterface dialog, int which) {
                                String selectedItem = adapter.getItem(which);
                                // Do something with the selected item
                            }
                        });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });


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

        running = true;
        frameHandler = new Handler();
        frameHandler.post(frameIterator);
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

                if (Const.STEREO_ENABLED) {
                    if (stereoView1.getVisibility() == View.INVISIBLE) {
                        stereoView1.setVisibility(View.VISIBLE);
                        stereoView2.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (Const.DISPLAY_REFERENCE) {
                        iconView.setVisibility(View.VISIBLE);
                    }
                    if (imgView.getVisibility() == View.INVISIBLE) {
                        imgView.setVisibility(View.VISIBLE);
                    }
                }

                iterationHandler.postDelayed(this, 1000 * Const.ITERATE_INTERVAL);
            } else {
                iterationHandler.postDelayed(this, 100);
            }
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        Log.v(LOG_TAG, "++onResume");

        sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);

        initOnce();
        Intent intent = getIntent();
        serverIP = intent.getStringExtra("SERVER_IP");
        initPerRun(serverIP);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(LOG_TAG, "++onPause");
        sensorManager.unregisterListener(this);


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

//            Extras extras = Extras.newBuilder().setStyle(styleType)
//                    .setScreenValue(screenValue)
//                    .setImuValue(imuValue)
//                    .build();

            Extras extras = Extras.newBuilder().setDepthThreshold(scene)
                    .setStyle(styleType)
                    .setScreenValue(screenValue)
                    .setImuValue(imuValue)
                    .setTouchValue(touchValue)
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
                }
                if (Const.STEREO_ENABLED) {
                    runOnUiThread(() -> {
                        stereoView1.setVisibility(View.VISIBLE);
                        stereoView2.setVisibility(View.VISIBLE);
                    });
                } else {
                    runOnUiThread(() -> imgView.setVisibility(View.VISIBLE));
                }
            } else if (!cleared) {
                Log.v(LOG_TAG, "Display Cleared");

                if (Const.STEREO_ENABLED) {
                    runOnUiThread(() -> {
                        stereoView1.setVisibility(View.INVISIBLE);
                        stereoView2.setVisibility(View.INVISIBLE);
                    });
                } else {
                    runOnUiThread(() -> imgView.setVisibility(View.INVISIBLE));
                }
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

        Consumer<ByteString> imageViewUpdater = Const.STEREO_ENABLED
                ? new StereoViewUpdater(stereoView1, stereoView2)
                : new ImageViewUpdater(this.imgView);
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
