package com.example.advertise;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.location.SettingInjectorService;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.IntegerRes;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {

    private BluetoothLeAdvertiser bleAdvertiser;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;

    private Map<Integer, byte[]> publicKeyByteList;

    Handler timeHandler = new Handler();
    Runnable runnable;
    private static List<byte[]> dataByteList = null;
    private static int sentDataByteIndex = 0;

    private static final ParcelUuid EDDYSTONE_SERVICE_UUID = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private static final String DeviceAddress = "E1:6E:58:BC:F1:81";
    private static final String testAddress = "02:00:AB:0C:10:00";

    private static final ScanFilter EDDYSTONE_SCAN_FILTER = new ScanFilter.Builder()
            .setServiceUuid(EDDYSTONE_SERVICE_UUID)
            .setDeviceAddress(DeviceAddress)
            .build();

    private ScanSettings SCAN_SETTINGS =
            new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0)
                    .build();


    private List<ScanFilter> SCAN_FILTERS = buildScanFilters();


    private static List<ScanFilter> buildScanFilters(){
        List<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(EDDYSTONE_SCAN_FILTER);
        return scanFilters;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        publicKeyByteList = new HashMap<>();

        int max = bluetoothAdapter.getLeMaximumAdvertisingDataLength();
        byte[] addressByte = convertMAC(testAddress);
        Log.e(MainActivity.class.getSimpleName(),addressByte.length+", max: "+max +", array: " +Arrays.toString(addressByte) + "array2: "+Arrays.toString(convertMAC(DeviceAddress)));

    }

    private AdvertisingSetCallback advertiseCallback = new AdvertisingSetCallback() {
        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
            Log.e(MainActivity.class.getSimpleName(),"Advertising started!");
            if(!isKeyReceived){
                AdvertiseData data = (new AdvertiseData.Builder())
                        .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                        .addServiceData(EDDYSTONE_SERVICE_UUID,buildDataBytes(convertMAC(DeviceAddress),isKeyReceived).get(0))
                        .build();
                advertisingSet.setAdvertisingData(data);
            }else{
                if(sentDataByteIndex == dataByteList.size()) sentDataByteIndex = 0;
                AdvertiseData data = (new AdvertiseData.Builder())
                        .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                        .addServiceData(EDDYSTONE_SERVICE_UUID,buildDataBytes(dataByteList.get(sentDataByteIndex++),isKeyReceived).get(0))
                        .build();
                advertisingSet.setAdvertisingData(data);
            }
        }


        @Override
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable, int status) {
            super.onAdvertisingEnabled(advertisingSet, enable, status);
        }

        @Override
        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            super.onAdvertisingDataSet(advertisingSet, status);
            AdvertiseData data;
            if(!isKeyReceived){
                data = (new AdvertiseData.Builder())
                        .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                        .addServiceData(EDDYSTONE_SERVICE_UUID,buildDataBytes(convertMAC(DeviceAddress),isKeyReceived).get(0))
                        .build();
                advertisingSet.setAdvertisingData(data);
            }else{
                if(sentDataByteIndex == dataByteList.size()) sentDataByteIndex = 0;
                 data = (new AdvertiseData.Builder())
                        .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                        .addServiceData(EDDYSTONE_SERVICE_UUID,buildDataBytes(dataByteList.get(sentDataByteIndex++),isKeyReceived).get(0))
                        .build();
                advertisingSet.setAdvertisingData(data);
            }
            Log.e(MainActivity.class.getSimpleName(),Arrays.toString(data.getServiceData().get(EDDYSTONE_SERVICE_UUID)));
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            super.onAdvertisingSetStopped(advertisingSet);
            Log.e(MainActivity.class.getSimpleName(),"Advertising stopped!");
        }


    };

    private static int packetNo = -1;
    private static boolean isKeyReceived  = false;
    private static boolean isListening = false;
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            ScanRecord record = result.getScanRecord();
            byte[] dataByteArray = record.getServiceData(EDDYSTONE_SERVICE_UUID);
            if(!isKeyReceived) {
                if (packetNo < 0) {
                    packetNo = (int) dataByteArray[1];
                } else if (publicKeyByteList.size() != packetNo) {
                    int dataID = (int) dataByteArray[3];
                    if (!publicKeyByteList.containsKey(dataID)) {
                        publicKeyByteList.put(dataID, dataByteArray);
                    }
                } else {
                    isKeyReceived = true;
                }
            }else{
            }
            Log.e(MainActivity.class.getSimpleName(),"Scan Address: "+result.getDevice().getAddress());
            Log.e(MainActivity.class.getSimpleName(),Arrays.toString(result.getScanRecord().getServiceData(EDDYSTONE_SERVICE_UUID)));
        }


    };

    private void startScan(){
        bleScanner.startScan(SCAN_FILTERS,SCAN_SETTINGS,scanCallback);
    }

    private void stopScan(){
        bleScanner.stopScan(scanCallback);
    }

    private byte[] buildData(@NonNull byte[]...byteList){

    }

    private byte[] buildPacketHeader(boolean isKeyReceivedCheck, int fragNo, int fragID){
        String flag = "";
        if(isKeyReceivedCheck){
            flag = "da";
        }else{
            flag = "ca";
        }
        Integer dataHead0 = Integer.parseInt("10",16);
        Integer dataHead1 = Integer.parseInt(fragNo+"");
        Integer dataHead2 = Integer.parseInt("02",16);
        Integer dataHead3 = Integer.parseInt(flag,16);
        Integer dataHead4 = Integer.parseInt(fragID+"");
        Integer dataHead5 = Integer.parseInt(DeviceAddress.substring(15),16);
        Integer dataHead6 = Integer.parseInt(DeviceAddress.substring(0,2),16);
        final byte [] dataHead = {dataHead0.byteValue(),dataHead1.byteValue(),dataHead2.byteValue(),dataHead3.byteValue(),dataHead4.byteValue(),dataHead5.byteValue(),dataHead6.byteValue()};

        return dataHead;
    }
    private void startAdvertising(){

        byte[] dataBytes = buildKey(isKeyReceived);
        dataByteList = buildDataBytes(dataBytes,isKeyReceived);
        Log.e(MainActivity.class.getSimpleName(),"DataBytes: "+Arrays.toString(dataBytes));

        final int txPower = AdvertisingSetParameters.TX_POWER_HIGH;
        AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder()
                .setConnectable(false)
                .setLegacyMode(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
                .setTxPowerLevel(txPower)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                .build();

        //Log.e(MainActivity.class.getSimpleName(),"Sent Data: "+Arrays.toString(data.getServiceData().get(EDDYSTONE_SERVICE_UUID)));
        bleAdvertiser.startAdvertisingSet(parameters,data,null,null,null,advertiseCallback);
    }

    private void stopAdvertising(){
        bleAdvertiser.stopAdvertisingSet(advertiseCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(bluetoothAdapter == null || bleScanner == null || bleAdvertiser == null){
            Toast.makeText(this,"Either bluetooth or BLE not supported!",Toast.LENGTH_SHORT);
            finish();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},1);
        }

        startTimer();
    }

    private byte[] convertMAC(String mac){
        String[] macArray = mac.split(":");
        byte[] macAddrByte = new byte[6];

        int i;
        for(i=0;i<6;i++){
            Integer hex = Integer.parseInt(macArray[i],16);
            macAddrByte[i] = hex.byteValue();
        }

        return macAddrByte;
    }

    private byte[] convertData(@NonNull String data){
        String[] charArray = data.split("");
        int length = data.length();
        byte [] dataByteArray = new byte[length];

        int i;
        int j = 0;
        if(length>8) {
            Integer hex0, hex1, hex2, hex3, hex4, hex5, hex6, hex7;
            for (i = 0; (i + 7) < length; i += 8) {
                j = i;
                hex0 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex0.byteValue();
                hex1 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex1.byteValue();
                hex2 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex2.byteValue();
                hex3 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex3.byteValue();
                hex4 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex4.byteValue();
                hex5 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex5.byteValue();
                hex6 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex6.byteValue();
                hex7 = Integer.parseInt(charArray[j]);
                dataByteArray[j++] = hex7.byteValue();
            }

            for(;j<length;j++){
                hex0 = Integer.parseInt(charArray[j]);
                dataByteArray[j] = hex0.byteValue();
            }
        }else{
            for(i=0;i<length;i++){
                Integer hex = Integer.parseInt(charArray[i]); dataByteArray[i] = hex.byteValue();
            }
        }

        return dataByteArray;

    }

    private void startAdvertising1(){
        AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.e(MainActivity.class.getSimpleName(),"Advertising started!");
            }
        };

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(false)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setTimeout(0)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                .addServiceData(EDDYSTONE_SERVICE_UUID,"Testing".getBytes())
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .build();
        bleAdvertiser.startAdvertising(settings,data,callback);
    }


    private static int seconds = 0;
    private static boolean isScanning = false;
    private static boolean isAdvertising = false;
    private void startTimer(){
        runnable = new Runnable() {
            @Override
            public void run() {
                if(seconds == 10000){
                    if(isAdvertising){
                        stopAdvertising();
                        if(!isKeyReceived) {
                            startScan();
                            isScanning = true;
                        }
                        isAdvertising = false;
                    }else if(isScanning && isKeyReceived){
                        stopScan();
                        startAdvertising();
                        isScanning = false;
                        isAdvertising = true;
                    }
                    seconds = 0;
                }else if(!isScanning && !isAdvertising){
                    startScan();
                    isScanning = true;
                }
                seconds += 1000;
                //Log.e(MainActivity.class.getSimpleName(),"Seconds: "+seconds);
                timeHandler.postDelayed(this,1000);
            }
        };
        timeHandler.postDelayed(runnable,1000);
    }

    private void stopTimer(){
        seconds = 0;
        timeHandler.removeCallbacks(runnable);
    }

    private byte[] buildKey(boolean isKeyReceivedCheck){
        if(isKeyReceivedCheck){
            Map<Integer,byte[]> map = new TreeMap(publicKeyByteList);
            String key = "";

            for(Map.Entry<Integer,byte[]> entry: map.entrySet()){
                byte[] entryArray = entry.getValue();
                int length = entryArray.length;
                int i;
                for(i=4;i<length;i++){
                    key += (char)entryArray[i];
                }
            }

            Log.e(MainActivity.class.getSimpleName(),"Built Key: "+key);

            return key.getBytes();

        }else{
            return convertMAC(DeviceAddress);
        }
    }

    private List<byte[]> buildDataBytes(@NonNull byte[] data, boolean isKeyReceivedCheck){
        //Log.e(MainActivity.class.getSimpleName(),"Data: "+Arrays.toString(data));
        int length = data.length;
        int fragments = length%11 == 0 ? length/11: length/11 + 1;
        int index;
        int i = 0;
        List<byte[]> dataByteList = new ArrayList<>();
        byte[] header;
        byte[] dataFragment;
        for(index=0;(index+11)<length;index+=11){
            dataFragment = Arrays.copyOfRange(data,index,index+11);
            header = buildPacketHeader(isKeyReceivedCheck,fragments,i++);
            byte[] builtFragment = buildData(header,dataFragment);
            //Log.e(MainActivity.class.getSimpleName(),"Data Fragment: "+ i +": "+Arrays.toString(builtFragment));
            dataByteList.add(builtFragment);
        }
        if(length%11 != 0){
            header = buildPacketHeader(isKeyReceivedCheck,fragments,i);
            dataFragment = Arrays.copyOfRange(data,index,length);
            dataByteList.add(buildData(header,dataFragment));
        }

        return dataByteList;
    }
}
