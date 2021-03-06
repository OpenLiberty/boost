/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
import org.gradle.testkit.runner.GradleRunner
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

public class BoostPackageJPA21Test extends AbstractBoostTest {

    private static String URL = "http://localhost:9080/"

    private static final String JPA_21_FEATURE = "<feature>jpa-2.1</feature>"

    @BeforeClass
    public static void setup() {
        resourceDir = new File("build/resources/test/test-jpa")
        testProjectDir = new File(integTestDir, "BoostPackageJPA21Test")
        buildFilename = "testJPA21.gradle"
        createDir(testProjectDir)
        createTestProject(testProjectDir, resourceDir, buildFilename)

        result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .forwardOutput()
            .withArguments("boostPackage", "boostStart", "-i", "-s")
            .build()
    }

    @AfterClass
    public static void teardown() {
        result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .forwardOutput()
            .withArguments("boostStop", "-i", "-s")
            .build()
       
        assertEquals(SUCCESS, result.task(":boostStop").getOutcome())
    }

    @Test
    public void testPackageSuccess() throws IOException {
        assertEquals(SUCCESS, result.task(":installLiberty").getOutcome())
        assertEquals(SUCCESS, result.task(":libertyCreate").getOutcome())
        assertEquals(SUCCESS, result.task(":boostPackage").getOutcome())
        assertEquals(SUCCESS, result.task(":boostStart").getOutcome())

        //Add back in with packaging option
        //assertTrue(new File(testProjectDir, "testWar.jar").exists())
    }

    @Test
    public void testPackageContents() throws IOException {
        testFeatureInServerXML(JPA_21_FEATURE);
    }

    @Test
    public void testServletResponse() throws Exception {
        testServlet(URL, "Hello JPA World")
        testServlet(URL, "Created Thing 2")
        testServlet(URL, "Created Thing 3")
    }
}
