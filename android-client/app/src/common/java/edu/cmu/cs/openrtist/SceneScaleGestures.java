package edu.cmu.cs.openrtist;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.google.protobuf.ByteString;

import java.util.function.Consumer;

import edu.cmu.cs.gabriel.camera.ImageViewUpdater;
// https://stackoverflow.com/questions/5790503/can-we-use-scale-gesture-detector-for-pinch-zoom-in-android

public class SceneScaleGestures implements View.OnTouchListener, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener {
    private View view;
    private GestureDetector gesture;
    private ScaleGestureDetector gestureScale;
    private float scaleFactor = 1.0f;
    private boolean inScale = false;
    private boolean doubleTab = false;
    private final GabrielClientActivity gabrielClientActivity;


    public SceneScaleGestures (Context c, GabrielClientActivity gabrielClientActivity){
        gesture = new GestureDetector(c, this);
        gestureScale = new ScaleGestureDetector(c, this);
        this.gabrielClientActivity = gabrielClientActivity;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        this.view = view;
        gesture.onTouchEvent(event);
        gestureScale.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, float x, float y) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent event) {
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        scaleFactor = detector.getScaleFactor();
//        scaleFactor = (Math.max(scaleFactor, 0.8f)); // prevent our view from becoming too small //
        scaleFactor = ((float)((int)(scaleFactor * 1000))) / 1000; // Change precision to help with jitter when user just rests their fingers //
        gabrielClientActivity.setScaleFactor(scaleFactor);
//        view.setScaleX(scaleFactor);
//        view.setScaleY(scaleFactor);
        onScroll(null, null, 0, 0);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        inScale = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        inScale = false;
        scaleFactor = 1.0f;
        gabrielClientActivity.setScaleFactor(scaleFactor);
        onScroll(null, null, 0, 0);
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float x, float y) {
        if (x == 0 && y == 0) {
            gabrielClientActivity.setXY(0, 0);
            return true;
        }

        if (Math.abs(x) > Math.abs(y)) {
            gabrielClientActivity.setXY(x, 0);
        } else {
            gabrielClientActivity.setXY(0, y);
        }

//        if (event1.getAction() == MotionEvent.ACTION_UP || event2.getAction() == MotionEvent.ACTION_UP ) {
//            // The pointer has gone up, ending the gesture
//            gabrielClientActivity.setXY(0, 0);
//        }
//
//        if (event1.getAction() == MotionEvent.ACTION_CANCEL || event2.getAction() == MotionEvent.ACTION_CANCEL) {
//            // The pointer has gone up, ending the gesture
//            gabrielClientActivity.setXY(0, 0);
//        }

        return true;
    }

    @Override
    public void onShowPress(MotionEvent event) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
        doubleTab = !doubleTab;
        if (doubleTab) {
            scaleFactor = 1.8f;
        } else {
            scaleFactor = 1.0f;
        }
        gabrielClientActivity.setScaleFactor(scaleFactor);
//        view.setScaleX(scaleFactor);
//        view.setScaleY(scaleFactor);
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        return true;
    }
}