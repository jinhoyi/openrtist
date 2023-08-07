package edu.cmu.cs.openfluid;

import android.content.Context;

import java.util.ArrayList;

import edu.cmu.cs.gabriel.MeasurementClientActivity;

public class MeasurementServerListAdapter extends ServerListAdapter {
    public MeasurementServerListAdapter(Context context, ArrayList<Server> modelList) {
        super(context, modelList);
    }

    @Override
    Class<?> gabrielClientActivityClass() {
        return MeasurementClientActivity.class;
    }
}