package com.github.mrzhqiang.testbluetooth;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import common.activities.SampleActivityBase;
import common.logger.Log;
import common.logger.LogFragment;
import common.logger.LogWrapper;
import common.logger.MessageOnlyLogFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 主页。
 */
public class MainActivity extends SampleActivityBase {

  /** 必须是 0 以上的值。 */
  // Intent request codes
  private static final int REQUEST_ENABLE_BT = 3;

  private static final String message = "hello bluetooth!";

  private BluetoothAdapter mBluetoothAdapter;

  private EditText editData;

  private BluetoothChatService mChatService;

  private String mConnectedDeviceName = null;

  /**
   * The action listener for the EditText widget, to listen for the return key
   */
  private TextView.OnEditorActionListener mWriteListener
      = new TextView.OnEditorActionListener() {
    public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
      // If the action is a key-up event on the return key, send the message
      if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
        String message = view.getText().toString();
        sendMessage(message);
      }
      return true;
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    if (mBluetoothAdapter == null) {
      Toast.makeText(this, "蓝牙不可用！", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }

    editData = findViewById(R.id.edit_data);
    editData.setText(message);
    // Initialize the compose field with a listener for the return key
    editData.setOnEditorActionListener(mWriteListener);

    mChatService = new BluetoothChatService(this, mHandler);
  }

  @Override protected void onStart() {
    super.onStart();
    if (!mBluetoothAdapter.isEnabled()) {
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
      Log.d(TAG, "请求开启蓝牙");
    }
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    if (mChatService != null) {
      mChatService.stop();
    }
  }

  @Override protected void onResume() {
    super.onResume();

    // Performing this check in onResume() covers the case in which BT was
    // not enabled during onStart(), so we were paused to enable it...
    // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
    if (mChatService != null) {
      // Only if the state is STATE_NONE, do we know that we haven't started already
      if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
        // Start the Bluetooth chat services
        mChatService.start();
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    switch (requestCode) {
      case REQUEST_ENABLE_BT:
        if (resultCode == Activity.RESULT_OK) {
          Log.d(TAG, "蓝牙开启成功");
        } else {
          Log.d(TAG, "用户拒绝开启蓝牙");
          Toast.makeText(this, "请先同意开启蓝牙", Toast.LENGTH_SHORT).show();
        }
    }
  }

  /** Create a chain of targets that will receive log data */
  @Override
  public void initializeLogging() {
    // Wraps Android's native log framework.
    LogWrapper logWrapper = new LogWrapper();
    // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
    Log.setLogNode(logWrapper);

    // Filter strips out everything except the message text.
    MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
    logWrapper.setNext(msgFilter);

    // On screen logging via a fragment with a TextView.
    LogFragment logFragment = (LogFragment) getSupportFragmentManager()
        .findFragmentById(R.id.log_fragment);
    msgFilter.setNext(logFragment.getLogView());

    Log.i(TAG, "日志已准备好");
  }

  public void sendData(View v) {
    sendMessage(editData.getText().toString());
  }

  private void sendMessage(String message) {
    if (!mBluetoothAdapter.isEnabled()) {
      Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(intent, REQUEST_ENABLE_BT);
      Log.d(TAG, "请求开启蓝牙");
      return;
    }
    if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
      Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
      if (devices.isEmpty()) {
        Log.w(TAG, "没有任何设备，请先自行配对");
        return;
      }
      final List<String> list = new ArrayList<>();
      for (BluetoothDevice device : devices) {
        list.add(device.getName() + "\n" + device.getAddress());
      }
      ListAdapter adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
      new AlertDialog.Builder(this)
          .setTitle("选择连接设备（请先自行配对）")
          .setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
              String data = list.get(which);
              Log.d(TAG, "准备连接设备：" + data);
              connectDevice(data.substring(data.length() - 17), true);
            }
          })
          .show();
      return;
    }
    // Check that there's actually something to send
    if (message.length() > 0) {
      // Get the message bytes and tell the BluetoothChatService to write
      byte[] send = message.getBytes();
      mChatService.write(send);

      // Reset out string buffer to zero and clear the edit text field
      //mOutStringBuffer.setLength(0);
      //editData.setText(mOutStringBuffer);
    } else {
      Log.w(TAG, "无效的数据，不执行发送");
    }
  }

  private void connectDevice(String address, boolean secure) {
    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
    mChatService.connect(device, secure);
  }

  /**
   * Updates the status on the action bar.
   *
   * @param subTitle status
   */
  private void setStatus(CharSequence subTitle) {
    setTitle(subTitle);
  }

  /**
   * The Handler that gets information back from the BluetoothChatService
   */
  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      final Activity activity = MainActivity.this;
      switch (msg.what) {
        case Constants.MESSAGE_STATE_CHANGE:
          switch (msg.arg1) {
            case BluetoothChatService.STATE_CONNECTED:
              Log.d(TAG, "已连接:" + mConnectedDeviceName);
              break;
            case BluetoothChatService.STATE_CONNECTING:
              Log.d(TAG, "正在连接");
              break;
            case BluetoothChatService.STATE_LISTEN:
              Log.d(TAG, "正在监听连接");
              break;
            case BluetoothChatService.STATE_NONE:
              Log.d(TAG, "未建立双向连接");
              break;
          }
          break;
        case Constants.MESSAGE_WRITE:
          byte[] writeBuf = (byte[]) msg.obj;
          // construct a string from the buffer
          String writeMessage = new String(writeBuf);
          Log.d(TAG, "发送:  " + writeMessage);
          break;
        case Constants.MESSAGE_READ:
          byte[] readBuf = (byte[]) msg.obj;
          // construct a string from the valid bytes in the buffer
          String readMessage = new String(readBuf, 0, msg.arg1);
          Log.d(TAG, "读取:  " + readMessage);
          break;
        case Constants.MESSAGE_DEVICE_NAME:
          // save the connected device's name
          mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
          if (null != activity) {
            Log.d(TAG, "连接设备 " + mConnectedDeviceName);
          }
          break;
        case Constants.MESSAGE_TOAST:
          if (null != activity) {
            Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                Toast.LENGTH_SHORT).show();
          }
          break;
      }
    }
  };
}
