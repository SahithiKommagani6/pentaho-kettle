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

package org.pentaho.di.ui.repo.menu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.pentaho.di.core.KettleClientEnvironment;
import org.pentaho.di.core.logging.KettleLogStore;
import org.pentaho.di.repository.RepositoryMeta;
import org.pentaho.di.ui.repo.controller.RepositoryConnectController;
import org.pentaho.di.ui.repo.service.BrowserAuthenticationService;
import org.pentaho.di.ui.repo.service.BrowserAuthenticationService.SessionInfo;
import org.pentaho.di.ui.repo.util.PurRepositoryUtils;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.session.AuthenticationContext;
import org.pentaho.di.ui.spoon.session.SpoonSessionManager;

/**
 * Tests for newly added browser-based SSO authentication code in {@link RepositoryConnectMenu}.
 * <p>
 * Covers:
 * <ul>
 *   <li>{@code connectBasedOnAuthMethod()} — SSO with valid URL, SSO with null URL, non-SSO fallback</li>
 *   <li>{@code openBrowserLogin()} — success path, exceptionally path, catch block</li>
 * </ul>
 */
public class RepositoryConnectMenuTest {

  private RepositoryConnectMenu menu;
  private Spoon spoon;
  private RepositoryConnectController repoController;
  private Shell mockShell;
  private Display mockDisplay;

  @BeforeClass
  public static void setUpClass() throws Exception {
    if ( !KettleLogStore.isInitialized() ) {
      KettleLogStore.init();
    }
    if ( !KettleClientEnvironment.isInitialized() ) {
      KettleClientEnvironment.init();
    }
  }

  @Before
  public void setUp() throws Exception {
    spoon = mock( Spoon.class );
    mockShell = mock( Shell.class );
    mockDisplay = mock( Display.class );
    repoController = mock( RepositoryConnectController.class );

    when( spoon.getShell() ).thenReturn( mockShell );
    when( spoon.getDisplay() ).thenReturn( mockDisplay );

    // Make Display.asyncExec run the Runnable immediately for synchronous testing
    doAnswer( inv -> {
      ( (Runnable) inv.getArgument( 0 ) ).run();
      return null;
    } ).when( mockDisplay ).asyncExec( any( Runnable.class ) );

    // Build menu object via reflection to avoid constructor side-effects
    // (constructor calls getRepoControllerInstance() and addListener())
    menu = createMenuWithReflection( spoon, repoController );
  }

  private RepositoryConnectMenu createMenuWithReflection( Spoon spoon,
    RepositoryConnectController controller ) throws Exception {
    // Use Objenesis (bundled with Mockito) to create instance without calling constructor
    RepositoryConnectMenu instance =
      org.objenesis.ObjenesisHelper.newInstance( RepositoryConnectMenu.class );

    // Set the private fields via reflection
    setField( instance, "spoon", spoon );
    setField( instance, "repoConnectController", controller );
    return instance;
  }

  private static void setField( Object target, String fieldName, Object value ) throws Exception {
    Field f = target.getClass().getDeclaredField( fieldName );
    f.setAccessible( true );
    f.set( target, value );
  }

  @Test
  public void testConnectBasedOnAuthMethod_SSO_ValidUrl_CallsOpenBrowserLogin() throws Exception {
    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
    String serverUrl = "http://localhost:8080/pentaho";

    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
          MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) -> {
              CompletableFuture<SessionInfo> pending = new CompletableFuture<>();
              when( mock.authenticate( anyString() ) ).thenReturn( pending );
            } ) ) {

      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( true );
      purUtils.when( () -> PurRepositoryUtils.getAuthMethod( repoMeta ) ).thenReturn( "SSO" );
      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( serverUrl );

      invokeConnectBasedOnAuthMethod( "myRepo", repoMeta );

      // BrowserAuthenticationService should have been constructed and authenticate called
      BrowserAuthenticationService constructed = authMock.constructed().get( 0 );
      verify( constructed ).authenticate( serverUrl );
    }
  }

  @Test
  public void testConnectBasedOnAuthMethod_SSO_NullUrl_DoesNotOpenBrowser() throws Exception {
    RepositoryMeta repoMeta = mock( RepositoryMeta.class );

    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
          MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class ) ) {

      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( true );
      purUtils.when( () -> PurRepositoryUtils.getAuthMethod( repoMeta ) ).thenReturn( "SSO" );
      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( null );

      invokeConnectBasedOnAuthMethod( "myRepo", repoMeta );

      // BrowserAuthenticationService should NOT have been constructed
      assert authMock.constructed().isEmpty();
    }
  }

  @Test
  public void testConnectBasedOnAuthMethod_SSO_EmptyUrl_DoesNotOpenBrowser() throws Exception {
    RepositoryMeta repoMeta = mock( RepositoryMeta.class );

    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
          MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class ) ) {

      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( true );
      purUtils.when( () -> PurRepositoryUtils.getAuthMethod( repoMeta ) ).thenReturn( "SSO" );
      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( "   " );

      invokeConnectBasedOnAuthMethod( "myRepo", repoMeta );

      assert authMock.constructed().isEmpty();
    }
  }

  @Test
  public void testConnectBasedOnAuthMethod_NonSSO_DoesNotOpenBrowserLogin() throws Exception {
    RepositoryMeta repoMeta = mock( RepositoryMeta.class );

    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
          MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class ) ) {

      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( false );
      purUtils.when( () -> PurRepositoryUtils.getAuthMethod( repoMeta ) ).thenReturn( "USERNAME_PASSWORD" );

      try {
        invokeConnectBasedOnAuthMethod( "myRepo", repoMeta );
      } catch ( Exception e ) {
        // Expected — RepositoryConnectionDialog has SWT static initializers that
        // cannot run in a unit test. The important thing is verifying the branch.
      }

      // BrowserAuthenticationService should NOT have been constructed (non-SSO path)
      assert authMock.constructed().isEmpty();
    }
  }

  @Test
  public void testOpenBrowserLogin_Success_StoresSessionAndConnects() throws Exception {
    String serverUrl = "http://localhost:8080/pentaho";
    String repoName = "myRepo";

    SessionInfo sessionInfo = new SessionInfo( "JSESS123", "adminUser" );
    CompletableFuture<SessionInfo> completedFuture = CompletableFuture.completedFuture( sessionInfo );

    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );
    SpoonSessionManager mockSessionMgr = mock( SpoonSessionManager.class );
    when( mockSessionMgr.getAuthenticationContext( serverUrl ) ).thenReturn( mockAuthContext );

    try ( MockedStatic<SpoonSessionManager> sessionMock = mockStatic( SpoonSessionManager.class );
          MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
              when( mock.authenticate( anyString() ) ).thenReturn( completedFuture ) ) ) {

      sessionMock.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionMgr );

      invokeOpenBrowserLogin( repoName, serverUrl );

      // Verify session was stored
      verify( mockAuthContext ).storeJSessionId( "JSESS123" );

      // Verify connectToRepository was called with session auth password
      verify( repoController ).connectToRepository(
        repoName,
        "adminUser",
        AuthenticationContext.SESSION_AUTH_TOKEN
      );
    }
  }

  @Test
  public void testOpenBrowserLogin_ConnectThrows_ShowsError() throws Exception {
    String serverUrl = "http://localhost:8080/pentaho";
    String repoName = "myRepo";

    SessionInfo sessionInfo = new SessionInfo( "JSESS456", "admin" );
    CompletableFuture<SessionInfo> completedFuture = CompletableFuture.completedFuture( sessionInfo );

    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );
    SpoonSessionManager mockSessionMgr = mock( SpoonSessionManager.class );
    when( mockSessionMgr.getAuthenticationContext( serverUrl ) ).thenReturn( mockAuthContext );
    doThrow( new RuntimeException( "Connection refused" ) )
      .when( repoController ).connectToRepository( anyString(), anyString(), anyString() );

    try ( MockedStatic<SpoonSessionManager> sessionMock = mockStatic( SpoonSessionManager.class );
          MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
              when( mock.authenticate( anyString() ) ).thenReturn( completedFuture ) );
          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {

      sessionMock.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionMgr );

      invokeOpenBrowserLogin( repoName, serverUrl );

      // Error dialog should have been shown
      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
      verify( msgBox ).setText( "Connection Error" );
      verify( msgBox ).open();
    }
  }

  @Test
  public void testOpenBrowserLogin_AuthFails_ShowsError() throws Exception {
    String serverUrl = "http://localhost:8080/pentaho";

    CompletableFuture<SessionInfo> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally( new RuntimeException( "Server unreachable" ) );

    try ( MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
              when( mock.authenticate( anyString() ) ).thenReturn( failedFuture ) );
          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {

      invokeOpenBrowserLogin( "myRepo", serverUrl );

      // Error dialog should have been shown with "Authentication Error"
      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
      verify( msgBox ).setText( "Authentication Error" );
      verify( msgBox ).open();

      // connectToRepository should NOT have been called
      verify( repoController, never() ).connectToRepository( anyString(), anyString(), anyString() );
    }
  }

  @Test
  public void testOpenBrowserLogin_Timeout_ShowsTimeoutMessage() throws Exception {
    String serverUrl = "http://localhost:8080/pentaho";

    CompletableFuture<SessionInfo> timedOutFuture = new CompletableFuture<>();
    timedOutFuture.completeExceptionally( new TimeoutException( "Timed out" ) );

    try ( MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
              when( mock.authenticate( anyString() ) ).thenReturn( timedOutFuture ) );
          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {

      invokeOpenBrowserLogin( "myRepo", serverUrl );

      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
      verify( msgBox ).setText( "Authentication Error" );
      verify( msgBox ).open();
    }
  }

  @Test
  public void testOpenBrowserLogin_AuthenticateThrows_ShowsError() throws Exception {
    String serverUrl = "http://localhost:8080/pentaho";

    try ( MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
              when( mock.authenticate( anyString() ) ).thenThrow(
                new RuntimeException( "Port already in use" ) ) );
          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {

      invokeOpenBrowserLogin( "myRepo", serverUrl );

      // Error dialog should show "Error" title
      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
      verify( msgBox ).setText( "Error" );
      verify( msgBox ).open();

      // connectToRepository should NOT have been called
      verify( repoController, never() ).connectToRepository( anyString(), anyString(), anyString() );
    }
  }

  @Test
  public void testOpenBrowserLogin_PassesCorrectServerUrl() throws Exception {
    String serverUrl = "https://secure-server:443/pentaho";

    CompletableFuture<SessionInfo> pending = new CompletableFuture<>();

    try ( MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
              when( mock.authenticate( anyString() ) ).thenReturn( pending ) ) ) {

      invokeOpenBrowserLogin( "myRepo", serverUrl );

      BrowserAuthenticationService constructed = authMock.constructed().get( 0 );
      verify( constructed ).authenticate( "https://secure-server:443/pentaho" );
    }
  }

  @Test
  public void testConnectBasedOnAuthMethod_SSO_OpenBrowserLoginThrows_LogsError() throws Exception {
    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
    String serverUrl = "http://localhost:8080/pentaho";

    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
          MockedConstruction<BrowserAuthenticationService> authMock =
            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) -> {
              when( mock.authenticate( anyString() ) ).thenThrow( new RuntimeException( "Fatal error" ) );
            } );
          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {

      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( true );
      purUtils.when( () -> PurRepositoryUtils.getAuthMethod( repoMeta ) ).thenReturn( "SSO" );
      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( serverUrl );

      invokeConnectBasedOnAuthMethod( "myRepo", repoMeta );

      // Error dialog should have been shown
      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
      verify( msgBox ).setText( "Error" );
      verify( msgBox ).open();
    }
  }

  private void invokeConnectBasedOnAuthMethod( String repoName, RepositoryMeta repositoryMeta ) throws Exception {
    Method method = RepositoryConnectMenu.class.getDeclaredMethod(
      "connectBasedOnAuthMethod", String.class, RepositoryMeta.class );
    method.setAccessible( true );
    method.invoke( menu, repoName, repositoryMeta );
  }

  private void invokeOpenBrowserLogin( String repoName, String serverUrl ) throws Exception {
    Method method = RepositoryConnectMenu.class.getDeclaredMethod(
      "openBrowserLogin", String.class, String.class );
    method.setAccessible( true );
    method.invoke( menu, repoName, serverUrl );
  }
}

