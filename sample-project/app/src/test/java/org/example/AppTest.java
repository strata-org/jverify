package org.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test void maxReturnsTheLargerArgument() {
        assertEquals(7, App.max(3, 7));
        assertEquals(7, App.max(7, 3));
    }
}
