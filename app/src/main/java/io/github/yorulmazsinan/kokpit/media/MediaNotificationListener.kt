package io.github.yorulmazsinan.kokpit.media

import android.service.notification.NotificationListenerService

/** Exists only so the user can grant notification access, which unlocks media sessions. */
class MediaNotificationListener : NotificationListenerService()
