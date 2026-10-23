package com.will.mvvmbase.feature.connectivity

internal object WifiCredentials {
    fun security(capabilities: String): WifiSecurity = when {
        listOf("EAP", "WEP", "WAPI", "OWE").any { capabilities.contains(it) } -> WifiSecurity.Unsupported
        capabilities.contains("PSK") -> WifiSecurity.Wpa2
        capabilities.contains("SAE") -> WifiSecurity.Wpa3
        capabilities.contains("RSN") || capabilities.contains("WPA") -> WifiSecurity.Unsupported
        else -> WifiSecurity.Open
    }

    fun validPassword(security: WifiSecurity, password: String): Boolean = when (security) {
        WifiSecurity.Open -> true
        WifiSecurity.Wpa2 -> password.length in 8..63 && password.all { it.code in 32..126 }
        WifiSecurity.Wpa3 -> password.isNotEmpty() && password.toByteArray(Charsets.UTF_8).size <= 63
        WifiSecurity.Unsupported -> false
    }
}
