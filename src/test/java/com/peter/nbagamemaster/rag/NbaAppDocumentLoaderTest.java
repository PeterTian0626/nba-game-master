package com.peter.nbagamemaster.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NbaAppDocumentLoaderTest {

    @Resource
    private NbaAppDocumentLoader nbaAppDocumentLoader;

    @Test
    void loadMarkdowns() {
        nbaAppDocumentLoader.loadMarkdowns();
    }
}