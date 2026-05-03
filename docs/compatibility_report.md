# Android Compatibility Report (2026)

This document outlines the compatibility and optimization status of **MessageMe** across various Android versions, including the latest flagship devices like the Samsung S24/S25/S26 series.

## 🚀 Optimization Status

| Android Version | Status | Key Feature Supported |
| :--- | :--- | :--- |
| **Android 11 (API 30)** | ✅ Minimum Support | Base SMS/MMS functionality. |
| **Android 12/13 (API 31-33)** | ✅ Working | Modern `SmsManager` APIs and Runtime Notification Permissions. |
| **Android 14/15 (API 34-35)** | ✅ Optimized | Mandatory Edge-to-Edge and strict Default App eligibility rules. |
| **Android 16 (API 36)** | ✅ Fully Compliant | Built using the latest 2026 SDK standards. |

## 🛠️ Key Technical Implementations for Modern Android

### 1. Android 15 & 16 (API 35/36) Readiness
*   **Edge-to-Edge:** Implemented using `enableEdgeToEdge()` and standard `Scaffold` insets. This ensures the app UI flows correctly behind system bars on devices running Android 15+, where this is now a mandatory requirement.
*   **Target SDK 36:** The app is configured to target the latest available SDK, ensuring it adheres to the newest system behavior and security policies.

### 2. Notification Security (Android 13+)
*   **Runtime Permissions:** The app explicitly requests the `POST_NOTIFICATIONS` permission. On modern Android versions, notifications are blocked by default until the user grants this permission.

### 3. Default SMS App Eligibility
*   To comply with strict security rules in newer Android versions, the app includes all mandatory components to be recognized as a system-wide SMS handler:
    *   `SmsReceiver` (SMS_DELIVER action)
    *   `MmsReceiver` (WAP_PUSH_DELIVER action)
    *   `HeadlessSmsSendService` (RESPOND_VIA_MESSAGE action)
    *   `SENDTO` Intent filters in `MainActivity`.

### 4. Hardware & Feature Compatibility
*   **Multi-SIM Support:** Optimized for dual-SIM devices (common in modern high-end phones like the Samsung S-series). Users can select specific SIM cards for sending messages.
*   **Hardware Feature Declaration:** Correctly declares `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to ensure the app is available on devices that may lack telephony but support large-screen messaging features (like tablets or ChromeOS).

## 📱 Tested Devices
*   **Samsung Galaxy S24/S20 Series:** Verified full functionality including SIM selection and notification handling.
*   **Google Pixel 6/7/8/9:** Verified Edge-to-Edge layout and system gesture compatibility.
*   **Android Emulator (API 36):** Standard baseline for regression testing.
