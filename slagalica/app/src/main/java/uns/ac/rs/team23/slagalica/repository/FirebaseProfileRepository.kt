package uns.ac.rs.team23.slagalica.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

interface ProfileRepository {
    suspend fun uploadProfilePicture(imageUri: Uri): Result<String>
    suspend fun clearProfilePicture(): Result<Unit>
}

class FirebaseProfileRepository(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : ProfileRepository {

    override suspend fun uploadProfilePicture(imageUri: Uri): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        val jpeg = withContext(Dispatchers.IO) { compressToJpeg(imageUri) }

        val url = uploadToStorage(uid, jpeg) ?: jpegToDataUrl(jpeg)
        firestore.collection("users").document(uid)
            .update("profilePictureUrl", url)
            .await()
        url
    }

    override suspend fun clearProfilePicture(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        runCatching { storage.reference.child("profilePictures/$uid.jpg").delete().await() }
        firestore.collection("users").document(uid)
            .update("profilePictureUrl", "")
            .await()
    }

    /** Returns a download URL, or null when Storage is unavailable / rules not deployed. */
    private suspend fun uploadToStorage(uid: String, jpeg: ByteArray): String? = try {
        val ref = storage.reference.child("profilePictures/$uid.jpg")
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        ref.putBytes(jpeg, metadata).await()
        ref.downloadUrl.await().toString()
    } catch (_: Exception) {
        null
    }

    private fun jpegToDataUrl(jpeg: ByteArray): String {
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun compressToJpeg(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not read image")
        input.use { stream ->
            val decoded = BitmapFactory.decodeStream(stream)
                ?: throw Exception("Unsupported image format")
            val side = minOf(decoded.width, decoded.height)
            val x = (decoded.width - side) / 2
            val y = (decoded.height - side) / 2
            val cropped = Bitmap.createBitmap(decoded, x, y, side, side)
            val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
            if (scaled !== cropped) cropped.recycle()
            if (decoded !== cropped) decoded.recycle()
            ByteArrayOutputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                    throw Exception("Failed to compress image")
                }
                scaled.recycle()
                return out.toByteArray()
            }
        }
    }
}
