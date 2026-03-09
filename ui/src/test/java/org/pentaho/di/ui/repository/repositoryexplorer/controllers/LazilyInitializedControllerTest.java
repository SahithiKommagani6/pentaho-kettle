/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.di.ui.repository.repositoryexplorer.controllers;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.KettleAuthenticationException;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.ui.repository.exception.RepositoryExceptionUtils;
import org.pentaho.di.ui.repository.repositoryexplorer.ControllerInitializationException;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorer;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.dom.Document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test cases for LazilyInitializedController.
 * Tests cover initialization, session expiry handling, error handling, and threading scenarios.
 */
public class LazilyInitializedControllerTest {

  private static final Class<?> PKG = RepositoryExplorer.class; // for i18n purposes

  private LazilyInitializedController controller;
  private XulDomContainer mockXulDomContainer;
  private Document mockDocument;
  private MainController mockMainController;
  private XulMessageBox mockMessageBox;

  @BeforeClass
  public static void setUpClass() {
    // Initialize log store to avoid "Central Log Store is not initialized" errors
    org.pentaho.di.core.logging.KettleLogStore.init();
  }

  @Before
  public void setUp() throws Exception {
    // Create a concrete implementation of the abstract class for testing
    controller = spy( new ConcreteTestController() );

    // Setup mocks
    Repository mockRepository = mock( Repository.class );
    mockXulDomContainer = mock( XulDomContainer.class );
    mockDocument = mock( Document.class );
    mockMainController = mock( MainController.class );
    mockMessageBox = mock( XulMessageBox.class );

    // Configure mocks
    when( mockXulDomContainer.getDocumentRoot() ).thenReturn( mockDocument );
    when( mockDocument.createElement( "messagebox" ) ).thenReturn( mockMessageBox );
    when( mockXulDomContainer.getEventHandler( "mainController" ) ).thenReturn( mockMainController );

    // Set up the controller
    controller.setXulDomContainer( mockXulDomContainer );
    controller.init( mockRepository );
  }

  @After
  public void tearDown() {
    controller = null;
  }

  /**
   * Test that lazyInit() performs lazy initialization only once.
   * Given controller is not initialized,
   * When lazyInit() is called multiple times,
   * Then doLazyInit() should be called only once and initialized flag should be set.
   */
  @Test
  public void testLazyInitializationOccursOnlyOnce() {
    // Setup doLazyInit to return true
    doReturn( true ).when( controller ).doLazyInit();

    // First call
    controller.lazyInit();
    // Second call
    controller.lazyInit();

    // Verify doLazyInit was called only once
    verify( controller, times( 1 ) ).doLazyInit();
    assertTrue( controller.initialized );
  }

  /**
   * Test successful lazy initialization.
   * Given doLazyInit() returns true,
   * When lazyInit() is called,
   * Then initialized flag should be set to true and no error dialog should show.
   */
  @Test
  public void testSuccessfulLazyInitialization() {
    doReturn( true ).when( controller ).doLazyInit();

    controller.lazyInit();

    assertTrue( controller.initialized );
    verify( mockMessageBox, never() ).open();
  }

  /**
   * Test failed lazy initialization without exception.
   * Given doLazyInit() returns false,
   * When lazyInit() is called,
   * Then initialized flag should remain false and error dialog should show with default message.
   */
  @Test
  public void testFailedLazyInitializationWithoutException() {
    doReturn( false ).when( controller ).doLazyInit();

    controller.lazyInit();

    assertFalse( controller.initialized );
    verify( mockMessageBox ).setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
    verify( mockMessageBox ).setMessage( BaseMessages.getString(
      PKG, "LazilyInitializedController.Message.UnableToInit" ) );
    verify( mockMessageBox ).open();
  }

  /**
   * Test lazy initialization with generic exception.
   * Given doLazyInit() throws a generic exception,
   * When lazyInit() is called,
   * Then initialized flag should remain false, error dialog should show with exception message,
   * and session expiry handler should NOT be invoked.
   */
  @Test
  public void testLazyInitializationWithGenericException() {
    String exceptionMessage = "Database connection failed";
    Exception testException = new RuntimeException( exceptionMessage );

    doThrow( testException ).when( controller ).doLazyInit();

    controller.lazyInit();

    assertFalse( controller.initialized );
    verify( mockMessageBox ).setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
    verify( mockMessageBox ).setMessage( BaseMessages.getString(
      PKG, "LazilyInitializedController.Message.UnableToInitWithParam", exceptionMessage ) );
    verify( mockMessageBox ).open();
    verify( mockMainController, never() ).handleSessionExpiry( any( Exception.class ) );
  }

  /**
   * Test lazy initialization with session expiry exception.
   * Given doLazyInit() throws a KettleAuthenticationException (session expiry),
   * When lazyInit() is called,
   * Then initialized flag should remain false and handleSessionExpiry should be invoked on MainController.
   */
  @Test
  public void testLazyInitializationWithSessionExpiryException() {
    KettleAuthenticationException sessionExpiredException = new KettleAuthenticationException(
      "Session expired" );

    doThrow( sessionExpiredException ).when( controller ).doLazyInit();

    controller.lazyInit();

    assertFalse( controller.initialized );
    verify( mockMainController ).handleSessionExpiry( sessionExpiredException );
    verify( mockMessageBox, never() ).open();
  }

  /**
   * Test session expiry handling delegated to MainController.
   * Given MainController is available,
   * When handleSessionExpiry() is called,
   * Then the call should be delegated to MainController.
   */
  @Test
  public void testHandleSessionExpiryDelegatedToMainController() {
    Exception sessionExpiredException = new KettleAuthenticationException( "Session expired" );

    controller.handleSessionExpiry( sessionExpiredException );

    verify( mockMainController ).handleSessionExpiry( sessionExpiredException );
  }

  /**
   * Test session expiry handling when MainController is not available.
   * Given MainController is not found in XulDomContainer,
   * When handleSessionExpiry() is called,
   * Then error should be logged but execution should continue.
   */
  @Test
  public void testHandleSessionExpiryWhenMainControllerUnavailable() throws XulException {
    when( mockXulDomContainer.getEventHandler( "mainController" ) ).thenReturn( null );
    Exception sessionExpiredException = new KettleAuthenticationException( "Session expired" );

    // This should not throw an exception
    controller.handleSessionExpiry( sessionExpiredException );

    // Verify no error is propagated
    verify( mockMessageBox, never() ).open();
  }

  /**
   * Test session expiry handling when MainController throws exception.
   * Given MainController throws an exception when handling session expiry,
   * When handleSessionExpiry() is called,
   * Then error should be logged and handled gracefully.
   */
  @Test
  public void testHandleSessionExpiryWithMainControllerException() throws XulException {
    Exception sessionExpiredException = new KettleAuthenticationException( "Session expired" );
    when( mockXulDomContainer.getEventHandler( "mainController" ) ).thenThrow(
      new RuntimeException( "MainController error" ) );

    // This should not throw an exception
    controller.handleSessionExpiry( sessionExpiredException );

    // Verify execution continues gracefully
    verify( mockMessageBox, never() ).open();
  }

  /**
   * Test error dialog message creation with exception.
   * Given an exception is passed to showErrorDialog,
   * When dialog is created,
   * Then message should include exception details.
   */
  @Test
  public void testErrorDialogIncludesExceptionMessage() {
    String exceptionMessage = "Connection timeout";
    Exception testException = new RuntimeException( exceptionMessage );

    doReturn( false ).when( controller ).doLazyInit();
    doThrow( testException ).when( controller ).doLazyInit();

    controller.lazyInit();

    verify( mockMessageBox ).setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
    verify( mockMessageBox ).setMessage( BaseMessages.getString(
      PKG, "LazilyInitializedController.Message.UnableToInitWithParam", exceptionMessage ) );
  }

  /**
   * Test repository is properly initialized.
   * Given a repository is passed to init(),
   * When init() is called,
   * Then repository field should be set.
   */
  @Test
  public void testRepositoryInitialization() throws ControllerInitializationException {
    Repository testRepository = mock( Repository.class );
    controller.init( testRepository );

    assertEquals( testRepository, controller.repository );
  }

  /**
   * Test initialized flag starts as false.
   * Given a new controller instance,
   * When checked immediately,
   * Then initialized flag should be false.
   */
  @Test
  public void testInitializedFlagStartsAsFalse() {
    LazilyInitializedController newController = spy( new ConcreteTestController() );

    assertFalse( newController.initialized );
  }

  /**
   * Test thread safety: Multiple concurrent lazyInit calls.
   * Given multiple threads calling lazyInit simultaneously,
   * When lazyInit() is called,
   * Then doLazyInit() should be called only once due to synchronization.
   */
  @Test
  public void testThreadSafetyOfLazyInitialization() throws InterruptedException {
    doReturn( true ).when( controller ).doLazyInit();

    Thread thread1 = new Thread( () -> controller.lazyInit() );
    Thread thread2 = new Thread( () -> controller.lazyInit() );
    Thread thread3 = new Thread( () -> controller.lazyInit() );

    thread1.start();
    thread2.start();
    thread3.start();

    thread1.join();
    thread2.join();
    thread3.join();

    // Even with concurrent calls, doLazyInit should be called only once
    verify( controller, times( 1 ) ).doLazyInit();
    assertTrue( controller.initialized );
  }

  /**
   * Test XulException handling in error dialog creation.
   * Given XulException is thrown during messagebox creation,
   * When lazyInit() is called,
   * Then RuntimeException should be thrown.
   */
  @Test( expected = RuntimeException.class )
  public void testXulExceptionInErrorDialogCreation() throws XulException {
    doReturn( false ).when( controller ).doLazyInit();
    when( mockDocument.createElement( "messagebox" ) ).thenThrow( new XulException( "XUL error" ) );

    controller.lazyInit();
  }

  /**
   * Test that RepositoryExceptionUtils.isSessionExpired is used for detection.
   * Given various exceptions,
   * When isSessionExpired is called,
   * Then it should correctly identify session expiry exceptions.
   */
  @Test
  public void testSessionExpiryDetectionUsingRepositoryExceptionUtils() {
    // Test with KettleAuthenticationException
    KettleAuthenticationException authException = new KettleAuthenticationException( "Auth failed" );
    assertTrue( RepositoryExceptionUtils.isSessionExpired( authException ) );

    // Test with generic exception
    RuntimeException genericException = new RuntimeException( "Generic error" );
    assertFalse( RepositoryExceptionUtils.isSessionExpired( genericException ) );

    // Test with null
    assertFalse( RepositoryExceptionUtils.isSessionExpired( null ) );
  }

  /**
   * Concrete test implementation of LazilyInitializedController for testing purposes.
   */
  public static class ConcreteTestController extends LazilyInitializedController {
    @Override
    protected boolean doLazyInit() {
      return true;
    }
  }
}

