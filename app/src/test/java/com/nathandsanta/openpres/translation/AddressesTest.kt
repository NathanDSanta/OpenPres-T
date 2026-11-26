package com.nathandsanta.openpres.translation

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressesTest {
    @Test
    fun typedAddressesGetTheDefaultPort() {
        assertEquals("http://192.168.1.20:8787", Addresses.normalize(" 192.168.1.20 "))
        assertEquals("http://studio-pc:9000", Addresses.normalize("studio-pc:9000/"))
        assertEquals("https://openpres.example:8787", Addresses.normalize("https://openpres.example"))
        assertEquals("http://[fe80::1]:8787", Addresses.normalize("[fe80::1]"))
        assertNull(Addresses.normalize(""))
        assertNull(Addresses.normalize("studio pc"))
        assertNull(Addresses.normalize("studio-pc:99999"))
    }

    @Test
    fun foundServersInBrackets() {
        assertEquals("http://192.168.1.20:8787", Addresses.of("192.168.1.20", 8787))
        assertEquals("http://[fe80::1%25wlan0]:8787", Addresses.of("fe80::1%wlan0", 8787))
    }

    @Test
    fun foundServersUseTheirIpv4Address() {
        val ip = { text: String -> InetAddress.getByName(text) }
        assertEquals("192.168.1.20", Addresses.ipv4(listOf(ip("fe80::1"), ip("192.168.1.20"), ip("10.0.0.5"))))
        assertEquals("10.0.0.5", Addresses.ipv4(listOf(ip("10.0.0.5"))))
        assertNull("only IPv6: not used", Addresses.ipv4(listOf(ip("fe80::1"), ip("2001:db8::7"))))
        assertNull(Addresses.ipv4(emptyList()))
    }
}
