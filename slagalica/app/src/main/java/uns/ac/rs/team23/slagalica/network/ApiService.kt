package uns.ac.rs.team23.slagalica.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
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

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: Map<String, String>): Response<Map<String, String>>

    @POST("api/auth/guest")
    suspend fun loginAsGuest(): Response<UserResponse>

    // ── Games ─────────────────────────────────────────────────────────────────

    @GET("api/games/korak-po-korak/question")
    suspend fun getKorakPoKorakQuestion(): Response<KorakPoKorakQuestionDto>

    @GET("api/games/moj-broj/generate")
    suspend fun generateMojBroj(): Response<MojBrojPuzzleDto>

    // ── Matches ───────────────────────────────────────────────────────────────

    @POST("api/matches/start")
    suspend fun startMatch(@Body body: StartMatchRequest): Response<MatchResponseDto>

    @GET("api/matches/current")
    suspend fun getCurrentMatch(): Response<MatchResponseDto>

    @GET("api/matches/{matchId}")
    suspend fun getMatch(@Path("matchId") matchId: Long): Response<MatchResponseDto>

    @POST("api/matches/{matchId}/submit")
    suspend fun submitGameScore(
        @Path("matchId") matchId: Long,
        @Body body: GameResultRequest,
    ): Response<MatchResponseDto>

    @POST("api/matches/{matchId}/abandon")
    suspend fun abandonMatch(@Path("matchId") matchId: Long): Response<MatchResponseDto>

    @DELETE("api/matches/queue")
    suspend fun cancelQueue(): Response<Map<String, String>>

    @GET("api/matches/invites/pending")
    suspend fun getPendingInvites(): Response<List<MatchInviteResponseDto>>

    @POST("api/matches/invites/{inviteId}/respond")
    suspend fun respondToInvite(
        @Path("inviteId") inviteId: Long,
        @Query("accept") accept: Boolean,
    ): Response<MatchResponseDto>

    @DELETE("api/matches/invites/{inviteId}")
    suspend fun cancelInvite(@Path("inviteId") inviteId: Long): Response<Map<String, String>>

    // ── Chat ──────────────────────────────────────────────────────────────────

    @GET("api/chat/{region}/messages")
    suspend fun getChatHistory(
        @Path("region") region: String,
        @Query("limit") limit: Int = 50,
    ): Response<List<ChatMessageDto>>

    @POST("api/chat/{region}/messages")
    suspend fun sendChatMessage(
        @Path("region") region: String,
        @Body body: SendMessageRequest,
    ): Response<ChatMessageDto>

    // ── Challenges ────────────────────────────────────────────────────────────

    @GET("api/challenges/region/{region}")
    suspend fun getChallenges(@Path("region") region: String): Response<List<ChallengeResponseDto>>

    @POST("api/challenges")
    suspend fun createChallenge(@Body body: CreateChallengeRequest): Response<ChallengeResponseDto>

    @POST("api/challenges/{challengeId}/join")
    suspend fun joinChallenge(@Path("challengeId") challengeId: Long): Response<ChallengeResponseDto>

    @POST("api/challenges/{challengeId}/submit")
    suspend fun submitChallengeScore(
        @Path("challengeId") challengeId: Long,
        @Body body: SubmitChallengeScoreRequest,
    ): Response<ChallengeResponseDto>

    @GET("api/challenges/{challengeId}")
    suspend fun getChallenge(@Path("challengeId") challengeId: Long): Response<ChallengeResponseDto>
}
