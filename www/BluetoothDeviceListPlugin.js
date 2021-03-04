var exec = require('cordova/exec');
var cordova = require('cordova');
var channel = require('cordova/channel');

function BluetoothEvents() {
}

BluetoothEvents.prototype.initDeviceDiscovery = function (successCallback, errorCallback) {
    console.log('BRIDGE: registering for BT events ');
    bluetoothEvents.startBtEvents(function (info) {
        console.log('BRIDGE: firing BT event');
        cordova.fireDocumentEvent('BluetoothEvent', info);
        // should only fire this once
        if (channel.onCordovaConnectionReady.state !== 2) {
            channel.onCordovaConnectionReady.fire();
        }
    },
        function (e) {
            if (channel.onCordovaConnectionReady.state !== 2) {
                channel.onCordovaConnectionReady.fire();
            }
            console.log('BRIDGE: Error in initiating BT events: ' + e);
        });
        
    exec(successCallback, errorCallback, 'BluetoothDeviceListPlugin', 'initDeviceDiscovery', []);
};

BluetoothEvents.prototype.startBtEvents = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'BluetoothDeviceListPlugin', 'startBtEvents', []);
};

BluetoothEvents.prototype.stopBtEvents = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'BluetoothDeviceListPlugin', 'stopBtEvents', []);
};

BluetoothEvents.prototype.getAdapterState = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'getAdapterState', []);
};

BluetoothEvents.prototype.requestEnable = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'requestEnable', []);
};

BluetoothEvents.prototype.enable = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'enable', []);
};

BluetoothEvents.prototype.disable = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'disable', []);
};

BluetoothEvents.prototype.getDevice = function (deviceAddress, success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'getDevice', [deviceAddress]);
};

BluetoothEvents.prototype.getDevices = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'getDevices', []);
};

BluetoothEvents.prototype.startDiscovery = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'startDiscovery', []);
};

BluetoothEvents.prototype.stopDiscovery = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'stopDiscovery', []);
};

BluetoothEvents.prototype.registerAdapterStateChanged = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'registerAdapterStateChanged', []);
};

BluetoothEvents.prototype.unRegisterAdapterStateChanged = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'unRegisterAdapterStateChanged', []);
};

BluetoothEvents.prototype.registerDeviceAdded = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'registerDeviceAdded', []);
};

BluetoothEvents.prototype.unRegisterDeviceAdded = function (success, error) {
	exec(success, error, 'BluetoothDeviceListPlugin', 'unRegisterDeviceAdded', []);
};

var bluetoothEvents = new BluetoothEvents();

channel.createSticky('onCordovaConnectionReady');
channel.waitForInitialization('onCordovaConnectionReady');

module.exports = bluetoothEvents;