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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.idp.gsi.server.data.DeviceInfo;
import de.gematik.idp.gsi.server.exceptions.GsiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceBindingServiceTest {

  private DeviceBindingService deviceBindingService;

  @BeforeEach
  void setUp() {
    deviceBindingService = new DeviceBindingService();
  }

  @Test
  void testRegisterDevice() {
    final String deviceId = "device-123";
    final String deviceType = "android";
    final String deviceName = "My Android Phone";

    final DeviceInfo result = deviceBindingService.registerDevice(deviceId, deviceType, deviceName);

    assertThat(result).isNotNull();
    assertThat(result.getDeviceId()).isEqualTo(deviceId);
    assertThat(result.getDeviceType()).isEqualTo(deviceType);
    assertThat(result.getDeviceName()).isEqualTo(deviceName);
    assertThat(result.getRegisteredAt()).isNotNull();
  }

  @Test
  void testCreatePreAuthToken() {
    final String deviceId = "device-123";
    final String userId = "X110411675";
    deviceBindingService.registerDevice(deviceId, "android", "Test Device");

    final String preAuthToken = deviceBindingService.createPreAuthToken(userId, deviceId);

    assertThat(preAuthToken).isNotNull().isNotEmpty();
  }

  @Test
  void testCreatePreAuthTokenForUnregisteredDevice() {
    final String deviceId = "unregistered-device";
    final String userId = "X110411675";

    assertThatThrownBy(() -> deviceBindingService.createPreAuthToken(userId, deviceId))
        .isInstanceOf(GsiException.class)
        .hasMessageContaining("Device not registered");
  }

  @Test
  void testValidateAndConsumePreAuthToken() {
    final String deviceId = "device-123";
    final String userId = "X110411675";
    deviceBindingService.registerDevice(deviceId, "android", "Test Device");

    final String preAuthToken = deviceBindingService.createPreAuthToken(userId, deviceId);
    final String validatedUserId =
        deviceBindingService.validateAndConsumePreAuthToken(preAuthToken, deviceId);

    assertThat(validatedUserId).isEqualTo(userId);
  }

  @Test
  void testValidateAndConsumePreAuthTokenWithWrongDevice() {
    final String deviceId = "device-123";
    final String wrongDeviceId = "device-456";
    final String userId = "X110411675";
    deviceBindingService.registerDevice(deviceId, "android", "Test Device");
    deviceBindingService.registerDevice(wrongDeviceId, "ios", "Test Device 2");

    final String preAuthToken = deviceBindingService.createPreAuthToken(userId, deviceId);

    assertThatThrownBy(
            () -> deviceBindingService.validateAndConsumePreAuthToken(preAuthToken, wrongDeviceId))
        .isInstanceOf(GsiException.class)
        .hasMessageContaining("Device mismatch");
  }

  @Test
  void testValidateAndConsumePreAuthTokenTwice() {
    final String deviceId = "device-123";
    final String userId = "X110411675";
    deviceBindingService.registerDevice(deviceId, "android", "Test Device");

    final String preAuthToken = deviceBindingService.createPreAuthToken(userId, deviceId);
    deviceBindingService.validateAndConsumePreAuthToken(preAuthToken, deviceId);

    // Try to use the token again
    assertThatThrownBy(
            () -> deviceBindingService.validateAndConsumePreAuthToken(preAuthToken, deviceId))
        .isInstanceOf(GsiException.class)
        .hasMessageContaining("already used");
  }

  @Test
  void testValidateAndConsumeInvalidToken() {
    final String deviceId = "device-123";
    final String invalidToken = "invalid-token";
    deviceBindingService.registerDevice(deviceId, "android", "Test Device");

    assertThatThrownBy(
            () -> deviceBindingService.validateAndConsumePreAuthToken(invalidToken, deviceId))
        .isInstanceOf(GsiException.class)
        .hasMessageContaining("Invalid pre-auth token");
  }

  @Test
  void testValidateDeviceBinding() {
    final String deviceId = "device-123";
    deviceBindingService.registerDevice(deviceId, "android", "Test Device");

    // Should not throw exception
    deviceBindingService.validateDeviceBinding(deviceId);
  }

  @Test
  void testValidateDeviceBindingForUnregisteredDevice() {
    final String deviceId = "unregistered-device";

    assertThatThrownBy(() -> deviceBindingService.validateDeviceBinding(deviceId))
        .isInstanceOf(GsiException.class)
        .hasMessageContaining("Device not registered");
  }

  @Test
  void testGetDeviceInfo() {
    final String deviceId = "device-123";
    final String deviceType = "android";
    deviceBindingService.registerDevice(deviceId, deviceType, "Test Device");

    final DeviceInfo deviceInfo = deviceBindingService.getDeviceInfo(deviceId);

    assertThat(deviceInfo).isNotNull();
    assertThat(deviceInfo.getDeviceId()).isEqualTo(deviceId);
    assertThat(deviceInfo.getDeviceType()).isEqualTo(deviceType);
  }

  @Test
  void testGetDeviceInfoForUnregisteredDevice() {
    final String deviceId = "unregistered-device";

    final DeviceInfo deviceInfo = deviceBindingService.getDeviceInfo(deviceId);

    assertThat(deviceInfo).isNull();
  }

  @Test
  void testGetPreAuthTokenTtl() {
    final long ttl = deviceBindingService.getPreAuthTokenTtl();
    assertThat(ttl).isEqualTo(600); // 10 minutes
  }
}
