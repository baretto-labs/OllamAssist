package fr.baretto.ollamassist.ai.store;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.util.Map;

public class DummyProject implements Project {
    @Override
    public @NotNull @NlsSafe String getName() {
        return "DummyProject";
    }

    @Override
    public VirtualFile getBaseDir() {
        return null;
    }

    @Override
    public @Nullable @NonNls @SystemIndependent String getBasePath() {
        return getClass().getClassLoader().getResource("data").getPath();
    }

    @Override
    public @Nullable VirtualFile getProjectFile() {
        return null;
    }

    @Override
    public @Nullable @NonNls @SystemIndependent String getProjectFilePath() {
        return "";
    }

    @Override
    public @Nullable VirtualFile getWorkspaceFile() {
        return null;
    }

    @Override
    public @NotNull @NonNls String getLocationHash() {
        return "";
    }

    @Override
    public void save() {

    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return false;
    }

    @Override
    public <T> T getComponent(@NotNull Class<T> interfaceClass) {
        return null;
    }

    @Override
    public boolean hasComponent(@NotNull Class<?> interfaceClass) {
        return false;
    }

    @Override
    public boolean isInjectionForExtensionSupported() {
        return false;
    }

    @Override
    public @NotNull MessageBus getMessageBus() {
        return null;
    }

    @Override
    public boolean isDisposed() {
        return false;
    }

    @Override
    public @NotNull Condition<?> getDisposed() {
        return null;
    }

    @Override
    public <T> T getService(@NotNull Class<T> serviceClass) {
        return null;
    }

    @Override
    public @NotNull ExtensionsArea getExtensionArea() {
        return null;
    }

    @Override
    public <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull PluginId pluginId) {
        return null;
    }

    @Override
    public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass, @NotNull Object key, @NotNull PluginId pluginId) {
        return null;
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
        return null;
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId) {
        return null;
    }

    @Override
    public @NotNull RuntimeException createError(@NotNull @NonNls String message, @Nullable Throwable error, @NotNull PluginId pluginId, @Nullable Map<String, String> attachments) {
        return null;
    }

    @Override
    public @NotNull <T> Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
        return null;
    }

    @Override
    public <T> @NotNull T instantiateClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) {
        return null;
    }

    @Override
    public @NotNull ActivityCategory getActivityCategory(boolean isExtension) {
        return null;
    }

    @Override
    public void dispose() {

    }

    @Override
    public <T> @Nullable T getUserData(@NotNull Key<T> key) {
        return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {

    }
}
