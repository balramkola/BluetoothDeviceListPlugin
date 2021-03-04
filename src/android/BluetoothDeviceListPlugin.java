package com.techm.bluetoothdevicelistplugin;

import android.os.AsyncTask;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.Manifest;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import android.support.v4.content.LocalBroadcastManager;

public class BluetoothDeviceListPlugin extends CordovaPlugin {
	private static final String TAG = "BluetoothDeviceListPlugin";
    private static final String LOG_PREFIX = "PLUGIN: ";
	public static final int REQUEST_ENABLE_BT = 1773;
	public static final int REQUEST_DISCOVERABLE_BT = 1885;
	public static final int START_DISCOVERY_REQ_CODE = 1997;

	public BluetoothAdapter mBluetoothAdapter = null;
	public ConcurrentHashMap<Integer, CallbackContext> mContextForActivity = new ConcurrentHashMap<Integer, CallbackContext>();
	public ConcurrentHashMap<Integer, CallbackContext> mContextForPermission = new ConcurrentHashMap<Integer, CallbackContext>();
	public CallbackContext mContextForAdapterStateChanged = null;
	public CallbackContext mContextForDeviceAdded = null;
	public CallbackContext mContextForEnable = null;
	public CallbackContext mContextForDisable = null;
	public boolean mDeviceAddedRegistered = false;
	public int mPreviousScanMode = BluetoothAdapter.SCAN_MODE_NONE;
    public static final String INIT_DEVICE_DISCOVERY = "initDeviceDiscovery";
    private String mCurrentAction = "";
    private CallbackContext mBtEventsCallbackContext;
	
	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (this.mBluetoothAdapter != null) {
			this.mPreviousScanMode = this.mBluetoothAdapter.getScanMode();
		}
		this.mBtEventsCallbackContext = null;
	}

	@Override
	public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
		IntentFilter filter;

		if (this.mBluetoothAdapter == null) {
			callbackContext.error("Device does not support Bluetooth");
			return false;
		}

        if (action.equals(INIT_DEVICE_DISCOVERY)) {
			Log.d(TAG, LOG_PREFIX + "action init device discovery called");
            mCurrentAction = INIT_DEVICE_DISCOVERY;
            initDeviceDiscovery(callbackContext,action,args);
			return true;
		}else if (action.equals("startBtEvents")) {
            Log.d(TAG, LOG_PREFIX + "startBtEvents");
			this.mBtEventsCallbackContext = callbackContext;
			sendBtEvent(getJson("Test","working"),true);
            registerAdapterStateChanges();
			return true;
		}else if (action.equals("stopBtEvents")) {
            this.mBtEventsCallbackContext = null;
            unRegisterAdapterStateChanges();
			return true;
		} else if (action.equals("registerAdapterStateChanged")) {
			this.mContextForAdapterStateChanged = callbackContext;
            registerAdapterStateChanges();
			return true;
		} else if (action.equals("unRegisterAdapterStateChanged")) {
			unRegisterAdapterStateChanges();
			return true;
		} else if (action.equals("registerDeviceAdded")) {
			this.mContextForDeviceAdded = callbackContext;
			return true;
		} else if (action.equals("unRegisterDeviceAdded")) {
			this.mContextForDeviceAdded = null;
			return true;
		} else if (action.equals("getAdapterState")) {
			this.getAdapterState(callbackContext, false);
			return true;
		} else if (action.equals("requestEnable")) {
			if (!this.mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.prepareActivity(action, args, callbackContext, enableBtIntent, REQUEST_ENABLE_BT);
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("enable")) {
			// If there already is another enable action pending, call the error callback in order
			// to notify that the previous action has been cancelled
			if (this.mContextForEnable != null) {
				this.mContextForEnable.error(1);
				this.mContextForEnable = null;
			}

			if (!this.mBluetoothAdapter.isEnabled()) {
				if (!this.mBluetoothAdapter.enable()) {
					callbackContext.error(0);
				} else {
					// Save the context, in order to send the result once the action has been completed
					this.mContextForEnable = callbackContext;
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("disable")) {
			// If there already is another disable action pending, call the error callback in order
			// to notify that the previous action has been cancelled
			if (this.mContextForDisable != null) {
				this.mContextForDisable.error(1);
				this.mContextForDisable = null;
			}

			if (this.mBluetoothAdapter.isEnabled()) {
				if (!this.mBluetoothAdapter.disable()) {
					callbackContext.error(0);
				} else {
					// Save the context, in order to send the result once the action has been completed
					this.mContextForDisable = callbackContext;
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("getDevice")) {
			String address = args.getString(0);
			BluetoothDevice device = this.mBluetoothAdapter.getRemoteDevice(address);
			callbackContext.success(this.getDeviceInfo(device));
			return true;
		} else if (action.equals("getDevices")) {
			Set<BluetoothDevice> devices = this.mBluetoothAdapter.getBondedDevices();
			JSONArray deviceInfos = new JSONArray();
			for (BluetoothDevice device : devices) {
				deviceInfos.put(this.getDeviceInfo(device));
			}
			callbackContext.success(deviceInfos);
			return true;
		} else if (action.equals("startDiscovery")) {
			// Automatically cancel any previous discovery
			if (this.mBluetoothAdapter.isDiscovering()) {
				this.mBluetoothAdapter.cancelDiscovery();
			}

			if (cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
				this.startDiscovery(callbackContext);
			} else {
				this.getPermission(callbackContext, START_DISCOVERY_REQ_CODE, Manifest.permission.ACCESS_COARSE_LOCATION);
			}
			return true;
		} else if (action.equals("stopDiscovery")) {
			if (this.mBluetoothAdapter.isDiscovering()) {
				if (this.mBluetoothAdapter.cancelDiscovery()) {
					callbackContext.success();
				} else {
					callbackContext.error(0);
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("requestDiscoverable")) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			this.prepareActivity(action, args, callbackContext, discoverableIntent, REQUEST_DISCOVERABLE_BT);
			return true;
		} else {
			callbackContext.error("Invalid action");
			return false;
		}
	}

	public void getAdapterState(CallbackContext callbackContext, boolean keepCallback) {
		PluginResult pluginResult;

		try {
			JSONObject adapterState = new JSONObject();
			adapterState.put("address", this.mBluetoothAdapter.getAddress());
			adapterState.put("name", this.mBluetoothAdapter.getName());
			adapterState.put("enabled", this.mBluetoothAdapter.isEnabled());
			adapterState.put("discovering", this.mBluetoothAdapter.isDiscovering());
			adapterState.put("discoverable", this.mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);

            pluginResult = new PluginResult(PluginResult.Status.OK, adapterState);
            pluginResult.setKeepCallback(keepCallback);
            if(callbackContext!=null){
                callbackContext.sendPluginResult(pluginResult);
            }
		} catch (JSONException e) {
            pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            pluginResult.setKeepCallback(keepCallback);
            if(callbackContext!=null){
                callbackContext.sendPluginResult(pluginResult);
            }
		}
	}

	public JSONObject getDeviceInfo(BluetoothDevice device) throws JSONException {
		JSONObject deviceInfo = new JSONObject();

		deviceInfo.put("address", device.getAddress());
		deviceInfo.put("name", device.getName());
		deviceInfo.put("paired", device.getBondState() == BluetoothDevice.BOND_BONDED);

		return deviceInfo;
	}

	public void prepareActivity(String action, CordovaArgs args, CallbackContext callbackContext, Intent intent, int requestCode) {
		// If there already is another activity with this request code, call the error callback in order
		// to notify that the activity has been cancelled
		if (this.mContextForActivity.containsKey(requestCode)) {
            if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
				Log.d(TAG, LOG_PREFIX + "Attempted to start the same activity twice");
                sendBtEvent(getJson("EnableBluetoothActivity", "Attempted to start the same activity twice"), true);
                return;
            }
			callbackContext.error("Attempted to start the same activity twice");
			return;
		}

		// Store the callbackContext, in order to send the result once the activity has been completed
		this.mContextForActivity.put(requestCode, callbackContext);

		// Store the callbackContext, in order to send the result once the activity has been completed
		Log.d(TAG, LOG_PREFIX + "Calling BT enable activity");
		cordova.startActivityForResult(this, intent, requestCode);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		CallbackContext callbackContext = this.mContextForActivity.remove(requestCode);

		if (callbackContext != null) {
			if (resultCode == Activity.RESULT_CANCELED) {
                if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
					Log.d(TAG, LOG_PREFIX + "User Cancelled BT enable");
                    sendBtEvent(getJson("EnableBluetoothActivity", "user cancelled BT enable"), true);
                    return;
                }
				callbackContext.error(0);
			} else {
                if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
					Log.d(TAG, LOG_PREFIX + "BT Adapter cancelling device discovery");
                    // Automatically cancel any previous discovery
                    if (this.mBluetoothAdapter.isDiscovering()) {
                        this.mBluetoothAdapter.cancelDiscovery();
                    }

                    if (cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
						Log.d(TAG, LOG_PREFIX + "has Manifest.permission.ACCESS_COARSE_LOCATION");
                        this.startDiscovery(callbackContext);
                    } else {
						Log.d(TAG, LOG_PREFIX + "requesting permission ACCESS_COARSE_LOCATION");
                        this.getPermission(callbackContext, START_DISCOVERY_REQ_CODE, Manifest.permission.ACCESS_COARSE_LOCATION);
                    }
                    return;
                }
				callbackContext.success();
			}
		} else {
			// TO DO -- This may be a bug on the JavaScript side, as we get here only if the
			// activity has been started twice, before waiting the completion of the first one.
			Log.e(TAG, "BUG: onActivityResult -- (callbackContext == null)");
            sendBtEvent(getJson("EnableBluetoothActivity", "activity has been started twice, before waiting the completion of the first one."), true);
		}
	}

	public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			PluginResult pluginResult;

			Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive called");
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive ACTION_STATE_CHANGED called");
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
				int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);

				// If there was an enable request pending, send the result
				if ((previousState == BluetoothAdapter.STATE_TURNING_ON) && (mContextForEnable != null)) {
					if (state == BluetoothAdapter.STATE_ON) {
						mContextForEnable.success();
					} else {
						mContextForEnable.error(2);
					}
					mContextForEnable = null;
				}

				// If there was a disable request pending, send the result
				if ((previousState == BluetoothAdapter.STATE_TURNING_OFF) && (mContextForDisable != null)) {
					if (state == BluetoothAdapter.STATE_OFF) {
						mContextForDisable.success();
					} else {
						mContextForDisable.error(2);
					}
					mContextForDisable = null;
				}

				// Send the state changed event only if the state is not a transitioning one
				if ((state == BluetoothAdapter.STATE_OFF) || (state == BluetoothAdapter.STATE_ON)) {
                    if(!mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
                        getAdapterState(mContextForAdapterStateChanged, true);
                    }
				}
			} else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
				Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive ACTION_DISCOVERY_STARTED called");
                if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
					Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive ACTION_DISCOVERY_STARTED called in if()");
                    sendBtEvent(getJson("DeviceDiscovery", "started"), true);
                    return;
                }
				getAdapterState(mContextForAdapterStateChanged, true);
			}else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
					Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive ACTION_DISCOVERY_FINISHED called");
                    sendBtEvent(getJson("DeviceDiscovery", "finished"), true);
					Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
					//JSONArray deviceInfos = new JSONArray();
					try{
						for (BluetoothDevice device : devices) {
							//deviceInfos.put(this.getDeviceInfo(device));
							sendBtEvent(getJson("BondedDevice", getDeviceInfo(device).toString()), true);
						}
					}catch(JSONException e){
						//TODO
					}
                    return;
                }
				getAdapterState(mContextForAdapterStateChanged, true);
			} else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
				Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive ACTION_FOUND called");
				try {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					JSONObject deviceInfo = getDeviceInfo(device);

                    if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
						Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive ACTION_FOUND called in if()");
                        sendBtEvent(getJson("DeviceFound", deviceInfo.toString()), true);
                        return;
                    }

					pluginResult = new PluginResult(PluginResult.Status.OK, deviceInfo);
					pluginResult.setKeepCallback(true);
                    if(mContextForDeviceAdded!=null){
                        mContextForDeviceAdded.sendPluginResult(pluginResult);
                    }
				} catch (JSONException e) {
                    if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
						Log.d(TAG, LOG_PREFIX + "BroadcastReceiver onReceive ACTION_FOUND Json exception called");
                        sendBtEvent(getJson("DeviceFoundError", e.toString()), true);
                        return;
                    }
					pluginResult = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
					pluginResult.setKeepCallback(true);
                    if(mContextForDeviceAdded!=null){
                        mContextForDeviceAdded.sendPluginResult(pluginResult);
                    }
				}
			} else if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
				// BUG: The documented EXTRA_PREVIOUS_SCAN_MODE field of the intent is not implemented on Android.
				// For details see:
				// http://stackoverflow.com/questions/30553911/extra-previous-scan-mode-always-returns-an-error-for-android-bluetooth
				// As a workaround, the previous scan mode is handled manually here
				int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);

				// Report only the transitions from/to SCAN_MODE_CONNECTABLE_DISCOVERABLE
				if ((scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) || (mPreviousScanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)) {
					getAdapterState(mContextForAdapterStateChanged, true);
				}
				mPreviousScanMode = scanMode;
			}
		}
	};

	public void startDiscovery(CallbackContext callbackContext) {
        if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
			Log.d(TAG, LOG_PREFIX + "startDiscovery called");
            //IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			//cordova.getActivity().registerReceiver(this.mReceiver, filter);
			//LocalBroadcastManager.getInstance(cordova.getActivity()).registerReceiver(this.mReceiver, filter);
			new AsyncTSLinit().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
            return;
        }
		if (!this.mDeviceAddedRegistered) {
			//IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			//cordova.getActivity().registerReceiver(this.mReceiver, filter);
			//LocalBroadcastManager.getInstance(cordova.getActivity()).registerReceiver(this.mReceiver, filter);
			this.mDeviceAddedRegistered = true;
		}

		if (this.mBluetoothAdapter.startDiscovery()) {
			callbackContext.success();
		} else {
			callbackContext.error(0);
		}
	}

	public void getPermission(CallbackContext callbackContext, int requestCode, String permission) {
		// If there already is another permission request with this request code, call the error callback in order
		// to notify that the request has been cancelled
		if (this.mContextForPermission.containsKey(requestCode)) {
            if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
				Log.d(TAG, LOG_PREFIX + "getPermission called: Attempted to request the same permission twice");
                sendBtEvent(getJson("Permission", "Attempted to request the same permission twice"), true);
                return;
            }
			callbackContext.error("Attempted to request the same permission twice");
			return;
		}

		// Store the callbackContext, in order to send the result once the activity has been completed
		this.mContextForPermission.put(requestCode, callbackContext);

		Log.d(TAG, LOG_PREFIX + "getPermission called: requesting Permission");
		cordova.requestPermission(this, requestCode, permission);
	}

	@Override
	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
		CallbackContext callbackContext = this.mContextForPermission.remove(requestCode);

        Log.d(TAG, LOG_PREFIX + "Permission Granted");
		if (requestCode == START_DISCOVERY_REQ_CODE) {
			if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
				Log.d(TAG, LOG_PREFIX + "getPermission starting device discovery");
				this.startDiscovery(callbackContext);
			} else {
                if(mCurrentAction.equals(INIT_DEVICE_DISCOVERY)){
					Log.d(TAG, LOG_PREFIX + "Permission not granted");
                    sendBtEvent(getJson("Permission", "Permission not granted"), true);
                    return;
                }
				callbackContext.error(0);
			}
		}
	}

    private void registerAdapterStateChanges(){
        Log.d(TAG, LOG_PREFIX + "registerAdapterStateChanges called");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		filter.addAction(BluetoothDevice.ACTION_FOUND);
        cordova.getActivity().registerReceiver(this.mReceiver, filter);
		//LocalBroadcastManager.getInstance(cordova.getActivity()).registerReceiver(this.mReceiver, filter);
    }

    private void unRegisterAdapterStateChanges(){
		Log.d(TAG, LOG_PREFIX + "unRegisterAdapterStateChanges called");
        cordova.getActivity().unregisterReceiver(this.mReceiver);
		//LocalBroadcastManager.getInstance(cordova.getActivity()).unregisterReceiver(this.mReceiver);
    }

    private void initDeviceDiscovery(CallbackContext callbackContext,String action, CordovaArgs args){
        if (!this.mBluetoothAdapter.isEnabled()) {
			Log.d(TAG, LOG_PREFIX + "BT is not enabled, enabling it...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.prepareActivity(action, args, callbackContext, enableBtIntent, REQUEST_ENABLE_BT);
        } else {
			 Log.d(TAG, LOG_PREFIX + "BT Adapter cancelling device discovery");
			 // Automatically cancel any previous discovery
			 if (this.mBluetoothAdapter.isDiscovering()) {
				 this.mBluetoothAdapter.cancelDiscovery();
			 }

			 if (cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
				 Log.d(TAG, LOG_PREFIX + "has Manifest.permission.ACCESS_COARSE_LOCATION");
				 this.startDiscovery(callbackContext);
			 } else {
				 Log.d(TAG, LOG_PREFIX + "requesting permission ACCESS_COARSE_LOCATION");
				 this.getPermission(callbackContext, START_DISCOVERY_REQ_CODE, Manifest.permission.ACCESS_COARSE_LOCATION);
			 }
        }
    }

    private JSONObject getJson(String eventType, String eventValue) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("eventType", eventType);
            obj.put("eventValue", eventValue);
        } catch (JSONException e) {
            Log.d(TAG, LOG_PREFIX + e.getMessage(), e);
        }
        return obj;
    }

    private void sendBtEvent(JSONObject info, boolean keepCallback) {
		Log.d(TAG, LOG_PREFIX + "sendBtEvent called");
        if (this.mBtEventsCallbackContext!= null) {
			Log.d(TAG, LOG_PREFIX + "mBtEventsCallbackContext is not null, firing BluetoothEvent");
            PluginResult result = new PluginResult(PluginResult.Status.OK, info);
            result.setKeepCallback(keepCallback);
            this.mBtEventsCallbackContext.sendPluginResult(result);
        }else{
			Log.d(TAG, LOG_PREFIX + "mBtEventsCallbackContext is null, unable to fire BluetoothEvent");
		}
        // webView.postMessage("networkconnection", type);
    }

	private class AsyncTSLinit extends AsyncTask<String[], Void, Void> {
        @Override
        protected Void doInBackground(String[]... params) {

			mBluetoothAdapter.startDiscovery();

            return null; 
        }
    }
}


