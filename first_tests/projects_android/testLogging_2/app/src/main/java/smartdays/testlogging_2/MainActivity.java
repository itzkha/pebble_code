package smartdays.testlogging_2;

// Works with test_accel_3 on the Pebble
// Plots the buffer

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.widget.TextView;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.UUID;

import android.view.WindowManager;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.*;
import android.graphics.Color;
import android.graphics.Paint;


public class MainActivity extends Activity {

    // Configuration
    private static final UUID WATCHAPP_UUID = UUID.fromString("24fbe952-c30a-4c7f-ac50-5ab72bdf8aca");
    private static final int DATA_LOG_TAG_ACCEL = 51;

    // App elements
    private PebbleDataLogReceiver dataloggingReceiver;
    private TextView textView;
    private XYPlot plot;
    private static final int bufferSize = 100;
    private int[][] buffer = new int[3][bufferSize];
    private int bufferIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        // Setup plot
        plot = (XYPlot) findViewById(R.id.accelerationPlot);

        SampleDynamicSeries xSeries = new SampleDynamicSeries("X", 0);
        SampleDynamicSeries ySeries = new SampleDynamicSeries("Y", 1);
        SampleDynamicSeries zSeries = new SampleDynamicSeries("Z", 2);

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        LineAndPointFormatter xFormatter = new LineAndPointFormatter(Color.rgb(200, 0, 0), null, null, null);
        xFormatter.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        xFormatter.getLinePaint().setStrokeWidth(2);
        plot.addSeries(xSeries, xFormatter);
        // Create a formatter to use for drawing a series using LineAndPointRenderer
        LineAndPointFormatter yFormatter = new LineAndPointFormatter(Color.rgb(0, 200, 0), null, null, null);
        yFormatter.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        yFormatter.getLinePaint().setStrokeWidth(2);
        plot.addSeries(ySeries, yFormatter);
        // Create a formatter to use for drawing a series using LineAndPointRenderer
        LineAndPointFormatter zFormatter = new LineAndPointFormatter(Color.rgb(0, 0, 200), null, null, null);
        zFormatter.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        zFormatter.getLinePaint().setStrokeWidth(2);
        plot.addSeries(zSeries, zFormatter);

        plot.setDomainBoundaries(0, 100, BoundaryMode.FIXED);
        plot.setRangeBoundaries(-4000, 4000, BoundaryMode.FIXED);

        plot.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
        plot.setDomainStepValue(20);
        plot.setRangeStepMode(XYStepMode.INCREMENT_BY_VAL);
        plot.setRangeStepValue(1000);
        plot.setDomainValueFormat(new DecimalFormat("#"));
        plot.setRangeValueFormat(new DecimalFormat("#"));

        // Setup TextView
        textView = (TextView)findViewById(R.id.text_view);
        textView.setText("Waiting for logging data...");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Define data reception behavior
        dataloggingReceiver = new PebbleDataLogReceiver(WATCHAPP_UUID) {

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                if(tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(data);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    // Get the acceleration value
                    long timestamp_data = byteBuffer.getLong(0);    //8 bytes of timestamp
                    int x = byteBuffer.getShort(8);                   //2 bytes of x
                    int y = byteBuffer.getShort(10);                  //2 bytes of y
                    int z = byteBuffer.getShort(12);                  //2 bytes of z
                    buffer[0][bufferIndex] = x;
                    buffer[1][bufferIndex] = y;
                    buffer[2][bufferIndex] = z;
                    bufferIndex++;
                    if (bufferIndex >= bufferSize) {
                        bufferIndex = 0;
                    }
                    textView.setText("Timestamp: " + String.valueOf(timestamp_data));
                    plot.redraw();
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                super.onFinishSession(context, logUuid, timestamp, tag);

                if(tag.intValue() == DATA_LOG_TAG_ACCEL) {
                    // Display all data received
                    textView.setText("Timestamp: " + timestamp.toString());
                    plot.redraw();
                }
            }

        };

        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Always unregister callbacks
        if(dataloggingReceiver != null) {
            unregisterReceiver(dataloggingReceiver);
        }
    }


    class SampleDynamicSeries implements XYSeries {
        private String title;
        private int seriesIndex;

        public SampleDynamicSeries(String t, int i) {
            title = t;
            seriesIndex = i;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public int size() {
            return bufferSize;
        }

        @Override
        public Number getX(int index) {
            return index;
        }

        @Override
        public Number getY(int index) {
            return buffer[seriesIndex][(bufferIndex + index + 1) % bufferSize];
        }
    }
}
