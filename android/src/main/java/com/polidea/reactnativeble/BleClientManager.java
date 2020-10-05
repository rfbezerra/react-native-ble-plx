package com.polidea.reactnativeble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.os.Build;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.polidea.multiplatformbleadapter.BleAdapter;
import com.polidea.multiplatformbleadapter.BleAdapterFactory;
import com.polidea.multiplatformbleadapter.Characteristic;
import com.polidea.multiplatformbleadapter.ConnectionOptions;
import com.polidea.multiplatformbleadapter.ConnectionState;
import com.polidea.multiplatformbleadapter.Descriptor;
import com.polidea.multiplatformbleadapter.Device;
import com.polidea.multiplatformbleadapter.OnErrorCallback;
import com.polidea.multiplatformbleadapter.OnEventCallback;
import com.polidea.multiplatformbleadapter.OnSuccessCallback;
import com.polidea.multiplatformbleadapter.RefreshGattMoment;
import com.polidea.multiplatformbleadapter.ScanResult;
import com.polidea.multiplatformbleadapter.Service;
import com.polidea.multiplatformbleadapter.errors.BleError;
import com.polidea.reactnativeble.converter.BleErrorToJsObjectConverter;
import com.polidea.reactnativeble.converter.CharacteristicToJsObjectConverter;
import com.polidea.reactnativeble.converter.DescriptorToJsObjectConverter;
import com.polidea.reactnativeble.converter.DeviceToJsObjectConverter;
import com.polidea.reactnativeble.converter.ScanResultToJsObjectConverter;
import com.polidea.reactnativeble.converter.ServiceToJsObjectConverter;
import com.polidea.reactnativeble.utils.ReadableArrayConverter;
import com.polidea.reactnativeble.utils.SafePromise;
import com.polidea.reactnativeble.utils.UUIDConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import br.com.vertitecnologia.vmpay.utils.Base32768Util;
import br.com.vertitecnologia.vmpay.utils.CRC16Util;
import br.com.vertitecnologia.vmpay.utils.FileUtil;
import br.com.vertitecnologia.vmpay.utils.StringUtil;
import br.com.vertitecnologia.vmpay.vmbox.Package;

public class BleClientManager extends ReactContextBaseJavaModule {

    // Name of module
    private static final String NAME = "BleClientManager";

    // Value converters
    private final BleErrorToJsObjectConverter errorConverter = new BleErrorToJsObjectConverter();
    private final ScanResultToJsObjectConverter scanResultConverter = new ScanResultToJsObjectConverter();
    private final DeviceToJsObjectConverter deviceConverter = new DeviceToJsObjectConverter();
    private final CharacteristicToJsObjectConverter characteristicConverter = new CharacteristicToJsObjectConverter();
    private final DescriptorToJsObjectConverter descriptorConverter = new DescriptorToJsObjectConverter();
    private final ServiceToJsObjectConverter serviceConverter = new ServiceToJsObjectConverter();

    private BleAdapter bleAdapter;

    public BleClientManager(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        for (Event event : Event.values()) {
            constants.put(event.name, event.name);
        }
        return constants;
    }

    // Lifecycle -----------------------------------------------------------------------------------

    @ReactMethod
    public void createClient(String restoreStateIdentifier) {
        bleAdapter = BleAdapterFactory.getNewAdapter(getReactApplicationContext());
        bleAdapter.createClient(restoreStateIdentifier,
                new OnEventCallback<String>() {
                    @Override
                    public void onEvent(String state) {
                        sendEvent(Event.StateChangeEvent, state);
                    }
                }, new OnEventCallback<Integer>() {
                    @Override
                    public void onEvent(Integer data) {
                        sendEvent(Event.RestoreStateEvent, null);
                    }
                });
    }

    @ReactMethod
    public void destroyClient() {
        bleAdapter.destroyClient();
        bleAdapter = null;
    }

    // Mark: Common --------------------------------------------------------------------------------

    @ReactMethod
    public void cancelTransaction(String transactionId) {
        bleAdapter.cancelTransaction(transactionId);
    }

    @ReactMethod
    public void setLogLevel(String logLevel) {
        bleAdapter.setLogLevel(logLevel);
    }

    @ReactMethod
    public void logLevel(Promise promise) {
        promise.resolve(bleAdapter.getLogLevel());
    }

    // Mark: Monitoring state ----------------------------------------------------------------------

    @ReactMethod
    public void enable(final String transactionId, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.enable(transactionId, new OnSuccessCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                safePromise.resolve(null);
            }
        }, new OnErrorCallback() {
            @Override
            public void onError(BleError error) {
                safePromise.reject(null, errorConverter.toJs(error));
            }
        });
    }

    @ReactMethod
    public void disable(final String transactionId, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.disable(transactionId, new OnSuccessCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                safePromise.resolve(null);
            }
        }, new OnErrorCallback() {
            @Override
            public void onError(BleError error) {
                safePromise.reject(null, errorConverter.toJs(error));
            }
        });
    }

    @ReactMethod
    public void state(Promise promise) {
        promise.resolve(bleAdapter.getCurrentState());
    }

    // Mark: Scanning ------------------------------------------------------------------------------

    @ReactMethod
    public void startDeviceScan(@Nullable ReadableArray filteredUUIDs, @Nullable ReadableMap options) {
        final int DEFAULT_SCAN_MODE_LOW_POWER = 0;
        final int DEFAULT_CALLBACK_TYPE_ALL_MATCHES = 1;

        int scanMode = DEFAULT_SCAN_MODE_LOW_POWER;
        int callbackType = DEFAULT_CALLBACK_TYPE_ALL_MATCHES;

        if (options != null) {
            if (options.hasKey("scanMode") && options.getType("scanMode") == ReadableType.Number) {
                scanMode = options.getInt("scanMode");
            }
            if (options.hasKey("callbackType") && options.getType("callbackType") == ReadableType.Number) {
                callbackType = options.getInt("callbackType");
            }
        }

        bleAdapter.startDeviceScan(
                filteredUUIDs != null ? ReadableArrayConverter.toStringArray(filteredUUIDs) : null,
                scanMode, callbackType,
                new OnEventCallback<ScanResult>() {
                    @Override
                    public void onEvent(ScanResult data) {
                        sendEvent(Event.ScanEvent, scanResultConverter.toJSCallback(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        sendEvent(Event.ScanEvent, errorConverter.toJSCallback(error));
                    }
                });
    }

    @ReactMethod
    public void stopDeviceScan() {
        bleAdapter.stopDeviceScan();
    }

    // Mark: Device management ---------------------------------------------------------------------

    @ReactMethod
    public void devices(final ReadableArray deviceIdentifiers, final Promise promise) {
        bleAdapter.getKnownDevices(ReadableArrayConverter.toStringArray(deviceIdentifiers),
                new OnSuccessCallback<Device[]>() {
                    @Override
                    public void onSuccess(Device[] data) {
                        WritableArray jsDevices = Arguments.createArray();
                        for (Device device : data) {
                            jsDevices.pushMap(deviceConverter.toJSObject(device));
                        }
                        promise.resolve(jsDevices);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        promise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    @ReactMethod
    public void connectedDevices(final ReadableArray serviceUUIDs, final Promise promise) {
        bleAdapter.getConnectedDevices(ReadableArrayConverter.toStringArray(serviceUUIDs),
                new OnSuccessCallback<Device[]>() {
                    @Override
                    public void onSuccess(Device[] data) {
                        final WritableArray writableArray = Arguments.createArray();
                        for (Device device : data) {
                            writableArray.pushMap(deviceConverter.toJSObject(device));
                        }
                        promise.resolve(writableArray);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        promise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    // Mark: Device operations ---------------------------------------------------------------------

    @ReactMethod
    public void requestConnectionPriorityForDevice(final String deviceId, int connectionPriority, final String transactionId, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.requestConnectionPriorityForDevice(deviceId, connectionPriority, transactionId,
                new OnSuccessCallback<Device>() {
                    @Override
                    public void onSuccess(Device data) {
                        safePromise.resolve(deviceConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    @ReactMethod
    public void requestMTUForDevice(final String deviceId, int mtu, final String transactionId, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.requestMTUForDevice(deviceId, mtu, transactionId,
                new OnSuccessCallback<Device>() {
                    @Override
                    public void onSuccess(Device data) {
                        safePromise.resolve(deviceConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    @ReactMethod
    public void readRSSIForDevice(final String deviceId, final String transactionId, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.readRSSIForDevice(deviceId, transactionId,
                new OnSuccessCallback<Device>() {
                    @Override
                    public void onSuccess(Device data) {
                        safePromise.resolve(deviceConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    @ReactMethod
    public void connectToDevice(final String deviceId, @Nullable ReadableMap options, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);

        boolean autoConnect = false;
        int requestMtu = 0;
        RefreshGattMoment refreshGattMoment = null;
        Integer timeout = null;
        int connectionPriority = 0; // CONNECTION_PRIORITY_BALANCED

        if (options != null) {
            if (options.hasKey("autoConnect") && options.getType("autoConnect") == ReadableType.Boolean) {
                autoConnect = options.getBoolean("autoConnect");
            }
            if (options.hasKey("requestMTU") && options.getType("requestMTU") == ReadableType.Number) {
                requestMtu = options.getInt("requestMTU");
            }
            if (options.hasKey("refreshGatt") && options.getType("refreshGatt") == ReadableType.String) {
                refreshGattMoment = RefreshGattMoment.getByName(options.getString("refreshGatt"));
            }
            if (options.hasKey("timeout") && options.getType("timeout") == ReadableType.Number) {
                timeout = options.getInt("timeout");
            }
            if (options.hasKey("connectionPriority") && options.getType("connectionPriority") == ReadableType.Number) {
                connectionPriority = options.getInt("connectionPriority");
            }
        }
        bleAdapter.connectToDevice(
                deviceId,
                new ConnectionOptions(autoConnect,
                        requestMtu,
                        refreshGattMoment,
                        timeout != null ? timeout.longValue() : null,
                        connectionPriority),
                new OnSuccessCallback<Device>() {
                    @Override
                    public void onSuccess(Device data) {
                        safePromise.resolve(deviceConverter.toJSObject(data));
                    }
                },
                new OnEventCallback<ConnectionState>() {
                    @Override
                    public void onEvent(ConnectionState connectionState) {
                        if (connectionState == ConnectionState.DISCONNECTED) {
                            WritableArray event = Arguments.createArray();
                            event.pushNull();
                            WritableMap device = Arguments.createMap();
                            device.putString("id", deviceId);
                            event.pushMap(device);
                            sendEvent(Event.DisconnectionEvent, event);
                        }
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    @ReactMethod
    public void cancelDeviceConnection(String deviceId, Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.cancelDeviceConnection(deviceId,
                new OnSuccessCallback<Device>() {
                    @Override
                    public void onSuccess(Device data) {
                        safePromise.resolve(deviceConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    @ReactMethod
    public void isDeviceConnected(String deviceId, final Promise promise) {
        bleAdapter.isDeviceConnected(deviceId,
                new OnSuccessCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isConnected) {
                        promise.resolve(isConnected);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        promise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    // Mark: Discovery -----------------------------------------------------------------------------

    @ReactMethod
    public void discoverAllServicesAndCharacteristicsForDevice(String deviceId, final String transactionId, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.discoverAllServicesAndCharacteristicsForDevice(deviceId, transactionId,
                new OnSuccessCallback<Device>() {
                    @Override
                    public void onSuccess(Device data) {
                        safePromise.resolve(deviceConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    // Mark: Service and characteristic getters ----------------------------------------------------

    @ReactMethod
    public void servicesForDevice(final String deviceId, final Promise promise) {
        try {
            List<Service> services = bleAdapter.getServicesForDevice(deviceId);
            WritableArray jsArray = Arguments.createArray();
            for (Service service : services) {
                jsArray.pushMap(serviceConverter.toJSObject(service));
            }
            promise.resolve(jsArray);
        } catch (BleError error) {
            promise.reject(null, errorConverter.toJs(error));
        }

    }

    @ReactMethod
    public void characteristicsForDevice(final String deviceId,
                                         final String serviceUUID,
                                         final Promise promise) {
        try {
            List<Characteristic> characteristics = bleAdapter.getCharacteristicsForDevice(deviceId, serviceUUID);

            WritableArray jsCharacteristics = Arguments.createArray();
            for (Characteristic characteristic : characteristics) {
                jsCharacteristics.pushMap(characteristicConverter.toJSObject(characteristic));
            }
            promise.resolve(jsCharacteristics);
        } catch (BleError error) {
            promise.reject(null, errorConverter.toJs(error));
        }
    }

    @ReactMethod
    public void characteristicsForService(final int serviceIdentifier, final Promise promise) {
        try {
            List<Characteristic> characteristics = bleAdapter.getCharacteristicsForService(serviceIdentifier);
            WritableArray jsCharacteristics = Arguments.createArray();
            for (Characteristic characteristic : characteristics) {
                jsCharacteristics.pushMap(characteristicConverter.toJSObject(characteristic));
            }
            promise.resolve(jsCharacteristics);
        } catch (BleError error) {
            promise.reject(null, errorConverter.toJs(error));
        }
    }

    @ReactMethod
    public void descriptorsForDevice(final String deviceIdentifier,
                                     final String serviceUUID,
                                     final String characteristicUUID,
                                     final Promise promise) {
        try {
            List<Descriptor> descriptors = bleAdapter.descriptorsForDevice(deviceIdentifier, serviceUUID, characteristicUUID);
            WritableArray jsDescriptors = Arguments.createArray();
            for (Descriptor descriptor : descriptors) {
                jsDescriptors.pushMap(descriptorConverter.toJSObject(descriptor));
            }
            promise.resolve(jsDescriptors);
        } catch (BleError error) {
            promise.reject(null, errorConverter.toJs(error));
        }
    }

    @ReactMethod
    public void descriptorsForService(final int serviceIdentifier,
                                      final String characteristicUUID,
                                      final Promise promise) {
        try {
            List<Descriptor> descriptors = bleAdapter.descriptorsForService(serviceIdentifier, characteristicUUID);
            WritableArray jsDescriptors = Arguments.createArray();
            for (Descriptor descriptor : descriptors) {
                jsDescriptors.pushMap(descriptorConverter.toJSObject(descriptor));
            }
            promise.resolve(jsDescriptors);
        } catch (BleError error) {
            promise.reject(null, errorConverter.toJs(error));
        }
    }

    @ReactMethod
    public void descriptorsForCharacteristic(final int characteristicIdentifier,
                                             final Promise promise) {
        try {
            List<Descriptor> descriptors = bleAdapter.descriptorsForCharacteristic(characteristicIdentifier);
            WritableArray jsDescriptors = Arguments.createArray();
            for (Descriptor descriptor : descriptors) {
                jsDescriptors.pushMap(descriptorConverter.toJSObject(descriptor));
            }
            promise.resolve(jsDescriptors);
        } catch (BleError error) {
            promise.reject(null, errorConverter.toJs(error));
        }
    }

    // Mark: Characteristics operations ------------------------------------------------------------

    @ReactMethod
    public void writeCharacteristicForDevice(final String deviceId,
                                             final String serviceUUID,
                                             final String characteristicUUID,
                                             final String valueBase64,
                                             final Boolean response,
                                             final String transactionId,
                                             final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);

        bleAdapter.writeCharacteristicForDevice(
                deviceId, serviceUUID, characteristicUUID, valueBase64, response, transactionId,
                new OnSuccessCallback<Characteristic>() {
                    @Override
                    public void onSuccess(Characteristic data) {
                        safePromise.resolve(characteristicConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void writeCharacteristicForService(final int serviceIdentifier,
                                              final String characteristicUUID,
                                              final String valueBase64,
                                              final Boolean response,
                                              final String transactionId,
                                              final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.writeCharacteristicForService(
                serviceIdentifier, characteristicUUID, valueBase64, response, transactionId,
                new OnSuccessCallback<Characteristic>() {
                    @Override
                    public void onSuccess(Characteristic data) {
                        safePromise.resolve(characteristicConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void writeCharacteristic(final int characteristicIdentifier,
                                    final String valueBase64,
                                    final Boolean response,
                                    final String transactionId,
                                    final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);

        bleAdapter.writeCharacteristic(characteristicIdentifier, valueBase64, response, transactionId,
                new OnSuccessCallback<Characteristic>() {
                    @Override
                    public void onSuccess(Characteristic data) {
                        safePromise.resolve(characteristicConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                });
    }

    @ReactMethod
    public void readCharacteristicForDevice(final String deviceId,
                                            final String serviceUUID,
                                            final String characteristicUUID,
                                            final String transactionId,
                                            final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);

        bleAdapter.readCharacteristicForDevice(
                deviceId, serviceUUID, characteristicUUID, transactionId,
                new OnSuccessCallback<Characteristic>() {
                    @Override
                    public void onSuccess(Characteristic data) {
                        safePromise.resolve(characteristicConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void readCharacteristicForService(final int serviceIdentifier,
                                             final String characteristicUUID,
                                             final String transactionId,
                                             final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);

        bleAdapter.readCharacteristicForService(
                serviceIdentifier, characteristicUUID, transactionId,
                new OnSuccessCallback<Characteristic>() {
                    @Override
                    public void onSuccess(Characteristic data) {
                        safePromise.resolve(characteristicConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void readCharacteristic(final int characteristicIdentifier,
                                   final String transactionId,
                                   final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);

        bleAdapter.readCharacteristic(
                characteristicIdentifier, transactionId,
                new OnSuccessCallback<Characteristic>() {
                    @Override
                    public void onSuccess(Characteristic data) {
                        safePromise.resolve(characteristicConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void monitorCharacteristicForDevice(final String deviceId,
                                               final String serviceUUID,
                                               final String characteristicUUID,
                                               final String transactionId,
                                               final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.monitorCharacteristicForDevice(
                deviceId, serviceUUID, characteristicUUID, transactionId,
                new OnEventCallback<Characteristic>() {
                    @Override
                    public void onEvent(Characteristic data) {
                        WritableArray jsResult = Arguments.createArray();
                        jsResult.pushNull();
                        jsResult.pushMap(characteristicConverter.toJSObject(data));
                        jsResult.pushString(transactionId);
                        sendEvent(Event.ReadEvent, jsResult);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void monitorCharacteristicForService(final int serviceIdentifier,
                                                final String characteristicUUID,
                                                final String transactionId,
                                                final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.monitorCharacteristicForService(
                serviceIdentifier, characteristicUUID, transactionId,
                new OnEventCallback<Characteristic>() {
                    @Override
                    public void onEvent(Characteristic data) {
                        WritableArray jsResult = Arguments.createArray();
                        jsResult.pushNull();
                        jsResult.pushMap(characteristicConverter.toJSObject(data));
                        jsResult.pushString(transactionId);
                        sendEvent(Event.ReadEvent, jsResult);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void monitorCharacteristic(final int characteristicIdentifier,
                                      final String transactionId,
                                      final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        //TODO resolve safePromise with null when monitoring has been completed
        bleAdapter.monitorCharacteristic(
                characteristicIdentifier, transactionId,
                new OnEventCallback<Characteristic>() {
                    @Override
                    public void onEvent(Characteristic data) {
                        WritableArray jsResult = Arguments.createArray();
                        jsResult.pushNull();
                        jsResult.pushMap(characteristicConverter.toJSObject(data));
                        jsResult.pushString(transactionId);
                        sendEvent(Event.ReadEvent, jsResult);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }

    @ReactMethod
    public void readDescriptorForDevice(final String deviceId,
                                        final String serviceUUID,
                                        final String characteristicUUID,
                                        final String descriptorUUID,
                                        final String transactionId,
                                        final Promise promise) {
        bleAdapter.readDescriptorForDevice(
                deviceId,
                serviceUUID,
                characteristicUUID,
                descriptorUUID,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                });
    }

    @ReactMethod
    public void readDescriptorForService(final int serviceIdentifier,
                                         final String characteristicUUID,
                                         final String descriptorUUID,
                                         final String transactionId,
                                         final Promise promise) {
        bleAdapter.readDescriptorForService(
                serviceIdentifier,
                characteristicUUID,
                descriptorUUID,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                });
    }

    @ReactMethod
    public void readDescriptorForCharacteristic(final int characteristicIdentifier,
                                                final String descriptorUUID,
                                                final String transactionId,
                                                final Promise promise) {
        bleAdapter.readDescriptorForCharacteristic(
                characteristicIdentifier,
                descriptorUUID,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                });
    }

    @ReactMethod
    public void readDescriptor(final int descriptorIdentifier,
                               final String transactionId,
                               final Promise promise) {
        bleAdapter.readDescriptor(
                descriptorIdentifier,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                });
    }

    @ReactMethod
    public void writeDescriptorForDevice(final String deviceId,
                                         final String serviceUUID,
                                         final String characteristicUUID,
                                         final String descriptorUUID,
                                         final String valueBase64,
                                         final String transactionId,
                                         final Promise promise) {
        bleAdapter.writeDescriptorForDevice(
                deviceId,
                serviceUUID,
                characteristicUUID,
                descriptorUUID,
                valueBase64,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                }
        );
    }

    @ReactMethod
    public void writeDescriptorForService(final int serviceIdentifier,
                                          final String characteristicUUID,
                                          final String descriptorUUID,
                                          final String valueBase64,
                                          final String transactionId,
                                          final Promise promise) {
        bleAdapter.writeDescriptorForService(
                serviceIdentifier,
                characteristicUUID,
                descriptorUUID,
                valueBase64,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                }
        );
    }

    @ReactMethod
    public void writeDescriptorForCharacteristic(final int characteristicIdentifier,
                                                 final String descriptorUUID,
                                                 final String valueBase64,
                                                 final String transactionId,
                                                 final Promise promise) {
        bleAdapter.writeDescriptorForCharacteristic(
                characteristicIdentifier,
                descriptorUUID,
                valueBase64,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                }
        );
    }

    @ReactMethod
    public void writeDescriptor(final int descriptorIdentifier,
                                final String valueBase64,
                                final String transactionId,
                                final Promise promise) {
        bleAdapter.writeDescriptor(
                descriptorIdentifier,
                valueBase64,
                transactionId,
                new OnSuccessCallback<Descriptor>() {
                    @Override
                    public void onSuccess(Descriptor descriptor) {
                        promise.resolve(descriptorConverter.toJSObject(descriptor));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError bleError) {
                        promise.reject(null, errorConverter.toJs(bleError));
                    }
                }
        );
    }

    private void sendEvent(@NonNull Event event, @Nullable Object params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(event.name, params);
    }

    // Mark: Verti
    private static final int BLOCK_SIZE = 180;
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private static final UUID serviceUUID = UUIDConverter.convert("ffe0");
    private static final UUID characteristicUUID = UUIDConverter.convert("ffe1");

    /* Arquivos configuração e firmware smart e mob v1 */
    private static class FileBlock {
        String number;
        String data;

        FileBlock(String number, String data) {
            this.number = number;
            this.data = data;
        }

        FileBlock(int number, String data) {
            this(String.format(Locale.US, "%02x%02x", number & 0xff, (number >> 8) & 0xff), data);
        }

        List<String> toArray() {
            return Arrays.asList(number, data);
        }

        static FileBlock first(int type) {
            return new FileBlock(0, String.format(Locale.US, "%02d", type));
        }

        static FileBlock last() {
            return new FileBlock("FFFF", "");
        }
    }

    private final List<FileBlock> fileDataBlocks = new ArrayList<>();

    private List<String> buildBlocks(byte[] data) throws IOException {
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i <= (data.length / BLOCK_SIZE); i++) {
            int from = i * BLOCK_SIZE;
            int to = Math.min(from + BLOCK_SIZE, data.length);

            byte[] blockData = Arrays.copyOfRange(data, from, to);
            ByteArrayInputStream bis = new ByteArrayInputStream(blockData);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Base32768Util.encode(bis, bos);

            blocks.add(StringUtil.byteArrayToString(bos.toByteArray(), CHARSET));
        }
        return blocks;
    }

    @ReactMethod
    public void loadFileBlocks(String path, int type, Promise promise) {
        try {
            /* 0 - Configuracao | 1 - Firmware */
            byte[] fileData = (type == 0) ?
                    FileUtil.readBytes(path) : FileUtil.readBytesFromPackageUpdateZip(path);
            if (fileData == null) {
                promise.reject(new RuntimeException("Error while load zip file data"));
                return;
            }

            List<String> blocks = buildBlocks(fileData);
            int blockNo = 1;
            this.fileDataBlocks.clear();
            this.fileDataBlocks.add(FileBlock.first(type));
            for (String data : blocks) {
                this.fileDataBlocks.add(new FileBlock(blockNo, data));
                blockNo += 1;
            }
            this.fileDataBlocks.add(FileBlock.last());

            WritableMap ret = Arguments.createMap();
            ret.putInt("size", this.fileDataBlocks.size());
            promise.resolve(ret);
        } catch (IOException ioe) {
            promise.reject(ioe);
        }
    }

    @ReactMethod
    public void clearFileData(final Promise promise) {
        this.fileDataBlocks.clear();
        promise.resolve(true);
    }

    @ReactMethod
    public void sendFileBlock(String deviceUUID, int pkgNo, int blockNo, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);

        FileBlock block = this.fileDataBlocks.get(blockNo);
        String cmd = new Package(pkgNo, "07", block.toArray()).toCommand();
        bleAdapter.longWriteCharacteristic(
                deviceUUID,
                serviceUUID.toString(),
                characteristicUUID.toString(),
                cmd.getBytes(CHARSET),
                false,
                null,
                new OnSuccessCallback<Characteristic>() {
                    @Override
                    public void onSuccess(Characteristic data) {
                        safePromise.resolve(characteristicConverter.toJSObject(data));
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }


    /* Arquivos configuração e firmware mob v2 */
    private byte[] mobfileData = new byte[0];

    @ReactMethod
    public void loadMobFile(String path, int type, final Promise promise) {
        try {
            /* 0 - Configuracao | 1 - FW STM | 2 - FW ESP */
            this.mobfileData = (type == 0) ?
                    FileUtil.readBytes(path) : FileUtil.readBytesFromPackageUpdateZip(path);

            if (this.mobfileData == null) {
                promise.reject(new RuntimeException("Error while load zip file data"));
                return;
            }

            WritableMap ret = Arguments.createMap();
            ret.putInt("size", this.mobfileData.length);
            ret.putString("crc", CRC16Util.calculate(this.mobfileData));
            promise.resolve(ret);
        } catch (IOException ioe) {
            promise.reject(ioe);
        }
    }

    @ReactMethod
    public void clearModFile(final Promise promise) {
        mobfileData = new byte[0];
        promise.resolve(true);
    }

    @ReactMethod
    public void sendMobFile(String deviceUUID, int pkgNo, int offset, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        try {
            int to = Math.min(offset + BLOCK_SIZE, mobfileData.length);

            final byte[] dataToSend = Arrays.copyOfRange(mobfileData, offset, to);
            ByteArrayInputStream bis = new ByteArrayInputStream(dataToSend);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Base32768Util.encode(bis, bos);

            String dataStr = StringUtil.byteArrayToString(bos.toByteArray(), CHARSET);
            List<String> data = Arrays.asList(StringUtil.intToHex(offset, 4), dataStr);

//            Log.i(VTAG, String.format("Escrevendo %s | %s", data.get(0), data.get(1)));
            String cmd = new Package(pkgNo, "12", data).toCommand();
//            Log.i(VTAG, String.format("Comando %s", cmd));

            bleAdapter.longWriteCharacteristic(
                    deviceUUID,
                    serviceUUID.toString(),
                    characteristicUUID.toString(),
                    cmd.getBytes(CHARSET),
                    false,
                    null,
                    new OnSuccessCallback<Characteristic>() {
                        @Override
                        public void onSuccess(Characteristic data) {
                            safePromise.resolve(characteristicConverter.toJSObject(data));
                        }
                    }, new OnErrorCallback() {
                        @Override
                        public void onError(BleError error) {
                            safePromise.reject(null, errorConverter.toJs(error));
                        }
                    }
            );
        } catch (IOException ioe) {
//            Log.e(VTAG, ioe.getMessage());
            safePromise.reject(ioe);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @ReactMethod
    public void setHighPriority(String deviceUUID, final Promise promise) {
        final SafePromise safePromise = new SafePromise(promise);
        bleAdapter.requestConnectionPriorityForDevice(
                deviceUUID,
                BluetoothGatt.CONNECTION_PRIORITY_HIGH,
                null,
                new OnSuccessCallback<Device>() {
                    @Override
                    public void onSuccess(Device data) {
                        safePromise.resolve(deviceConverter.toJSObject(data));
                    }
                },
                new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        safePromise.reject(null, errorConverter.toJs(error));
                    }
                }
        );
    }
}
