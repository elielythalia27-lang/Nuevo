package com.example

import com.example.data.api.SecureEndpointManager
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun endpointConfigurationIsAvailableWithoutLoggingSensitiveValues() {
    val urlMethod = SecureEndpointManager.javaClass.getDeclaredMethod("reconstructUrl").apply {
      isAccessible = true
    }
    val url = urlMethod.invoke(SecureEndpointManager) as String
    assertTrue("Endpoint must use HTTP or HTTPS", url.startsWith("http://") || url.startsWith("https://"))

    val secretMethod = SecureEndpointManager.javaClass.getDeclaredMethod("reconstructSecret").apply {
      isAccessible = true
    }
    val secret = secretMethod.invoke(SecureEndpointManager) as String
    assertTrue("Decryption material must not be empty", secret.isNotBlank())
  }
}
