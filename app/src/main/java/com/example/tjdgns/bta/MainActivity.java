package com.example.tjdgns.bta;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity  implements SensorEventListener {

    private static final boolean D = true;
    //debugging
    private static final String TAG = "MAIN";

    // Sending Mode ( Realtime, requested )
    public static final int MODE_REQUEST = 1 ; // button state
    private int mSelectedBtn ;
    // synchronized flags
    private static final int STATE_SENDING = 1 ;
    private static final int STATE_NO_SENDING = 2 ;
    private int mSendingState ;

    //Intent request mode
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    //Messeages
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_WRITE = 2;

    // 게임 상태
    public static int gState;

    //가속도 센서
    float accelXValue;
    float accelYValue;
    float accelZValue;

    private SensorManager mSensorManager;
    private Sensor accSensor;

    TextView x1;
    TextView y1;
    TextView z1;

    // 타이머
    TextView timer;
    long mBaseTime;

    final static int IDLE = 0;
    final static int RUNNING = 1;

    int tState = IDLE;//처음 상태는 IDLE

    DecimalFormat df = new DecimalFormat("0.0"); //float 형의 소수점 지정

    //layout
    private Button btn_Connect;
    private Button mbtn1;
    private Button mbtn2;
    //bluetoothservice 클래스에 접근하는 객체
    private BluetoothService bluetoothService_obj = null;
    private StringBuffer mOutStringBuffer;

    Handler mTimer = new Handler(){
        public void handleMessage(Message msg){
            timer.setText(getTimeOut());

            //sendEmptyMessage 는 비어있는 메세지를 Handler 에게 전송하는겁니다.
            mTimer.sendEmptyMessage(0);
        }
    };

    //핸들러의 기능을 수행할 클래스(handleMessage)
    private final Handler mHandler = new Handler() {
        //BluetoothService로부터 메시지(msg)를 받는다.
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what)
            {
                case MESSAGE_STATE_CHANGE :
                    if(D) Log.i(TAG,"MESSAGE_STATE_CHANGE : " + msg.arg1);  // msg 가공

                    switch ( msg.arg1)
                    {
                        //연결 성공
                        case BluetoothService.STATE_CONNECTED :
                            Toast.makeText(getApplicationContext(),"연결 성공", Toast.LENGTH_SHORT).show();
                            break;

                        //연결 실패
                        case BluetoothService.STATE_FAIL :
                            Toast.makeText(getApplicationContext(),"연결 실패", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;

                case MESSAGE_WRITE :
                    String writeMessage = null ;

                    if ( mSelectedBtn == 1 ) {
                        writeMessage = mbtn1.getText().toString();
                        mSelectedBtn = -1 ;
                    } else if ( mSelectedBtn == 2 ) {
                        writeMessage = mbtn2.getText().toString() ;
                        mSelectedBtn = -1 ;
                    } else { // mSelectedBtn = -1 : not selected

                        byte[] writeBuf = (byte[]) msg.obj;
                        // construct a string from the buffer
                        writeMessage = new String(writeBuf);
                    }

                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG,"onCreate");
        setContentView(R.layout.activity_main);

        mSelectedBtn = -1;

        //main layout
        btn_Connect = (Button)findViewById(R.id.bluetooth_connect);
        btn_Connect.setOnClickListener(mClickListener);
        mbtn1 = (Button)findViewById(R.id.btn1);
        mbtn1.setOnClickListener(mClickListener);
        mbtn2 = (Button)findViewById(R.id.btn2);
        mbtn2.setOnClickListener(mClickListener);
        x1 = (TextView) findViewById(R.id.TextViewX);
        y1 = (TextView) findViewById(R.id.TextViewY);
        z1 = (TextView) findViewById(R.id.TextViewZ);
        timer = (TextView) findViewById(R.id.Time);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if(bluetoothService_obj == null)
            bluetoothService_obj = new BluetoothService(this, mHandler);
        mOutStringBuffer = new StringBuffer("");
    }

    @Override

    protected void onDestroy() {
        mTimer.removeMessages(0);//메시지를 지워서 메모리릭 방지
        super.onDestroy();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, accSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    /*블루투스 접속에 따른 결과를 처리하는 메소드 이다.*/
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        Log.d(TAG, "onActivityResult" + resultCode);

        switch(requestCode)
        {

            case REQUEST_ENABLE_BT:
                //When the request to enable Bluetooth returns
                // 블루투스를 활성화 시켰을 경우
                if(resultCode == Activity.RESULT_OK)
                {
                    bluetoothService_obj.scanDevice();
                }
                // 블루투스를 활성화 취소
                else
                {
                    Log.d(TAG,"Bluetooth is not enable");
                }
                break;

            case REQUEST_CONNECT_DEVICE:
                if(resultCode == Activity.RESULT_OK)
                {
                    bluetoothService_obj.getDeviceInfo(data);
                }
                break;
        }
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //분기.
            switch ( v.getId() ){

                case R.id.bluetooth_connect : //모든 블루투스의 활성화는 블루투스 서비스 객체를 통해 접근한다.
                    if(bluetoothService_obj.getDeviceState()) // 블루투스 기기의 지원여부가 true 일때
                    {
                        bluetoothService_obj.enableBluetooth(); //블루투스 활성화 시작.
                    }
                    else
                    {
                        finish();
                    }
                    break ;

                case R.id.btn1 :
                    //연결된 상태에서만 값을 보낸다.
//                    if( bluetoothService_obj.getState() == BluetoothService.STATE_CONNECTED){
//                        sendMessage("s", MODE_REQUEST);
//                        gState = 1;
//                        mSelectedBtn = 1;
//
//                        //타이머에 현재 값 세팅
//                        mBaseTime = SystemClock.elapsedRealtime();
//                        mTimer.sendEmptyMessage(0);
//
//                    }else {
//                        Toast.makeText(getApplicationContext(), "블루투스 연결을 먼저 해 주세요!! ", Toast.LENGTH_SHORT).show();
//                    }
                    mBaseTime = SystemClock.elapsedRealtime();
                    mTimer.sendEmptyMessage(0);

                    mbtn1.setEnabled(false);
                    mbtn2.setEnabled(true);
                    break ;

                case R.id.btn2 :
//                    if( bluetoothService_obj.getState() == BluetoothService.STATE_CONNECTED){
//                        sendMessage( "f", MODE_REQUEST ) ;
//                        gState = 0;
//                        mSelectedBtn = 2 ;
//
//                        mTimer.removeMessages(0);
//
//                        mbtn1.setEnabled(true);
//                        mbtn2.setEnabled(false);
//                        break;
//                    }else {
//                        Toast.makeText(getApplicationContext(), "블루투스 연결을 먼저 해 주세요!! ", Toast.LENGTH_SHORT).show();
//                    }
//                    break ;
                    mTimer.removeMessages(0);

                    mbtn1.setEnabled(true);
                    mbtn2.setEnabled(false);
                    break;

                default: break ;

            }//switch
        }
    };

    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            accelXValue = event.values[0];
            accelYValue = event.values[1];
            accelZValue = event.values[2];

            accelXValue = Float.parseFloat(df.format(accelXValue));
            accelYValue = Float.parseFloat(df.format(accelYValue));
            accelZValue = Float.parseFloat(df.format(accelZValue));

            x1.setText("X: "+accelXValue);
            y1.setText("Y: "+accelYValue);
            z1.setText("Z: "+accelZValue);

            String val = (accelXValue + "X" + accelYValue + "Y" + "\0");

            if( bluetoothService_obj.getState() == BluetoothService.STATE_CONNECTED){
                sendMessage(val, MODE_REQUEST);
            }
        }
    }

    /*메시지를 보낼 메소드 정의*/
    private synchronized void sendMessage( String message, int mode ) {

        if ( mSendingState == STATE_SENDING ) {
            try {
                wait() ;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mSendingState = STATE_SENDING ;

// Check that we're actually connected before trying anything
        if ( bluetoothService_obj.getState() != BluetoothService.STATE_CONNECTED ) {
            mSendingState = STATE_NO_SENDING ;
            return ;
        }

// Check that there's actually something to send
        if ( message.length() > 0 ) {
// Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes() ;
            bluetoothService_obj.write(send, mode) ;

// Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0) ;

        }

        mSendingState = STATE_NO_SENDING ;
        notify() ;
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    String getTimeOut(){
        long now = SystemClock.elapsedRealtime();
        long outTime = now - mBaseTime;
        String easy_outTime = String.format("%02d:%02d:%02d", outTime/1000 / 60, (outTime/1000)%60,(outTime%1000)/10);
        return easy_outTime;
    }
}
