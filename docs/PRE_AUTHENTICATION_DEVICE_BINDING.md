# Pre-Authentication and Device Binding

This document describes the pre-authentication and device binding (Gerätebindung) features implemented in the gematik sectoral IDP.

## Overview

The implementation provides two key security features:

1. **Device Binding (Gerätebindung)**: Ensures that authentication sessions are bound to specific devices, preventing session hijacking across devices.
2. **Pre-Authentication**: Allows devices to obtain a pre-authentication token that can be used to streamline the authentication flow.

## Architecture

### Components

#### DeviceBindingService
Core service managing device registration and pre-authentication tokens.

#### New Data Models
- `DeviceInfo`: Stores device information (ID, type, name, registration timestamp)
- `PreAuthToken`: Represents pre-authentication tokens with user and device binding
- `PreAuthResponse`: Response object for pre-authentication token creation

#### Updated Models
- `FedIdpAuthSession`: Extended with device binding fields (`deviceId`, `deviceType`)

## API Endpoints

### 1. Device Registration

**Endpoint:** `POST /device/register`

**Description:** Registers a device for device binding

**Request Parameters:**
- `device_id` (required): Unique device identifier
- `device_type` (required): Type of device (e.g., "android", "ios", "web")
- `device_name` (optional): Human-readable device name

**Request Example:**
```bash
curl -X POST http://localhost:8085/device/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "device_id=unique-device-123" \
  -d "device_type=android" \
  -d "device_name=My Android Phone"
```

**Response:**
```json
{
  "status": "registered",
  "device_id": "unique-device-123",
  "device_type": "android"
}
```

### 2. Pre-Authentication

**Endpoint:** `POST /pre-auth`

**Description:** Creates a pre-authentication token for a user and device combination

**Request Parameters:**
- `user_id` (required): User identifier (KVNR format: one letter followed by 9 digits)
- `device_id` (required): Previously registered device identifier

**Request Example:**
```bash
curl -X POST http://localhost:8085/pre-auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "user_id=X110411675" \
  -d "device_id=unique-device-123"
```

**Response:**
```json
{
  "preAuthToken": "8e22d4f759a3d03315b05155b76862d1...",
  "expiresIn": 600
}
```

### 3. Enhanced Authorization Flow

#### Pushed Authorization Request (PAR)

The PAR endpoint now supports optional device binding parameters:

**Additional Parameters:**
- `device_id` (optional): Device identifier for session binding
- `device_type` (optional): Device type

**Example:**
```bash
curl -X POST http://localhost:8085/PAR_Auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=https://example.com" \
  -d "state=state123" \
  -d "redirect_uri=https://example.com/callback" \
  -d "code_challenge=challenge123..." \
  -d "code_challenge_method=S256" \
  -d "response_type=code" \
  -d "nonce=nonce123" \
  -d "scope=openid" \
  -d "acr_values=gematik-ehealth-loa-high" \
  -d "device_id=unique-device-123" \
  -d "device_type=android"
```

#### Authorization Endpoint

The authorization endpoint now supports pre-authentication:

**Additional Parameters:**
- `pre_auth_token` (optional): Pre-authentication token obtained from `/pre-auth`
- `device_id` (optional): Device identifier for validation

**Example:**
```bash
curl -X GET "http://localhost:8085/auth?request_uri=urn:...&user_id=X110411675&pre_auth_token=8e22d4f759a3d03315b05155b76862d1...&device_id=unique-device-123"
```

## Usage Flows

### Flow 1: Basic Device Binding

1. **Register Device:**
   ```
   POST /device/register
   → device_id, device_type, device_name
   ```

2. **Initiate PAR with Device Binding:**
   ```
   POST /PAR_Auth
   → ... standard PAR params ...
   → device_id, device_type
   ```

3. **Complete Authorization:**
   ```
   GET /auth?request_uri=...&user_id=...&device_id=...
   ```

### Flow 2: Pre-Authentication Flow

1. **Register Device:**
   ```
   POST /device/register
   → device_id, device_type
   ```

2. **Create Pre-Auth Token:**
   ```
   POST /pre-auth
   → user_id, device_id
   → Returns: preAuthToken (valid for 600 seconds)
   ```

3. **Initiate PAR with Device Binding:**
   ```
   POST /PAR_Auth
   → ... standard PAR params ...
   → device_id, device_type
   ```

4. **Complete Authorization with Pre-Auth Token:**
   ```
   GET /auth?request_uri=...&user_id=...&pre_auth_token=...&device_id=...
   ```

## Security Features

### Device Binding Validation

- Device must be registered before use
- Session-to-device binding is enforced
- Device mismatch results in HTTP 401 Unauthorized

### Pre-Authentication Token Security

- **Single-use tokens**: Each token can only be used once
- **Short-lived**: 10-minute expiration (600 seconds)
- **Device-bound**: Token is tied to a specific device
- **User-verified**: User ID must match the pre-authenticated user
- **Automatic cleanup**: Expired tokens are removed

### Error Handling

The implementation provides specific error responses:

- **Device not registered**: HTTP 400 with "Device not registered" message
- **Invalid pre-auth token**: HTTP 401 with "Invalid pre-auth token" message
- **Token already used**: HTTP 401 with "Pre-auth token already used" message
- **Token expired**: HTTP 401 with "Pre-auth token expired" message
- **Device mismatch**: HTTP 401 with "Device mismatch for pre-auth token" message
- **User ID mismatch**: HTTP 401 with "User ID mismatch with pre-auth token" message

## Configuration

No additional configuration is required. The feature uses these default values:

- Pre-auth token TTL: 600 seconds (10 minutes)
- Pre-auth token length: 32 bytes (64 hex characters)

## Testing

Comprehensive unit tests are provided in `DeviceBindingServiceTest.java`:

```bash
mvn test -Dtest=DeviceBindingServiceTest
```

Test coverage includes:
- Device registration
- Pre-auth token creation and validation
- Token expiration
- Device binding validation
- Error scenarios (unregistered devices, token reuse, etc.)

## Implementation Notes

### Thread Safety
- All device and token storage uses concurrent collections
- Operations are atomic where required

### Backward Compatibility
- All new parameters are optional
- Existing flows continue to work without modification
- Device binding is opt-in

### Performance
- In-memory storage for device information and tokens
- No database queries required
- Automatic cleanup prevents memory leaks

## Future Enhancements

Potential improvements for future versions:

1. **Persistent Storage**: Move device registration to a database
2. **Token Refresh**: Allow pre-auth token renewal
3. **Device Trust Levels**: Different security levels for different device types
4. **Biometric Binding**: Link tokens to biometric authentication
5. **Device Deregistration**: API for removing registered devices
6. **Admin Interface**: Management UI for device and token administration

## References

- [gematik Specification](https://www.gematik.de/)
- [OpenID Connect](https://openid.net/connect/)
- [OAuth 2.0 Pushed Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9126)
