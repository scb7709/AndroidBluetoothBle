package com.androidblueble.activity;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidblueble.MyBlue.MyBlueCommunicationManager;
import com.androidblueble.MyBlue.MyBuleConnectManager;
import com.androidblueble.MyBlue.MyBuleSearchManager;
import com.androidblueble.R;

import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {
    TextView result;
    private MyBuleSearchManager myBuleSerachManager;
    private MyBuleConnectManager myBuleConnectManager;
    String MAC = "D6:48:63:F7:97:79";//目标设备蓝牙地址  （笔者实际项目的中一款腕设备）
    private BluetoothGattCharacteristic WRITE_BluetoothGattCharacteristic;
    private BluetoothGatt mBluetoothGatt;//发现蓝牙服务，根据特征值处理数据交互
    boolean isconnect;//判断是否连接上设备了

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        result = (TextView) findViewById(R.id.result);
        findViewById(R.id.serach_deivceList).setOnClickListener(this);
        findViewById(R.id.serach_deivceOne).setOnClickListener(this);
        findViewById(R.id.check_deivceSport).setOnClickListener(this);
        findViewById(R.id.bangding_deivce).setOnClickListener(this);
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.serach_deivceList:
                serachBule(5000);
                break;
            case R.id.serach_deivceOne:
                serachBule(0);
                break;
            case R.id.check_deivceSport:
                if (isconnect) {
                    byte[] bytes = MyBlueCommunicationManager.getWatchBuleData();//数据传输格式 需要具体的蓝牙协议
                    MyBlueCommunicationManager.sendToBule(bytes, WRITE_BluetoothGattCharacteristic, mBluetoothGatt);
                } else {
                    Toast.makeText(MainActivity.this, "设备未连接", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.bangding_deivce:
                if (isconnect) {
                    byte[] bytes = MyBlueCommunicationManager.bangding("1134");//数据传输格式 需要具体的蓝牙协议
                    MyBlueCommunicationManager.sendToBule(bytes, WRITE_BluetoothGattCharacteristic, mBluetoothGatt);
                } else {
                    Toast.makeText(MainActivity.this, "设备未连接", Toast.LENGTH_LONG).show();
                }
                break;
        }


    }

    //搜索蓝牙设备
    private void serachBule(final int status) {
        myBuleSerachManager = MyBuleSearchManager.getInstance(MainActivity.this, status, new MyBuleSearchManager.LeScanCallbackListener() {
            @Override
            public void getBluetoothDeviceList(List<MyBuleSearchManager.BluetoothDeviceEntity> bluetoothDevices) {
                Log.i("myblue", "有搜索到设备");
                if (status != 0 && bluetoothDevices != null & bluetoothDevices.size() > 0) {//在指定时间内搜索到设备集合 可以列表展示
                    for (MyBuleSearchManager.BluetoothDeviceEntity bluetoothDeviceEntity : bluetoothDevices) {
                        Log.i("myblue", bluetoothDeviceEntity.device.getName() + "  " + bluetoothDeviceEntity.device.getAddress());
                        if (bluetoothDeviceEntity.device.getAddress().equals(MAC)) {
                            Log.i("myblue", "搜索到目标设备开始连接");
                            connectBule();
                            return;
                        } else {
                            Log.i("myblue", "未搜索到目标设备");
                        }
                    }

                }

            }

            @Override//所搜确定的单个设备
            public void getBluetoothDevice(MyBuleSearchManager.BluetoothDeviceEntity bluetoothDevice) {
                if (bluetoothDevice == null) {
                    return;//监听未触发
                } else {
                    if (bluetoothDevice.rssi == 1111) {//搜索八秒也未搜索到目标设备
                        Log.i("myblue", "未搜索到目标设备");
                        return;
                    }
                    if (bluetoothDevice.device != null) {
                        if (bluetoothDevice.device.getAddress() != null && bluetoothDevice.device.getName() != null) {
                            if (bluetoothDevice.device.getAddress().equals(MAC)) {//搜索到目标设备
                                if (myBuleSerachManager != null) {
                                    myBuleSerachManager.endSearch();//搜索到目标设备了需要手动结束搜索 不然 会触发8秒搜不到机制
                                }
                                Log.i("myblue", "搜索到目标设备开始连接");
                                connectBule();
                            }

                        }
                    }

                }
            }
        });
    }


    private void connectBule() {
        myBuleConnectManager = MyBuleConnectManager.getInstance(MainActivity.this, MAC, new MyBuleConnectManager.OnCharacteristicListener() {
            @Override
            public void onServicesDiscovered(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic WRITE_BluetoothGattCharacteristi) {
                WRITE_BluetoothGattCharacteristic = WRITE_BluetoothGattCharacteristi;
                mBluetoothGatt = bluetoothGatt;
                if (WRITE_BluetoothGattCharacteristic != null && mBluetoothGatt != null) {
                    isconnect = true;
                }
            }

            @Override
            public void onCharacteristicChanged(byte[] data) {//这个回调是 子线程 可以切换到主线程处理接收到的数据
                //  Log.i("myblue", "数据通知");
                String text = MyBlueCommunicationManager.format(data);
                Message message = Message.obtain();
                message.obj = text;
                message.what = 1;
                handler.sendMessage(message);

            }
        });


    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String text = msg.obj.toString();
            Log.i("myblue", text);
            result.setText(text);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myBuleConnectManager != null) {//退出的时候别忘了断开连接
            myBuleConnectManager.endConnect();
        }
        if (myBuleSerachManager != null) {
            myBuleSerachManager.endSearch();//停止搜索
        }

    }
}
