package edu.cmu.cs.openrtist;

import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.fragment.app.DialogFragment;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

public class MenuDialogFragment extends DialogFragment {

    public static MenuDialogFragment newInstance() {
        return new MenuDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_menu, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        Window window = getDialog().getWindow();
        if (window != null) {
            Point size = new Point();
            Display display = window.getWindowManager().getDefaultDisplay();
            display.getSize(size);
            int width = size.x;
            int height = size.y;
            window.setLayout((int) (width * 0.75), (int) (height * 0.75));
            window.setGravity(Gravity.CENTER);
        }
    }
}