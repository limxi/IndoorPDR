package com.audioar.wifipositioning.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.audioar.wifipositioning.R;
import com.onlylemi.mapview.library.MapView;
import com.onlylemi.mapview.library.entity.MapMark;
import com.onlylemi.mapview.library.layer.LocationLayer;
import com.onlylemi.mapview.library.layer.MarkLayer;
import com.onlylemi.mapview.library.layer.RouteLayer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.hardware.Sensor.TYPE_MAGNETIC_FIELD;
import static android.hardware.Sensor.TYPE_ROTATION_VECTOR;
import static android.hardware.Sensor.TYPE_STEP_COUNTER;
import static android.hardware.Sensor.TYPE_STEP_DETECTOR;

public class PDRActivity extends AppCompatActivity implements SensorEventListener {

    // 传感器管理器
    SensorManager mSensorManager;
    Sensor mStepDetector;
    Sensor mStepCounter;
    Sensor mAccelerometer;
    Sensor mMagneticField;
    Sensor mRotationVector;

    // 坐标转换系数
    static final double X = 0.00000899321619;
    static final double Y = 0.00001049178636;

    // 步长参数
    static double stepLengthConstant = 75;
    static double stepLengthHeight = 82;
    double stepLength;

    // 传感器存在标志
    Boolean isSensorStepDetectorPresent = false;
    Boolean isSensorStepCounterPresent = false;
    Boolean isSensorAccelerometerPresent = false;
    Boolean isSensorMagneticFieldPresent = false;
    Boolean isSensorRotationVectorPresent = false;

    Boolean lastAccelerometerSet = false;
    Boolean lastMagnetometerSet = false;
    Boolean stepCountingActive = false;

    int numberOfStepsDetected = 0;
    int numberOfStepsCounted = 0;
    int initialStepCounterValue = 0;
    int azimuthInDegress = 0;
    float orientationStable = 0f;

    double distance = 0;
    double distanceHeight = 0;
    double distanceFrequency = 0;
    double detectedStepsSensorValue = 0;
    double countedStepsSensorValue = 0;

    float[] lastAccelerometer = new float[3];
    float[] lastMagnetometer = new float[3];
    float[] mRotationMatrix = new float[9];
    float[] mOrientationAngles = new float[3];
    float[] mRotationMatrixFromVector = new float[16];

    long timeCountingStarted = 0;
    long timeOfStep;
    double stepFrequency = 0;
    long totalTime = 0;
    double stepMeanFrequency = 0;
    double stepMeanTime = 0;
    double stepMeanAccDiff = 0;

    ArrayList<Long> stepTimeStamp = new ArrayList<>();
    double accelerationTotalMax = 0;
    double accelerationTotalMin = 0;
    double azimuthInRadians = 0;
    double sumAccData = 0;
    double accelerationTotal = 0;

    static final float ALPHA = 0.25f;

    // 当前坐标
    private float currentX = 500f;  // 初始X坐标（像素）
    private float currentY = 500f;  // 初始Y坐标（像素）

    // MapView组件
    private MapView mapView;
    private LocationLayer locationLayer;
    private RouteLayer routeLayer;
    private MarkLayer markLayer;
    private List<com.onlylemi.mapview.library.entity.MapPoint> pathPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdr);

        // 初始化传感器
        initSensors();

        // 初始化地图
        initMapView();

        // 初始化UI
        initUI();
    }

    private void initSensors() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (mSensorManager.getDefaultSensor(TYPE_STEP_DETECTOR) != null) {
            mStepDetector = mSensorManager.getDefaultSensor(TYPE_STEP_DETECTOR);
            isSensorStepDetectorPresent = true;
        }
        if (mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER) != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
            isSensorAccelerometerPresent = true;
        }
        if (mSensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD) != null) {
            mMagneticField = mSensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD);
            isSensorMagneticFieldPresent = true;
        }
        if (mSensorManager.getDefaultSensor(TYPE_ROTATION_VECTOR) != null) {
            mRotationVector = mSensorManager.getDefaultSensor(TYPE_ROTATION_VECTOR);
            isSensorRotationVectorPresent = true;
        }
        if (mSensorManager.getDefaultSensor(TYPE_STEP_COUNTER) != null) {
            mStepCounter = mSensorManager.getDefaultSensor(TYPE_STEP_COUNTER);
            isSensorStepCounterPresent = true;
        }
    }

    private void initMapView() {
        mapView = findViewById(R.id.mapview);

        // 加载地图图片
        try {
            InputStream is = getAssets().open("floor_plan.png");
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            mapView.loadMap(bitmap);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载地图图片失败", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取图层
        locationLayer = mapView.getLocationLayer();
        routeLayer = mapView.getRouteLayer();
        markLayer = mapView.getMarkLayer();

        // 设置定位图标
        Bitmap locationBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.blue_dot);
        locationLayer.setLocationMarker(locationBitmap);

        // 初始化位置
        locationLayer.updateLocation(currentX, currentY, 0);

        // 添加起点标记
        Bitmap startBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pin);
        MapMark startMark = new MapMark();
        startMark.setId("start");
        startMark.setName("起点");
        startMark.setX(currentX);
        startMark.setY(currentY);
        startMark.setMarkImg(startBitmap);
        markLayer.addMark(startMark);

        // 初始化路径点
        com.onlylemi.mapview.library.entity.MapPoint startPoint =
                new com.onlylemi.mapview.library.entity.MapPoint(currentX, currentY, 0);
        pathPoints.add(startPoint);
    }

    private void initUI() {
        Button startStopButton = findViewById(R.id.startStopButton);
        startStopButton.setOnClickListener(v -> startStop(v));

        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> reset(v));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case TYPE_STEP_DETECTOR:
                handleStepDetector(event);
                break;
            case TYPE_STEP_COUNTER:
                handleStepCounter(event);
                break;
            case TYPE_ACCELEROMETER:
                handleAccelerometer(event);
                break;
            case TYPE_MAGNETIC_FIELD:
                handleMagneticField(event);
                break;
            case TYPE_ROTATION_VECTOR:
                handleRotationVector(event);
                break;
        }
    }

    private void handleStepDetector(SensorEvent event) {
        if (!stepCountingActive) return;

        numberOfStepsDetected++;
        detectedStepsSensorValue++;

        distance = distance + stepLengthConstant;
        distanceHeight = distanceHeight + stepLengthHeight;

        stepTimeStamp.add(event.timestamp);

        if (numberOfStepsDetected == 1) {
            timeOfStep = event.timestamp / 1000000L - timeCountingStarted;
            totalTime = 0;
            distanceFrequency = distanceFrequency + stepLengthHeight;
        } else {
            timeOfStep = (event.timestamp - stepTimeStamp.get(stepTimeStamp.size() - 2)) / 1000000L;
            totalTime = totalTime + timeOfStep;
            stepFrequency = 1000D / timeOfStep;

            stepLength = 44 * stepFrequency + 4.4;
            distanceFrequency = distanceFrequency + stepLength;
        }

        // 更新位置
        double radian = Math.toRadians(orientationStable);
        float stepIncrement = (float) (stepLength / 100); // 转换为像素（假设比例尺）

        currentX += stepIncrement * Math.cos(radian);
        currentY += stepIncrement * Math.sin(radian);

        // 更新地图上的位置
        locationLayer.updateLocation(currentX, currentY, 0);

        // 添加路径点
        com.onlylemi.mapview.library.entity.MapPoint point =
                new com.onlylemi.mapview.library.entity.MapPoint(currentX, currentY, 0);
        pathPoints.add(point);

        // 绘制路径
        List<com.onlylemi.mapview.library.entity.MapPoint> route = new ArrayList<>(pathPoints);
        routeLayer.setRoutePoints(route);

        mapView.refresh();

        // 更新UI显示
        updateUIText();
    }

    private void handleStepCounter(SensorEvent event) {
        if (stepCountingActive) {
            if (initialStepCounterValue < 1) {
                initialStepCounterValue = (int) event.values[0];
            }
            numberOfStepsCounted = (int) event.values[0] - initialStepCounterValue;

            TextView countedSteps = findViewById(R.id.countedStepsTextView);
            countedSteps.setText(String.valueOf(numberOfStepsCounted));
        } else {
            initialStepCounterValue = (int) event.values[0];
        }
    }

    private void handleAccelerometer(SensorEvent event) {
        lastAccelerometerSet = true;
        System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);

        if (stepCountingActive && numberOfStepsDetected > 0) {
            accelerationTotal = Math.sqrt(
                    Math.pow(event.values[0], 2) +
                            Math.pow(event.values[1], 2) +
                            Math.pow(event.values[2], 2));

            if (accelerationTotalMin == 0) {
                accelerationTotalMin = accelerationTotal;
            } else if (accelerationTotal < accelerationTotalMin) {
                accelerationTotalMin = accelerationTotal;
            }
            if (accelerationTotalMax == 0) {
                accelerationTotalMax = accelerationTotal;
            } else if (accelerationTotal > accelerationTotalMax) {
                accelerationTotalMax = accelerationTotal;
            }
        }
    }

    private void handleMagneticField(SensorEvent event) {
        lastMagnetometerSet = true;
        System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
        lastMagnetometer = lowPass(event.values.clone(), lastMagnetometer);
    }

    private void handleRotationVector(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(mRotationMatrixFromVector, event.values);
        SensorManager.getOrientation(mRotationMatrixFromVector, mOrientationAngles);

        orientationInDegress = ((int) (mOrientationAngles[0] * 180 / (float) Math.PI) + 360) % 360;
        orientationStable = ((orientationInDegress - getAngleDisplacement()) + 360) % 360;

        TextView orientationView = findViewById(R.id.rotationVectorTextView);
        orientationView.setText("" + orientationInDegress);
    }

    private void updateUIText() {
        TextView detectedSteps = findViewById(R.id.stepsDetectedTextView);
        detectedSteps.setText("" + detectedStepsSensorValue);

        TextView distanceView = findViewById(R.id.distanceTextView);
        distanceView.setText(String.format("%.2f", distance / 100D));

        TextView distanceHeightView = findViewById(R.id.distanceHeight);
        distanceHeightView.setText(String.format("%.2f", distanceHeight / 100D));

        TextView distanceFrequencyView = findViewById(R.id.distanceFreq);
        distanceFrequencyView.setText(String.format("%.2f", distanceFrequency / 100D));

        TextView TotalTimeView = findViewById(R.id.totalTime);
        TotalTimeView.setText(Long.toString(totalTime));

        TextView meanFreqView = findViewById(R.id.meanFreq);
        meanFreqView.setText(String.format("%.2f", stepMeanFrequency));

        TextView meanAccqView = findViewById(R.id.meanAccdiff);
        meanAccqView.setText(String.format("%.2f", stepMeanAccDiff));
    }

    protected float[] lowPass(float[] input, float[] output) {
        if (output == null) return input;
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不需要处理
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensors();
    }

    private void registerSensors() {
        if (isSensorStepDetectorPresent) {
            mSensorManager.registerListener(this, mStepDetector,
                    SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (isSensorAccelerometerPresent) {
            mSensorManager.registerListener(this, mAccelerometer,
                    SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (isSensorMagneticFieldPresent) {
            mSensorManager.registerListener(this, mMagneticField,
                    SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (isSensorRotationVectorPresent) {
            mSensorManager.registerListener(this, mRotationVector,
                    SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (isSensorStepCounterPresent) {
            mSensorManager.registerListener(this, mStepCounter,
                    SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    public void startStop(View view) {
        Button myButton = findViewById(R.id.startStopButton);
        if (stepCountingActive) {
            stepCountingActive = false;
            timeCountingStarted = 0;
            myButton.setText("开始");
        } else {
            stepCountingActive = true;
            timeCountingStarted = SystemClock.elapsedRealtime();
            myButton.setText("停止");
        }
    }

    private SeekBar mSeekBar = null;

    private int getAngleDisplacement() {
        if (mSeekBar == null) {
            mSeekBar = findViewById(R.id.adjust);
        }
        return (mSeekBar.getProgress() - 20);
    }

    public void reset(View view) {
        stepCountingActive = false;
        timeCountingStarted = 0;
        initialStepCounterValue = initialStepCounterValue + numberOfStepsCounted;

        numberOfStepsCounted = 0;
        numberOfStepsDetected = 0;
        detectedStepsSensorValue = 0;
        countedStepsSensorValue = 0;

        stepMeanFrequency = 0;
        stepMeanTime = 0;
        sumAccData = 0;

        distance = 0;
        distanceHeight = 0;
        distanceFrequency = 0;
        totalTime = 0;

        // 重置位置
        currentX = 500f;
        currentY = 500f;
        locationLayer.updateLocation(currentX, currentY, 0);

        pathPoints.clear();
        com.onlylemi.mapview.library.entity.MapPoint startPoint =
                new com.onlylemi.mapview.library.entity.MapPoint(currentX, currentY, 0);
        pathPoints.add(startPoint);

        routeLayer.setRoutePoints(null);
        mapView.refresh();

        updateUIText();

        TextView countedSteps = findViewById(R.id.countedStepsTextView);
        countedSteps.setText(String.valueOf(numberOfStepsCounted));

        Button myButton = findViewById(R.id.startStopButton);
        myButton.setText("开始");
    }
}