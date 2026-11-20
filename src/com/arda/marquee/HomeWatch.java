package com.arda.marquee;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** Google TV keeps the Home key for its own launcher; when that launcher comes forward, put Marquee on top. */
public class HomeWatch extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        startActivity(new Intent(this, Home.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }
    @Override public void onInterrupt() {}
}
