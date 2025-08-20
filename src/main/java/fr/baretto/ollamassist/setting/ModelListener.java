package fr.baretto.ollamassist.setting;

import com.intellij.util.messages.Topic;

public interface ModelListener {

    Topic<ModelListener> TOPIC =
            Topic.create("Model reload", ModelListener.class);

    void reloadModel();
}
