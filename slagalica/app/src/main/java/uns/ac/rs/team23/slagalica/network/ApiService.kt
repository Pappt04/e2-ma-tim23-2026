package uns.ac.rs.team23.slagalica.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import uns.ac.rs.team23.slagalica.network.dto.*

interface ApiService {

    // ── Auth ─────────────────────────────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<Map<String, String>>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<UserResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Map<String, String>>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Map<String, String>>

    @GET("api/auth/me")
    suspend fun getMe(): Response<UserResponse>

    // ── Games ─────────────────────────────────────────────────────────────────

    @GET("api/games/korak-po-korak/question")
    suspend fun getKorakPoKorakQuestion(): Response<KorakPoKorakQuestionDto>

    @GET("api/games/moj-broj/generate")
    suspend fun generateMojBroj(): Response<MojBrojPuzzleDto>
}
