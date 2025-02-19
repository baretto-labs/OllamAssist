package fr.baretto.ollamassist.chat.ui;

import com.intellij.openapi.util.IconLoader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IconUtils {

    public static final Icon USER_ICON = load("/icons/user.svg");
    public static final Icon OLLAMASSIST_ICON = load("/icons/icon.svg");
    public static final Icon SUBMIT = load("/icons/submit.svg");
    public static final Icon SUBMIT_PRESSED = load("/icons/submit_pressed.svg");
    public static final Icon NEW_CONVERSATION = load("/icons/new_conversation.svg");
    public static final Icon INSERT = load("/icons/insert.svg");
    public static final Icon COPY = load("/icons/copy.svg");

    public static Icon load(String path) {
        return IconLoader.getIcon(path, IconUtils.class);
    }
}
