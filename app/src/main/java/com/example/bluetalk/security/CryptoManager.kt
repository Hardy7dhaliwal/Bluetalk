
import android.annotation.SuppressLint
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@SuppressLint("GetInstance")
class CryptoManager {

    private  var publicKey:PublicKey?=null
    private  var privateKey:PrivateKey?=null
    private  var sharedSecret: ByteArray?=null
    private  var cipherAES: Cipher?=null
    private  var aesKeySpec: SecretKeySpec?=null
    private  var iv:ByteArray?=null

    init {
        generateECDHKeyPair()

        cipherAES = Cipher.getInstance("AES/ECB/PKCS5Padding")
        iv = ByteArray(cipherAES!!.blockSize).also {
            SecureRandom().nextBytes(it)
        }
    }

    private fun generateECDHKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance("ECDH","SC")
        keyPairGenerator.initialize(ECGenParameterSpec("brainpoolp256r1")) // Using a common curve
        val keyPair = keyPairGenerator.generateKeyPair()
        publicKey = keyPair.public
        privateKey = keyPair.private
    }

    fun getPublicKey():ByteArray{
         return publicKey!!.encoded
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
        aesKeySpec = deriveAESKeyFromSharedSecret()
    }

    private fun deriveAESKeyFromSharedSecret(): SecretKeySpec {
        // Using a hash function like SHA-256 to derive an AES key from the shared secret
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val hashedSecret = sharedSecret?.let { messageDigest.digest(it) }
        // Using the first 16 bytes (128 bits) for the AES key : AES-128 encryption
        return SecretKeySpec(hashedSecret, 0, 16, "AES")
    }

    fun encryptDataWithAES(plainText: String): String {
        val ivSpec = IvParameterSpec(iv)

        cipherAES?.init(Cipher.ENCRYPT_MODE, aesKeySpec)

        // Perform encryption
        val cipherTextBytes = cipherAES?.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Convert the byte array into a Base64 encoded string
        return Base64.getEncoder().encodeToString(cipherTextBytes)
    }

    fun decryptDataWithAES(cipherText: String): String {

        cipherAES?.init(Cipher.DECRYPT_MODE, aesKeySpec)

        // Decode the Base64 encoded string to a byte array
        val cipherTextBytes = Base64.getDecoder().decode(cipherText)

        // Perform decryption
        val decryptedBytes = cipherAES?.doFinal(cipherTextBytes)

        // Convert the decrypted byte array into a string
        return String(decryptedBytes!!, Charsets.UTF_8)
    }

    fun isInittialized():Boolean{
        return sharedSecret!=null
    }
}