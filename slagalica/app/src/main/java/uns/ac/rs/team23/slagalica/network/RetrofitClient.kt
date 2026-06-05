package uns.ac.rs.team23.slagalica.network

import android.content.Context
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import uns.ac.rs.team23.slagalica.BuildConfig
import java.util.concurrent.TimeUnit

class RetrofitClient(context: Context) {

    val cookieJar = PersistentCookieJar(context)

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

/** Parses {"error":"..."} from an OkHttp error body. */
fun parseErrorMessage(errorJson: String?): String {
    if (errorJson.isNullOrBlank()) return "Unknown error"
    return try {
        @Suppress("UNCHECKED_CAST")
        (Gson().fromJson(errorJson, Map::class.java) as Map<String, Any>)["error"] as? String
            ?: "Server error"
    } catch (_: Exception) {
        "Server error"
    }
}
