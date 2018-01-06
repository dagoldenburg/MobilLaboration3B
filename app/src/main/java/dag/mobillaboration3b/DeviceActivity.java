package dag.mobillaboration3b;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * This is where we manage the BLE device and the corresponding services, characteristics et c.
 * <p>
 * NB: In this simple example there is no other way to turn off notifications than to
 * leave the activity (the BluetoothGatt is disconnected and closed in activity.onStop).
 */
public class DeviceActivity extends AppCompatActivity {

    /**
     * Documentation on UUID:s and such for services on a BBC Micro:bit.
     * Characteristics et c. are found at
     * https://lancaster-university.github.io/microbit-docs/resources/bluetooth/bluetooth_profile.html
     */
    // UART service and characteristics
    public static UUID UARTSERVICE_SERVICE_UUID =
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID UART_RX_CHARACTERISTIC_UUID = // receiver on Micro:bit
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID UART_TX_CHARACTERISTIC_UUID = // transmitter on Micro:bit
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    // UUID for the UART BTLE client characteristic which is necessary for notifications
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private boolean stop = false;
    private LinkedList<Beat> beats = new LinkedList<>();
    private LinkedList<Beat> rBeats = new LinkedList<>();
    private BluetoothDevice mConnectedDevice = null;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothGattService mUartService = null;
    private long dataCount;
    private Handler mHandler;
    private StringBuilder sendData = new StringBuilder();

    @Override
    protected void onStart() {
        super.onStart();
        start();
        new HttpTaskReset(getApplicationContext()).execute();
    }

    private void start(){
        mConnectedDevice = ConnectedDevice.getInstance();
        if (mConnectedDevice != null) {
            connect();
        }
        startGraph();
    }

    private void stop(){
        beats.clear();
        rBeats.clear();
        Log.i("onStop", "-");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            Log.i("onStop", "gatt closed");
            mConnectedDevice = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stop();
        mHandler.post(new Runnable() {
            public void run() {
                mDataView.setText("Disconnected");
            }
        });
    }

    private void connect() {
        if (mConnectedDevice != null) {
            mBluetoothGatt = mConnectedDevice.connectGatt(this, false, mBtGattCallback);
            Log.i("connect", "connectGatt called");
        }
    }

    private BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt;
                gatt.discoverServices();
                dataCount=0;
                mHandler.post(new Runnable() {
                    public void run() {
                        mDataView.setText("Connected");
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mBluetoothGatt = null;
                mHandler.post(new Runnable() {
                    public void run() {
                        mDataView.setText("Disconnected");
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    String uuid = service.getUuid().toString();
                    Log.i("service", uuid);
                }

                mUartService = gatt.getService(UARTSERVICE_SERVICE_UUID);
                // debug, list characteristics for detected service
                if (mUartService != null) {
                    List<BluetoothGattCharacteristic> characteristics =
                            mUartService.getCharacteristics();
                    for (BluetoothGattCharacteristic chara : characteristics) {
                        Log.i("characteristic", chara.getUuid().toString());
                    }

                    /*
                     * Enable notifications on UART data
                     * First: call setCharacteristicNotification
                     */
                    BluetoothGattCharacteristic rx =
                            mUartService.getCharacteristic(UART_RX_CHARACTERISTIC_UUID);
                    boolean success = gatt.setCharacteristicNotification(rx, true);
                    if (success) {
                        gatt.readCharacteristic(rx);
                        Log.i("setCharactNotification", "success");
                        /*
                         * Second: set enable notification
                         * (why isn't this done by setCharacteristicNotification - a flaw in the API?)
                         */
                        BluetoothGattDescriptor descriptor =
                                rx.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        gatt.writeDescriptor(descriptor); // callback: onDescriptorWrite
                    } else {
                        Log.i("setCharactNotification", "failed");
                    }
                } else {
                    mHandler.post(new Runnable() {
                        public void run() {
                            showToast("UART RX characteristic not found");
                        }
                    });
                }
            }
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor
                descriptor, int status) {
        }

        final int arrSize=4,statArrSize=8;
        int[] array = new int[arrSize];
        int[] staticCheck = new int[statArrSize];
        int i,j,oldAvg,average,bpmTemp;
        long showtime,bpm;
        boolean rising,calibrating=false;
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic) {
            if(calibrating){
                return;
            }
            Log.i("onCharacteristicChanged", characteristic.getUuid().toString());
            BluetoothGattCharacteristic rx =
                    mUartService.getCharacteristic(UART_RX_CHARACTERISTIC_UUID);
            String data = rx.getStringValue(0);
            Log.i("data as string", data);
            final int raw = Integer.parseInt(data);
            array[i++] = raw;
            if (i == arrSize) {
                i = 0;
                oldAvg = average;
                average = 0;
                for (int index = 0; index < array.length; index++) {
                    average += array[index];
                }
                average = average / arrSize;
            }
            staticCheck[j++]=raw;

            if(j>=statArrSize){
                j=0;
                if(checkForStatic()){
                    stop();
                    calibrating=true;
                    mHandler.post(new Runnable() {
                        public void run() {
                            mDataView.setText("Calibrating");
                        }
                    });
                    mHandler.postDelayed(new Runnable(){
                        public void run(){
                            calibrating=false;
                            Log.i("asd2","asdasd");
                            start();
                        }
                    },5000);
                }
            }
            sendData.append(raw+",");
            if(sendData.length()>50){
                new HttpTaskPost(getApplicationContext()).execute(sendData.toString());
                sendData = new StringBuilder();
            }
            Log.i("hjelp",sendData.length()+"");
            if (rising == false) {
                if (oldAvg < average) {
                    rising = true;
                }
            } else if (rising == true) {
                if (oldAvg > average) {
                    beats.add(new Beat(System.currentTimeMillis()));
                    rising = false;
                }
            }
            if (System.currentTimeMillis() > showtime + 500) {
                    for (Beat b : beats) {
                        if (System.currentTimeMillis() - b.getTime() > 5000) {
                            rBeats.add(b);
                        }
                    }
                    for(Beat b:rBeats){
                        beats.remove(b);
                    }
                    rBeats.clear();
                bpmTemp = beats.size();
                bpm = (long) ((0.5*bpm)+((1-0.5)*(bpmTemp * 60000 / (System.currentTimeMillis()-beats.getFirst().getTime()))));
                showtime = System.currentTimeMillis();
                mHandler.post(new Runnable() {
                    public void run() {
                        mDataView.setText("bpm: " + (int) bpm);
                    }
                });
            }
            try {
                Log.i("data as string", "bpmTemp: " + bpmTemp + " testBpmTemp: " + bpmTemp * 60000 / (System.currentTimeMillis() - beats.getFirst().getTime()));
            }catch(NoSuchElementException e){

            }
            seriesX.appendData(new DataPoint(dataCount++, raw), true, 200);

        }

        private boolean checkForStatic(){
            int counter=0;
            try {
                for (int i = 0; i < staticCheck.length; i++) {
                    if (i % 2 == 0) {
                        if (staticCheck[i] > staticCheck[i + 1]) {
                            counter++;
                        }
                    } else {
                        if (staticCheck[i] < staticCheck[i + 1]) {
                            counter++;
                        }
                    }
                }
            }catch(IndexOutOfBoundsException e){
                Log.i("asd","erorr");
            }
            if(counter==staticCheck.length-1){
                Log.i("asd","static");
                return true;
            }
            Log.i("asd","nostatic "+counter);
            return false;
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {}

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {}
    };

    // Below: gui stuff...
    //private TextView mDeviceView;
    private TextView mDataView;
    private GraphView graphView1;
    private LineGraphSeries<DataPoint> seriesX;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        mDataView = findViewById(R.id.dataView);

        mHandler = new Handler();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.bluetooth_control_menu);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.ss:
                        if(stop){
                            start();
                        }else
                            stop();
                        stop=!stop;
                        return true;
                    default:
                        return false;
                }
            }
        });
        toolbar.bringToFront();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }



    protected void showToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void startGraph(){
        graphView1 = findViewById(R.id.graph);
        graphView1.removeAllSeries();
        seriesX = new LineGraphSeries<>(new  DataPoint[]{});
        seriesX.setColor(Color.RED);
        graphView1.addSeries(seriesX);
        graphView1.getViewport().setXAxisBoundsManual(true);
        graphView1.getViewport().setMinX(0);
        graphView1.getViewport().setMaxX(100);
        graphView1.getViewport().setMinY(0);
        graphView1.getViewport().setMaxY(2000);
        graphView1.getViewport().setScalable(false);
    }
}
