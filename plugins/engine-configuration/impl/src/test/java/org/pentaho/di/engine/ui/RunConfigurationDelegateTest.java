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


package org.pentaho.di.engine.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import org.pentaho.di.core.bowl.DefaultBowl;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.extension.ExtensionPointHandler;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.engine.configuration.api.RunConfigurationService;
import org.pentaho.di.engine.configuration.impl.RunConfigurationManager;
import org.pentaho.di.engine.configuration.impl.pentaho.DefaultRunConfiguration;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.job.entries.trans.JobEntryTrans;
import org.pentaho.di.job.entry.JobEntryCopy;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.repository.exception.RepositoryExceptionUtils;
import org.pentaho.di.ui.spoon.Spoon;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
  * @author Luis Martins (16-Feb-2018)
  */
@RunWith( MockitoJUnitRunner.StrictStubs.class )
@Ignore
public class RunConfigurationDelegateTest {

  private Spoon spoon;
  private RunConfigurationService service;
  private RunConfigurationDelegate delegate;
  private MockedStatic<Spoon> mockedSpoon;

  @Before
  public void setup() {
    spoon = mock( Spoon.class );
    doReturn( mock( Shell.class ) ).when( spoon ).getShell();

    mockedSpoon = mockStatic( Spoon.class );
    when( Spoon.getInstance() ).thenReturn( spoon );

    delegate = spy( RunConfigurationDelegate.getInstance( () -> DefaultBowl.getInstance().getMetastore() ) );
    service = mock( RunConfigurationManager.class );
    delegate.setRunConfigurationManager( service );
  }

  @After
  public void teardown() {
    mockedSpoon.close();
  }

  @Test
  public void testCreate() throws Exception {
    List<String> list = new ArrayList<>();
    list.add( "Configuration 1" );

    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );
    config.setServer( "localhost" );

    doReturn( list ).when( service ).getNames();

    try ( MockedConstruction<RunConfigurationDialog> mockedConfDialog = mockConstruction( RunConfigurationDialog.class,
      (mock, context) -> when( mock.open() ).thenReturn( config ) ) ) {
      delegate.create();

      verify( service, times( 1 ) ).save( config );
      verify( spoon, times( 1 ) ).refreshTree( RunConfigurationFolderProvider.STRING_RUN_CONFIGURATIONS );
    }
  }

  @Test
  public void testDelete() throws Exception {
    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );
    config.setServer( "localhost" );

    try ( MockedConstruction<RunConfigurationDeleteDialog> mockedConfDialog = mockConstruction( RunConfigurationDeleteDialog.class,
      (mock, context) -> when( mock.open() ).thenReturn( SWT.YES ) ) ) {
      delegate.delete( config );

      verify( service, times( 1 ) ).delete( "Test" );
      verify( spoon, times( 1 ) ).refreshTree( RunConfigurationFolderProvider.STRING_RUN_CONFIGURATIONS );
    }
  }

  @Test
  public void testEdit() throws Exception {
    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );
    config.setServer( "localhost" );

    doNothing().when( delegate ).updateLoadedJobs( "Test", config );

    try ( MockedConstruction<RunConfigurationDialog> mockedConfDialog = mockConstruction( RunConfigurationDialog.class,
      (mock, context) -> when( mock.open() ).thenReturn( config ) ) ) {
      delegate.edit( config );

      verify( delegate, times( 1 ) ).updateLoadedJobs( "Test", config );
      verify( service, times( 1 ) ).delete( "Test" );
      verify( service, times( 1 ) ).save( config );
      verify( spoon, times( 1 ) ).refreshTree( RunConfigurationFolderProvider.STRING_RUN_CONFIGURATIONS );
    }
  }

  @Test
  public void testLoad() {
    delegate.load();
    verify( service, times( 1 ) ).load();
  }


  @Test
  public void testUpdateLoadedJobs_PDI16777() {
    JobEntryTrans trans = new JobEntryTrans();
    trans.setRunConfiguration( "key" );

    JobMeta meta = new JobMeta();
    meta.addJobEntry( new JobEntryCopy( trans ) );

    JobMeta[] jobs = new JobMeta[] { meta };
    doReturn( jobs ).when( spoon ).getLoadedJobs();

    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );
    config.setServer( "localhost" );

    delegate.updateLoadedJobs( "key", config );

    assertEquals( "Test", trans.getRunConfiguration() );
    assertEquals( "localhost", trans.getRemoteSlaveServerName() );
  }

  @Test
  public void testUpdateLoadedJobs_Exception() throws Exception {
    JobEntryTrans trans = new JobEntryTrans();
    trans.setRunConfiguration( "key" );

    JobMeta meta = new JobMeta();
    meta.addJobEntry( new JobEntryCopy( trans ) );

    JobMeta[] jobs = new JobMeta[] { meta };
    doReturn( jobs ).when( spoon ).getLoadedJobs();

    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );
    config.setServer( "localhost" );

    LogChannelInterface log = mock( LogChannelInterface.class );
    doReturn( log ).when( spoon ).getLog();

    try ( MockedStatic<ExtensionPointHandler> mockedHandler = mockStatic( ExtensionPointHandler.class ) ) {
      mockedHandler.when( () -> ExtensionPointHandler.callExtensionPoint( any(), any(), any() ) ).thenThrow( KettleException.class );
      delegate.updateLoadedJobs( "key", config );

      verify( log, times( 1 ) ).logBasic( any() );
    }
  }

  // ================================================================
  // executeWithSessionRetry() — operation succeeds on first try
  // ================================================================

  @Test
  public void testEdit_OperationSucceeds_NoRetry() {
    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );

    doNothing().when( delegate ).updateLoadedJobs( "Test", config );

    try ( MockedConstruction<RunConfigurationDialog> mockedDialog = mockConstruction( RunConfigurationDialog.class,
            ( mock, context ) -> when( mock.open() ).thenReturn( config ) );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      delegate.edit( config );

      // Operation succeeded — no ErrorDialog should have been created
      assertEquals( 0, mockedError.constructed().size() );
      verify( service, times( 1 ) ).save( config );
    }
  }

  // ================================================================
  // executeWithSessionRetry() — non-session error shows ErrorDialog
  // ================================================================

  @Test
  public void testCreate_NonSessionError_ShowsErrorDialog() throws Exception {
    RuntimeException nonSessionError = new RuntimeException( "Some generic error" );
    doThrow( nonSessionError ).when( service ).getNames();

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( nonSessionError ) ).thenReturn( false );

      delegate.create();

      // ErrorDialog should have been shown
      assertEquals( 1, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry() — session expired + successful reconnect + retry succeeds
  // ================================================================

  @Test
  public void testCreate_SessionExpired_ReconnectSucceeds_RetrySucceeds() throws Exception {
    RuntimeException sessionError = new RuntimeException( "Session expired" );

    // First call throws, second call succeeds
    List<String> emptyList = new ArrayList<>();
    when( service.getNames() )
      .thenThrow( sessionError )
      .thenReturn( emptyList );

    when( spoon.handleSessionExpiryWithRelogin() ).thenReturn( true );

    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<RunConfigurationDialog> mockedDialog = mockConstruction( RunConfigurationDialog.class,
            ( mock, context ) -> when( mock.open() ).thenReturn( config ) );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( sessionError ) ).thenReturn( true );

      delegate.create();

      // Reconnect should have been called
      verify( spoon, times( 1 ) ).handleSessionExpiryWithRelogin();

      // Retry succeeded — config should have been saved
      verify( service, times( 1 ) ).save( config );

      // No error dialog
      assertEquals( 0, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry() — session expired + successful reconnect + retry fails
  // ================================================================

  @Test
  public void testCreate_SessionExpired_ReconnectSucceeds_RetryFails() throws Exception {
    RuntimeException sessionError = new RuntimeException( "Session expired" );
    RuntimeException retryError = new RuntimeException( "Retry also failed" );

    when( service.getNames() )
      .thenThrow( sessionError )
      .thenThrow( retryError );

    when( spoon.handleSessionExpiryWithRelogin() ).thenReturn( true );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( sessionError ) ).thenReturn( true );

      delegate.create();

      // Reconnect was attempted
      verify( spoon, times( 1 ) ).handleSessionExpiryWithRelogin();

      // Retry failed — ErrorDialog should have been shown
      assertEquals( 1, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry() — session expired + reconnect fails
  // ================================================================

  @Test
  public void testCreate_SessionExpired_ReconnectFails_NoRetry() throws Exception {
    RuntimeException sessionError = new RuntimeException( "Session expired" );

    when( service.getNames() ).thenThrow( sessionError );
    when( spoon.handleSessionExpiryWithRelogin() ).thenReturn( false );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( sessionError ) ).thenReturn( true );

      delegate.create();

      // Reconnect was attempted but returned false
      verify( spoon, times( 1 ) ).handleSessionExpiryWithRelogin();

      // getNames() should only have been called once (no retry)
      verify( service, times( 1 ) ).getNames();

      // No error dialog from executeWithSessionRetry when handleSessionExpiry returns false
      // (handleSessionExpiry doesn't call showError when it returns false normally)
    }
  }

  // ================================================================
  // handleSessionExpiry() — Spoon throws exception
  // ================================================================

  @Test
  public void testDelete_SessionExpired_HandleSessionExpiryThrows() throws Exception {
    RuntimeException sessionError = new RuntimeException( "Session expired" );
    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );

    try ( MockedConstruction<RunConfigurationDeleteDialog> mockedDelDialog = mockConstruction(
            RunConfigurationDeleteDialog.class,
            ( mock, context ) -> when( mock.open() ).thenThrow( sessionError ) );
          MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( sessionError ) ).thenReturn( true );
      when( spoon.handleSessionExpiryWithRelogin() ).thenThrow( new RuntimeException( "Spoon unavailable" ) );

      delegate.delete( config );

      // handleSessionExpiry caught the exception — should have shown an ErrorDialog
      // (one from handleSessionExpiry, but no retry occurs)
      verify( spoon, times( 1 ) ).handleSessionExpiryWithRelogin();
    }
  }

  // ================================================================
  // executeWithSessionRetry wraps edit() — session expired during edit
  // ================================================================

  @Test
  public void testEdit_SessionExpired_ReconnectsAndRetries() throws Exception {
    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );

    RuntimeException sessionError = new RuntimeException( "Session expired" );

    // First call to getNames (inside editInternal) throws, second succeeds
    when( service.getNames() )
      .thenThrow( sessionError )
      .thenReturn( new ArrayList<>() );

    when( spoon.handleSessionExpiryWithRelogin() ).thenReturn( true );
    doNothing().when( delegate ).updateLoadedJobs( anyString(), any() );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<RunConfigurationDialog> mockedDialog = mockConstruction( RunConfigurationDialog.class,
            ( mock, context ) -> when( mock.open() ).thenReturn( config ) );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( sessionError ) ).thenReturn( true );

      delegate.edit( config );

      verify( spoon, times( 1 ) ).handleSessionExpiryWithRelogin();
      // On successful retry, save should be called
      verify( service, times( 1 ) ).save( config );
      assertEquals( 0, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry wraps delete() — session expired during delete
  // ================================================================

  @Test
  public void testDelete_SessionExpired_ReconnectsAndRetries() throws Exception {
    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "TestConfig" );

    RuntimeException sessionError = new RuntimeException( "Session expired" );

    // Use a counter to make delete dialog throw first time, succeed second time
    final int[] callCount = { 0 };

    when( spoon.handleSessionExpiryWithRelogin() ).thenReturn( true );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<RunConfigurationDeleteDialog> mockedDelDialog = mockConstruction(
            RunConfigurationDeleteDialog.class,
            ( mock, context ) -> {
              callCount[0]++;
              if ( callCount[0] == 1 ) {
                when( mock.open() ).thenThrow( sessionError );
              } else {
                when( mock.open() ).thenReturn( SWT.YES );
              }
            } );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( sessionError ) ).thenReturn( true );

      delegate.delete( config );

      verify( spoon, times( 1 ) ).handleSessionExpiryWithRelogin();
      // On successful retry, delete should be called
      verify( service, times( 1 ) ).delete( "TestConfig" );
      assertEquals( 0, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry wraps loadAndEdit()
  // ================================================================

  @Test
  public void testLoadAndEdit_NonSessionError_ShowsError() throws Exception {
    RuntimeException error = new RuntimeException( "Load failed" );
    when( service.load( "myConfig" ) ).thenThrow( error );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( error ) ).thenReturn( false );

      delegate.loadAndEdit( "myConfig" );

      assertEquals( 1, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry wraps loadAndDelete()
  // ================================================================

  @Test
  public void testLoadAndDelete_NonSessionError_ShowsError() throws Exception {
    RuntimeException error = new RuntimeException( "Load failed" );
    when( service.load( "myConfig" ) ).thenThrow( error );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( error ) ).thenReturn( false );

      delegate.loadAndDelete( "myConfig" );

      assertEquals( 1, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry wraps loadAndDuplicate()
  // ================================================================

  @Test
  public void testLoadAndDuplicate_NonSessionError_ShowsError() throws Exception {
    RuntimeException error = new RuntimeException( "Load failed" );
    when( service.load( "myConfig" ) ).thenThrow( error );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( error ) ).thenReturn( false );

      delegate.loadAndDuplicate( "myConfig" );

      assertEquals( 1, mockedError.constructed().size() );
    }
  }

  // ================================================================
  // executeWithSessionRetry wraps duplicate()
  // ================================================================

  @Test
  public void testDuplicate_NonSessionError_ShowsError() throws Exception {
    DefaultRunConfiguration config = new DefaultRunConfiguration();
    config.setName( "Test" );

    RuntimeException error = new RuntimeException( "getNames failed" );
    when( service.getNames() ).thenThrow( error );

    try ( MockedStatic<RepositoryExceptionUtils> repoUtils = mockStatic( RepositoryExceptionUtils.class );
          MockedConstruction<ErrorDialog> mockedError = mockConstruction( ErrorDialog.class ) ) {

      repoUtils.when( () -> RepositoryExceptionUtils.isSessionExpired( error ) ).thenReturn( false );

      delegate.duplicate( config );

      assertEquals( 1, mockedError.constructed().size() );
    }
  }
}
