package com.getyourlocation.app.client.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.getyourlocation.app.client.util.CommonUtil;
import com.getyourlocation.app.client.util.SensorUtil;
import com.getyourlocation.app.client.widget.CameraPreview;
import com.getyourlocation.app.client.R;
import com.getyourlocation.app.client.widget.MapDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class CollectDataActivity extends AppCompatActivity {
    private static final String TAG = "CollectDataActivity";
    private static final String STORAGE_DIR = "GYL-Data";
    private static final String FRAMES_DIR = "JPEGImages";
    private static final String SENSOR_FILENAME = "sensor.txt";
    private static final String ANNOTATION_FILENAME = "annotation.txt";

    private TextView infoTxt;
    private Button recordBtn;
    private MapDialog mapDialog;

    private Camera camera;
    private CameraPreview cameraPreview;

    private File framesDir;
    private SensorUtil sensorUtil;
    private List<String> sensorData;

    private Handler handler;
    private Runnable timingRunnable;

    private boolean isRecording = false;
    private int seconds = 0;
    private int frameCnt = 1;

    private boolean initCam = true;

    private Camera.Parameters params;
    private List<Float> focalLengthData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collect_data);
        initInfoTxt();
        initSensor();
        initZoom();
        initTiming();
        initCamera();
        initMapDialog();
        initRecordBtn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorUtil.register();
        if (initCam == false) {
            initCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorUtil.unregister();
        releaseCamera();
        FrameLayout layout = (FrameLayout) findViewById(R.id.data_preview_layout);
        layout.removeAllViews();
    }

    private void releaseCamera(){
        if (camera != null){
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
            initCam = false;
        }
    }

    private void initInfoTxt() {
        infoTxt = (TextView) findViewById(R.id.data_infoTxt);
    }

    private void initSensor() {
        sensorData = new ArrayList<>();
        sensorUtil = SensorUtil.getInstance(this);
    }

    private void initZoom() {
        focalLengthData = new ArrayList<>();
    }

    private void initTiming() {
        handler = new Handler();
        timingRunnable = new Runnable() {
            @Override
            public void run() {
                recordBtn.setText(String.valueOf(seconds++));
                handler.postDelayed(timingRunnable, 1000);
            }
        };
    }

    private void initCamera() {
        camera = Camera.open();
        // 获取摄像头的参数
        params = camera.getParameters();
        params.set("focus-mode","auto");
//        params.set("zoom",90);
        camera.setParameters(params);
//        camera.cancelAutoFocus();
        // 调整摄像头的角度
        camera.setDisplayOrientation(90);
        cameraPreview = new CameraPreview(this, camera, new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (isRecording) {
                    sensorData.add(sensorUtil.getSensorDataString());
                    params = camera.getParameters();
                    // 焦距
                    Log.i(TAG,params.getFocalLength() + " focal length ");
                    focalLengthData.add(params.getFocalLength());
                    saveFrameToFile(data);
                }
            }
        });
        FrameLayout layout = (FrameLayout) findViewById(R.id.data_preview_layout);
        layout.addView(cameraPreview);
        initCam = true;

        SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar);
        int max_focal_length = 2;
        seekBar.setMax(max_focal_length*100);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                params.set("focal-length",Float.toString(convertProgress(i)));
                camera.setParameters(params);
                Log.i("dick2",params.getFocalLength() + " focal length "+ convertProgress(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }
    private float convertProgress(int intVal) {
        float result;
        //这里/100 是因为前面每一个数都扩大10倍，因此这里需要/100还原
        result = intVal/100f;
        return result;
    }

    private void initMapDialog() {
        mapDialog = new MapDialog(this, null);
    }

    private void initRecordBtn() {
        recordBtn = (Button) findViewById(R.id.data_recordBtn);
        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    if (createStorageDir()) {
                        startRecord();
                    }
                } else {
                    endRecord();
                }
            }
        });
    }

    private boolean createStorageDir() {
        framesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                STORAGE_DIR + File.separator + CommonUtil.getTimestamp() + File.separator + FRAMES_DIR);
        if (!framesDir.exists() && !framesDir.mkdirs()) {
            CommonUtil.showToast(CollectDataActivity.this, "Failed to create storage directory");
            return false;
        } else {
            return true;
        }
    }

    private void saveFrameToFile(byte[] raw) {
        Camera.Size size = cameraPreview.getPreviewSize();
        YuvImage im = new YuvImage(raw, ImageFormat.NV21, size.width, size.height, null);
        Rect r = new Rect(0, 0, size.width, size.height);
        ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
        im.compressToJpeg(r, CameraPreview.JPEG_QUALITY, jpegStream);
        String filename = framesDir.getPath() + File.separator + frameCnt + ".jpg";
        File frameFile = new File(filename);
        try {
            FileOutputStream fos = new FileOutputStream(frameFile);
            fos.write(jpegStream.toByteArray());
            fos.close();
            jpegStream.close();
            Log.d(TAG, "Frame " + frameCnt + " saved");
            ++frameCnt;
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }

    private void startRecord() {
        seconds = 0;
        frameCnt = 1;
        handler.post(timingRunnable);
        sensorUtil.reset();
        sensorData.clear();
        isRecording = true;
    }

    private void endRecord() {
        handler.removeCallbacks(timingRunnable);
        recordBtn.setText("Start");
        isRecording = false;
        inputAnnotation();
        saveSensorDataToFile();
        infoTxt.setText("Frame count: " + (frameCnt - 1) + "\nData saved to " + framesDir.getParent());
    }

    private void inputAnnotation(){
        AlertDialog.Builder builder = new AlertDialog.Builder(CollectDataActivity.this);
        builder.setTitle("请输入注释");    //设置对话框标题
        final EditText edit = new EditText(CollectDataActivity.this);
        builder.setView(edit);
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveAnnotationToFile(edit.getText().toString());
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(CollectDataActivity.this, "你取消了注释的输入", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setCancelable(true);    //设置按钮是否可以按返回键取消,false则不可以取消
        AlertDialog dialog = builder.create();  //创建对话框
        dialog.setCanceledOnTouchOutside(true); //设置弹出框失去焦点是否隐藏,即点击屏蔽其它地方是否隐藏
        dialog.show();
    }

    private void saveSensorDataToFile() {
        String filename = framesDir.getParent() + File.separator + SENSOR_FILENAME;
        File sensorFile = new File(filename);
        try {
            FileWriter fos = new FileWriter(sensorFile);
            fos.write("frame," +"focal-length,"+ sensorUtil.getDescription() + "\n");
            for (int i = 0; i < sensorData.size(); ++i) {
                fos.write(i + 1 + " : " + focalLengthData.get(i) + ";"+ sensorData.get(i));
                Log.i("test"+i,i + 1 + " : " + sensorData.get(i));
                fos.write("\n");
            }
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }
    private void saveAnnotationToFile(String annotation) {
        String filename = framesDir.getParent() + File.separator + ANNOTATION_FILENAME;
        File sensorFile = new File(filename);
        try {
            FileWriter fos = new FileWriter(sensorFile);
            fos.write(CommonUtil.getTimestamp() + " : " +annotation+ "\n");
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
    }
}
