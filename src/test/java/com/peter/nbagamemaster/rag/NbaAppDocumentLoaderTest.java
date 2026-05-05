package com.peter.nbagamemaster.rag;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NbaAppDocumentLoaderTest {

    @Autowired
    private NbaAppDocumentLoader nbaAppDocumentLoader;

    @Test
    void loadMarkdowns() {
        nbaAppDocumentLoader.loadMarkdowns();
    }
}