package com.example.tjdgns.bta;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    public static int gState;   // 0 : not running | 1 : running | 2 : give up

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
    TextView readBuf;

    TextView timer;
    long mBaseTime;

    // 랭크
    TextView First;
    TextView Second;
    TextView Third;
    TextView Fourth;
    TextView Fifth;

    String[] key = new String[] {"1st","2nd","3rd","4th","5th"};
    long[] rankArr = new long[] {3599999,3599999,3599999,3599999,3599999};

    final static int IDLE = 0;
    final static int RUNNING = 1;

    int tState = IDLE;//처음 상태는 IDLE

    public static int checkVal = 0 ;
    public static String bufVal ;

    DecimalFormat df = new DecimalFormat("0.0"); //float 형의 소수점 지정

    //layout
    private Button btn_Connect;
    private Button mbtn1;
    private Button mbtn2;
    private Button mbtn3;

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
                            btn_Connect.setEnabled(false);
                            btn_Connect.setAlpha((float)0.3);
                            mbtn1.setEnabled(true);
                            mbtn1.setAlpha((float)1.0);
                            break;

                        //연결 실패
                        case BluetoothService.STATE_FAIL :
                            Toast.makeText(getApplicationContext(),"연결 실패", Toast.LENGTH_SHORT).show();
                            break;


                        case BluetoothService.STATE_LISTEN :
                            Toast.makeText(getApplicationContext(),"연결 종료", Toast.LENGTH_SHORT).show();
                            btn_Connect.setEnabled(true);
                            btn_Connect.setAlpha((float)1.0);
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
        mbtn3 = (Button)findViewById(R.id.btn3);
        mbtn3.setOnClickListener(mClickListener);

        x1 = (TextView) findViewById(R.id.TextViewX);
        y1 = (TextView) findViewById(R.id.TextViewY);
        z1 = (TextView) findViewById(R.id.TextViewZ);

        timer = (TextView) findViewById(R.id.Time);
        readBuf = (TextView) findViewById(R.id.readbuf);
        readBuf.setText("Receive data");

        First = (TextView) findViewById(R.id.First);
        Second = (TextView) findViewById(R.id.Second);
        Third = (TextView) findViewById(R.id.Third);
        Fourth = (TextView) findViewById(R.id.Fourth);
        Fifth = (TextView) findViewById(R.id.Fifth);

        SharedPreferences sf = getSharedPreferences("pref",0);

        for(int i = 0; i<5; i++) {
            rankArr[i] = Long.parseLong(sf.getString(key[i],"3599999"));
        }
        setRank();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if(bluetoothService_obj == null) {
            bluetoothService_obj = new BluetoothService(this, mHandler);
        }
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    protected void onStop() {

        super.onStop();
        mTimer.removeMessages(0);//메시지를 지워서 메모리릭 방지
        SharedPreferences sf = getSharedPreferences("pref",0);
        SharedPreferences.Editor editor = sf.edit();
        String str1 = String.valueOf(rankArr[0]);
        String str2 = String.valueOf(rankArr[1]);
        String str3 = String.valueOf(rankArr[2]);
        String str4 = String.valueOf(rankArr[3]);
        String str5 = String.valueOf(rankArr[4]);

        editor.putString(key[0],str1);
        editor.putString(key[1],str2);
        editor.putString(key[2],str3);
        editor.putString(key[3],str4);
        editor.putString(key[4],str5);

        editor.commit();
    }

    @Override
    protected void onDestroy() {
        mTimer.removeMessages(0);//메시지를 지워서 메모리릭 방지
        SharedPreferences sf = getSharedPreferences("pref",0);
        SharedPreferences.Editor editor = sf.edit();
        String str1 = String.valueOf(rankArr[0]);
        String str2 = String.valueOf(rankArr[1]);
        String str3 = String.valueOf(rankArr[2]);
        String str4 = String.valueOf(rankArr[3]);
        String str5 = String.valueOf(rankArr[4]);

        editor.putString(key[0],str1);
        editor.putString(key[1],str2);
        editor.putString(key[2],str3);
        editor.putString(key[3],str4);
        editor.putString(key[4],str5);

        editor.commit();
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
                    if( bluetoothService_obj.getState() == BluetoothService.STATE_CONNECTED){
                        sendMessage("s", MODE_REQUEST);
                        gState = 1;
                        checkVal = 1;
                        tState = RUNNING;
                        mSelectedBtn = 1;

                        //타이머에 현재 값 세팅
                        mBaseTime = SystemClock.elapsedRealtime();
                        mTimer.sendEmptyMessage(0);

                        mbtn1.setEnabled(false);
                        mbtn1.setAlpha((float)0.3);
                        mbtn2.setEnabled(true);
                        mbtn2.setAlpha((float)1.0);

                    }else {
                        Toast.makeText(getApplicationContext(), "블루투스 연결을 먼저 해 주세요!! ", Toast.LENGTH_SHORT).show();
                    }
                    break ;

                case R.id.btn2 :
                    if( bluetoothService_obj.getState() == BluetoothService.STATE_CONNECTED){
                        sendMessage( "2", MODE_REQUEST ) ;
                        mSelectedBtn = 2 ;
                        gState = 0;
                        checkVal = 0;

                        tState = IDLE;

                        mTimer.removeMessages(0);

                        mbtn1.setEnabled(true);
                        mbtn1.setAlpha((float)1.0);
                        mbtn2.setEnabled(false);
                        mbtn2.setAlpha((float)0.3);
                    }else {
                        Toast.makeText(getApplicationContext(), "블루투스 연결을 먼저 해 주세요!! ", Toast.LENGTH_SHORT).show();
                    }
                    break ;

                case R.id.btn3 :
                    for(int i = 0; i < 5; i++)
                    {
                        rankArr[i] = 3599999;
                    }
                    setRank();
                    break;

                default: break ;

            }//switch
        }
    };

    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if( bluetoothService_obj.getState() != BluetoothService.STATE_CONNECTED){
            btn_Connect.setEnabled(true);
            btn_Connect.setAlpha((float)1.0);
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            accelXValue = event.values[0];
            accelYValue = event.values[1];
            accelZValue = event.values[2];

            accelXValue = Float.parseFloat(df.format(accelXValue));
            accelYValue = Float.parseFloat(df.format(accelYValue));
            accelZValue = Float.parseFloat(df.format(accelZValue));

            accelXValue = convertVal(accelXValue);
            accelYValue = convertVal(accelYValue);
            accelZValue = convertVal(accelZValue);

            x1.setText("X: "+accelXValue);
            y1.setText("Y: "+accelYValue);
            z1.setText("Z: "+accelZValue);


            String val = ((char)accelXValue + "0" + (char)((accelYValue - 90) * (-1) + 90) + "1");

            readBuf.setText(bufVal);
            if(gState == 0 && checkVal == 1) {
                Endgame();
                checkVal = 0;
            }

            if( bluetoothService_obj.getState() == BluetoothService.STATE_CONNECTED && gState == 1){
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
        String easy_outTime = String.format("%02d:%02d.%02d", outTime/1000 / 60, (outTime/1000)%60,(outTime%1000)/10);
        return easy_outTime;
    }

    void Endgame()
    {
        long now = SystemClock.elapsedRealtime();
        long outTime = now - mBaseTime;
        long temp = 0;
        long temp2 = 0;
        int chk = 0;
        tState = IDLE;

        mTimer.removeMessages(0);

        mbtn1.setEnabled(true);
        mbtn1.setAlpha((float)1.0);
        mbtn2.setEnabled(false);
        mbtn2.setAlpha((float)0.3);

        for(int i = 0; i < 5; i++)
        {
            if(chk == 1)
            {
                temp2 = rankArr[i];
                rankArr[i] = temp;
                temp = temp2;
            }

            if(outTime < rankArr[i] && chk == 0)
            {
                temp = rankArr[i];
                rankArr[i] = outTime;
                chk = 1;
            }
        }
        setRank();
    }

    String convertTime(long outTime)
    {
        String mTime = String.format("%02d:%02d.%02d", outTime/1000 / 60, (outTime/1000)%60,(outTime%1000)/10);
        return mTime;
    }


    int convertVal(float val)
    {
        float temp;
        int result;

        if(val >= 6.0)
        {
            val = (float)6.0;
        }
        else if(val <= -6.0)
        {
            val = (float)(6.0 * -1.0);
        }

        temp = val * (float)3.33;
        result = (int)temp + 90;

        return result;
    }

    void setRank()
    {
        if(rankArr[0] != 3599999)
            First.setText("1    " + convertTime(rankArr[0]));
        else
            First.setText("1    --:--.--");

        if(rankArr[1] != 3599999)
            Second.setText("2    " + convertTime(rankArr[1]));
        else
            Second.setText("2    --:--.--");


        if(rankArr[2] != 3599999)
            Third.setText("3    " + convertTime(rankArr[2]));
        else
            Third.setText("3    --:--.--");

        if(rankArr[3] != 3599999)
            Fourth.setText("4    " + convertTime(rankArr[3]));
        else
            Fourth.setText("4    --:--.--");


        if(rankArr[4] != 3599999)
            Fifth.setText("5    " + convertTime(rankArr[4]));
        else
            Fifth.setText("5    --:--.--");

    }


//
//    void clearUI()
//    {
//        btn_Connect.setAlpha((float)0.0);
//        mbtn1.setAlpha((float)0.0);
//        mbtn2.setAlpha((float)0.0);
//        mbtn3.((float)0.0);
//
//        x1.setAlpha((float)0.0);
//        y1.setAlpha((float)0.0);
//        z1.setAlpha((float)0.0);
//
//        timer.setAlpha((float)1.0);
//        readBuf.setAlpha((float)0.0);
//
//        First.setAlpha((float)0.0);
//        Second.setAlpha((float)0.0);
//        Third.setAlpha((float)0.0);
//        Fourth.setAlpha((float)0.0);
//        Fifth.setAlpha((float)0.0);
//    }
//
//    void setUI()
//    {
//        btn_Connect.setAlpha((float)1.0);
//        mbtn1.setAlpha((float)1.0);
//        mbtn2.setAlpha((float)1.0);
//        mbtn3.setAlpha((float)1.0);
//
//        x1.setAlpha((float)1.0);
//        y1.setAlpha((float)1.0);
//        z1.setAlpha((float)1.0);
//
//        timer.setAlpha((float)0.0);
//        readBuf.setAlpha((float)1.0);
//
//        First.setAlpha((float)1.0);
//        Second.setAlpha((float)1.0);
//        Third.setAlpha((float)1.0);
//        Fourth.setAlpha((float)1.0);
//        Fifth.setAlpha((float)1.0);
//
//    }
//
//
//    // 값 불러오기
//    private String getPreferences(String key,String outTime){
//        SharedPreferences pref = getSharedPreferences("pref", MODE_PRIVATE);
//        outTime = pref.getString(key, "");
//        return outTime;
//    }
//
//    // 값 저장하기
//    private void savePreferences(String key, long outTime){
//
//        String str = String.format("%02d:%02d:%02d", outTime/1000 / 60, (outTime/1000)%60,(outTime%1000)/10);
//        SharedPreferences pref = getSharedPreferences("pref", MODE_PRIVATE);
//        SharedPreferences.Editor editor = pref.edit();
//        editor.putString(key, str);
//        editor.commit();
//    }
//
//    // 값(ALL Data) 삭제하기
//    private void removeAllPreferences(){
//        SharedPreferences pref = getSharedPreferences("pref", MODE_PRIVATE);
//        SharedPreferences.Editor editor = pref.edit();
//        editor.clear();
//        editor.commit();
//    }

}
