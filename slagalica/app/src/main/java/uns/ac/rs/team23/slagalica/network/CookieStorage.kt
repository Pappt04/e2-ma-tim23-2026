package uns.ac.rs.team23.slagalica.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Persists session cookies across app restarts using SharedPreferences. */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = context.getSharedPreferences("slagalica_cookies", Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val editor = prefs.edit()
        cookies.forEach { cookie ->
            editor.putString("${url.host}||${cookie.name}", cookie.toString())
        }
        editor.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        prefs.all.entries
            .filter { it.key.startsWith("${url.host}||") }
            .mapNotNull { Cookie.parse(url, it.value as? String ?: return@mapNotNull null) }

    fun clear() = prefs.edit().clear().apply()
}
