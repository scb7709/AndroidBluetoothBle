package com.androidblueble.MyBlue;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.Serializable;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Created by abc on 2017/7/19.
 */
public class MyBuleConnectManager implements Serializable {

    //（笔者实际项目的中一款腕设备） 需要根据实际需求 更改
    public final static String SERVICE_UUID = "0000fee7-0000-1000-8000-00805f9b34fb";//服务
    public final static String RateCharacteristic_READE_UUID = "000036f6-0000-1000-8000-00805f9b34fb";//读（通知）的特性
    public final static String RateCharacteristic_WRITE_UUID = "000036f5-0000-1000-8000-00805f9b34fb";//写的特性


    /**
     * 发现服务、数据读写操作接口
     */
    public interface OnCharacteristicListener {
        void onServicesDiscovered(BluetoothGatt mBluetoothGatt,BluetoothGattCharacteristic WRITE_BluetoothGattCharacteristic);

        void onCharacteristicChanged(byte[] data);
    }

    private static OnCharacteristicListener CharacteristicListener;
    private static MyBuleConnectManager myBuleConnectManager;
    private Activity activity;
    private BluetoothAdapter bluetoothAdapter;
    private String ADRS;
    private static BluetoothGatt mBluetoothGatt;//发现蓝牙服务，根据特征值处理数据交互
    public static boolean IS_CONNECT;//连接状态
    private static int CONNECT_TIME;//两次重新连接间的最小时间间隔
    private boolean FirstRemind, IS_FIRST = true;//当前设备被其他设备连接
    public static boolean OVER;//结束
    private Timer timer;
    private TimerTask timerTask;
    private static BluetoothGattCharacteristic READE_BluetoothGattCharacteristic;
    private static BluetoothGattCharacteristic WRITE_BluetoothGattCharacteristic;

    public MyBuleConnectManager(Activity activity, String ADRS, OnCharacteristicListener mCharacteristicListener) {
        this.activity = activity;
        this.ADRS = ADRS;
        this.CONNECT_TIME = 5000;//默认重连间隔
        CharacteristicListener = mCharacteristicListener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean flag = bluetoothAdapter.isEnabled();
        timer = new Timer();
        if (flag) {
            first_connect();//开启首次连接
        } else {
            activity.registerReceiver(receiver, MyBuleSearchManager.registBroadcast());
            MyBuleSearchManager.openBluetooth(activity);
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("", "蓝牙广播回调 action=" + action);
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {//系统蓝牙关闭
                Log.i("", "系统蓝牙断开！！");
            } else if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {//系统蓝牙打开
                first_connect();//开启首次连接
            }
        }
    };

    //首次连接
    public static MyBuleConnectManager getInstance(Activity activity, String ADRS, OnCharacteristicListener mCharacteristicListener) {
        if (myBuleConnectManager == null) {//首次连接
            synchronized (MyBuleConnectManager.class) {
                if (myBuleConnectManager == null) {
                    myBuleConnectManager = new MyBuleConnectManager(activity, ADRS, mCharacteristicListener);
                }
            }
        } else {//只需要传入一个接口监听替换之前的监听  用于页面切换了 仍然能继续通信
            if (CharacteristicListener != null) {
                CharacteristicListener = null;
            }
            CharacteristicListener = mCharacteristicListener;
            CharacteristicListener.onServicesDiscovered( mBluetoothGatt, WRITE_BluetoothGattCharacteristic);
        }
        //  myBuleConnectManager = new MyBuleConnectManager(activity, ADRS, CONNECT_TIME, mCharacteristicListener);
        return myBuleConnectManager;
    }
    /**
     * 判断设备是否连接
     * <p>
     * //首次连接
     */
    public boolean first_connect() {
        if (ADRS == null) {
            return false;
        }
        BluetoothDevice device;
        if (bluetoothAdapter == null) {
            device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(ADRS);

        } else {
            device = bluetoothAdapter.getDefaultAdapter().getRemoteDevice(ADRS);
        }

        if (device == null) {
            return false;
        }
        //核心方法
        mBluetoothGatt = device.connectGatt(activity, false, mGattCallback);//创建新的连接
        return true;
    }

    public void nofirst_connect() {
        if (mBluetoothGatt != null && mBluetoothGatt.connect()) {//蓝牙控制对象还在 会尝试主动连接
            Log.i("myblue", "拦截" + mBluetoothGatt.connect());
            return;
        }
        // Log.i("myblue", "nofirst_connect开始连接+" + ADRS);
        if (ADRS == null) {
            return;
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }
        first_connect();
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0://断开连接
                    Log.i("myblue", "断开连接");
                    IS_CONNECT = false;
                    if (timerTask != null) {
                        timerTask.cancel();
                        timerTask = null;
                    }
                    timerTask = new TimerTask() {
                        @Override
                        public void run() {
                            handler.sendEmptyMessage(1);//重启连接每 CONNECT_TIME 秒重连一次
                        }
                    };
                    timer.schedule(timerTask, 0, CONNECT_TIME);
                    break;
                case 1://重启连接
                    Log.i("myblue", "重启连接");
                    if (!IS_CONNECT) {
                        nofirst_connect();
                    }
                    break;
                case 2://成功连接
                    Log.i("myblue", "连接上了");
                    IS_CONNECT = true;
                    if (timerTask != null) {
                        timerTask.cancel();
                        timerTask = null;
                    }
                    break;

                case 3://设备可用
                    Log.i("myblue", "找到特征");
                    CharacteristicListener.onServicesDiscovered( mBluetoothGatt, WRITE_BluetoothGattCharacteristic);
                    break;
            }
        }
    };

    /**
     * 蓝牙协议回调
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        /**
         * 连接状态
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothGatt.STATE_CONNECTED) {// 连接状态
                handler.sendEmptyMessage(2);//重启连接每 CONNECT_TIME 秒重连一次
                gatt.discoverServices();//设备连接成功，查找服务!
                //   }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {// 断开连接
                if (!OVER)
                    handler.sendEmptyMessage(0);//重启连接每 CONNECT_TIME 秒重连一次
            }
        }

        /**
         * 是否发现服务
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i("myblue", "发现服务" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CharacteristicListener != null) {
                    List<BluetoothGattService> gattServices = gatt.getServices();
                    //  Log.i("myblue", "发现服务");
                    for (BluetoothGattService gattService : gattServices) {
                        //Log.i("myblue", "服务UUID" + gattService.getUuid().toString());
                        UUID uuid = gattService.getUuid();
                        if (uuid.equals(UUID.fromString(MyBuleConnectManager.SERVICE_UUID))) {
                            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                                // Log.i("myblue", "特征的" + gattCharacteristic.getUuid().toString());
                                UUID gattCharacteristic_uuid = gattCharacteristic.getUuid();
                                // BluetoothGattDescriptor bluetoothGattDescriptor = gattCharacteristic.getDescriptor(uuid);
                                if (gattCharacteristic_uuid.toString().equals(MyBuleConnectManager.RateCharacteristic_WRITE_UUID)) {//写的特性
                                    if (WRITE_BluetoothGattCharacteristic == null)
                                        WRITE_BluetoothGattCharacteristic = gattCharacteristic;
                                    if ((WRITE_BluetoothGattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) {
                                        WRITE_BluetoothGattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                                    } else
                                        WRITE_BluetoothGattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                                } else if (gattCharacteristic_uuid.toString().equals(MyBuleConnectManager.RateCharacteristic_READE_UUID)) {//读的特性
                                    if (READE_BluetoothGattCharacteristic == null)
                                        READE_BluetoothGattCharacteristic = gattCharacteristic;
                                    boolean isEnableNotification = gatt.setCharacteristicNotification(READE_BluetoothGattCharacteristic, true);
                                    if (isEnableNotification) {
                                        List<BluetoothGattDescriptor> descriptorList = READE_BluetoothGattCharacteristic.getDescriptors();
                                        if (descriptorList != null && descriptorList.size() > 0) {
                                            for (BluetoothGattDescriptor descriptor : descriptorList) {
                                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                                gatt.writeDescriptor(descriptor);
                                            }
                                        }
                                    }

                                }
                            }


                        }
                    }

                }
                if (READE_BluetoothGattCharacteristic != null && WRITE_BluetoothGattCharacteristic != null) {
                    if (IS_FIRST) {
                        handler.sendEmptyMessageDelayed(3, 2000);//最好延迟两秒在进行数据通信
                        IS_FIRST = false;
                    }
                }


            } else {
                if (!FirstRemind) {
                    FirstRemind = true;
                    Toast.makeText(activity, "无法连接目标设备", Toast.LENGTH_LONG).show();
                }
            }
        }

        /**
         * 读操作回调
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //    Log.i("myblue", "数据通知");
            if (CharacteristicListener != null) {
                CharacteristicListener.onCharacteristicChanged(characteristic.getValue());//返回交互的数据
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

        }

        /**
         * 信号强度
         */
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }
    };

    public void endConnect() {
        OVER = true;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        try {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        } catch (Exception e) {
        }
        if (CharacteristicListener != null)
            CharacteristicListener = null;
        IS_CONNECT = false;
        myBuleConnectManager = null;

        Log.i("myblue", "disconnect");
    }

    public BluetoothGatt getBluetoothGatt() {
        return mBluetoothGatt;

    }

    public BluetoothGattCharacteristic getBluetoothGattCharacteristic() {
        return WRITE_BluetoothGattCharacteristic;
    }


}
