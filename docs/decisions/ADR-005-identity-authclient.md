# ADR-005: Identity.getAuthorizationClient Replaces GoogleSignIn

**Date:** 2026-05-03
**Status:** Accepted

## Context

The app used `com.google.android.gms.auth.api.signin.GoogleSignIn` to authenticate users for Google Drive backup. Google announced deprecation of the legacy Sign-In SDK in 2023. On devices with Play Services auto-updates (Android 14+), `GoogleSignInClient.signInIntent` may return `SIGN_IN_FAILED` (error code 10) silently.

## Decision

Authentication for Google Drive access uses `Identity.getAuthorizationClient` from `play-services-auth:21.3.0`:

```kotlin
val authRequest = AuthorizationRequest.builder()
    .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_FILE)))
    .build()

Identity.getAuthorizationClient(context)
    .authorize(authRequest)
    .addOnSuccessListener { authResult ->
        if (authResult.hasResolution()) {
            // Launch consent screen via StartIntentSenderForResult
            launcher.launch(IntentSenderRequest.Builder(authResult.pendingIntent!!.intentSender).build())
        } else {
            // Already authorized — use access token directly
            backupToDrive(context, authResult.accessToken!!, messages)
        }
    }
```

The `backupToDrive` function authenticates to the Drive API using `BearerToken.authorizationHeaderAccessMethod()` with the returned `accessToken` — no `GoogleAccountCredential` needed.

## Consequences

- **`GoogleSignIn`, `GoogleSignInAccount`, `GoogleSignInOptions` must never be imported again** (Detekt `ForbiddenMethodCall` rule enforces this)
- The `driveAuthLauncher` uses `StartIntentSenderForResult`, not `StartActivityForResult`
- No `serverClientId` (Web OAuth client ID) is required for this flow — it works with the existing Android OAuth client in `google-services.json`
- `GoogleAccountCredential.usingOAuth2` is removed — the Drive `Credential` is created from the raw access token
