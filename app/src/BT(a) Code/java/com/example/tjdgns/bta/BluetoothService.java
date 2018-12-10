package com.example.tjdgns.bta;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {
    //intent request
    private static final int REQUEST_CONNEXT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    //debugging
    private static final String TAG = "BluetoothService";
    //RFCOMM protocool
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public int mMode;

    // 상태를 나타내는 상태 변수
    public static final int STATE_NONE = 1; // 아무것도 하지 않을 때
    public static final int STATE_LISTEN = 2; // 연결을 위해 리스닝에 들어갈 때
    public static final int STATE_CONNECTING = 3; // 연결 과정이 이루어 질 때
    public static final int STATE_CONNECTED = 4; // 기기 사이에서의 연결이 이루어 졌을 때
    public static final int STATE_FAIL = 7; // 연결이 실패 했을 때

    private int mState;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private BluetoothAdapter btAdapter;
    private Activity mActivity;
    private Handler mHandler;

    //bluetoothService 생성자
    public BluetoothService(Activity activity, Handler handler) {
        mActivity = activity;
        mHandler = handler;

        //bluetoothAdapter 얻기
        btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /* (1) getDeviceState() : 먼저 기기의 블루투스 지원여부를 확인한다.*/
    public boolean getDeviceState() {
        Log.d(TAG, "Check the Bluetooth support");

        if (btAdapter == null) {
            Log.d(TAG, "Bluetooth is not available");
            return false;
        } else {
            Log.d(TAG, "Bluetooth is available");
            return true;
        }
    }

    /*(2) enableBluetooth() : bluetooth활성화 메소드 (getDeviceState가 true를 반환시 활성화를 요청)*/
    public void enableBluetooth() {
        Log.i(TAG, "Check the enable Bluetooth");

        //기기의 블루투스 상태가 On일 경우 장치 검색 시작
        if (btAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth Enable Now");
            scanDevice();

        }
        //기기의 블루투스 상태가 off일 경우
        else {
            Log.d(TAG, "Bluetooth Enable Request");

            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(i, REQUEST_ENABLE_BT);
        }
    }

    //접속가능한 기기 스캔
    public void scanDevice() {
        Log.d(TAG, "Scan Device");
        // 인텐트로 액티비티를 디바이스 서치 클래스로 넘김
        Intent serverIntent = new Intent(mActivity, DeviceListActivity.class);
        //새로운 액티비티를 띄워 처리된 결과를 main activity로 전달
        mActivity.startActivityForResult(serverIntent, REQUEST_CONNEXT_DEVICE);
    }


    /* setState() : Bluetooth 상태를 set한다.*/
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // 핸들러를 통해 상태를 메인에 넘겨준다.
        mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }


    /* getState() : Bluetooth 상태를 get한다. */
    public synchronized int getState() {
        return mState;
    }


    //Thread관련 service를 시작하는 start메소드를 작성합니다.

    /* start() : Thread관련 service를 시작합니다.*/
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread == null) {

        } else {
            mConnectThread.cancel();
            mConnectThread = null;
        }

    }

    /* getDeviceInfo() : 기기의 주소를 가져와 정보를 connect 메소드에 넘긴다.*/
    public void getDeviceInfo(Intent data)
    {
        //MAC address를 가져온다.
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        //BluetoothDevice object를 가져온다
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        Log.d(TAG, "Get Device Info \n" + "address : "+address);

        connect(device);
    }

    /* connect() : ConnectThread 초기화와 시작 device의 모든 연결 제거*/
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread == null) {

            } else {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread == null) {

        } else {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);

        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /* connected() : ConnectedThread 초기화*/
    public synchronized void connected(BluetoothSocket socket,
                                       BluetoothDevice device) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread == null) {

        } else {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread == null) {

        } else {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
    }

    /* stop() : 모든 thread stop */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }



    /* ConnectThread() : 소켓과 쓰레드를 생성하여 기기사이의 connecttion을 가능하게 합니다.*/
    private class ConnectThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device)
        {
            mmDevice = device;
            BluetoothSocket tmp = null;

            //디바이스 정보를 얻어서 BluetoothSocket 생성
            try
            {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            }
            catch(IOException e)
            {
                Log.e(TAG, "create() failed",e);
            }
            mmSocket = tmp;
        }

        public void run()
        {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // 연결을 시도하기 전에는 항상 기기 검색을 중지한다.
            // 기기 검색이 계속되면 연결속도가 느려지기 때문이다.
            btAdapter.cancelDiscovery();

            // BluetoothSocket 연결 시도
            try
            {
                // BluetoothSocket 연결 시도에 대한 return 값은 succes 또는 exception이다.
                mmSocket.connect();
                Log.d(TAG, "Connect Success");
            }
            catch(IOException e)
            {
                connectionFailed(); //연결 실패 시 불러오는 메소드
                Log.d(TAG, "Connect Fail");

                //소켓을 닫는다.
                try
                {
                    mmSocket.close();
                }
                catch(IOException e2)
                {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                //연결 중 혹은 연결 대기상태인 메소드를 호출
                BluetoothService.this.start();
                return;
            }
            // ConnectThread 클래스를 reset한다.
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }
            // ConnectThread를 시작한다.
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    // connectedThread 메소드
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

// BluetoothSocket의 inputstream 과 outputstream을 얻는다.
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

// Keep listening to the InputStream while connected
            while (true) {
                try {
// InputStream으로부터 값을 받는 읽는 부분(값을 받는다)
                    bytes = mmInStream.read(buffer);

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer, int mode) {
            try {
                // 값을 쓰는 부분(값을 보낸다)
                mmOutStream.write(buffer);
                mMode = mode;

                if(mode == MainActivity.MODE_REQUEST)
                {
                    //버퍼에 담은 메세지를 main activity로 전송
                    mHandler.obtainMessage(MainActivity.MESSAGE_WRITE,-1,-1,buffer).sendToTarget();
                }

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }



    /* write() : 쓰레드를 통해 값을 쓰는 부분(보내는 부분) */
    public void write(byte[] out, int mode) { // Create temporary object
        ConnectedThread r; // Synchronize a copy of the ConnectedThread

        synchronized (this) {
            if (mState != STATE_CONNECTED)  // 블루투스에 연결되는 상태일 때 값을 쓰지 않음
                return;
            r = mConnectedThread;   // 그렇지 않으면 ConnectedThread 객체 생성
        } // Perform the write unsynchronized r.write(out); }

        r.write(out,mode);  //ConnectedThread클래스 내에 있는 write함수를 호출하여 메시지를 보낸다.
    }

    /* connectionFailed() : 연결 실패했을때 */
    private void connectionFailed() {
        setState(STATE_FAIL);
    }

    /* connectionLost() : 연결을 잃었을 때 */
    private void connectionLost() {
        setState(STATE_LISTEN);
    }


}
