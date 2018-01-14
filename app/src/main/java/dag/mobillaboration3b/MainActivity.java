package dag.mobillaboration3b;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;

import dag.mobillaboration3b.Model.DeviceService;

/**
 * A example on how to use the Android BLE API to connect to a BLE device, in this case
 * a BBC Micro:bit, and read some accelerometer data via notifications.
 * The actual manipulation of the sensors services and characteristics is performed in the
 * DeviceActivity class.
 * NB! This example only aims to demonstrate the basic functionality in the Android BLE API.
 * Checks for life cycle connectivity, correct service, nulls et c. is not fully implemented.
 * <p/>
 * More elaborate example on Android BLE:
 * http://developer.android.com/guide/topics/connectivity/bluetooth-le.html
 * Documentation on the BBC Micro:bit:
 * https://lancaster-university.github.io/microbit-docs/ble/profile/
 */
public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_ENABLE_BT = 1000;
    public static final int REQUEST_ACCESS_LOCATION = 1001;
    // period for scan, 5000 ms
    private static final long SCAN_PERIOD = 5000;

    SharedPreferences shareprefs;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private boolean mScanning;
    private Handler mHandler;
    private Intent serviceIntent;
    private ListView mScanListView;
    private ProgressBar progressBar;
    private ArrayList<BluetoothDevice> mDeviceList;
    private BTDeviceArrayAdapter mAdapter;

    private void toggleDeviceScan(boolean scanning) {
        if(!scanning){
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            progressBar.setVisibility(View.INVISIBLE);
        }else{
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mScanning == true) {
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        progressBar.setVisibility(View.INVISIBLE);
                    }
                }
            }, SCAN_PERIOD);
            mScanning=true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            progressBar.setVisibility(View.VISIBLE);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String name = device.getName();
                            if (name != null && name.contains("BBC micro:bit")&& !mDeviceList.contains(device)) {
                                mDeviceList.add(device);
                                mAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        mScanListView = (ListView) findViewById(R.id.scanListView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar2);
        mDeviceList = new ArrayList<BluetoothDevice>();
        mAdapter = new BTDeviceArrayAdapter(this, mDeviceList);
        mScanListView.setAdapter(mAdapter);
        mScanListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                ConnectedDevice.setInstance(mDeviceList.get(position));
                if(Integer.parseInt(shareprefs.getString("type","1"))==1) {
                    Intent intent = new Intent(MainActivity.this, DeviceActivity.class);
                    startActivity(intent);
                }else {
                    try {
                        stopService(serviceIntent);
                    }catch(Exception e){
                    }
                    serviceIntent = new Intent(getApplicationContext(), DeviceService.class);
                    startService(serviceIntent);
                    Toast.makeText(getApplicationContext(), "Service started", Toast.LENGTH_SHORT).show();
                }
            }
        });
        shareprefs = PreferenceManager.getDefaultSharedPreferences(this);
        String s = shareprefs.getString("ip","0.0.0.0");
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }
    // callback for ActivityCompat.requestPermissions
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.scan_menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem){
        switch(menuItem.getItemId()){
            case(R.id.scan):mDeviceList.clear();
            toggleDeviceScan(true);
            return true;
            case R.id.preference:
                serviceIntent = new Intent(getApplicationContext(), PreferencesActivity.class);
                startActivity(serviceIntent);
                return true;
            case R.id.stopService: stopService(serviceIntent);
            Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
            return true;
            default:return false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_ACCESS_LOCATION);
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        toggleDeviceScan(false);
    }

}
