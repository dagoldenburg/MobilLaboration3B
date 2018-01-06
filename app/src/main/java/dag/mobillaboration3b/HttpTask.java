package dag.mobillaboration3b;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Micke on 1/6/2018.
 */

public class HttpTask extends AsyncTask<String,Void,Void> {
    private Context context;

    public HttpTask(Context context){
        this.context = context;
    }

    @Override
    protected Void doInBackground(String... params) {
        SharedPreferences shareprefs = PreferenceManager.getDefaultSharedPreferences(context);
        String urlString = shareprefs.getString("ip","0.0.0.0");
        String data = params[0]; //data to post
        OutputStream out = null;
        try {
            URL url = new URL("http://"+urlString+":3000/upload");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            out = new BufferedOutputStream(urlConnection.getOutputStream());
            BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(out, "UTF-8"));
            writer.write(data);
            writer.flush();
            writer.close();
            out.close();
            urlConnection.connect();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }
}
