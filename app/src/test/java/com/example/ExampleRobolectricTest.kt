package com.example

import com.example.data.api.SecureEndpointManager
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

  @Test
  fun testSecureEndpointReconstruction() {
    val methodUrl = SecureEndpointManager.javaClass.getDeclaredMethod("reconstructUrl")
    methodUrl.isAccessible = true
    val url = methodUrl.invoke(SecureEndpointManager) as String
    assertNotNull(url)

    val methodSec = SecureEndpointManager.javaClass.getDeclaredMethod("reconstructSecret")
    methodSec.isAccessible = true
    val sec = methodSec.invoke(SecureEndpointManager) as String
    assertNotNull(sec)
  }
}



