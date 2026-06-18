package net.programmierecke.radiodroid2.utils

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Enables TLS v1.2 when creating SSLSockets.
 * Android supports TLS v1.2 from API 16, but enables it by default only from API 20.
 */
class Tls12SocketFactory(val delegate: SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean) = patch(delegate.createSocket(s, host, port, autoClose))

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String, port: Int) = patch(delegate.createSocket(host, port))

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) = patch(delegate.createSocket(host, port, localHost, localPort))

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int) = patch(delegate.createSocket(host, port))

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int) = patch(delegate.createSocket(address, port, localAddress, localPort))

    private fun patch(s: Socket): Socket {
        if (s is SSLSocket) s.enabledProtocols = TLS_V12_ONLY
        return s
    }

    companion object {
        private val TLS_V12_ONLY = arrayOf("TLSv1.2")
    }
}
