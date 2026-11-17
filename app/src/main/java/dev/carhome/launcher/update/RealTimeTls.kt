package dev.carhome.launcher.update

import dev.carhome.launcher.time.TimeSync
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Secure connections check that the website's certificate is valid *today*. Head units often
 * boot with a wrong date (e.g. 2024), so a perfectly good certificate looks "not valid yet"
 * and the connection fails.
 *
 * Once Car Home knows the real time (from GPS, or from internet time, which doesn't need a
 * correct clock), this checks the certificate against that real time instead. Everything else
 * about the check is unchanged: the full chain must lead to one of Android's trusted
 * certificate authorities, and the website name is still verified. If Car Home doesn't know
 * the real time yet, the normal check is used.
 */
object RealTimeTls {

    /** A socket factory that validates with the real time, or null if it isn't known yet. */
    fun socketFactory(): SSLSocketFactory? {
        if (TimeSync.state.value.source == null) return null
        return try {
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(RealTimeTrustManager(Date(TimeSync.now()))), null)
            context.socketFactory
        } catch (e: Exception) {
            null
        }
    }

    /** True if the unit's own clock is clearly wrong (before 2025). */
    fun unitClockLooksWrong() = System.currentTimeMillis() < 1_735_689_600_000L

    private class RealTimeTrustManager(private val realNow: Date) : X509TrustManager {
        private val system: X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
                .trustManagers.filterIsInstance<X509TrustManager>().first()

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                // Only a date problem gets a second look; any other failure stands.
                if (!isDateProblem(e)) throw e
                validateAt(chain, realNow)
            }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            system.checkClientTrusted(chain, authType)

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

        /** The full standard check (chain up to a trusted authority), using the real date. */
        private fun validateAt(chain: Array<X509Certificate>, date: Date) {
            try {
                val anchors = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
                val params = PKIXParameters(anchors).apply {
                    this.date = date
                    isRevocationEnabled = false // as Android's own check does
                }
                val path = CertificateFactory.getInstance("X.509").generateCertPath(chain.toList())
                CertPathValidator.getInstance("PKIX").validate(path, params)
            } catch (e: Exception) {
                throw CertificateException("Certificate not valid at the real time", e)
            }
        }

        private fun isDateProblem(e: Throwable): Boolean {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is CertificateNotYetValidException || cause is CertificateExpiredException) return true
                val msg = cause.message.orEmpty().lowercase()
                if ("not yet valid" in msg || "expired" in msg) return true
                cause = cause.cause
            }
            return false
        }
    }
}
