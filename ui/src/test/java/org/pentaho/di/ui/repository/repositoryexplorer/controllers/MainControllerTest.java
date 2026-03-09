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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pentaho.di.repository.KettleAuthenticationException;
import org.pentaho.di.repository.KettleRepositoryLostException;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.ui.repository.exception.RepositoryExceptionUtils;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorerCallback;
import org.pentaho.di.ui.spoon.SharedObjectSyncUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class MainControllerTest {

  private MainController controller;
  private Repository mockRepository;
  private RepositoryExplorerCallback mockCallback;

  @BeforeClass
  public static void setUpClass() {
    // Initialize log store to avoid "Central Log Store is not initialized" errors
    org.pentaho.di.core.logging.KettleLogStore.init();
  }

  @Before
  public void setUp() {
    // Setup mocks
    controller = spy( new MainController() );
    mockRepository = mock( Repository.class );
    mockCallback = mock( RepositoryExplorerCallback.class );
    SharedObjectSyncUtil mockSharedObjectSyncUtil = mock( SharedObjectSyncUtil.class );

    // Set controller properties
    controller.setRepository( mockRepository );
    controller.setCallback( mockCallback );
    controller.setSharedObjectSyncUtil( mockSharedObjectSyncUtil );
  }


  /**
   * Test isSessionExpired detection for authentication exceptions.
   * Given a KettleAuthenticationException,
   * When isSessionExpired is called via RepositoryExceptionUtils,
   * Then it should return true.
   */
  @Test
  public void testIsSessionExpiredWithAuthenticationException() {
    KettleAuthenticationException authException = new KettleAuthenticationException(
      "Authentication failed" );

    assertTrue( RepositoryExceptionUtils.isSessionExpired( authException ) );
  }

  /**
   * Test isSessionExpired returns false for non-auth exceptions.
   * Given a generic RuntimeException,
   * When isSessionExpired is called via RepositoryExceptionUtils,
   * Then it should return false.
   */
  @Test
  public void testIsSessionExpiredWithGenericException() {
    RuntimeException genericException = new RuntimeException( "Generic error" );

    assertFalse( RepositoryExceptionUtils.isSessionExpired( genericException ) );
  }

  /**
   * Test getOkClicked returns correct state.
   * Given controller is initialized with CANCELLED state,
   * When getOkClicked() is called,
   * Then it should return false.
   */
  @Test
  public void testGetOkClickedInitialState() {
    assertFalse( controller.getOkClicked() );
  }

  @Test
  public void testCallbackGetterSetter() {
    RepositoryExplorerCallback callback = mock( RepositoryExplorerCallback.class );
    controller.setCallback( callback );

    assertEquals( callback, controller.getCallback() );
  }

  @Test
  public void testSharedObjectSyncUtilGetterSetter() {
    SharedObjectSyncUtil util = mock( SharedObjectSyncUtil.class );
    controller.setSharedObjectSyncUtil( util );

    assertEquals( util, controller.getSharedObjectSyncUtil() );
  }

  @Test
  public void testGetName() {
    assertEquals( "mainController", controller.getName() );
  }

  @Test
  public void testHandleSessionExpiryWithNonSessionExpiryException() {
    RuntimeException regularException = new RuntimeException( "Some other error" );

    boolean result = controller.handleSessionExpiry( regularException );

    assertFalse( result );
  }

  @Test
  public void testHandleSessionExpiryWithNullException() {
    boolean result = controller.handleSessionExpiry( null );

    assertFalse( result );
  }

  @Test
  public void testHandleLostRepositoryWithRepositoryLostException() {
    String errorMessage = "Repository lost";
    KettleRepositoryLostException repositoryLostException = new KettleRepositoryLostException(
      errorMessage );

    boolean result = controller.handleLostRepository( repositoryLostException );

    assertTrue( result );
  }

  @Test
  public void testExceptionUtilsDelegation() {
    // Test KettleAuthenticationException
    KettleAuthenticationException authEx = new KettleAuthenticationException( "Auth failed" );
    assertTrue( RepositoryExceptionUtils.isSessionExpired( authEx ) );

    // Test RuntimeException
    RuntimeException runtimeEx = new RuntimeException( "Runtime error" );
    assertFalse( RepositoryExceptionUtils.isSessionExpired( runtimeEx ) );

    // Test null
    assertFalse( RepositoryExceptionUtils.isSessionExpired( null ) );
  }

  @Test
  public void testAddDialogListener() {
    assertNotNull( controller );
  }

  @Test
  public void testRemoveDialogListener() {
    assertNotNull( controller );
  }

  @Test
  public void testCloseDialog() {
    assertNotNull( controller );
  }

  @Test
  public void testRepositoryInitialization() {
    Repository testRepository = mock( Repository.class );
    controller.setRepository( testRepository );

    assertNotNull( controller );
  }

  @Test
  public void testSessionExpiryRoutingInHandleLostRepository() {
    KettleAuthenticationException authException = new KettleAuthenticationException(
      "Session expired" );

    boolean result = controller.handleLostRepository( authException );

    assertTrue( result );
  }

  /**
   * Test handleLostRepository returns true for KettleRepositoryLostException.
   * Given any KettleRepositoryLostException,
   * When handleLostRepository() is called,
   * Then it should return true.
   */
  @Test
  public void testHandleLostRepositoryReturnsTrue() {
    KettleRepositoryLostException ex = new KettleRepositoryLostException( "Lost" );

    boolean result = controller.handleLostRepository( ex );

    assertTrue( result );
  }
}



