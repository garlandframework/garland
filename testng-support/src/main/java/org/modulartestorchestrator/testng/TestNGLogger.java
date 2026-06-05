package org.modulartestorchestrator.testng;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IClassListener;
import org.testng.ITestClass;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.Test;

public class TestNGLogger implements ITestListener, IClassListener {

    private static final Logger log = LoggerFactory.getLogger("TEST");

    private static final String RESET  = "[0m";
    private static final String BOLD   = "[1m";
    private static final String CYAN   = "[36m";
    private static final String GREEN  = "[32m";
    private static final String RED    = "[31m";
    private static final String YELLOW = "[33m";
    private static final String DIM    = "[2m";

    private boolean classFailed;

    @Override
    public void onBeforeClass(ITestClass testClass) {
        classFailed = false;
        log.info("");
        log.info("");
        log.info(BOLD + CYAN + "┌─ CLASS " + testClass.getRealClass().getSimpleName() + RESET);
        Test annotation = testClass.getRealClass().getAnnotation(Test.class);
        if (annotation != null && !annotation.description().isBlank()) {
            log.info(DIM + "   DESCRIPTION " + annotation.description() + RESET);
        }
    }

    @Override
    public void onAfterClass(ITestClass testClass) {
        String name = testClass.getRealClass().getSimpleName();
        if (classFailed) {
            log.info(BOLD + RED + "└─ CLASS ✗ " + name + RESET);
        } else {
            log.info(BOLD + GREEN + "└─ CLASS ✓ " + name + RESET);
        }
        log.info("");
    }

    @Override
    public void onTestStart(ITestResult result) {
        log.info("");
        log.info("");
        log.info(DIM + "   ▶ METHOD " + result.getMethod().getMethodName() + RESET);
        String description = result.getMethod().getDescription();
        if (description != null && !description.isBlank()) {
            log.info(DIM + "     DESCRIPTION " + description + RESET);
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        log.info(BOLD + GREEN + "   ✓ METHOD " + result.getMethod().getMethodName() + RESET);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        classFailed = true;
        Throwable t = result.getThrowable();
        log.info(BOLD + RED + "   ✗ METHOD " + result.getMethod().getMethodName() + RESET);
        if (t != null) {
            log.info(RED + "     " + t.getMessage() + RESET);
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.info(BOLD + YELLOW + "   ⊘ METHOD " + result.getMethod().getMethodName() + RESET);
    }
}
