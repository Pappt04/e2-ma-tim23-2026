package uns.ac.rs.team23.slagalica.repository

import uns.ac.rs.team23.slagalica.models.Notification

interface NotificationRepository {
    suspend fun saveNotification(notification: Notification): Result<Unit>
    suspend fun getNotifications(): Result<List<Notification>>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun markAllAsRead(): Result<Unit>
}
