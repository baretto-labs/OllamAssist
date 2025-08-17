package fr.baretto.ollamassist.chat.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.AnimatedIcon;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IconUtils {

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
    public static final Icon NEW_CONVERSATION = load("/icons/new_conversation.svg");
    public static final Icon ADD_TO_CONTEXT = AllIcons.Actions.AddFile;
    public static final Icon REMOVE_TO_CONTEXT = AllIcons.General.Remove;
    public static final Icon DELETE_CONVERSATION = AllIcons.General.Delete;
    public static final Icon INSERT = load("/icons/insert.svg");
    public static final Icon COPY = load("/icons/copy.svg");
    public static final Icon OLLAMASSIST_WARN_ICON = AllIcons.General.Warning;
    public static final Icon OLLAMASSIST_ERROR_ICON = AllIcons.General.Error;
    public static final Icon VALIDATE = load("/icons/checkmark.svg");
    public static final Icon ERROR = load("/icons/error.svg");
    public static final Icon INFORMATION = load("/icons/information.svg");
    public static final Icon RESTART = load("/icons/restart.svg");
    public static final Icon LOADING = new AnimatedIcon(100,
            AllIcons.Process.Big.Step_1,
            AllIcons.Process.Big.Step_2,
            AllIcons.Process.Big.Step_3,
            AllIcons.Process.Big.Step_4,
            AllIcons.Process.Big.Step_5,
            AllIcons.Process.Big.Step_6,
            AllIcons.Process.Big.Step_7,
            AllIcons.Process.Big.Step_8);
    public static final Icon WEB_SEARCH_DISABLED =  load("/icons/web_search_disabled.svg");
    public static final Icon WEB_SEARCH_ENABLED =  load("/icons/web_search_enabled.svg");
    public static final Icon RAG_SEARCH_DISABLED =  load("/icons/rag_search_disabled.svg");
    public static final Icon RAG_SEARCH_ENABLED =  load("/icons/rag_search_enabled.svg");
    public static final Icon STOP = AllIcons.Run.Stop;

    public static Icon load(String path) {
        return IconLoader.getIcon(path, IconUtils.class);
    }
}
