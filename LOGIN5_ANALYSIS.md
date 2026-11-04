# Login5 Authentication Failure Analysis

## Problem Summary

**Error**: `java.io.IOException: Login5 token request was rejected: 1`

**Location**: `TokenProvider.java:74`

**Error Code**: `1` = `INVALID_CREDENTIALS` (from Login5 LoginError enum)

---

## Root Cause Analysis

### 1. **Login5 Protocol Deprecated by Spotify**

From research documents (F:\trh\resch\2.txt, 3.txt):

- The Login5 endpoint `https://login5.spotify.com/v3/login` has been **deactivated by Spotify**
- Server returns `403 Forbidden` or `503 Service Unavailable` for Login5 requests
- The stored credentials format (`AUTHENTICATION_STORED_SPOTIFY_CREDENTIALS`) is **incompatible** with current Spotify infrastructure

### 2. **librespot-java 1.6.5 is Obsolete**

Per research documents:
```
"Библиотека xyz.gianlu.librespot:librespot-lib версии 1.6.5 не поддерживается 
на протяжении нескольких лет и не способна функционировать в актуальной 
сетевой среде Android."
```

Translation: "The library librespot-lib version 1.6.5 has not been maintained for several years and cannot function in the current Android network environment."

The project itself shows this - librespot-java repository is marked **[DEPRECATED]** on GitHub.

### 3. **Missing Login5Api Implementation**

**Finding**: The `Login5Api` class referenced in `TokenProvider.java:69` exists in the librespot-lib JAR but the proto-generated classes it depends on (`ClientInfo`, `Login5.LoginRequest` fields) may be incomplete or outdated in version 1.6.5.

### 4. **Required LoginRequest Fields (from upstream research)**

Per commit `90978c1ce60b40344e3a6f70dae560b9c9a8a818`:

**Minimal required fields for stored credential authentication:**

1. **client_info** (must be added by Login5Api)
   - `client_id`: `"65b708073fc0480ea92a077233ca87bd"` (official desktop client)
   - `device_id`: From `session.deviceId()`

2. **stored_credential** (provided by TokenProvider)
   - `username`: From `session.username()`
   - `data`: From `session.apWelcome().getReusableAuthCredentials()`

**Current TokenProvider.java (lines 65-71)** is missing the `client_info` enrichment step.

---

## Why the Fix Won't Work Long-Term

Even if we add the missing `client_info` fields to the LoginRequest:

1. **Spotify's servers reject Login5 requests** - the endpoint is dead
2. **Mobile carrier DPI blocks Shannon protocol** on port 443 (non-TLS traffic detection)
3. **Free accounts cannot get audio keys** - server requires DRM LicenseVersion 2
4. **Stored credentials expire** and cannot be refreshed without Zeroconf

---

## Recommended Solutions

### Option 1: Quick Patch (Temporary Fix)

**Add client_info to LoginRequest in TokenProvider.java:**

```java
// At line 65-71, replace with:
ClientInfo clientInfo = ClientInfo.newBuilder()
        .setClientId("65b708073fc0480ea92a077233ca87bd")
        .setDeviceId(session.deviceId())
        .build();

Login5.LoginRequest request = Login5.LoginRequest.newBuilder()
        .setClientInfo(clientInfo)
        .setStoredCredential(Credentials.StoredCredential.newBuilder()
                .setUsername(session.username())
                .setData(ByteString.copyFrom(session.apWelcome().getReusableAuthCredentials().toByteArray()))
                .build())
        .build();

Login5.LoginResponse response = new Login5Api(session).login5(request);
```

**Limitations:**
- May still fail with INVALID_CREDENTIALS if server rejects Login5
- Requires valid stored credentials from Zeroconf pairing
- Won't work on mobile networks due to DPI blocking

### Option 2: Use Zeroconf Authentication (Recommended for Connect)

Implement Spotify Connect discovery to obtain valid credentials:

```java
// Start mDNS service advertising
ZeroconfServer zeroconf = new ZeroconfServer.Builder(session)
        .setPreferredLocale("en")
        .setDeviceType(Connect.DeviceType.COMPUTER)
        .setDeviceName("ArchiveTune Player")
        .create();

// User pairs via official Spotify app -> receives valid credentials
// Store credentials for future use
```

### Option 3: Migrate to Spclient REST API (Recommended for Mobile)

**Complete replacement of librespot-java approach:**

1. **Authentication**: OAuth2 PKCE flow
   ```
   Client ID: 65b708073fc0480ea92a077233ca87bd
   Endpoint: https://accounts.spotify.com/api/token
   ```

2. **Token Refresh**: Standard OAuth refresh_token flow

3. **Audio Streaming**: Spclient CDN + Widevine DRM
   ```
   Storage resolve: https://spclient.wg.spotify.com/storage-resolve/files/audio/...
   License: https://spclient.wg.spotify.com/widevine-license/v1/audio/license
   ```

4. **Player**: Android Media3 (ExoPlayer) with native DRM support

**Advantages:**
- ✅ No DPI blocking (standard HTTPS)
- ✅ Works with Free accounts
- ✅ Official protocol (won't break)
- ✅ Better battery life (no raw TCP sockets)

### Option 4: Update to librespot-rust 0.8.0+

**Requires significant refactoring:**
- Compile librespot-rust core via Android NDK
- Create JNI bridge for Kotlin/Java interop
- Implement TLS tunneling for Shannon protocol to bypass DPI

---

## Files Examined

### Local Project
- `app/src/main/java/xyz/gianlu/librespot/core/TokenProvider.java` - Token request logic
- `app/src/main/java/xyz/gianlu/librespot/core/ApResolver.java` - Access point resolution
- `app/build.gradle.kts:409` - librespot-lib dependency
- `gradle/libs.versions.toml:54` - librespot version 1.6.5

### Research Documents
- `F:\trh\resch\1.txt` - Architecture analysis of mobile Spotify clients
- `F:\trh\resch\2.txt` - Librespot session failure diagnosis, DPI blocking
- `F:\trh\resch\3.txt` - Login5 protocol deprecation, Spclient migration guide

### Upstream Repository
- GitHub: `librespot-org/librespot-java` (DEPRECATED)
- Commit: `90978c1ce60b40344e3a6f70dae560b9c9a8a818` - "Request token from Login5"
- Current: librespot-lib JAR contains Login5Api but insufficient for current servers

---

## Conclusion

**The Login5 authentication method is fundamentally broken and cannot be fixed** with simple code changes. Spotify has deprecated the protocol server-side.

**Immediate action**: Implement Option 2 (Zeroconf) or Option 3 (Spclient migration) for long-term stability.

**Quick test**: Add `client_info` fields (Option 1) to see if specific error changes, but expect continued failures.
