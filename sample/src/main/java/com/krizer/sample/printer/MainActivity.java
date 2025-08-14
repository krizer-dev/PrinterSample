package com.krizer.sample.printer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.icod.serial.SerialPortFinder;
import com.krizer.printer.PrinterController;
import com.szsicod.print.io.InterfaceAPI;
import com.szsicod.print.io.SerialAPI;
import com.szsicod.print.io.USBAPI;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public final String[] types = new String[]{"Serial", "USB"};
    public final String[] baudrates = new String[]{"38400"};
    public String[] ports;
    private Spinner typeSpinner;
    private Spinner portSpinner;
    private Spinner baudrateSpinner;
    private Button btnConnect;
    private Button btnPrintString;
    private Button btnPrintBarcode;
    private Button btnPrintQRcode;
    private Button btnPrintImage;
    private Button btnPrintFeed;
    private Button btnFullCut;
    private Button btnHalfCut;
    private Button btnStatus;
    private Button btnDisconnect;
    private LinearLayout funcLayout;
    private PrinterController printerController;
    private ExecutorService printerExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        for (UsbDevice usbDevice : ((UsbManager) getSystemService(USB_SERVICE)).getDeviceList().values()) {
            Log.d(TAG, "onCreate: " + usbDevice);
        }

        printerController = new PrinterController(this);

        initViews();
    }

    private void initViews() {
        typeSpinner = findViewById(R.id.typeSpinner);
        portSpinner = findViewById(R.id.portSpinner);
        baudrateSpinner = findViewById(R.id.baudrateSpinner);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, types);
        ArrayAdapter<String> portAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        ArrayAdapter<String> baudrateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, baudrates);

        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        portAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        baudrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        portAdapter.add("type을 선택해주세요");

        typeSpinner.setAdapter(typeAdapter);
        portSpinner.setAdapter(portAdapter);
        baudrateSpinner.setAdapter(baudrateAdapter);

        typeSpinner.setSelection(0);
        portSpinner.setSelection(0);
        baudrateSpinner.setSelection(0);

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        ports = SerialPortFinder.getAllDevicesPath();
                        portAdapter.clear();
                        portAdapter.addAll(Arrays.asList(ports));
                        portAdapter.notifyDataSetChanged();
                        baudrateSpinner.setVisibility(View.VISIBLE);
                        break;
                    case 1:
                        UsbDevice[] usbDevices = ((UsbManager) getSystemService(USB_SERVICE)).getDeviceList().values().toArray(new UsbDevice[0]);
                        ports = new String[usbDevices.length];

                        for (int i = 0; i < usbDevices.length; i++) {
                            ports[i] = usbDevices[i].getProductName();
                        }

                        portAdapter.clear();
                        portAdapter.addAll(Arrays.asList(ports));
                        portAdapter.notifyDataSetChanged();
                        baudrateSpinner.setVisibility(View.GONE);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO. nothing
            }
        });

        btnConnect = findViewById(R.id.btnConnect);
        btnPrintString = findViewById(R.id.btnPrintString);
        btnPrintBarcode = findViewById(R.id.btnPrintBarcode);
        btnPrintQRcode = findViewById(R.id.btnPrintQRcode);
        btnPrintImage = findViewById(R.id.btnPrintImage);
        btnPrintFeed = findViewById(R.id.btnPrintFeed);
        btnHalfCut = findViewById(R.id.btnHalfCut);
        btnFullCut = findViewById(R.id.btnFullCut);
        btnStatus = findViewById(R.id.btnStatus);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        funcLayout = findViewById(R.id.funcLayout);

        btnConnect.setOnClickListener(v -> printerExecutor.execute(this::connect));
        btnPrintString.setOnClickListener(v -> printerExecutor.execute(this::printText));
        btnPrintBarcode.setOnClickListener(v -> printerExecutor.execute(this::printBarcode));
        btnPrintQRcode.setOnClickListener(v -> printerExecutor.execute(this::printQRcode));
        btnPrintImage.setOnClickListener(v -> printerExecutor.execute(this::printImage));
        btnPrintFeed.setOnClickListener(v -> printerExecutor.execute(this::printFeed));
        btnHalfCut.setOnClickListener(v -> printerExecutor.execute(this::halfCut));
        btnFullCut.setOnClickListener(v -> printerExecutor.execute(this::fullCut));
        btnStatus.setOnClickListener(v -> printerExecutor.execute(this::getStatus));
        btnDisconnect.setOnClickListener(v -> printerExecutor.execute(this::disconnect));
    }

    @WorkerThread
    private void connect() {
        int typePosition = typeSpinner.getSelectedItemPosition();
        int portPosition = portSpinner.getSelectedItemPosition();
        String port = ports[portPosition];

        InterfaceAPI interfaceAPI = null;

        if (typePosition == 0) {
            int baudratePosition = baudrateSpinner.getSelectedItemPosition();
            String baudrate = baudrates[baudratePosition];
            interfaceAPI = new SerialAPI(new File(port), Integer.parseInt(baudrate), 0);
        } else if (typePosition == 1) {
            List<UsbDevice> usbDevices = new ArrayList<>(((UsbManager) getSystemService(USB_SERVICE)).getDeviceList().values());
            UsbDevice selectedUsbDevice = null;
            for (UsbDevice usbDevice : usbDevices) {
                if (port.equals(usbDevice.getProductName())) {
                    selectedUsbDevice = usbDevice;
                }
            }

            if (selectedUsbDevice != null) {
                interfaceAPI = new USBAPI(this, selectedUsbDevice);
            }
        }

        if (interfaceAPI == null) {
            runOnUiThread(() -> Toast.makeText(this, "Failed to open printer", Toast.LENGTH_SHORT).show());
            return;
        }

        printerController.connect(interfaceAPI);

        if (printerController.isConnected()) {
            runOnUiThread(()->funcLayout.setVisibility(View.VISIBLE));
        }
    }

    @WorkerThread
    private void printText() {
        try {
            printerController.printString("테스트", "euc-kr", false);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "printText: ", e);
        }
    }

    @WorkerThread
    private void printBarcode() {
        printerController.printBarcode("1231231231", 3, 120, "A");
    }

    @WorkerThread
    private void printQRcode() {
        printerController.printQr("https://www.krizer.com", 10);
    }

    @WorkerThread
    private void printImage() {
        try {
            Bitmap bitmap = getBitmapFromAssets();
            printerController.printImage(bitmap);
        } catch (IOException e) {
            Log.e(TAG, "printImage: ", e);
        }
    }

    private Bitmap getBitmapFromAssets() throws IOException {
        try (InputStream inputStream = getAssets().open("logo.jpg")) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            Matrix matrix = new Matrix();
            matrix.setScale(3, 3);

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        }
    }

    @WorkerThread
    private void printFeed() {
        printerController.printFeed();
    }

    @WorkerThread
    private void fullCut() {
        printerController.fullCut();
    }

    @WorkerThread
    private void halfCut() {
        printerController.halfCut();
    }

    @WorkerThread
    private void getStatus() {
        String status = printerController.getStatus();
        runOnUiThread(() -> Toast.makeText(this, status, Toast.LENGTH_SHORT).show());
    }

    @WorkerThread
    private void disconnect() {
        printerController.disconnect();

        if (!printerController.isConnected()) {
            runOnUiThread(()->funcLayout.setVisibility(View.GONE));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        printerExecutor.shutdown();
    }
}