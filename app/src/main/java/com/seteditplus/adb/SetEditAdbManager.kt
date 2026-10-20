package com.seteditplus.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * کلاینت ADB داخلی (بر پایهٔ libadb-android). کلید RSA یک بار ساخته و در حافظهٔ خصوصی اپ ذخیره می‌شود؛
 * بعد از یک بار جفت‌سازی، adbd این کلید را برای همیشه می‌شناسد.
 */
class SetEditAdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val mKey: PrivateKey
    private val mCert: Certificate

    init {
        api = Build.VERSION.SDK_INT
        setTimeout(15, TimeUnit.SECONDS)
        val dir = context.noBackupFilesDir
        val keyFile = File(dir, "adb_key.pk8")
        val certFile = File(dir, "adb_cert.der")
        var key: PrivateKey? = null
        var cert: Certificate? = null
        if (keyFile.exists() && certFile.exists()) {
            try {
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            } catch (_: Throwable) {
                key = null; cert = null
            }
        }
        if (key == null || cert == null) {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048, SecureRandom())
            val pair = gen.generateKeyPair()
            val name = X500Name("CN=SetEdit+")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000L),
                Date(now + 30L * 365 * 86_400_000L), name, pair.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
            val x509 = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            keyFile.writeBytes(pair.private.encoded)
            certFile.writeBytes(x509.encoded)
            key = pair.private
            cert = x509
        }
        mKey = key!!
        mCert = cert!!
    }

    override fun getPrivateKey(): PrivateKey = mKey
    override fun getCertificate(): Certificate = mCert
    override fun getDeviceName(): String = "SetEdit+"

    companion object {
        @Volatile
        private var instance: SetEditAdbManager? = null

        fun get(context: Context): SetEditAdbManager =
            instance ?: synchronized(this) {
                instance ?: SetEditAdbManager(context.applicationContext).also { instance = it }
            }

        fun peek(): SetEditAdbManager? = instance
    }
}
