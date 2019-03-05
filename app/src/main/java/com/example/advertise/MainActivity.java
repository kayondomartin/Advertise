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
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

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
    private static final Integer isScannedGuestsFlagInteger = Integer.parseInt("aa",16);
    private static final Integer isDataReceivedFlagInteger = Integer.parseInt("ab",16);
    private static final Integer isFlagPacketInteger = Integer.parseInt("ff",16);

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
            sentDataByteIndex = 0;
            AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                    .addServiceData(EDDYSTONE_SERVICE_UUID,dataByteList.get(sentDataByteIndex++))
                    .build();
            advertisingSet.setAdvertisingData(data);
            Log.e(MainActivity.class.getSimpleName(),"Sent: "+data.getServiceData());
        }


        @Override
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable, int status) {
            super.onAdvertisingEnabled(advertisingSet, enable, status);
        }

        @Override
        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            super.onAdvertisingDataSet(advertisingSet, status);
            Log.e(MainActivity.class.getSimpleName(), "onAdvertisingDataSet()");
            if(sentDataByteIndex == dataByteList.size()){
                sentDataByteIndex = 0;
            }
            AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceUuid(EDDYSTONE_SERVICE_UUID)
                    .addServiceData(EDDYSTONE_SERVICE_UUID,dataByteList.get(sentDataByteIndex++))
                    .build();
            advertisingSet.setAdvertisingData(data);
            Log.e(MainActivity.class.getSimpleName(),"Sent: "+data.getServiceData());
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            super.onAdvertisingSetStopped(advertisingSet);
            Log.e(MainActivity.class.getSimpleName(),"Advertising stopped!");
        }


    };

    private static int packetNo = -1;
    private static boolean isKeyReceived  = false;
    private static boolean isDataNeededFlag = false;
    private static boolean isDataReceivedFlag = false;
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
                if((dataByteArray.length == 6) && ((int)dataByteArray[2] == 1) && (dataByteArray[4] == isFlagPacketInteger.byteValue())){
                    if(dataByteArray[5] == isDataReceivedFlagInteger.byteValue()){
                        isDataReceivedFlag = true;
                    }else if(dataByteArray[5] == isScannedGuestsFlagInteger.byteValue()){
                        isDataNeededFlag = true;
                    }
                }
            }
            Log.e(MainActivity.class.getSimpleName(),"Scan Address: "+result.getDevice().getAddress());
            Log.e(MainActivity.class.getSimpleName(),getHexData(dataByteArray));
        }


    };

    private void startScan(){
        bleScanner.startScan(SCAN_FILTERS,SCAN_SETTINGS,scanCallback);
        isScanning = true;
    }

    private void stopScan(){
        bleScanner.stopScan(scanCallback);
        isScanning = false;
    }

    private byte[] buildData(@NonNull byte[]...byteList){
        int setLength = byteList.length;
        int i;
        int [] setLengths = new int[setLength];
        int totalLength = 0;
        for(i=0;i<setLength;i++){
            int itemLength = byteList[i].length;
            totalLength+= itemLength;
            setLengths[i] = itemLength;
        }

        byte[] toReturn = new byte[totalLength];
        int destPos = 0;
        for(i=0;i<setLength;i++){
            System.arraycopy(byteList[i],0,toReturn,destPos,destPos=setLengths[i]);
        }

        return toReturn;
    }

    private String getHexData(byte[] bytes){
        StringBuilder sb = new StringBuilder();
        int i;
        int length = bytes.length;
        for(i=0;i<length;i++){
            sb.append(String.format("%02X ",bytes[i]));
        }

        return sb.toString();
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
        isAdvertising = true;
    }

    private void stopAdvertising(){
        bleAdvertiser.stopAdvertisingSet(advertiseCallback);
        isAdvertising = false;
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

    private static int seconds = 0;
    private static boolean isScanning = false;
    private static boolean isAdvertising = false;
    private void startTimer(){
        runnable = new Runnable() {
            @Override
            public void run() {

                if(!isScanning && !isAdvertising){
                    startScan();
                }else if(seconds == 30){
                    if(isScanning && isKeyReceived){
                        stopScan();
                        buildToSendData();
                        startAdvertising();
                        seconds = 0;
                    }else if(isAdvertising){
                        stopAdvertising();
                        startScan();
                        seconds = 0;
                    }else if(isDataReceivedFlag){
                        if(isScanning) stopScan();
                        else if(isAdvertising) stopAdvertising();
                        stopTimer();
                    }else {
                        startScan();
                    }
                }
                seconds += 10;
                timeHandler.postDelayed(this,10);
            }
        };
        timeHandler.postDelayed(runnable,10);
    }

    private void stopTimer(){
        seconds = 0;
        timeHandler.removeCallbacks(runnable);
    }

    private static final String passwd = "Passw()rd!";
    private void buildToSendData(){

        if(isKeyReceived && !isDataNeededFlag){
            byte[] header = buildPacketHeader(isKeyReceived,1,0);
            byte[] data = convertMAC(DeviceAddress);
            byte[] built = buildData(header,data);
            dataByteList.add(built);
        }else if(isKeyReceived && isDataNeededFlag){
            byte [] keyBytes = buildKey();
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey key = keyFactory.generatePublic(keySpec);
                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.ENCRYPT_MODE,key);
                byte [] encryptedData = cipher.doFinal(passwd.getBytes());
                dataByteList = segmentData(encryptedData,11);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeySpecException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            }

        }

    }

    private List<byte[]> segmentData(@NonNull byte[] data, int segmentLength){

        int dataSize = data.length;
        int startIndex = 0;
        List<byte[]> segmentList = new ArrayList<>();
        if(dataSize <= segmentLength) {
            byte[] header = buildPacketHeader(isKeyReceived,1,0);
            byte[] built = buildData(header,data);
           segmentList.add(built);
        }
        else{
            byte[] segment;
            byte[] header;
            int totalSegments = (dataSize/segmentLength) + (dataSize%segmentLength) == 0 ? 0: 1;
            int segIndex = 0;
            while((dataSize-startIndex) >= segmentLength){
                header = buildPacketHeader(isKeyReceived,totalSegments,segIndex++);
                segment = new byte[segmentLength];
                System.arraycopy(data,startIndex,segment,0,segmentLength);
                startIndex+=segmentLength;
                segmentList.add(buildData(header,segment));
            }
            if((dataSize-startIndex) > 0){
                int finalLength = dataSize-startIndex;
                header = buildPacketHeader(isKeyReceived,totalSegments,segIndex);
                segment = new byte[finalLength];
                System.arraycopy(data,startIndex,segment,0,finalLength);
                segmentList.add(buildData(header,segment));
            }
        }

        return segmentList;
    }

    private byte[] buildKey(){

        Map<Integer,byte[]> sortedKeyBytes = new TreeMap<>(publicKeyByteList);
        String key = "";

        for(Map.Entry<Integer,byte[]> entry:sortedKeyBytes.entrySet()){
            byte[] packetBytes = entry.getValue();
            int packetLength = packetBytes.length;
            byte[] packetDataBytes = new byte[packetBytes.length-4];
            System.arraycopy(packetBytes,4,packetDataBytes,0,packetLength-4);
            key.concat(new String(packetDataBytes));
        }

        return key.getBytes();
    }

}
