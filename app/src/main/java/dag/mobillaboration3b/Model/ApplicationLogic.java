package dag.mobillaboration3b.Model;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

import com.jjoe64.graphview.series.DataPoint;

import java.util.LinkedList;
import java.util.NoSuchElementException;

import dag.mobillaboration3b.ConnectedDevice;
import dag.mobillaboration3b.DeviceActivity;
import dag.mobillaboration3b.HttpTaskPost;

/**
 * Created by Dag on 1/14/2018.
 */

public class ApplicationLogic {
    static final int arrSize=4,statArrSize=8;
    static int[] array = new int[arrSize];
    public static boolean rising,calibrating=false;
    static int i,j,oldAvg,average,bpmTemp;
    public static long showtime,bpm;
    static private LinkedList<Beat> beats = new LinkedList<>();
    static private LinkedList<Beat> rBeats = new LinkedList<>();
    static int[] staticCheck = new int[statArrSize];
    static StringBuilder sendData = new StringBuilder();

    public static void stop(){
        beats.clear();
        rBeats.clear();
    }



    public static boolean checkStatic(int raw){
        staticCheck[j++]=raw;
        if(j==statArrSize){
            j=0;
            if(checkForStatic()){
                calibrating=true;
            }
        }
        return calibrating;
    }

    public static boolean calculateBeats(int raw,Context context){
        array[i++] = raw;
        sendData.append(raw+",");
            if(sendData.length()>500){
                new HttpTaskPost(context).execute(sendData.toString());
                sendData = new StringBuilder();
            }
        if (i == arrSize) {
            i = 0;
            oldAvg = average;
            average = 0;
            for (int index = 0; index < array.length; index++) {
                average += array[index];
            }
            average = average / arrSize;
        }
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
            for (Beat b : rBeats) {
                beats.remove(b);
            }
            rBeats.clear();
            bpmTemp = beats.size();
            bpm = (long) ((0.7 * bpm) + ((1 - 0.7) * (bpmTemp * 60000 / (System.currentTimeMillis() - beats.getFirst().getTime()))));
            showtime = System.currentTimeMillis();
            Log.i("data as string", "bpmTemp: " + bpmTemp + " testBpmTemp: " + bpmTemp * 60000 / (System.currentTimeMillis() - beats.getFirst().getTime()));
            return true;
        }
        return false;
    }

    private static boolean checkForStatic(){
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

}
