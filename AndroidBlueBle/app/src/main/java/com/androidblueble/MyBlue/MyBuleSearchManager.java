package com.androidblueble.MyBlue;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
/**
 * Created by abc on 2017/7/19.
 */
public class MyBuleSearchManager {
    public interface LeScanCallbackListener {//搜索到蓝牙设备的回调接口

        void getBluetoothDeviceList(List<BluetoothDeviceEntity> bluetoothDevices);//当 SERACH_TIME不为0时 搜索完成后返回一个设备集合

        void getBluetoothDevice(BluetoothDeviceEntity bluetoothDevice);//当 SERACH_TIME为0时 每次收到一个设备就抛出
    }

    private static MyBuleSearchManager myBuleSerachManager;
    private Activity activity;
    private BluetoothAdapter bluetoothAdapter;
    private static int SERACH_TIME;//单次搜索时长
    private static LeScanCallbackListener leScanCallbackListener;//搜索到设备的监听器
    private List<BluetoothDeviceEntity> bluetoothDeviceList;//单次搜索到的蓝牙设备的集合

    public MyBuleSearchManager(Activity activity, int SERACH_TIME, LeScanCallbackListener LeScanCallbackListener) {
        this.activity = activity;
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {//是否支持4.0ble低功耗蓝牙
            Toast.makeText(activity, "设备版本过低,不支持低功耗蓝牙蓝牙服务", Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        } else {
            this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                this.SERACH_TIME = SERACH_TIME;//搜索时长  启动搜索传过来如果==0便是 搜索一个处理一个  反正表示搜索指定时间段 然后把结果统一处理
                this.bluetoothDeviceList = SERACH_TIME == 0 ? null : new ArrayList<BluetoothDeviceEntity>();//存储搜索到的蓝牙设备 搜索指定时间段的模式才用得着
                leScanCallbackListener = LeScanCallbackListener;//实例化接口监听用来返回搜索结果
                activity.registerReceiver(receiver, registBroadcast());//判断蓝牙是否打开
                boolean flag = bluetoothAdapter.isEnabled();
                if (flag) {
                    StartScan();//蓝牙已经打开开启所搜
                } else {
                    openBluetooth(activity);//手机蓝牙未打开 提示打开蓝牙
                }
            } else {
                Toast.makeText(activity, "设备版本过低,不支持低功耗蓝牙蓝牙服务", Toast.LENGTH_LONG).show();
                //activity.finish();
            }
        }
        //
    }

    public static MyBuleSearchManager getInstance(Activity activity, int SERACH_TIME, LeScanCallbackListener LeScanCallbackListener) {
        if (myBuleSerachManager == null) {
            myBuleSerachManager = new MyBuleSearchManager(activity, SERACH_TIME, LeScanCallbackListener);
        } else {
            leScanCallbackListener = LeScanCallbackListener;//实例化接口监听用来返回搜索结果
        }
        return myBuleSerachManager;
    }


    /**
     * 查找蓝牙广播回调
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("", "蓝牙广播回调 action=" + action);
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {//关闭系统蓝牙
                Log.i("", "系统蓝牙断开！！");
                boolean isEnable = enable();
                if (!isEnable)
                    openBluetooth(activity);
            } else if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {//系统蓝牙打开
                StartScan();//搜索设备
                Log.i("", "系统蓝牙打开！！");

            }
        }
    };

    public boolean enable() {
        return bluetoothAdapter != null && bluetoothAdapter.enable();
    }

    public static void openBluetooth(Activity activity) {
        //打开蓝牙提示框
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent, 100);
    }

    public static IntentFilter registBroadcast() {
        //  Logger.e("注册监听系统蓝牙状态变化广播 ");
        IntentFilter filter = new IntentFilter();
        //蓝牙状态改变action
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        //蓝牙断开action
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        return filter;
    }

    //包含开始搜索,停止搜索的方法
    public void StartScan() {
        Log.i("myblue","开始搜索"+SERACH_TIME);
        if (bluetoothDeviceList != null) {
            bluetoothDeviceList.clear();
        }
        bluetoothAdapter.startLeScan(mLeScanCallback);
        new Thread() {
            @Override
            public void run() {
                super.run();
                if (SERACH_TIME != 0) {
                    mHandler.sendEmptyMessageDelayed(1, SERACH_TIME);
                } else {
                    mHandler.sendEmptyMessageDelayed(1, 8000);//如果是搜索一个处理一个这种模式 设置最大搜索时长(蓝牙搜索很耗性能 不建议 长时间搜索)
                }
            }
        }.start();


    }


    // 搜索到设备的回调
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            new Thread() {//子线程处理
                @Override
                public void run() {
                    super.run();
                    try {
                        if (device != null && device.getAddress() != null) {

                            BluetoothDeviceEntity bluetoothDeviceEntity = new BluetoothDeviceEntity(device, rssi, scanRecord);
                            if (SERACH_TIME == 0) {//搜索到一个处理一个
                                Message message = Message.obtain();
                                message.obj = bluetoothDeviceEntity;
                                message.what = 0;
                                mHandler.sendMessage(message);
                            } else {//搜索固定时间了统一处理（列表展示等）
                                bluetoothDeviceList.add(bluetoothDeviceEntity);
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }.start();

        }
    };

    public void endSearch() {
        if (bluetoothDeviceList != null) {
            bluetoothDeviceList.clear();
        }
        try {
            mHandler.removeMessages(1);
        } catch (Exception e) {
        }
        try {
            activity.unregisterReceiver(receiver);
        } catch (Exception e) {
        }
        try {
            bluetoothAdapter.stopLeScan(mLeScanCallback);
        } catch (Exception e) {
        }
        myBuleSerachManager = null;
    }


    /**
     * 使用handler处理
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 0) {//搜索一个处理一个
                leScanCallbackListener.getBluetoothDevice((BluetoothDeviceEntity) msg.obj);
            } else if (msg.what == 1) {//统一处理
                try {
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                    if (SERACH_TIME != 0) {
                        leScanCallbackListener.getBluetoothDeviceList(bluetoothDeviceList);
                    } else {//触发了8秒搜索机制 说明为搜索到目标设备  未搜索到目标设备
                        leScanCallbackListener.getBluetoothDevice(new BluetoothDeviceEntity(null, 1111, null));
                    }
                } catch (Exception e) {
                }

            } else {

            }

        }
    };

    public class BluetoothDeviceEntity {
        public BluetoothDevice device;//蓝牙设备
        public int rssi;//型号强度
        public byte[] scanRecord;

        public BluetoothDeviceEntity(BluetoothDevice device, int rssi, byte[] scanRecord) {
            this.device = device;
            this.rssi = rssi;
            this.scanRecord = scanRecord;
        }
    }
}
