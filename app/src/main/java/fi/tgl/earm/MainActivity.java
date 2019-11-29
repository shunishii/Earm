package fi.tgl.earm;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import io.esense.esenselib.*;

public class MainActivity extends Activity implements ESenseConnectionListener, ESenseSensorListener, SensorEventListener {

    private static final String TAG = "MainActivity";
    private static final String DeviceName = "eSense-0056";
    private boolean isMeasuring;
    private int id;
    private long startTime;
    private long progressTime;
    private ArrayList<Long> eSenseTimeData;
    private ArrayList<Long> eSenseCurrentTimeData;
    private ArrayList<ArrayList<Short>> eSenseData;
    private ArrayList<ArrayList<Float>> phoneData;
    private ArrayList<Long> phoneTimeData;
    private ArrayList<Long> phoneCurrentTimeData;
    private TextView statusText;
    private EditText idText;
    private Button startButton;
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        idText = findViewById(R.id.id_Text);
        isMeasuring = false;
        statusText = findViewById(R.id.Status);
        startButton = findViewById(R.id.StartButton);
        statusText.setText("Finding eSense...");

        SensorManager phoneManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor acc = phoneManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        phoneManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_FASTEST);

        ESenseManager eSenseManager = new ESenseManager(DeviceName, this,this);
        eSenseManager.connect(5000);
    }

    @Override
    public void onDeviceFound(ESenseManager eSenseManager) {
        Log.d(TAG, "onDeviceFound");
        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("Device found.\n\nConnecting...");
                    }
                });
            }
        }).start();
    }

    @Override
    public void onDeviceNotFound(ESenseManager eSenseManager) {
        Log.d(TAG, "onDeviceNotFound");
        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("Device not found.\n\nCheck bluetooth status and restart app.");
                    }
                });
            }
        }).start();
    }

    @Override
    public void onConnected(ESenseManager eSenseManager) {
        Log.d(TAG, "onConnected");
        eSenseManager.registerSensorListener(this,52);
        new Thread(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("eSense connected.");
                    }
                });
            }
        }).start();
    }

    @Override
    public void onDisconnected(ESenseManager eSenseManager) {
        Log.d(TAG, "onDisconnected");
    }

    @Override
    public void onSensorChanged(ESenseEvent eSenseEvent) {
        //Log.d(TAG, "onSensorChanged");
        short[] val = eSenseEvent.getAccel();
        Log.d(TAG, "onSensorChanged: " + val[0] + ", " + val[1] + ", " + val[2]);
        if (isMeasuring) {
            for (int i = 0; i < 3; i++) {
                eSenseData.get(i).add(val[i]);
            }
            progressTime = System.nanoTime() - startTime;
            eSenseTimeData.add(progressTime);
            eSenseCurrentTimeData.add(System.currentTimeMillis());
        }
    }

    public void onClickButton(View v) {
        Log.d(TAG, "onClick");
        if (isMeasuring){
            isMeasuring = false;
            startButton.setText(R.string.start_button_text);
            statusText.setText("Writing...");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    OutputFile();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Tap to start");
                        }
                    });
                }
            }).start();
        }
        else {
            if (idText.getText().toString().equals("")) {
                Toast.makeText(this, "Input you ID", Toast.LENGTH_SHORT).show();
            }
            else {
                isMeasuring = true;
                startButton.setText(R.string.stop_button_text);
                statusText.setText("Measuring...");
                eSenseData = new ArrayList<>();
                eSenseTimeData = new ArrayList<>();
                eSenseCurrentTimeData = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    ArrayList<Short> arr = new ArrayList<>();
                    eSenseData.add(arr);
                }
                phoneData = new ArrayList<>();
                phoneTimeData = new ArrayList<>();
                phoneCurrentTimeData = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    ArrayList<Float> arr = new ArrayList<>();
                    phoneData.add(arr);
                }
                id = Integer.parseInt(idText.getText().toString());
                startTime = System.nanoTime();
            }
        }
    }

    private void OutputFile() {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_kkmmss");
        String filename = "eSense_" + String.format("%03d", id) + "_" + sdf.format(date) + ".csv";
        Log.d(TAG, filename);
        try {
            FileOutputStream fout = openFileOutput(filename, MODE_PRIVATE);
            String comma = ",";
            String newline = "\n";
            for (int i = 0; i < eSenseData.get(0).size(); i++) {
                for (int j = 0; j < 3; j++)
                {
                    fout.write(String.valueOf((float)eSenseData.get(j).get(i)/1000f).getBytes());
                    fout.write(comma.getBytes());
                }
                fout.write(String.format("%.6f", Float.parseFloat(eSenseTimeData.get(i).toString())/1000000000f).getBytes());
                fout.write(comma.getBytes());
                fout.write(String.valueOf(eSenseCurrentTimeData.get(i)).getBytes());
                fout.write(newline.getBytes());
            }
            fout.close();
            //statusText.setText("Tap to start");
            Log.d(TAG, "File created.");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Cannot open file.");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "Cannot write string.");
            e.printStackTrace();
        }

        filename = "Phone_" + String.format("%03d", id) + "_" + sdf.format(date) + ".csv";
        Log.d(TAG, filename);
        try {
            FileOutputStream fout = openFileOutput(filename, MODE_PRIVATE);
            String comma = ",";
            String newline = "\n";
            for (int i = 0; i < phoneData.get(0).size(); i++) {
                for (int j = 0; j < 3; j++)
                {
                    fout.write(String.valueOf(phoneData.get(j).get(i)).getBytes());
                    fout.write(comma.getBytes());
                }
                fout.write(String.format("%.6f", Float.parseFloat(phoneTimeData.get(i).toString())/1000000000f).getBytes());
                fout.write(comma.getBytes());
                fout.write(String.valueOf(phoneCurrentTimeData.get(i)).getBytes());
                fout.write(newline.getBytes());
            }
            fout.close();
            Log.d(TAG, "File created.");
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Cannot open file.");
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "Cannot write string.");
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isMeasuring) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            for (int i = 0; i < 3; i++) {
                phoneData.get(i).add(event.values[i]);
            }
            progressTime = System.nanoTime() - startTime;
            phoneTimeData.add(progressTime);
            phoneCurrentTimeData.add(System.currentTimeMillis());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
