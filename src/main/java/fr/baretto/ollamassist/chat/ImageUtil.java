package fr.baretto.ollamassist.chat;

import com.intellij.openapi.util.IconLoader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ImageUtil {

    public static final Icon USER_ICON = load("/icons/user.svg");
    public static final Icon OLLAMASSIST_ICON = load("/icons/icon.svg");
    public static final Icon SUBMIT = load("/icons/submit.svg");
    public static final Icon SUBMIT_PRESSED = load("/icons/submit_pressed.svg");


    public static Icon load(String path) {
        return IconLoader.getIcon(path, ImageUtil.class);
    }
}
