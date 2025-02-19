package fr.baretto.ollamassist.chat.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.AnimatedIcon;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ImageUtil {

    public static final Icon USER_ICON = load("/icons/user.svg");
    public static final Icon OLLAMASSIST_ICON = load("/icons/icon.svg");
    public static final Icon OLLAMASSIST_THINKING_ICON = new AnimatedIcon(100,
        AllIcons.Process.Step_1,
        AllIcons.Process.Step_2,
        AllIcons.Process.Step_3,
        AllIcons.Process.Step_4,
        AllIcons.Process.Step_5,
        AllIcons.Process.Step_6,
        AllIcons.Process.Step_7,
        AllIcons.Process.Step_8);
    public static final Icon SUBMIT = load("/icons/submit.svg");
    public static final Icon SUBMIT_PRESSED = load("/icons/submit_pressed.svg");
    public static final Icon NEW_CONVERSATION = load("/icons/new_conversation.svg");


    public static Icon load(String path) {
        return IconLoader.getIcon(path, ImageUtil.class.getClassLoader());
    }
}
