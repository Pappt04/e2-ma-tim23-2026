package uns.ac.rs.team23.slagalica.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uns.ac.rs.team23.slagalica.models.Notification
import uns.ac.rs.team23.slagalica.models.NotificationType
import uns.ac.rs.team23.slagalica.viewmodels.NotificationFilter
import uns.ac.rs.team23.slagalica.viewmodels.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onMatchStarted: () -> Unit = {},
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val allNotifications by viewModel.notifications.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val countdowns by viewModel.inviteCountdowns.collectAsState()

    val displayed = allNotifications.filter {
        when (filter) {
            NotificationFilter.ALL -> true
            NotificationFilter.UNREAD -> !it.isRead
            NotificationFilter.READ -> it.isRead
        }
    }
    val unreadCount = allNotifications.count { !it.isRead }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications")
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge { Text("$unreadCount") }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = viewModel::markAllAsRead) { Text("Mark all read") }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterTabRow(filter, viewModel::setFilter, allNotifications)

            if (displayed.isEmpty()) {
                EmptyNotificationsPlaceholder(filter)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayed, key = { it.id }) { notif ->
                        if (notif.type == NotificationType.INVITE && notif.inviteId != null) {
                            InviteNotificationCard(
                                notification = notif,
                                secondsLeft = countdowns[notif.id] ?: 0,
                                onAccept = {
                                    viewModel.respondToInvite(
                                        notif.inviteId,
                                        accept = true,
                                        notificationId = notif.id,
                                        onMatchStarted = onMatchStarted,
                                    )
                                },
                                onReject = {
                                    viewModel.respondToInvite(notif.inviteId, accept = false, notificationId = notif.id)
                                },
                            )
                        } else {
                            NotificationCard(
                                notification = notif,
                                onMarkRead = { viewModel.markAsRead(notif.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteNotificationCard(
    notification: Notification,
    secondsLeft: Int,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SportsEsports, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(notification.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                // Countdown badge
                Surface(
                    shape = CircleShape,
                    color = if (secondsLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$secondsLeft",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(notification.message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun FilterTabRow(
    current: NotificationFilter,
    onSelect: (NotificationFilter) -> Unit,
    all: List<Notification>,
) {
    val unread = all.count { !it.isRead }
    val read = all.count { it.isRead }

    TabRow(selectedTabIndex = current.ordinal) {
        Tab(selected = current == NotificationFilter.ALL, onClick = { onSelect(NotificationFilter.ALL) },
            text = { Text("All (${all.size})") })
        Tab(selected = current == NotificationFilter.UNREAD, onClick = { onSelect(NotificationFilter.UNREAD) },
            text = { Text("Unread ($unread)") })
        Tab(selected = current == NotificationFilter.READ, onClick = { onSelect(NotificationFilter.READ) },
            text = { Text("Read ($read)") })
    }
}

@Composable
private fun NotificationCard(notification: Notification, onMarkRead: () -> Unit) {
    val bgColor by animateColorAsState(
        if (notification.isRead) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        label = "bg",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(if (notification.isRead) 1.dp else 3.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(notification.type.cardColor().copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = notification.type.icon(), contentDescription = null,
                    tint = notification.type.cardColor(), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(notification.type.label, style = MaterialTheme.typography.labelSmall,
                        color = notification.type.cardColor(), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(notification.timestamp, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Text(notification.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(notification.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!notification.isRead) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onMarkRead, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)) {
                        Text("Mark as read", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (!notification.isRead) {
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary).align(Alignment.Top))
            }
        }
    }
}

@Composable
private fun EmptyNotificationsPlaceholder(filter: NotificationFilter) {
    Column(modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(imageVector = Icons.Default.Notifications, contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))
        Text(text = when (filter) {
            NotificationFilter.UNREAD -> "No unread notifications"
            NotificationFilter.READ -> "No read notifications"
            NotificationFilter.ALL -> "No notifications"
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotificationType.cardColor(): Color = when (this) {
    NotificationType.CHAT -> MaterialTheme.colorScheme.tertiary
    NotificationType.RANKING -> MaterialTheme.colorScheme.secondary
    NotificationType.REWARD -> MaterialTheme.colorScheme.primary
    NotificationType.INVITE -> MaterialTheme.colorScheme.primary
    NotificationType.OTHER -> MaterialTheme.colorScheme.primary
}

private fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.CHAT -> Icons.Default.MailOutline
    NotificationType.RANKING -> Icons.Default.Star
    NotificationType.REWARD -> Icons.Default.Star
    NotificationType.INVITE -> Icons.Default.SportsEsports
    NotificationType.OTHER -> Icons.Default.Notifications
}
