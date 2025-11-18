package fr.baretto.ollamassist.notification.service;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import fr.baretto.ollamassist.notification.core.NotificationManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Application-level startup activity that displays notifications once per IDE session.
 * Uses a static flag to ensure notifications are shown only once, even when multiple projects are opened.
 */
@Slf4j
public class NotificationApplicationStartup implements ProjectActivity {

    private static final AtomicBoolean notificationsDisplayed = new AtomicBoolean(false);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // Only display notifications once per IDE session, using the first opened project
        if (notificationsDisplayed.compareAndSet(false, true)) {
            log.info("Displaying application-level notifications for the first time");
            NotificationManager notificationManager = ApplicationManager.getApplication()
                    .getService(NotificationManager.class);

            if (notificationManager != null) {
                // Use any open project (or null if none available)
                Project displayProject = ProjectUtil.getOpenProjects().length > 0
                        ? ProjectUtil.getOpenProjects()[0]
                        : project;

                notificationManager.displayPendingNotifications(displayProject);
            } else {
                log.error("NotificationManager service not available");
            }
        } else {
            log.debug("Notifications already displayed in this IDE session, skipping");
        }

        return null;
    }
}
