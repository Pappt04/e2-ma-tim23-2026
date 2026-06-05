package uns.ac.rs.team23.server.dto.match

import uns.ac.rs.team23.server.model.MatchInvite

data class MatchInviteResponse(
    val id: Long,
    val inviterId: Long,
    val inviterUsername: String,
    val isFriendly: Boolean,
    val status: String,
    val expiresAt: String,
) {
    companion object {
        fun from(invite: MatchInvite) = MatchInviteResponse(
            id = invite.id,
            inviterId = invite.inviter.id,
            inviterUsername = invite.inviter.username,
            isFriendly = invite.isFriendly,
            status = invite.status.name,
            expiresAt = invite.expiresAt.toString(),
        )
    }
}
