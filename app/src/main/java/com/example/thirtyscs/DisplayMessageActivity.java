package com.example.thirtyscs;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.util.Calendar;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class DisplayMessageActivity extends WearableActivity {

    public Context context;

    String imudata,hrdata,resultdata;
    SensorManager mSensorManager;
    Sensor mSensor;
    Sensor gyroSensor;
    Sensor hrSensor;
    SensorEventListener mListener;

    String x_value = "00";
    String y_value = "00";
    String z_value = "00";
    float hr_value = 0;
    String gx_value = "00";
    String gy_value = "00";
    String gz_value = "00";
    String senseTime;
    int deltaHR = 0;
    float gx,gy,gz = 0;
    int stands=0;
    float standDuration = 0;
    float time;
    float hrtime = 0, imutime = 0;
    public ArrayList<Stand> maxStands = new ArrayList<Stand>();

    File imufile=null;
    File hrfile=null;
    final String pTAG = "Processs30SCSData";

    //static client declared here so it can be accessed anywhere in the app
    static public MQTTClient myClient;

    Vibrator vibrator;

    Handler accHandler = new Handler();
    Handler stopaccHandler = new Handler();
    Handler stoptestHandler = new Handler();

    Runnable accRun = new Runnable(){
        public void run(){
            mSensorManager.registerListener(mListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 10000);
            mSensorManager.registerListener(mListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 10000);
            vibrator.vibrate(VibrationEffect.createOneShot(1000, 250));
        }
    };

    Runnable stopaccRun = new Runnable(){
        @Override
        public void run(){
            mSensorManager.unregisterListener(mListener, mSensor);
            mSensorManager.unregisterListener(mListener, gyroSensor);
            vibrator.vibrate(VibrationEffect.createOneShot(1000, 250));
	        new CountDownTimer(15000, 1000) {
                public void onTick(long millisUntilFinished) {
                    mTextCount.setText("" + millisUntilFinished / 1000);
                }
                public void onFinish() {
                    mSensorManager.unregisterListener(mListener, hrSensor);   // try unregistering just hr listener
                }
            }.start();
        }
    };

    Runnable stoptestRun = new Runnable(){
        @Override
        public void run(){
            ProcessData();
            stands = maxStands.size();
            time = (float)(IMU.timeStamp.size())/100;
            Log.e("UAH", "time "+time+" stands "+stands+" deltaHR "+deltaHR);
            Intent end = new Intent(getApplicationContext(), Result.class);
            end.putExtra("stands",stands);
            end.putExtra("time",time);
            end.putExtra("dHR",deltaHR);
            startActivity(end);
            finish();
        }
    };

    Calendar c = Calendar.getInstance();
    SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmm");
    String startTime = df.format(c.getTime());

    //private String android_id; //= Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);

    /* ***********************************************
        Data structure class definition
       ***********************************************
     */

    public class IMUData{
        ArrayList<Float> timeStamp = new ArrayList<Float>();
        ArrayList<Float> relativeTime = new ArrayList<Float>();
        public ArrayList<Float> xAcc = new ArrayList<Float>();
        ArrayList<Float> yAcc = new ArrayList<Float>();
        ArrayList<Float> zAcc = new ArrayList<Float>();
        ArrayList<Float> xGy = new ArrayList<Float>();
        ArrayList<Float> yGy = new ArrayList<Float>();
        ArrayList<Float> zGy = new ArrayList<Float>();
    }

    public class HRData{
        ArrayList<Float> hrts = new ArrayList<Float>();
        ArrayList<Float> hr = new ArrayList<Float>();

    }

    public class Stand {
        public float timecount;
        public float gxyval;

        public Stand(float tcount, float gxycount){
            timecount = tcount;
            gxyval = gxycount;
        }
    }
    
    public IMUData IMU = new IMUData();
    public HRData HR = new HRData();

    private TextView mTextCount;
    private TextView mHRData;
    private TextView mTextbutton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android_id = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        context = getApplicationContext();
        imufile = new File(getExternalFilesDir(null), Build.MODEL+"_30SCS_IMU_"+startTime+".txt");
		writeheader(imufile.toString());
        hrfile = new File(getExternalFilesDir(null), Build.MODEL+"_30SCS_HR_"+startTime+".txt");
	    writeheader(hrfile.toString());

        //initialize client
        myClient = new MQTTClient(this.getApplicationContext());
    } // end of onCreate()

    private void writeheader(String file) {
        String print_acc_header = "time,AX,AY,AZ,GX,GY,GZ";
        String print_hr_header = "time,HeartRate";
        byte[] header;
        try {
            FileOutputStream os = new FileOutputStream(file, true);
            if (file.contains("IMU") == true) {
                //ACC file, write ACC file header
                header = print_acc_header.getBytes();
            }
            else {
                //HR file, write HR file header
                header = print_hr_header.getBytes();
            }
            String newline = "\n";
            byte[] nl = newline.getBytes();

            os.write(header);
            os.write(nl);
            os.close();
        } catch(IOException e){
            Log.w("ExternalStorage", "Error writing " + file, e);
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        setContentView(R.layout.activity_display_message);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mTextCount = (TextView)findViewById(R.id.countDown);
        mHRData = (TextView)findViewById(R.id.hrData);
        new CountDownTimer(15000, 1000) {

            public void onTick(long millisUntilFinished) {
                mTextCount.setText("" + millisUntilFinished / 1000);
            }

            public void onFinish() {
                //mTextCount.setText("done!");
            }
        }.start();

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        vibrator.vibrate(VibrationEffect.createOneShot(1000, 250));

        //////////////////////////////////
        // Enables Always-on
        setAmbientEnabled();
        /////////////////////////////////

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        hrSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

        accHandler.postDelayed(accRun, 15000);
        stopaccHandler.postDelayed(stopaccRun,45000);
        stoptestHandler.postDelayed(stoptestRun, 60000);

        mListener = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor arg0, int arg1) {
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor sensor = event.sensor;

                if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                    float time = event.timestamp;

                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];

                    //String eventTime = String.format(time);
                    x_value = String.format("%.2f", x);
                    y_value = String.format("%.2f", y);
                    z_value = String.format("%.2f", z);

                    senseTime= String.valueOf(time);

                    // Input IMU data into arraylist IMU

                    IMU.timeStamp.add(time);
                    IMU.relativeTime.add(imutime);
                    IMU.xAcc.add(x);
                    IMU.yAcc.add(y);
                    IMU.zAcc.add(z);
                    IMU.xGy.add(gx);
                    IMU.yGy.add(gy);
                    IMU.zGy.add(gz);
		            //imutime = imutime + ((long) (event.sensor.getMinDelay() > sensorSamplingPeriodInt ? event.sensor.getMinDelay() : sensorSamplingPeriodInt)) / 1000000;
		            imutime = ++imutime;

                } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                    gx = event.values[0];
                    gy = event.values[1];
                    gz = event.values[2];

                    gx_value = String.format("%.2f",gx);
                    gy_value = String.format("%.2f",gy);
                    gz_value = String.format("%.2f",gz);

                } else if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {

                    String msg = "" + (int) event.values[0];
                    hr_value = event.values[0];

                    mHRData.setText(msg);

                    HR.hrts.add(hrtime);
                    HR.hr.add(hr_value);
		            hrtime = ++hrtime;
                }
            } // end of onSensorChanged()
        }; // end of SensorEventListener()

        mSensorManager.registerListener(mListener, mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE), SensorManager.SENSOR_DELAY_FASTEST);

    } // end of onResume()


    protected void onPause(){
        super.onPause();
        mSensorManager.unregisterListener(mListener);

        accHandler.removeCallbacks(accRun);
        stopaccHandler.removeCallbacks(stopaccRun);
        stoptestHandler.removeCallbacks(stoptestRun);

        String comma = ",";
        String newline = "\n";
        byte[] com = comma.getBytes();
        byte[] nl = newline.getBytes();

        try{
            FileOutputStream imuf = new FileOutputStream(imufile, true);
            int size=IMU.timeStamp.size();
            for(int i=0;i<size;i++){

                byte[] ts = String.valueOf(IMU.relativeTime.get(i)).getBytes();
                byte[] xacc = String.format("%.2f",IMU.xAcc.get(i)).getBytes();
                byte[] yacc = String.format("%.2f",IMU.yAcc.get(i)).getBytes();
                byte[] zacc = String.format("%.2f",IMU.zAcc.get(i)).getBytes();
                byte[] xgy = String.format("%.2f",IMU.xGy.get(i)).getBytes();
                byte[] ygy = String.format("%.2f",IMU.yGy.get(i)).getBytes();
                byte[] zgy = String.format("%.2f",IMU.zGy.get(i)).getBytes();
                imuf.write(ts);
                imuf.write(com);
                imuf.write(xacc);
                imuf.write(com);
                imuf.write(yacc);
                imuf.write(com);
                imuf.write(zacc);
                imuf.write(com);
                imuf.write(xgy);
                imuf.write(com);
                imuf.write(ygy);
                imuf.write(com);
                imuf.write(zgy);
                imuf.write(nl);

            }

        } catch (
                IOException e) {
            Log.w("ExternalStorage", "Error writing " + imufile, e);
        }

		 try{
            FileOutputStream ftest = new FileOutputStream(hrfile, true);
            int size=HR.hrts.size();
            for(int i=0;i<size;i++){

                byte[] ts = String.valueOf(HR.hrts.get(i)).getBytes();
                byte[] hrdat = String.format("%.2f",HR.hr.get(i)).getBytes();

                ftest.write(ts);
                ftest.write(com);
                ftest.write(hrdat);
                ftest.write(nl);
            }

        } catch (
                IOException e) {
            Log.w("ExternalStorage", "Error writing " + hrfile, e);
        }
		
        final File resultfile = new File(getExternalFilesDir(null), Build.MODEL+"_30SCS_RESULT_"+startTime+".txt");

        String total = "Total Time: "+(((float)IMU.timeStamp.size())/100)+" Total Steps: "+maxStands.size();

        try
        {
            FileOutputStream dat = new FileOutputStream(resultfile, true);

            dat.write(total.getBytes());
            dat.write(nl);

	        for(int count=0; count<maxStands.size(); count++)
	        {
                String steps = "Step :"+(count+1)+" Time: "+maxStands.get(count).timecount;
		        dat.write(steps.getBytes());
                dat.write(nl);
                if(count>0) {
                    standDuration += maxStands.get(count).timecount - maxStands.get(count-1).timecount;
                }
            }
            String duration = "Average Stand Duration :"+String.format("%.3f",(standDuration/(maxStands.size()-1)));
            dat.write(duration.getBytes());
            dat.write(nl);
            String dheartrate = "Delta in Heart rate from Start to End :"+String.format(String.valueOf(deltaHR));
            dat.write(dheartrate.getBytes());
            dat.write(nl);
        }
        catch (IOException e)
        {
            Log.w("ExternalStorage", "Error writing " + resultfile, e);
        }

        imudata = "/storage/emulated/0/Android/data/com.example.thirtyscs/files/"+Build.MODEL+"_30SCS_IMU_"+startTime+".txt";
		hrdata = "/storage/emulated/0/Android/data/com.example.thirtyscs/files/"+Build.MODEL+"_30SCS_HR_"+startTime+".txt";
        resultdata = "/storage/emulated/0/Android/data/com.example.thirtyscs/files/"+Build.MODEL+"_30SCS_RESULT_"+startTime+".txt";

        uploadData(String.valueOf(((float)IMU.timeStamp.size())/100),String.valueOf(maxStands.size()),String.valueOf(deltaHR));

    } // end of onPause()

    public void uploadData(String totalTime, String totalSteps, String stepRatio) {

        if (isOnline(this)) {

            myClient.publishMessage(totalTime,"UAHSMARTWATCH/30SCST/TIME/");
            myClient.publishMessage(totalSteps,"UAHSMARTWATCH/30SCST/STANDS/");
            myClient.publishMessage(stepRatio,"UAHSMARTWATCH/30SCST/DELTAHR/");
            String path = getExternalFilesDir(null).getAbsolutePath();

            File directory = null;
            try {
                directory = new File(path);
                Log.i("FtpActivity", "dir = " + directory);
            } catch (Exception e) {
                Log.i("FtpActivity", "Uri e = " + e);
            }
            File[] files = directory.listFiles();

            connectAndUpload(files);

        } else {
            Log.i("FtpActivity", "Please check your internet connection!");
        }
    }

    private boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }
    private void connectAndUpload(File[] files) {

        new Thread(new Runnable() {
            public void run() {
                final String host = "ftp.drivehq.com";
                final String TAG = "FtpActivity";
                final String username = "talksuresh";
                final String password = "UAHThesis2022";

                FTPClientFunctions ftpclient = new FTPClientFunctions();

                boolean status = ftpclient.ftpConnect(host, username, password, 21);
                if (status == true) {
                    Log.d(TAG, "FTP Connection Success");
                    if (files != null) {
                        for (int i = 0; i < files.length; i++) {
                            String filename = files[i].getName();
                            Log.d(TAG, "Uploading the file " + filename);
                            status = ftpclient.ftpUpload(filename, filename, context);

                            if (status == true) {
                                Log.d(TAG, "Upload success " + filename);
                            } else {
                                Log.d(TAG, "Upload failed " + filename);
                                return;
                            }
                            Log.d(TAG, "Delete file from local storage " + filename);
                            files[i].delete();
                        }
                    }
                }
                else {
                    Log.d(TAG, "Connection failed");
                }
            }
        }).start();
    }

    public void ProcessData(){
        int count=0;
        int size=0;
        int numberOfStands=0; // number of stands
        boolean ascend=false;

        // Store Time
        size=IMU.timeStamp.size();
        float[] timecount = new float[size];
        for(count=0;count<size;count++){
            timecount[count] = IMU.relativeTime.get(count);
        }

        // store xGy+yGy values
        float[] gxy = new float[size];
        for(count=0;count<size-1;count++){
            gxy[count] = IMU.xGy.get(count)+IMU.yGy.get(count);
            //Log.d(pTAG, "gxy " + gxy[count]);
        }

        //Find gxy zerocrossing values
        boolean [] zerogxy = new boolean[size];
        for(count=0;count<size-1;count++)
        {
            if(((gxy[count]>=0)&&gxy[count+1]<0) || ((gxy[count]<=0)&&(gxy[count+1]>0))) {
                zerogxy[count+1]=true;
                //Log.d(pTAG, "Zero Crossing "+timecount[count+1]+" gxyval "+gxy[count+1]+" zerogxy count "+(count+1));
            }
        }

        //Find peak gxy Magnitude values
        if(size>1)
        {
            if(gxy[0]<gxy[1]){
                ascend = true;
            }
        }

        boolean [] peakgxy = new boolean[size];
        for(count=0;count<size-8;count++)
        {
            if((ascend)&&(gxy[count]>0.5)&&(gxy[count+1]<gxy[count])&&(gxy[count+8]<gxy[count])) {//last check is to prevent small/negligable spikes
                peakgxy[count] = true;
                System.out.println("Peak Value "+timecount[count]+" gxy "+gxy[count]);
                ascend = false;
            }
            else if((!ascend)&&(((gxy[count+1]-gxy[count]))>0)){
                //System.out.println("Valley Value "+timecount[count]+" gxy "+gxy[count]);
                ascend = true;
            }
        }

        //////////////////////////////
        // START OF ALGORITHM
        //////////////////////////////

        int lastStandCount = 0;
        //find stands from zero crossing and valley values
        //use 2 seconds as threshold to allow sit to stand time
        for(count=0;count<size-1;count++)
        {
            boolean standFound = false;
            int standCount = 0;
            float peakVal = 0;

            if(zerogxy[count]==true)
            {
                System.out.println("looking for zerogxy count "+count+" time count "+timecount[count]);
                // check for highest peak after zero crossing
                for(int peakCount=count+1;(zerogxy[peakCount]!=true)&&(peakCount<size-1);peakCount++)
                {
                    // Find the First peak after zero crossing
                    if(peakgxy[peakCount]==true)
                    {
                        peakVal = gxy[peakCount];
                        standFound = true;
                        standCount = peakCount;
                        System.out.println("peak Found "+timecount[peakCount]);
                        break;
                    }
                }

                if((standFound == true)&&(standCount>=lastStandCount+25))
                {
                    lastStandCount = standCount;
                    numberOfStands++; //Increment stand counter
                    if(numberOfStands%2!=0)//count only even stands(standing)
                    {
                        System.out.println("Adding Stand "+timecount[standCount]+" gxy "+gxy[standCount]);
                        addStand((timecount[standCount]/100), gxy[standCount]);
                    }
                }
                else
                {
                    System.out.println("Not Adding Stand "+standCount+" lastStandCount "+lastStandCount);
                }
            }
        }

	int hrsize=HR.hrts.size()-1;
	float startHR=0, endHR=0;
    int startHRcount = 0;
    for(int i=0;i<hrsize;i++){
	    if(HR.hr.get(i)>0)
	    {
	        startHR = HR.hr.get(i);
            startHRcount = i;
		    break;
        }
    }
	endHR = HR.hr.get(hrsize);
	deltaHR=(int)(endHR-startHR);
    Log.e("UAH", "startHRcount "+startHRcount+" startHR "+startHR+" endHR count "+hrsize+" endHR "+endHR+" deltaHR "+deltaHR);

    } // end of ProcessData()

    public boolean addStand(float val, float gxycount){
        maxStands.add(new Stand(val,gxycount));
        return true;
    }
} // end of DisplayMessageActivity
