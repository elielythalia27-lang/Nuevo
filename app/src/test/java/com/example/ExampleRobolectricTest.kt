package com.example

import com.example.data.api.SecureEndpointManager
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun testSecureEndpointReconstruction() {
    val methodUrl = SecureEndpointManager.javaClass.getDeclaredMethod("reconstructUrl")
    methodUrl.isAccessible = true
    val url = methodUrl.invoke(SecureEndpointManager) as String
    println("RECONSTRUCTED_URL: $url")

    val methodSec = SecureEndpointManager.javaClass.getDeclaredMethod("reconstructSecret")
    methodSec.isAccessible = true
    val sec = methodSec.invoke(SecureEndpointManager) as String
    println("RECONSTRUCTED_SECRET: $sec")
  }
}


