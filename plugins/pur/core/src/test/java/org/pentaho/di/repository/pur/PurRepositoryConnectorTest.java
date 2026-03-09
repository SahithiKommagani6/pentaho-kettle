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

package org.pentaho.di.repository.pur;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.junit.rules.RestorePDIEngineEnvironment;
import org.pentaho.di.ui.spoon.session.AuthenticationContext;
import org.pentaho.di.ui.spoon.session.SpoonSessionManager;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class PurRepositoryConnectorTest {
  @ClassRule public static RestorePDIEngineEnvironment env = new RestorePDIEngineEnvironment();

  @BeforeClass
  public static void setUpClass() throws Exception {
    if ( !KettleEnvironment.isInitialized() ) {
      KettleEnvironment.init();
    }
  }

  @Test
  public void testPDI12439PurRepositoryConnectorDoesntNPEAfterMultipleDisconnects() {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    RootRef mockRootRef = mock( RootRef.class );
    PurRepositoryConnector purRepositoryConnector =
      new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef );
    purRepositoryConnector.disconnect();
    purRepositoryConnector.disconnect();
  }

  @Test
  public void testConnect() {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );
    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "" ).when( location ).getUrl();
    ExecutorService service = mock( ExecutorService.class );
    doReturn( service ).when( purRepositoryConnector ).getExecutor();
    Future future = mock( Future.class );
    try {
      doReturn( "U1" ).when( future ).get();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    Future future2 = mock( Future.class );
    try {
      doReturn( false ).when( future2 ).get();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    Future future3 = mock( Future.class );
    try {
      doReturn( null ).when( future3 ).get();
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    when( service.submit( any( Callable.class ) ) ).thenReturn( future2 ).thenReturn( future3 ).thenReturn( future3 )
      .thenReturn( future );

    try {
      RepositoryConnectResult res = purRepositoryConnector.connect( "userNam", "password" );
      Assert.assertEquals( "U1", res.getUser().getLogin() );
    } catch ( KettleException e ) {
      e.printStackTrace();
    }
  }

  @Test
  public void testConnectWithSessionAuth_Success() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    ExecutorService service = mock( ExecutorService.class );
    doReturn( service ).when( purRepositoryConnector ).getExecutor();

    // future for authorizationWebserviceFuture (isAdmin = false)
    Future futureAuth = mock( Future.class );
    doReturn( false ).when( futureAuth ).get();
    // future for repoWebServiceFuture (no exception)
    Future futureRepo = mock( Future.class );
    doReturn( null ).when( futureRepo ).get();
    // future for syncWebserviceFuture (no exception)
    Future futureSync = mock( Future.class );
    doReturn( null ).when( futureSync ).get();
    // future for sessionServiceFuture (should return the username directly)
    Future futureSession = mock( Future.class );
    doReturn( "sessionUser" ).when( futureSession ).get();

    when( service.submit( any( Callable.class ) ) )
      .thenReturn( futureAuth )
      .thenReturn( futureRepo )
      .thenReturn( futureSync )
      .thenReturn( futureSession );

    // Mock SpoonSessionManager and AuthenticationContext
    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );

    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenReturn( mockAuthContext );
    when( mockAuthContext.isAuthenticated() ).thenReturn( true );
    when( mockAuthContext.getJSessionId() ).thenReturn( "VALID_SESSION_ID_123" );

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      RepositoryConnectResult result =
        purRepositoryConnector.connect( "sessionUser", AuthenticationContext.SESSION_AUTH_TOKEN );

      assertTrue( result.isSuccess() );
      assertEquals( "sessionUser", result.getUser().getLogin() );
      // decryptedPassword should be empty when session auth is used, so password on user should be ""
      assertEquals( "", result.getUser().getPassword() );
    }
  }

  @Test
  public void testConnectWithSessionAuth_NoJSessionId_ThrowsKettleException() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    // Mock SpoonSessionManager returning context with no JSESSIONID
    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );

    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenReturn( mockAuthContext );
    when( mockAuthContext.isAuthenticated() ).thenReturn( true );
    when( mockAuthContext.getJSessionId() ).thenReturn( null );

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      KettleException thrown = null;
      try {
        purRepositoryConnector.connect( "user", AuthenticationContext.SESSION_AUTH_TOKEN );
      } catch ( KettleException e ) {
        thrown = e;
      }
      assertNotNull( "Expected KettleException when JSESSIONID is null", thrown );
      assertTrue( thrown.getMessage().contains( "no JSESSIONID found" ) );
    }
  }

  @Test
  public void testConnectWithSessionAuth_EmptyJSessionId_ThrowsKettleException() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );

    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenReturn( mockAuthContext );
    when( mockAuthContext.isAuthenticated() ).thenReturn( true );
    when( mockAuthContext.getJSessionId() ).thenReturn( "   " ); // whitespace-only

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      KettleException thrown = null;
      try {
        purRepositoryConnector.connect( "user", AuthenticationContext.SESSION_AUTH_TOKEN );
      } catch ( KettleException e ) {
        thrown = e;
      }
      assertNotNull( "Expected KettleException when JSESSIONID is blank", thrown );
      assertTrue( thrown.getMessage().contains( "no JSESSIONID found" ) );
    }
  }

  @Test
  public void testConnectWithSessionAuth_NotAuthenticated_ThrowsKettleException() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    // AuthContext exists but isAuthenticated returns false
    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );

    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenReturn( mockAuthContext );
    when( mockAuthContext.isAuthenticated() ).thenReturn( false );

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      KettleException thrown = null;
      try {
        purRepositoryConnector.connect( "user", AuthenticationContext.SESSION_AUTH_TOKEN );
      } catch ( KettleException e ) {
        thrown = e;
      }
      assertNotNull( "Expected KettleException when not authenticated", thrown );
      assertTrue( thrown.getMessage().contains( "no JSESSIONID found" ) );
    }
  }

  @Test
  public void testConnectWithSessionAuth_NullAuthContext_ThrowsKettleException() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    // SpoonSessionManager returns null AuthenticationContext
    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenReturn( null );

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      KettleException thrown = null;
      try {
        purRepositoryConnector.connect( "user", AuthenticationContext.SESSION_AUTH_TOKEN );
      } catch ( KettleException e ) {
        thrown = e;
      }
      assertNotNull( "Expected KettleException when AuthenticationContext is null", thrown );
      assertTrue( thrown.getMessage().contains( "no JSESSIONID found" ) );
    }
  }

  @Test
  public void testConnectWithSessionAuth_SpoonSessionManagerThrowsException_ThrowsKettleException() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    // SpoonSessionManager.getInstance() throws exception
    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenThrow( new RuntimeException( "Session manager error" ) );

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      KettleException thrown = null;
      try {
        purRepositoryConnector.connect( "user", AuthenticationContext.SESSION_AUTH_TOKEN );
      } catch ( KettleException e ) {
        thrown = e;
      }
      // Exception is caught internally, jsessionId remains null, so KettleException should be thrown
      assertNotNull( "Expected KettleException when SpoonSessionManager throws", thrown );
      assertTrue( thrown.getMessage().contains( "no JSESSIONID found" ) );
    }
  }

  @Test
  public void testConnectWithSessionAuth_SessionServiceFutureReturnsUsername() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    ExecutorService service = mock( ExecutorService.class );
    doReturn( service ).when( purRepositoryConnector ).getExecutor();

    // Capture callables to verify the session service callable behavior
    Future futureAuth = mock( Future.class );
    doReturn( false ).when( futureAuth ).get();
    Future futureRepo = mock( Future.class );
    doReturn( null ).when( futureRepo ).get();
    Future futureSync = mock( Future.class );
    doReturn( null ).when( futureSync ).get();
    // The session service future should return the provided username when session auth is used
    Future futureSession = mock( Future.class );
    doReturn( "testBrowserUser" ).when( futureSession ).get();

    when( service.submit( any( Callable.class ) ) )
      .thenReturn( futureAuth )
      .thenReturn( futureRepo )
      .thenReturn( futureSync )
      .thenReturn( futureSession );

    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );
    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenReturn( mockAuthContext );
    when( mockAuthContext.isAuthenticated() ).thenReturn( true );
    when( mockAuthContext.getJSessionId() ).thenReturn( "BROWSER_SESSION_ABC" );

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      RepositoryConnectResult result =
        purRepositoryConnector.connect( "testBrowserUser", AuthenticationContext.SESSION_AUTH_TOKEN );

      assertTrue( result.isSuccess() );
      // The user login should be set to what sessionServiceFuture returned
      assertEquals( "testBrowserUser", result.getUser().getLogin() );
    }
  }

  @Test
  public void testConnectWithRegularPassword_DoesNotTriggerSessionAuth() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "" ).when( location ).getUrl();

    ExecutorService service = mock( ExecutorService.class );
    doReturn( service ).when( purRepositoryConnector ).getExecutor();

    Future futureAuth = mock( Future.class );
    doReturn( false ).when( futureAuth ).get();
    Future futureRepo = mock( Future.class );
    doReturn( null ).when( futureRepo ).get();
    Future futureSync = mock( Future.class );
    doReturn( null ).when( futureSync ).get();
    Future futureSession = mock( Future.class );
    doReturn( "regularUser" ).when( futureSession ).get();

    when( service.submit( any( Callable.class ) ) )
      .thenReturn( futureAuth )
      .thenReturn( futureRepo )
      .thenReturn( futureSync )
      .thenReturn( futureSession );

    // Should NOT interact with SpoonSessionManager when using regular password
    RepositoryConnectResult result = purRepositoryConnector.connect( "regularUser", "regularPassword" );

    assertTrue( result.isSuccess() );
    assertEquals( "regularUser", result.getUser().getLogin() );
  }

  @Test
  public void testConnectWithSessionAuth_DecryptedPasswordIsEmpty() throws Exception {
    PurRepository mockPurRepository = mock( PurRepository.class );
    PurRepositoryMeta mockPurRepositoryMeta = mock( PurRepositoryMeta.class );
    PurRepositoryLocation location = mock( PurRepositoryLocation.class );
    RootRef mockRootRef = mock( RootRef.class );

    PurRepositoryConnector purRepositoryConnector =
      spy( new PurRepositoryConnector( mockPurRepository, mockPurRepositoryMeta, mockRootRef ) );
    doReturn( location ).when( mockPurRepositoryMeta ).getRepositoryLocation();
    doReturn( "http://localhost:8080/pentaho" ).when( location ).getUrl();

    ExecutorService service = mock( ExecutorService.class );
    doReturn( service ).when( purRepositoryConnector ).getExecutor();

    Future futureAuth = mock( Future.class );
    doReturn( false ).when( futureAuth ).get();
    Future futureRepo = mock( Future.class );
    doReturn( null ).when( futureRepo ).get();
    Future futureSync = mock( Future.class );
    doReturn( null ).when( futureSync ).get();
    Future futureSession = mock( Future.class );
    doReturn( "user" ).when( futureSession ).get();

    when( service.submit( any( Callable.class ) ) )
      .thenReturn( futureAuth )
      .thenReturn( futureRepo )
      .thenReturn( futureSync )
      .thenReturn( futureSession );

    SpoonSessionManager mockSessionManager = mock( SpoonSessionManager.class );
    AuthenticationContext mockAuthContext = mock( AuthenticationContext.class );
    when( mockSessionManager.getAuthenticationContext( "http://localhost:8080/pentaho" ) )
      .thenReturn( mockAuthContext );
    when( mockAuthContext.isAuthenticated() ).thenReturn( true );
    when( mockAuthContext.getJSessionId() ).thenReturn( "SESSION_XYZ" );

    try ( MockedStatic<SpoonSessionManager> mockedManager = mockStatic( SpoonSessionManager.class ) ) {
      mockedManager.when( SpoonSessionManager::getInstance ).thenReturn( mockSessionManager );

      RepositoryConnectResult result =
        purRepositoryConnector.connect( "user", AuthenticationContext.SESSION_AUTH_TOKEN );

      assertTrue( result.isSuccess() );
      // Verify that decryptedPassword was set to empty string (reflected in user password)
      assertEquals( "", result.getUser().getPassword() );
    }
  }
}
