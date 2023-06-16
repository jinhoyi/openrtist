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
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;
import android.widget.ImageView;
import androidx.appcompat.widget.Toolbar;
import android.os.Bundle;
import android.Manifest;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.content.Context;
import android.hardware.camera2.CameraManager;

import android.hardware.SensorManager;
import android.hardware.Sensor;

import org.w3c.dom.Text;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.client.socket.SocketWrapper;
import edu.cmu.cs.gabriel.serverlist.Server;
import edu.cmu.cs.gabriel.serverlist.ServerListFragment;

public class ServerListActivity extends AppCompatActivity implements SensorEventListener {
      CameraManager camMan = null;
    private SharedPreferences mSharedPreferences;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 23;

    private SensorManager sensorManager;
    private Sensor mSensor;

    void loadPref(SharedPreferences sharedPreferences, String key) {
        Const.loadPref(sharedPreferences, key);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
//        float lux = event.values[0];
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z);

        String updateText = String.format("Acc: (x,y,z) = (%.4f, %.4f, %.4f) = %.4f", x, y, z, currentAcceleration);
//        String updateText = "Acceleration: (x,y,z) = (" + Float.toString(x) + ", " + Float.toString(y) +
        // Do something with this sensor value.
        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText(updateText);
    }

    //activity menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.about:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setMessage(this.getString(R.string.about_message, BuildConfig.VERSION_NAME))
                        .setTitle(R.string.about_title);
                AlertDialog dialog = builder.create();
                dialog.show();
                return true;
            case R.id.settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //intent.putExtra("", faceTable);
                this.startActivity(intent);
                return true;
            default:
                return false;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermission();
        
        setContentView(R.layout.activity_serverlist);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        String pkg = getApplicationContext().getPackageName();



        Fragment fragment =  new ServerListFragment(pkg,pkg+ ".GabrielClientActivity");
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_serverlist, fragment)
                .commitNow();

        mSharedPreferences=PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, ?> m = mSharedPreferences.getAll();
        for(Map.Entry<String,?> entry : m.entrySet()){
            Log.d("SharedPreferences",entry.getKey() + ": " +
                    entry.getValue().toString());
            this.loadPref(mSharedPreferences, entry.getKey());

        }
        camMan = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        //Display list of sensors:
        String sensorsList = "";
        for(Sensor sensorItem: deviceSensors) {
            sensorsList = sensorsList + sensorItem.getName() + "\n";
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            List<Sensor> gravSensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
            for (Sensor sensorItem: gravSensors) {
                Toast.makeText(
                        this,
                        sensorItem.getName() + "Version = " + Integer.toString(sensorItem.getVersion()),
                        Toast.LENGTH_LONG).show();
                mSensor = sensorItem;
//                Log.d("Gravity Sensor", "Version = " + Integer.toString(sensorItem.getVersion()));
//                if (sensorItem.getVersion() == 3){
//                    mSensor = sensorItem;
//
//                }
            }
        }

//        Toast.makeText(
//                this,
//                sensorsList,
//                Toast.LENGTH_LONG).show();

        Log.d("Devices",sensorsList);

    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    void requestPermissionHelper(String permissions[]) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            ActivityCompat.requestPermissions(this,
                    permissions,
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    void requestPermission() {
        String permissions[] = {Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        this.requestPermissionHelper(permissions);
    }

}
