package dag.mobillaboration3b;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

class BTDeviceArrayAdapter extends ArrayAdapter<BluetoothDevice> {

    public BTDeviceArrayAdapter(Context context, List<BluetoothDevice> deviceList) {
        super(context, R.layout.device_item_layout, deviceList);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.device_item_layout, parent,
                false);

        TextView nameView = (TextView) rowView.findViewById(R.id.deviceName);
        BluetoothDevice device = this.getItem(position);
        String name = device.getName();
        nameView.setText(name == null ? "Unknown" : name);
        return rowView;
    }
}
