package com.kmpfeaturekit.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object KmpFeatureKitNotifier {
    fun info(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Compose Template Generator")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }
}
