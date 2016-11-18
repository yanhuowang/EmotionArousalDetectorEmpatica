package com.empatica.sample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

public class BaselineActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long STREAMING_TIME = 50000; // Stops streaming 10 seconds after connection

    private static final String EMPATICA_API_KEY = "f1cb320f9ae94325a9651068f68b01ec"; // TODO insert your API Key here

    private final int EDA_CHANGED = 1;

    private EmpaDeviceManager deviceManager;


    private TextView edaLabel;

    private TextView statusLabel;
    private TextView deviceNameLabel;
    private TableRow dataCnt;

    private ImageView edaImage;
    private SeekBar edaSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (TableRow) findViewById(R.id.dataArea);


        edaLabel = (TextView) findViewById(R.id.eda);


        deviceNameLabel = (TextView) findViewById(R.id.deviceName);

        edaImage = (ImageView) findViewById(R.id.eda_image);
        edaSeekBar = (SeekBar) findViewById(R.id.eda_seekBar);

        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);

        edaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) edaImage.getLayoutParams();
                params.width = progress * 8;
                params.height = progress * 8;
                edaImage.setLayoutParams(params);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        deviceManager.stopScanning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deviceManager.cleanUp();
    }

    private Handler mHandler = new Handler() {

        // handle the messages from other threads
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            int what = bundle.getInt("what");
            float value = bundle.getFloat("value");
            int progress = (int)(value / 2 * 100);
            switch (what) {
                case EDA_CHANGED:
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) edaImage.getLayoutParams();
                    params.width = progress * 8;
                    params.height = progress * 8;
                    edaImage.setLayoutParams(params);
                    break;
            }
        };
    };

    @Override
    public void didDiscoverDevice(BluetoothDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(BaselineActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(EmpaSensorStatus status, EmpaSensorType type) {
        // No need to implement this right now
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
        // The device manager has established a connection
        } else if (status == EmpaStatus.CONNECTED) {
            // Stop streaming after STREAMING_TIME
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataCnt.setVisibility(View.VISIBLE);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Disconnect device
                            deviceManager.disconnect();
                        }
                    }, STREAMING_TIME);
                }
            });
        // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {

    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {

    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {

    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        updateLabel(edaLabel, "" + gsr);
        // send message to UI thread
        Message message = new Message();
        Bundle bundle = new Bundle();
        bundle.putInt("what", EDA_CHANGED);
        bundle.putFloat("value", gsr);
        message.setData(bundle);
        mHandler.sendMessage(message);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {

    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }
}
