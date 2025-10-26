/*
 * Copyright (Change Date see Readme), gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.idp.gsi.server.services;

import static de.gematik.idp.data.Oauth2ErrorCode.INVALID_REQUEST;

import de.gematik.idp.crypto.Nonce;
import de.gematik.idp.gsi.server.data.DeviceInfo;
import de.gematik.idp.gsi.server.data.PreAuthToken;
import de.gematik.idp.gsi.server.exceptions.GsiException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeviceBindingService {

  private static final int PRE_AUTH_TOKEN_LENGTH = 32;
  private static final long PRE_AUTH_TOKEN_TTL_SECONDS = 600; // 10 minutes

  private final Map<String, DeviceInfo> devices = new ConcurrentHashMap<>();
  private final Map<String, PreAuthToken> preAuthTokens = new ConcurrentHashMap<>();

  /**
   * Register a device for device binding
   *
   * @param deviceId unique device identifier
   * @param deviceType type of device (e.g., "android", "ios", "web")
   * @param deviceName optional device name
   * @return registered DeviceInfo
   */
  public DeviceInfo registerDevice(
      final String deviceId, final String deviceType, final String deviceName) {
    final DeviceInfo deviceInfo =
        DeviceInfo.builder()
            .deviceId(deviceId)
            .deviceType(deviceType)
            .deviceName(deviceName)
            .registeredAt(ZonedDateTime.now())
            .build();

    devices.put(deviceId, deviceInfo);
    log.info("Device registered: deviceId={}, deviceType={}", deviceId, deviceType);

    return deviceInfo;
  }

  /**
   * Create a pre-authentication token for a user and device
   *
   * @param userId user identifier (e.g., KVNR)
   * @param deviceId device identifier
   * @return pre-authentication token string
   */
  public String createPreAuthToken(final String userId, final String deviceId) {
    // Verify device is registered
    if (!devices.containsKey(deviceId)) {
      throw new GsiException(
          INVALID_REQUEST, "Device not registered: " + deviceId, HttpStatus.BAD_REQUEST);
    }

    final String tokenString = Nonce.getNonceAsHex(PRE_AUTH_TOKEN_LENGTH);
    final PreAuthToken token =
        PreAuthToken.builder()
            .token(tokenString)
            .userId(userId)
            .deviceId(deviceId)
            .expiresAt(ZonedDateTime.now().plusSeconds(PRE_AUTH_TOKEN_TTL_SECONDS))
            .used(false)
            .build();

    preAuthTokens.put(tokenString, token);
    log.info("Pre-auth token created: userId={}, deviceId={}", userId, deviceId);

    return tokenString;
  }

  /**
   * Validate and consume a pre-authentication token
   *
   * @param tokenString pre-authentication token
   * @param deviceId device identifier that should match the token
   * @return userId associated with the token
   * @throws GsiException if token is invalid, expired, already used, or device doesn't match
   */
  public String validateAndConsumePreAuthToken(final String tokenString, final String deviceId) {
    final PreAuthToken token = preAuthTokens.get(tokenString);

    if (token == null) {
      throw new GsiException(INVALID_REQUEST, "Invalid pre-auth token", HttpStatus.UNAUTHORIZED);
    }

    if (token.isUsed()) {
      throw new GsiException(
          INVALID_REQUEST, "Pre-auth token already used", HttpStatus.UNAUTHORIZED);
    }

    if (ZonedDateTime.now().isAfter(token.getExpiresAt())) {
      preAuthTokens.remove(tokenString);
      throw new GsiException(INVALID_REQUEST, "Pre-auth token expired", HttpStatus.UNAUTHORIZED);
    }

    if (!token.getDeviceId().equals(deviceId)) {
      throw new GsiException(
          INVALID_REQUEST, "Device mismatch for pre-auth token", HttpStatus.UNAUTHORIZED);
    }

    // Mark token as used
    final PreAuthToken usedToken =
        PreAuthToken.builder()
            .token(token.getToken())
            .userId(token.getUserId())
            .deviceId(token.getDeviceId())
            .expiresAt(token.getExpiresAt())
            .used(true)
            .build();
    preAuthTokens.put(tokenString, usedToken);

    log.info(
        "Pre-auth token validated and consumed: userId={}, deviceId={}",
        token.getUserId(),
        deviceId);

    return token.getUserId();
  }

  /**
   * Validate that a device is bound to a session
   *
   * @param deviceId device identifier
   * @throws GsiException if device is not registered
   */
  public void validateDeviceBinding(final String deviceId) {
    if (!devices.containsKey(deviceId)) {
      throw new GsiException(
          INVALID_REQUEST, "Device not registered: " + deviceId, HttpStatus.UNAUTHORIZED);
    }
  }

  /**
   * Get device information
   *
   * @param deviceId device identifier
   * @return DeviceInfo or null if not found
   */
  public DeviceInfo getDeviceInfo(final String deviceId) {
    return devices.get(deviceId);
  }

  /**
   * Get the TTL for pre-auth tokens
   *
   * @return TTL in seconds
   */
  public long getPreAuthTokenTtl() {
    return PRE_AUTH_TOKEN_TTL_SECONDS;
  }

  /** Clean up expired pre-auth tokens (should be called periodically) */
  public void cleanupExpiredTokens() {
    final ZonedDateTime now = ZonedDateTime.now();
    preAuthTokens.entrySet().removeIf(entry -> now.isAfter(entry.getValue().getExpiresAt()));
  }

  /** Get unmodifiable view of registered devices (for testing) */
  public Map<String, DeviceInfo> getDevices() {
    return Collections.unmodifiableMap(devices);
  }

  /** Get unmodifiable view of pre-auth tokens (for testing) */
  public Map<String, PreAuthToken> getPreAuthTokens() {
    return Collections.unmodifiableMap(preAuthTokens);
  }
}
