package com.example.tjdgns.bta;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

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



    //layout
    private Button btn_Connect;
    private Button mbtn1;
    private Button mbtn2;
    //bluetoothservice 클래스에 접근하는 객체
    private BluetoothService bluetoothService_obj = null;
    private StringBuffer mOutStringBuffer;


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
                        writeMessage = mbtn1.getText().toString() ;
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

        if(bluetoothService_obj == null)
            bluetoothService_obj = new BluetoothService(this, mHandler);
        mOutStringBuffer = new StringBuffer("");
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
                        sendMessage("0", MODE_REQUEST);
                        mSelectedBtn = 1;
                    }else {
                        Toast.makeText(getApplicationContext(), "블루투스 연결을 먼저 해 주세요!! ", Toast.LENGTH_SHORT).show();
                    }
                    break ;

                case R.id.btn2 :

                    if( bluetoothService_obj.getState() == BluetoothService.STATE_CONNECTED){
                        sendMessage( "1", MODE_REQUEST ) ;
                        mSelectedBtn = 2 ;
                    }else {
                        Toast.makeText(getApplicationContext(), "블루투스 연결을 먼저 해 주세요!! ", Toast.LENGTH_SHORT).show();
                    }
                    break ;

                default: break ;

            }//switch
        }
    };

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

}
