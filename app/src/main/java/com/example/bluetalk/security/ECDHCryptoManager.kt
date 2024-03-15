
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

class ECDHCryptoManager {

    private lateinit var publicKey:PublicKey
    private lateinit var privateKey:PrivateKey
    private lateinit var sharedSecret: ByteArray

    init {
        generateECDHKeyPair()
    }

    private fun generateECDHKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance("ECDH","SC")
        keyPairGenerator.initialize(ECGenParameterSpec("brainpoolp256r1")) // Using a common curve
        val keyPair = keyPairGenerator.generateKeyPair()
        publicKey = keyPair.public
        privateKey = keyPair.private
    }

    fun getPublicKey():ByteArray{
         return publicKey.encoded
    }

    fun deriveSharedSecret(otherPublicKeyBytes: ByteArray) {
        // Convert the received public key bytes back to a PublicKey object
        val keyFactory = KeyFactory.getInstance("ECDH", "SC")
        val otherPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(otherPublicKeyBytes))
        // Perform key agreement
        val keyAgreement = KeyAgreement.getInstance("ECDH", "SC")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(otherPublicKey, true)

        // Generate the shared secret
        sharedSecret = keyAgreement.generateSecret()
    }

}