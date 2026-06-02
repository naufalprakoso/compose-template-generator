package com.kmpfeaturekit.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object KmpFeatureKitNotifier {
    fun info(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION)
    }

    fun warning(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.WARNING)
    }

    fun error(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.ERROR)
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Compose Template Generator")
            .createNotification(title, content, type)
            .notify(project)
    }
}
