package fr.baretto.ollamassist.git;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageCleaner {


    static String clean(String message) {
        return message.replaceAll("(?s)<think>.*?</think>", "")
                .replace("`", "")
                .trim();
    }
}
