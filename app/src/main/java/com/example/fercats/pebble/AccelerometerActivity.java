package com.example.fercats.pebble;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.UUID;


/**
 *  Receive accelerometer vectors from Pebble watch via PebblePointer app.
 *
 *  @author robin.callender@gmail.com
 */
public class AccelerometerActivity extends Activity {

    private static final String TAG = "PebblePointer";

    // The tuple key corresponding to a vector received from the watch
    private static final int PP_KEY_CMD = 128;
    private static final int PP_KEY_X   = 1;
    private static final int PP_KEY_Y   = 2;
    private static final int PP_KEY_Z   = 3;

    @SuppressWarnings("unused")
    private static final int PP_CMD_INVALID = 0;
    private static final int PP_CMD_VECTOR  = 1;

    public static final int VECTOR_INDEX_X  = 0;
    public static final int VECTOR_INDEX_Y  = 1;
    public static final int VECTOR_INDEX_Z  = 2;

    private static int vector[] = new int[3];
    private static float norm_x = 0;
    private static float norm_y = 0;
    private static float norm_z = 0;

    private static float cur_x = 0;
    private static float cur_y = 0;
    private static float cur_z = 0;

    private DatagramSocket socket = null;
    private Button btn;


    private PebbleKit.PebbleDataReceiver dataReceiver;

    // This UUID identifies the PebblePointer app.
    private static final UUID PEBBLEPOINTER_UUID = UUID.fromString("273761eb-97dc-4f08-b353-3384a2170902");

    private static final int SAMPLE_SIZE = 30;

    private XYPlot dynamicPlot = null;

    SimpleXYSeries xSeries = null;
    SimpleXYSeries ySeries = null;
    SimpleXYSeries zSeries = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate: ");

        setContentView(R.layout.activity_accelerometer);

        vector[VECTOR_INDEX_X] = 0;
        vector[VECTOR_INDEX_Y] = 0;
        vector[VECTOR_INDEX_Z] = 0;

        PebbleKit.startAppOnPebble(getApplicationContext(), PEBBLEPOINTER_UUID);

        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    while(true) {
                        String coords = norm_x + "," + norm_z;
                        sleep(50);
                        sendPacket(coords);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();

    }

    private void sendPacket(String message) {
        byte[] messageData = message.getBytes();
//        ipInput.setText("192.168.42.101");
//        portInput.setText("12345");

        try {
//            InetAddress addr = InetAddress.getByName(ipInput.getText().toString());
//            int port = Integer.parseInt(portInput.getText().toString());
//            InetAddress addr = InetAddress.getByName("10.12.171.192");
            InetAddress addr = InetAddress.getByName("10.12.173.15"); //sifu
            int port = Integer.parseInt("12345");
            DatagramPacket sendPacket = new DatagramPacket(messageData, 0, messageData.length, addr, port);
            if (socket != null) {
                socket.disconnect();
                socket.close();
            }
            socket = new DatagramSocket(port);
            socket.send(sendPacket);
        } catch (UnknownHostException e) {
            Log.e("MainActivity sendPacket", "getByName failed");
        } catch (IOException e) {
            Log.e("MainActivity sendPacket", "send failed: " + e.toString());
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.i(TAG, "onPause: ");

        setContentView(R.layout.activity_accelerometer);

        if (dataReceiver != null) {
                unregisterReceiver(dataReceiver);
                dataReceiver = null;
        }
        PebbleKit.closeAppOnPebble(getApplicationContext(), PEBBLEPOINTER_UUID);
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.i(TAG, "onResume: ");

        final Handler handler = new Handler();

        dataReceiver = new PebbleKit.PebbleDataReceiver(PEBBLEPOINTER_UUID) {

            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary dict) {

                handler.post(new Runnable() {
                    @Override
                    public void run() {

                        PebbleKit.sendAckToPebble(context, transactionId);

                        final Long cmdValue = dict.getInteger(PP_KEY_CMD);
                        if (cmdValue == null) {
                            return;
                        }

                        if (cmdValue.intValue() == PP_CMD_VECTOR) {

                            // Capture the received vector.
                            final Long xValue = dict.getInteger(PP_KEY_X);
                            if (xValue != null) {
                                vector[VECTOR_INDEX_X] = xValue.intValue();
                            }

                            final Long yValue = dict.getInteger(PP_KEY_Y);
                            if (yValue != null) {
                                vector[VECTOR_INDEX_Y] = yValue.intValue();
                            }

                            final Long zValue = dict.getInteger(PP_KEY_Z);
                            if (zValue != null) {
                                vector[VECTOR_INDEX_Z] = zValue.intValue();
                            }

                            // Update the user interface.
                            updateUI();
                            normalize();
                            generate_touch();
                        }
                    }
                });
            }
        };;

        PebbleKit.registerReceivedDataHandler(this, dataReceiver);
    }

    public void updateUI() {

        final String x = String.format(Locale.getDefault(), "X: %d", vector[VECTOR_INDEX_X]);
        final String y = String.format(Locale.getDefault(), "Y: %d", vector[VECTOR_INDEX_Y]);
        final String z = String.format(Locale.getDefault(), "Z: %d", vector[VECTOR_INDEX_Z]);

        // Update the numerical fields

        TextView x_axis_tv = (TextView) findViewById(R.id.x_axis_Text);
        x_axis_tv.setText(x);

        TextView y_axis_tv = (TextView) findViewById(R.id.y_axis_Text);
        y_axis_tv.setText(y);

        TextView z_axis_tv = (TextView) findViewById(R.id.z_axis_Text);
        z_axis_tv.setText(z);


    }

    public void normalize() {
        norm_x = vector[VECTOR_INDEX_X] / 1000.0f;
        norm_y = vector[VECTOR_INDEX_Y] / 1000.0f;
        norm_z = vector[VECTOR_INDEX_Z] / 1000.0f;
    }

    public void generate_touch() {
        // Obtain MotionEvent object
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis() + 100;


        cur_x += norm_x;
        cur_y += norm_y;
        cur_z += norm_z;

        Log.v("X: " , String.valueOf(cur_x));
        Log.v("Y: " , String.valueOf(cur_y));
        Log.v("Z: " , String.valueOf(cur_z));

        // List of meta states found here:     developer.android.com/reference/android/view/KeyEvent.html#getMetaState()
        int metaState = 0;
        MotionEvent motionEvent = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_UP,
                cur_x,
                cur_z,
                metaState
        );

        // Dispatch touch event to view
        View v = getWindow().getDecorView().findViewById(android.R.id.content);
        v.dispatchTouchEvent(motionEvent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        socket.disconnect();
        socket.close();
    }

}