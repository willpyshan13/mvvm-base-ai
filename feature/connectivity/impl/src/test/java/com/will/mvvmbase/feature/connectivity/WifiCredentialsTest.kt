package com.will.mvvmbase.feature.connectivity

import org.junit.Assert.*
import org.junit.Test

class WifiCredentialsTest {
    @Test fun classifiesSecurityWithoutTreatingEnterpriseOrEnhancedOpenAsOpen() {
        assertEquals(WifiSecurity.Open, WifiCredentials.security("[ESS]"))
        assertEquals(WifiSecurity.Wpa2, WifiCredentials.security("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.Wpa2, WifiCredentials.security("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.Wpa3, WifiCredentials.security("[RSN-SAE-CCMP][ESS]"))
        listOf("[WEP]", "[WPA2-EAP-CCMP]", "[OWE_TRANSITION]", "[WAPI-PSK]", "[RSN-UNKNOWN]").forEach {
            assertEquals(it, WifiSecurity.Unsupported, WifiCredentials.security(it))
        }
    }

    @Test fun validatesPasswordBoundariesAndEncodingBeforePassingCredentialsToAndroid() {
        assertTrue(WifiCredentials.validPassword(WifiSecurity.Open, ""))
        assertFalse(WifiCredentials.validPassword(WifiSecurity.Wpa2, "1234567"))
        assertTrue(WifiCredentials.validPassword(WifiSecurity.Wpa2, "12345678"))
        assertTrue(WifiCredentials.validPassword(WifiSecurity.Wpa2, "a".repeat(63)))
        assertFalse(WifiCredentials.validPassword(WifiSecurity.Wpa2, "a".repeat(64)))
        assertFalse(WifiCredentials.validPassword(WifiSecurity.Wpa2, "1234567\n"))
        assertFalse(WifiCredentials.validPassword(WifiSecurity.Wpa2, "中文密码测试例子"))
        assertTrue(WifiCredentials.validPassword(WifiSecurity.Wpa3, "中".repeat(21)))
        assertFalse(WifiCredentials.validPassword(WifiSecurity.Wpa3, "中".repeat(22)))
        assertFalse(WifiCredentials.validPassword(WifiSecurity.Unsupported, "12345678"))
    }
}
