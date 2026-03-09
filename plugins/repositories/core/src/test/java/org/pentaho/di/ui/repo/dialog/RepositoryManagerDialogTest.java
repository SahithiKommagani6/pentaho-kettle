///*! ******************************************************************************
// *
// * Pentaho
// *
// * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
// *
// * Use of this software is governed by the Business Source License included
// * in the LICENSE.TXT file.
// *
// * Change Date: 2029-07-20
// ******************************************************************************/
//
//package org.pentaho.di.ui.repo.dialog;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.doAnswer;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.mockConstruction;
//import static org.mockito.Mockito.mockStatic;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//import java.lang.reflect.Field;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.TimeoutException;
//
//import org.eclipse.swt.graphics.Image;
//import org.eclipse.swt.widgets.Display;
//import org.eclipse.swt.widgets.MenuItem;
//import org.eclipse.swt.widgets.Shell;
//import org.junit.AfterClass;
//import org.junit.Before;
//import org.junit.BeforeClass;
//import org.junit.Test;
//import org.mockito.MockedConstruction;
//import org.mockito.MockedStatic;
//import org.pentaho.di.core.KettleClientEnvironment;
//import org.pentaho.di.core.logging.KettleLogStore;
//import org.pentaho.di.core.logging.LogChannelInterface;
//import org.pentaho.di.repository.RepositoriesMeta;
//import org.pentaho.di.repository.RepositoryMeta;
//import org.pentaho.di.ui.core.PropsUI;
//import org.pentaho.di.ui.core.gui.GUIResource;
//import org.pentaho.di.ui.repo.controller.RepositoryConnectController;
//import org.pentaho.di.ui.repo.service.BrowserAuthenticationService;
//import org.pentaho.di.ui.repo.service.BrowserAuthenticationService.SessionInfo;
//import org.pentaho.di.ui.repo.util.PurRepositoryUtils;
//import org.pentaho.di.ui.spoon.Spoon;
//import org.pentaho.di.ui.spoon.session.AuthenticationContext;
//import org.pentaho.di.ui.spoon.session.SpoonSessionManager;
//
///**
// * Tests for the newly added {@code openBrowserLogin()} method in {@link RepositoryManagerDialog}.
// * <p>
// * {@code RepositoryManagerDialog} has a static initializer that calls
// * {@code GUIResource.getInstance().getImageLogoSmall()}, which requires a running SWT Display.
// * We mock {@code GUIResource} and {@code PropsUI} statically at the class level (before the
// * class under test is loaded) to allow instantiation in a headless test environment.
// */
//public class RepositoryManagerDialogTest {
//
//  // Class-level mocks — must be opened BEFORE RepositoryManagerDialog is loaded
//  private static MockedStatic<GUIResource> guiResourceMock;
//  private static MockedStatic<PropsUI> propsUIMock;
//
//  private RepositoryManagerDialog dialogInstance;
//  private Shell mockShell;
//  private Shell mockDialogShell;
//  private LogChannelInterface mockLog;
//  private Display mockDisplay;
//
//  @BeforeClass
//  public static void setUpClass() throws Exception {
//    if ( !KettleLogStore.isInitialized() ) {
//      KettleLogStore.init();
//    }
//    if ( !KettleClientEnvironment.isInitialized() ) {
//      KettleClientEnvironment.init();
//    }
//
//    // Mock GUIResource and PropsUI BEFORE RepositoryManagerDialog class is loaded.
//    // This prevents the static initializer from crashing.
//    GUIResource mockGui = mock( GUIResource.class );
//    Image mockImage = mock( Image.class );
//    when( mockGui.getImageLogoSmall() ).thenReturn( mockImage );
//
//    guiResourceMock = mockStatic( GUIResource.class );
//    guiResourceMock.when( GUIResource::getInstance ).thenReturn( mockGui );
//
//    propsUIMock = mockStatic( PropsUI.class );
//    propsUIMock.when( PropsUI::getInstance ).thenReturn( mock( PropsUI.class ) );
//  }
//
//  @AfterClass
//  public static void tearDownClass() {
//    if ( guiResourceMock != null ) {
//      guiResourceMock.close();
//    }
//    if ( propsUIMock != null ) {
//      propsUIMock.close();
//    }
//  }
//
//  @Before
//  public void setUp() throws Exception {
//    mockShell = mock( Shell.class );
//    mockDialogShell = mock( Shell.class );
//    mockLog = mock( LogChannelInterface.class );
//    mockDisplay = mock( Display.class );
//
//    // Make Display.asyncExec run the Runnable immediately for synchronous testing
//    doAnswer( inv -> {
//      ( (Runnable) inv.getArgument( 0 ) ).run();
//      return null;
//    } ).when( mockDisplay ).asyncExec( any( Runnable.class ) );
//
//    // Use Objenesis to create instance without calling the constructor
//    dialogInstance = org.objenesis.ObjenesisHelper.newInstance( RepositoryManagerDialog.class );
//
//    // Set internal fields via reflection
//    setField( dialogInstance, "dialog", mockDialogShell );
//    setField( dialogInstance, "log", mockLog );
//
//    when( mockDialogShell.isDisposed() ).thenReturn( false );
//  }
//
//  // ================================================================
//  // openBrowserLogin() — success path: stores session and connects
//  // ================================================================
//
//  @Test
//  public void testOpenBrowserLogin_Success_StoresSessionAndConnects() throws Exception {
//    SessionInfo sessionInfo = new SessionInfo( "JSESS123", "adminUser" );
//    CompletableFuture<SessionInfo> completedFuture = CompletableFuture.completedFuture( sessionInfo );
//
//    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );
//    SpoonSessionManager mockSessionMgr = mock( SpoonSessionManager.class );
//    when( mockSessionMgr.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
//      .thenReturn( mockAuthContext );
//
//    RepositoryConnectController mockController = mock( RepositoryConnectController.class );
//
//    try ( MockedStatic<SpoonSessionManager> sessionMock = mockStatic( SpoonSessionManager.class );
//          MockedStatic<Display> displayMock = mockStatic( Display.class );
//          MockedStatic<RepositoryConnectController> ctrlMock = mockStatic( RepositoryConnectController.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( completedFuture ) ) ) {
//
//      sessionMock.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionMgr );
//      displayMock.when( Display::getDefault ).thenReturn( mockDisplay );
//      ctrlMock.when( RepositoryConnectController::getInstance ).thenReturn( mockController );
//
//      dialogInstance.openBrowserLogin( "myRepo", "http://localhost:8080/pentaho" ,new RepositoriesMeta() );
//
//      // Verify dialog was closed
//      verify( mockDialogShell ).close();
//
//      // Verify session was stored
//      verify( mockAuthContext ).storeJSessionId( "JSESS123" );
//
//      // Verify connectToRepository was called with correct arguments
//      verify( mockController ).connectToRepository(
//        eq( "myRepo" ),
//        eq( "adminUser" ),
//        eq( AuthenticationContext.SESSION_AUTH_TOKEN )
//      );
//    }
//  }
//
//  // ================================================================
//  // openBrowserLogin() — success path with connect failure
//  // ================================================================
//
//
//  // ================================================================
//  // openBrowserLogin() — exceptionally path (generic error)
//  // ================================================================
//
//  @Test
//  public void testOpenBrowserLogin_AuthFails_ShowsAuthenticationFailedError() throws Exception {
//    CompletableFuture<SessionInfo> failedFuture = new CompletableFuture<>();
//    failedFuture.completeExceptionally( new RuntimeException( "Server unreachable" ) );
//
//    Spoon mockSpoon = mock( Spoon.class );
//    when( mockSpoon.getShell() ).thenReturn( mockShell );
//
//    try ( MockedStatic<Display> displayMock = mockStatic( Display.class );
//          MockedStatic<Spoon> spoonMock = mockStatic( Spoon.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( failedFuture ) );
//          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
//            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {
//
//      displayMock.when( Display::getDefault ).thenReturn( mockDisplay );
//      spoonMock.when( Spoon::getInstance ).thenReturn( mockSpoon );
//
//      dialogInstance.openBrowserLogin( "myRepo", "http://localhost:8080/pentaho" );
//
//      // Verify error was logged
//      verify( mockLog ).logError( eq( "Browser authentication failed" ), any( Throwable.class ) );
//
//      // Verify error dialog shown with "Authentication Failed" title
//      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
//      verify( msgBox ).setText( "Authentication Failed" );
//      verify( msgBox ).open();
//    }
//  }
//
//  // ================================================================
//  // openBrowserLogin() — exceptionally path (TimeoutException)
//  // ================================================================
//
//  @Test
//  public void testOpenBrowserLogin_Timeout_ShowsTimeoutMessage() throws Exception {
//    CompletableFuture<SessionInfo> timedOutFuture = new CompletableFuture<>();
//    timedOutFuture.completeExceptionally( new TimeoutException( "Timed out" ) );
//
//    Spoon mockSpoon = mock( Spoon.class );
//    when( mockSpoon.getShell() ).thenReturn( mockShell );
//
//    try ( MockedStatic<Display> displayMock = mockStatic( Display.class );
//          MockedStatic<Spoon> spoonMock = mockStatic( Spoon.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( timedOutFuture ) );
//          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
//            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {
//
//      displayMock.when( Display::getDefault ).thenReturn( mockDisplay );
//      spoonMock.when( Spoon::getInstance ).thenReturn( mockSpoon );
//
//      dialogInstance.openBrowserLogin( "myRepo", "http://localhost:8080/pentaho" );
//
//      // Verify error dialog shown with "Authentication Failed"
//      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
//      verify( msgBox ).setText( "Authentication Failed" );
//      verify( msgBox ).open();
//    }
//  }
//
//  // ================================================================
//  // openBrowserLogin() — catch block when authenticate() throws
//  // ================================================================
//
//  @Test
//  public void testOpenBrowserLogin_AuthenticateThrows_ShowsError() throws Exception {
//    Spoon mockSpoon = mock( Spoon.class );
//    when( mockSpoon.getShell() ).thenReturn( mockShell );
//
//    try ( MockedStatic<Spoon> spoonMock = mockStatic( Spoon.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenThrow(
//                new RuntimeException( "Port already in use" ) ) );
//          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
//            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {
//
//      spoonMock.when( Spoon::getInstance ).thenReturn( mockSpoon );
//
//      dialogInstance.openBrowserLogin( "myRepo", "http://localhost:8080/pentaho" );
//
//      // Verify error was logged
//      verify( mockLog ).logError( eq( "Error opening browser login" ), any( Exception.class ) );
//
//      // Verify error dialog shown with "Error" title
//      org.eclipse.swt.widgets.MessageBox msgBox = mbMock.constructed().get( 0 );
//      verify( msgBox ).setText( "Error" );
//      verify( msgBox ).open();
//    }
//  }
//
//  // ================================================================
//  // openBrowserLogin() — dialog already disposed: skip close
//  // ================================================================
//
//  @Test
//  public void testOpenBrowserLogin_DialogAlreadyDisposed_SkipsClose() throws Exception {
//    when( mockDialogShell.isDisposed() ).thenReturn( true );
//
//    CompletableFuture<SessionInfo> pending = new CompletableFuture<>();
//
//    try ( MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( pending ) ) ) {
//
//      dialogInstance.openBrowserLogin( "myRepo", "http://localhost:8080/pentaho" );
//
//      // dialog.close() should NOT have been called since it's already disposed
//      verify( mockDialogShell, never() ).close();
//    }
//  }
//
//  // ================================================================
//  // openBrowserLogin() — dialog is null: no NPE on close check
//  // ================================================================
//
//  @Test
//  public void testOpenBrowserLogin_DialogNull_DoesNotThrow() throws Exception {
//    setField( dialogInstance, "dialog", null );
//
//    CompletableFuture<SessionInfo> pending = new CompletableFuture<>();
//
//    try ( MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( pending ) ) ) {
//
//      // Should not throw NPE
//      dialogInstance.openBrowserLogin( "myRepo", "http://localhost:8080/pentaho" );
//    }
//  }
//
//  // ================================================================
//  // openBrowserLogin() — verifies correct URL passed to authenticate
//  // ================================================================
//
//  @Test
//  public void testOpenBrowserLogin_PassesCorrectServerUrl() throws Exception {
//    String serverUrl = "https://secure-server:443/pentaho";
//    CompletableFuture<SessionInfo> pending = new CompletableFuture<>();
//
//    try ( MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( pending ) ) ) {
//
//      dialogInstance.openBrowserLogin( "myRepo", serverUrl );
//
//      BrowserAuthenticationService constructed = authMock.constructed().get( 0 );
//      verify( constructed ).authenticate( "https://secure-server:443/pentaho" );
//    }
//  }
//
//  // ================================================================
//  // handleSsoLoginAction() — repo supports browser auth → opens browser login
//  // ================================================================
//
//  @Test
//  public void testHandleSsoLoginAction_Supported_CallsOpenBrowserLogin() throws Exception {
//    RepositoriesMeta reposMeta = mock( RepositoriesMeta.class );
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//    when( reposMeta.findRepository( "myRepo" ) ).thenReturn( repoMeta );
//
//    CompletableFuture<SessionInfo> pending = new CompletableFuture<>();
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( pending ) ) ) {
//
//      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( true );
//      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( "http://server:8080/pentaho" );
//
//      dialogInstance.handleSsoLoginAction( "myRepo", reposMeta );
//
//      // BrowserAuthenticationService should have been created and authenticate called
//      BrowserAuthenticationService constructed = authMock.constructed().get( 0 );
//      verify( constructed ).authenticate( "http://server:8080/pentaho" );
//    }
//  }
//
//  // ================================================================
//  // handleSsoLoginAction() — repo does NOT support browser auth
//  // ================================================================
//
//  @Test
//  public void testHandleSsoLoginAction_NotSupported_DoesNotOpenBrowser() throws Exception {
//    RepositoriesMeta reposMeta = mock( RepositoriesMeta.class );
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//    when( reposMeta.findRepository( "myRepo" ) ).thenReturn( repoMeta );
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class ) ) {
//
//      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( false );
//
//      dialogInstance.handleSsoLoginAction( "myRepo", reposMeta );
//
//      // BrowserAuthenticationService should NOT have been constructed
//      assert authMock.constructed().isEmpty();
//    }
//  }
//
//  // ================================================================
//  // handleSsoLoginAction() — repo not found (null)
//  // ================================================================
//
//  @Test
//  public void testHandleSsoLoginAction_RepoNotFound_DoesNotThrow() throws Exception {
//    RepositoriesMeta reposMeta = mock( RepositoriesMeta.class );
//    when( reposMeta.findRepository( "noSuchRepo" ) ).thenReturn( null );
//
//    try ( MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class ) ) {
//
//      dialogInstance.handleSsoLoginAction( "noSuchRepo", reposMeta );
//
//      assert authMock.constructed().isEmpty();
//    }
//  }
//
//  // ================================================================
//  // handleSsoLoginAction() — exception logged on error
//  // ================================================================
//
//  @Test
//  public void testHandleSsoLoginAction_Exception_LogsError() throws Exception {
//    RepositoriesMeta reposMeta = mock( RepositoriesMeta.class );
//    when( reposMeta.findRepository( "myRepo" ) ).thenThrow( new RuntimeException( "Boom" ) );
//
//    dialogInstance.handleSsoLoginAction( "myRepo", reposMeta );
//
//    verify( mockLog ).logError( eq( "Error opening browser login" ), any( Exception.class ) );
//  }
//
//  // ================================================================
//  // updateBrowserLoginMenuItem() — browser auth supported
//  // ================================================================
//
//  @Test
//  public void testUpdateBrowserLoginMenuItem_Supported_EnablesAndSetsText() throws Exception {
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//    MenuItem menuItem = mock( MenuItem.class );
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class ) ) {
//      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( true );
//
//      dialogInstance.updateBrowserLoginMenuItem( menuItem, repoMeta );
//
//      verify( menuItem ).setEnabled( true );
//      verify( menuItem ).setText( "Login with SSO" );
//    }
//  }
//
//  // ================================================================
//  // updateBrowserLoginMenuItem() — browser auth NOT supported
//  // ================================================================
//
//  @Test
//  public void testUpdateBrowserLoginMenuItem_NotSupported_DisablesAndSetsText() throws Exception {
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//    MenuItem menuItem = mock( MenuItem.class );
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class ) ) {
//      purUtils.when( () -> PurRepositoryUtils.supportsBrowserAuth( repoMeta ) ).thenReturn( false );
//
//      dialogInstance.updateBrowserLoginMenuItem( menuItem, repoMeta );
//
//      verify( menuItem ).setEnabled( false );
//      verify( menuItem ).setText( "Login with Browser (Not available for this repository type)" );
//    }
//  }
//
//  // ================================================================
//  // connectBasedOnAuthMethod() — SSO with valid URL opens browser
//  // ================================================================
//
//  @Test
//  public void testConnectBasedOnAuthMethod_SSO_ValidUrl_OpensBrowser() throws Exception {
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//
//    CompletableFuture<SessionInfo> pending = new CompletableFuture<>();
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenReturn( pending ) ) ) {
//
//      purUtils.when( () -> PurRepositoryUtils.isSsoAuth( repoMeta ) ).thenReturn( true );
//      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( "http://server:8080/pentaho" );
//
//      dialogInstance.connectBasedOnAuthMethod( "myRepo", repoMeta );
//
//      BrowserAuthenticationService constructed = authMock.constructed().get( 0 );
//      verify( constructed ).authenticate( "http://server:8080/pentaho" );
//    }
//  }
//
//  // ================================================================
//  // connectBasedOnAuthMethod() — SSO with null URL logs error
//  // ================================================================
//
//  @Test
//  public void testConnectBasedOnAuthMethod_SSO_NullUrl_LogsError() throws Exception {
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class ) ) {
//
//      purUtils.when( () -> PurRepositoryUtils.isSsoAuth( repoMeta ) ).thenReturn( true );
//      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( null );
//
//      dialogInstance.connectBasedOnAuthMethod( "myRepo", repoMeta );
//
//      verify( mockLog ).logError( "Cannot connect using SSO: Server URL is not configured" );
//      assert authMock.constructed().isEmpty();
//    }
//  }
//
//  // ================================================================
//  // connectBasedOnAuthMethod() — SSO with empty URL logs error
//  // ================================================================
//
//  @Test
//  public void testConnectBasedOnAuthMethod_SSO_EmptyUrl_LogsError() throws Exception {
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class ) ) {
//
//      purUtils.when( () -> PurRepositoryUtils.isSsoAuth( repoMeta ) ).thenReturn( true );
//      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( "   " );
//
//      dialogInstance.connectBasedOnAuthMethod( "myRepo", repoMeta );
//
//      verify( mockLog ).logError( "Cannot connect using SSO: Server URL is not configured" );
//      assert authMock.constructed().isEmpty();
//    }
//  }
//
//  // ================================================================
//  // connectBasedOnAuthMethod() — non-SSO (dialog path, catches init error)
//  // ================================================================
//
//  @Test
//  public void testConnectBasedOnAuthMethod_NonSSO_DoesNotOpenBrowser() throws Exception {
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class ) ) {
//
//      purUtils.when( () -> PurRepositoryUtils.isSsoAuth( repoMeta ) ).thenReturn( false );
//
//      try {
//        dialogInstance.connectBasedOnAuthMethod( "myRepo", repoMeta );
//      } catch ( Exception e ) {
//        // Expected — RepositoryConnectionDialog has SWT static initializers
//      }
//
//      // BrowserAuthenticationService should NOT have been constructed
//      assert authMock.constructed().isEmpty();
//    }
//  }
//
//  // ================================================================
//  // connectBasedOnAuthMethod() — SSO with openBrowserLogin throwing
//  // ================================================================
//
//  @Test
//  public void testConnectBasedOnAuthMethod_SSO_OpenBrowserThrows_LogsError() throws Exception {
//    RepositoryMeta repoMeta = mock( RepositoryMeta.class );
//
//    Spoon mockSpoon = mock( Spoon.class );
//    when( mockSpoon.getShell() ).thenReturn( mockShell );
//
//    try ( MockedStatic<PurRepositoryUtils> purUtils = mockStatic( PurRepositoryUtils.class );
//          MockedStatic<Spoon> spoonMock = mockStatic( Spoon.class );
//          MockedConstruction<BrowserAuthenticationService> authMock =
//            mockConstruction( BrowserAuthenticationService.class, ( mock, ctx ) ->
//              when( mock.authenticate( anyString() ) ).thenThrow( new RuntimeException( "Port in use" ) ) );
//          MockedConstruction<org.eclipse.swt.widgets.MessageBox> mbMock =
//            mockConstruction( org.eclipse.swt.widgets.MessageBox.class ) ) {
//
//      purUtils.when( () -> PurRepositoryUtils.isSsoAuth( repoMeta ) ).thenReturn( true );
//      purUtils.when( () -> PurRepositoryUtils.getServerUrl( repoMeta ) ).thenReturn( "http://server:8080/pentaho" );
//      spoonMock.when( Spoon::getInstance ).thenReturn( mockSpoon );
//
//      dialogInstance.connectBasedOnAuthMethod( "myRepo", repoMeta );
//
//      // The error from openBrowserLogin's catch block should be logged
//      verify( mockLog ).logError( eq( "Error opening browser login" ), any( Exception.class ) );
//    }
//  }
//
//  // ================================================================
//  // Helper: set private/inherited field via reflection
//  // ================================================================
//
//  private static void setField( Object target, String fieldName, Object value ) throws Exception {
//    Class<?> clazz = target.getClass();
//    while ( clazz != null ) {
//      try {
//        Field f = clazz.getDeclaredField( fieldName );
//        f.setAccessible( true );
//        f.set( target, value );
//        return;
//      } catch ( NoSuchFieldException e ) {
//        clazz = clazz.getSuperclass();
//      }
//    }
//    throw new NoSuchFieldException( fieldName + " not found in " + target.getClass().getName() + " hierarchy" );
//  }
//}
//
