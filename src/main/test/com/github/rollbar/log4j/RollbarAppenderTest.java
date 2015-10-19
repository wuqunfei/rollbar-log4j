package com.github.rollbar.log4j;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Qunfei Wu on 19/10/15. When you configured log4j.xml, then you can push your logger.
 *
 */
public class RollbarAppenderTest {
    private Logger logger = null;

    @Before
    public void setUp() throws Exception {
        logger = Logger.getLogger(RollbarAppenderTest.class.getName());
    }

    @After
    public void tearDown() throws Exception {
        logger = null;
    }

    @Test
    public void testPushLogger() {
        logger.error("Hello Emilie");
    }
}