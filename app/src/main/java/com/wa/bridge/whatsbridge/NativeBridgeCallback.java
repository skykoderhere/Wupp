package com.wa.bridge.whatsbridge;

public interface NativeBridgeCallback {
    void onJsonEvent(String eventType, String payloadJson);
}
