package com.androidblueble.MyBlue;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import android.util.Log;

/**
 * Created by abc on 2017/5/2.
 */

public class MyBlueCommunicationManager {
    public static byte[] bangding(String UID) {
        byte[] bytes = new byte[19];
        for (int i = 0; i < 19; i++) {//初始化全部为0
            bytes[i] = 0;
        }
        bytes[0] = 1;
        bytes[1] = 19;
        String str = "";
        for (int i = 0; i < UID.length(); i++) {
            byte asc = (byte) UID.charAt(i);//获取字符的Ascii码
            bytes[i + 2] = asc;
            str += asc;
        }
        byte sum = 0;

        for (int i = 0; i < bytes.length; i++) {
            Log.i("myblue", bytes[i] + "");
            sum += bytes[i];
        }
        bytes[18] = sum;
        Log.i("myblue", str + "");
        return bytes;
    }

    //获取腕表的运动状态
    public static byte[] getWatchBuleData() {
        byte[] bytes = new byte[4];
        bytes[0] = 19;
        bytes[1] = 4;
        bytes[2] = 1;
        bytes[3] = 24;
        return bytes;
    }


    public static void sendToBule(byte[] bytes, BluetoothGattCharacteristic WRITE_BluetoothGattCharacteristic, BluetoothGatt mBluetoothGatt) {
        if (bytes != null && bytes.length > 0) {
            WRITE_BluetoothGattCharacteristic.setValue(bytes);
            mBluetoothGatt.writeCharacteristic(WRITE_BluetoothGattCharacteristic);
        }
    }
//16进制查看2进制数据  （蓝牙设备返回的数据一般都是二进制）
    public static String format(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte b : data) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    //16进制转换10进制
    public static int getInt_10(String num) {
        int temp = 0;
        try {
            temp = Integer.parseInt(num, 16);

        } catch (Exception n) {
            temp = 0;
        }
        return temp;
    }



    //16进制字符串转化字节数组  发送中文的时候用得着
    public static byte[] hexStr2ByteArray(String hexString) {
        hexString = hexString.toLowerCase();
        final byte[] byteArray = new byte[hexString.length() / 2];
        int k = 0;
        for (int i = 0; i < byteArray.length; i++) {
            //因为是16进制，最多只会占用4位，转换成字节需要两个16进制的字符，高位在先
            //将hex 转换成byte   "&" 操作为了防止负数的自动扩展
            // hex转换成byte 其实只占用了4位，然后把高位进行右移四位
            // 然后“|”操作  低四位 就能得到 两个 16进制数转换成一个byte.
            //
            byte high = (byte) (Character.digit(hexString.charAt(k), 16) & 0xff);
            byte low = (byte) (Character.digit(hexString.charAt(k + 1), 16) & 0xff);
            byteArray[i] = (byte) (high << 4 | low);
            k += 2;
        }
        return byteArray;
    }
}
