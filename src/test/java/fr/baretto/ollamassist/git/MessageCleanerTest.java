package fr.baretto.ollamassist.git;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class MessageCleanerTest {


    @Test
    void should_remove_think_balise(){
        String message = "<think> some induction </think> the response.";

        Assertions.assertEquals("the response.", MessageCleaner.clean(message));
    }

}