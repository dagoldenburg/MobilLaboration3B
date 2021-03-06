package dag.mobillaboration3b.Model;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import dag.mobillaboration3b.ConnectedDevice;
import dag.mobillaboration3b.HttpTaskPost;
import dag.mobillaboration3b.HttpTaskReset;
import dag.mobillaboration3b.Model.Beat;

public class DeviceService extends Service {

    public static UUID UARTSERVICE_SERVICE_UUID =
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID UART_RX_CHARACTERISTIC_UUID = // receiver on Micro:bit
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID UART_TX_CHARACTERISTIC_UUID = // transmitter on Micro:bit
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

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

    public DeviceService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        start();

        new HttpTaskReset(getApplicationContext()).execute();
        return Service.START_NOT_STICKY;
    }


    private void start(){
        mConnectedDevice = ConnectedDevice.getInstance();
        if (mConnectedDevice != null) {
            connect();
        }
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
    public void onDestroy() {
        super.onDestroy();
        stop();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        start();
        return null;
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

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mBluetoothGatt = null;

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
            ApplicationLogic.checkStatic(raw);
            ApplicationLogic.calculateBeats(raw,getApplicationContext());
            try {
                Log.i("data as string", "bpmTemp: " + bpmTemp + " testBpmTemp: " + bpmTemp * 60000 / (System.currentTimeMillis() - beats.getFirst().getTime()));
            }catch(NoSuchElementException e){

            }
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


}
