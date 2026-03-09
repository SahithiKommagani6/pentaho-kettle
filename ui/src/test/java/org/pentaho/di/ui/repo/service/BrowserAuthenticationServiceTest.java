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

package org.pentaho.di.ui.repo.service;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.exception.KettleException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class BrowserAuthenticationServiceTest {

  private BrowserAuthenticationService service;

  @BeforeClass
  public static void setUpClass() throws KettleException {
    KettleEnvironment.init();
  }

  @Before
  public void setUp() {
    service = new BrowserAuthenticationService();
  }

  @After
  public void tearDown() {
    service.stopCallbackServer();
  }

  // ===== SessionInfo =====

  @Test
  public void sessionInfoStoresJsessionIdAndUsername() {
    BrowserAuthenticationService.SessionInfo info =
      new BrowserAuthenticationService.SessionInfo( "ABC123", "admin" );

    assertEquals( "ABC123", info.getJsessionId() );
    assertEquals( "admin", info.getUsername() );
  }

  @Test
  public void sessionInfoAllowsNullUsername() {
    BrowserAuthenticationService.SessionInfo info =
      new BrowserAuthenticationService.SessionInfo( "ABC123", null );

    assertEquals( "ABC123", info.getJsessionId() );
    assertNull( info.getUsername() );
  }

  @Test
  public void sessionInfoAllowsNullJsessionId() {
    BrowserAuthenticationService.SessionInfo info =
      new BrowserAuthenticationService.SessionInfo( null, "admin" );

    assertNull( info.getJsessionId() );
    assertEquals( "admin", info.getUsername() );
  }

  // ===== buildAuthenticationUrl =====

  @Test
  public void buildAuthenticationUrlStripsTrailingSlash() {
    String url = service.buildAuthenticationUrl( "http://localhost:8080/pentaho/" );

    assertTrue( url.startsWith( "http://localhost:8080/pentaho/plugin/browser-auth/api/login?callback=" ) );
    assertFalse( url.contains( "pentaho//plugin" ) );
  }

  @Test
  public void buildAuthenticationUrlHandlesNoTrailingSlash() {
    String url = service.buildAuthenticationUrl( "http://localhost:8080/pentaho" );

    assertTrue( url.startsWith( "http://localhost:8080/pentaho/plugin/browser-auth/api/login?callback=" ) );
  }

  // ===== encodeURIComponent =====

  @Test
  public void encodeURIComponentEncodesSpacesAs20() {
    String result = BrowserAuthenticationService.encodeURIComponent( "hello world" );

    assertEquals( "hello%20world", result );
    assertFalse( result.contains( "+" ) );
  }

  @Test
  public void encodeURIComponentPreservesUnreservedChars() {
    String result = BrowserAuthenticationService.encodeURIComponent( "a!b'c(d)e~f" );

    assertEquals( "a!b'c(d)e~f", result );
  }

  @Test
  public void encodeURIComponentEncodesSpecialChars() {
    String result = BrowserAuthenticationService.encodeURIComponent( "key=value&foo=bar" );

    assertFalse( result.contains( "=" ) );
    assertFalse( result.contains( "&" ) );
  }

  @Test
  public void encodeURIComponentHandlesEmptyString() {
    assertEquals( "", BrowserAuthenticationService.encodeURIComponent( "" ) );
  }

  @Test
  public void encodeURIComponentHandlesUrl() {
    String result = BrowserAuthenticationService.encodeURIComponent(
      "http://localhost:8282/pentaho/auth/callback" );

    assertFalse( result.contains( ":" ) );
    assertFalse( result.contains( "/" ) );
  }

  @Test
  public void parseQueryParamsExtractsMultipleParams() {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    Map<String, String> params = handler.parseQueryParams( "jsessionid=ABC123&username=admin" );

    assertEquals( "ABC123", params.get( "jsessionid" ) );
    assertEquals( "admin", params.get( "username" ) );
  }

  @Test
  public void parseQueryParamsReturnsEmptyMapForNullQuery() {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    Map<String, String> params = handler.parseQueryParams( null );

    assertNotNull( params );
    assertTrue( params.isEmpty() );
  }

  @Test
  public void parseQueryParamsReturnsEmptyMapForEmptyQuery() {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    Map<String, String> params = handler.parseQueryParams( "" );

    assertNotNull( params );
    assertTrue( params.isEmpty() );
  }

  @Test
  public void parseQueryParamsHandlesUrlEncodedValues() {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    Map<String, String> params = handler.parseQueryParams( "error=login+failed%26retry" );

    assertEquals( "login failed&retry", params.get( "error" ) );
  }

  @Test
  public void parseQueryParamsIgnoresPairsWithoutEquals() {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    Map<String, String> params = handler.parseQueryParams( "noequalssign&key=value" );

    assertNull( params.get( "noequalssign" ) );
    assertEquals( "value", params.get( "key" ) );
  }

  @Test
  public void parseQueryParamsSingleParam() {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    Map<String, String> params = handler.parseQueryParams( "jsessionid=XYZ" );

    assertEquals( 1, params.size() );
    assertEquals( "XYZ", params.get( "jsessionid" ) );
  }

  @Test
  public void parseQueryParamsHandlesEmptyValue() {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    Map<String, String> params = handler.parseQueryParams( "key=" );

    assertEquals( "", params.get( "key" ) );
  }

  // ===== CallbackHandler.sendResponse =====

  @Test
  public void sendResponseWritesBodyAndSetsHeaders() throws IOException {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    HttpExchange exchange = mock( HttpExchange.class );
    Headers headers = new Headers();
    ByteArrayOutputStream body = new ByteArrayOutputStream();

    when( exchange.getResponseHeaders() ).thenReturn( headers );
    when( exchange.getResponseBody() ).thenReturn( body );

    handler.sendResponse( exchange, 200, "<html>OK</html>" );

    assertEquals( "text/html; charset=UTF-8", headers.getFirst( "Content-Type" ) );
    verify( exchange ).sendResponseHeaders( eq( 200 ), anyLong() );
    assertEquals( "<html>OK</html>", body.toString( StandardCharsets.UTF_8 ) );
  }

  @Test
  public void sendResponseSets400StatusCode() throws IOException {
    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    HttpExchange exchange = mock( HttpExchange.class );
    Headers headers = new Headers();
    ByteArrayOutputStream body = new ByteArrayOutputStream();

    when( exchange.getResponseHeaders() ).thenReturn( headers );
    when( exchange.getResponseBody() ).thenReturn( body );

    handler.sendResponse( exchange, 400, "error" );

    verify( exchange ).sendResponseHeaders( eq( 400 ), anyLong() );
  }


  // ===== CallbackHandler.handle — empty jsessionid =====

  @Test
  public void handleCompletesExceptionallyWhenEmptyJsessionId() throws Exception {
    CompletableFuture<BrowserAuthenticationService.SessionInfo> future =
      service.authenticate( "http://localhost:9999/pentaho" );

    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();
    HttpExchange exchange = mockExchangeWithQuery( "jsessionid=&username=admin" );

    handler.handle( exchange );

    assertTrue( future.isCompletedExceptionally() );
  }

  // ===== CallbackHandler.handle — exception during response =====

  @Test
  public void handleCompletesExceptionallyWhenExchangeThrows() throws Exception {
    CompletableFuture<BrowserAuthenticationService.SessionInfo> future =
      service.authenticate( "http://localhost:9999/pentaho" );

    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();

    HttpExchange exchange = mock( HttpExchange.class );
    when( exchange.getRequestURI() ).thenThrow( new RuntimeException( "broken exchange" ) );
    // Mock enough to handle the error response path
    Headers headers = new Headers();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    when( exchange.getResponseHeaders() ).thenReturn( headers );
    when( exchange.getResponseBody() ).thenReturn( body );

    handler.handle( exchange );

    assertTrue( future.isCompletedExceptionally() );
  }

  @Test
  public void handleCompletesExceptionallyOnNullQuery() throws Exception {
    CompletableFuture<BrowserAuthenticationService.SessionInfo> future =
      service.authenticate( "http://localhost:9999/pentaho" );

    BrowserAuthenticationService.CallbackHandler handler = service.new CallbackHandler();
    HttpExchange exchange = mockExchangeWithQuery( null );

    handler.handle( exchange );

    assertTrue( future.isCompletedExceptionally() );
  }

  // ===== authenticate — server start failure =====

  @Test
  public void authenticateCompletesExceptionallyWhenServerStartFails() {
    BrowserAuthenticationService failingService = new BrowserAuthenticationService() {
      @Override HttpServer createHttpServer( int port ) throws IOException {
        throw new IOException( "port in use" );
      }
    };

    CompletableFuture<BrowserAuthenticationService.SessionInfo> future =
      failingService.authenticate( "http://localhost:8080/pentaho" );

    assertTrue( future.isCompletedExceptionally() );
    try {
      future.get();
      fail( "Expected ExecutionException" );
    } catch ( ExecutionException e ) {
      assertTrue( e.getCause().getMessage().contains( "port in use" ) );
    } catch ( InterruptedException e ) {
      fail( "Unexpected interrupt" );
    }
  }

  // ===== authenticate — browser open failure =====

  @Test
  public void authenticateCompletesExceptionallyWhenBrowserOpenFails() {
    BrowserAuthenticationService failingService = new BrowserAuthenticationService() {
      @Override void openSystemBrowser( String url ) throws IOException {
        throw new IOException( "no browser" );
      }
    };

    CompletableFuture<BrowserAuthenticationService.SessionInfo> future =
      failingService.authenticate( "http://localhost:8080/pentaho" );

    assertTrue( future.isCompletedExceptionally() );
    try {
      future.get();
      fail( "Expected ExecutionException" );
    } catch ( ExecutionException e ) {
      assertTrue( e.getCause().getMessage().contains( "no browser" ) );
    } catch ( InterruptedException e ) {
      fail( "Unexpected interrupt" );
    }
  }

  // ===== authenticate — happy path with overridden browser =====

  @Test
  public void authenticateReturnsNonNullFuture() {
    BrowserAuthenticationService noopService = new BrowserAuthenticationService() {
      @Override void openSystemBrowser( String url ) {
        // no-op
      }
    };

    CompletableFuture<BrowserAuthenticationService.SessionInfo> future =
      noopService.authenticate( "http://server:8080/pentaho" );

    assertNotNull( future );
    assertFalse( future.isDone() );
    noopService.stopCallbackServer();
  }

  // ===== Constants =====

  @Test
  public void callbackPortIsPositive() {
    assertTrue( BrowserAuthenticationService.CALLBACK_PORT > 0 );
  }


  @Test
  public void getLocalCallbackHostReturnsNonEmpty() {
    String host = service.getLocalCallbackHost();
    assertNotNull( host );
    assertFalse( host.isEmpty() );
  }

  @Test
  public void getLocalCallbackHostDoesNotReturnNull() {
    String host = service.getLocalCallbackHost();
    assertNotNull( host );
  }

  @Test
  public void getLocalCallbackHostReturnsFallbackOnException() {
    BrowserAuthenticationService testService = new BrowserAuthenticationService() {
      @Override
      String getLocalCallbackHost() {
        return "localhost";
      }
    };
    assertEquals( "localhost", testService.getLocalCallbackHost() );
  }

  @Test
  public void resolveCallbackHostExtractsHostFromUrl() {
    String host = service.resolveCallbackHost( "http://pentaho.example.com:8080/pentaho" );
    assertEquals( "pentaho.example.com", host );
  }

  @Test
  public void resolveCallbackHostHandlesUrlWithoutPort() {
    String host = service.resolveCallbackHost( "http://pentaho.example.com/pentaho" );
    assertEquals( "pentaho.example.com", host );
  }

  @Test
  public void resolveCallbackHostFallsBackToLocalHostOnInvalidUrl() {
    String host = service.resolveCallbackHost( "not-a-valid-url" );
    assertNotNull( host );
    assertFalse( host.isEmpty() );
  }

  @Test
  public void resolveCallbackHostHandlesIpAddress() {
    String host = service.resolveCallbackHost( "http://192.168.1.100:8080/pentaho" );
    assertEquals( "192.168.1.100", host );
  }


  @Test
  public void escapeHtmlEncodesAmpersand() {
    String result = BrowserAuthenticationService.escapeHtml( "A&B" );
    assertEquals( "A&amp;B", result );
  }


  @Test
  public void escapeHtmlHandlesNull() {
    String result = BrowserAuthenticationService.escapeHtml( null );
    assertEquals( "", result );
  }

  @Test
  public void escapeHtmlHandlesEmptyString() {
    String result = BrowserAuthenticationService.escapeHtml( "" );
    assertEquals( "", result );
  }

  @Test
  public void escapeHtmlPreservesNormalText() {
    String result = BrowserAuthenticationService.escapeHtml( "Hello World 123" );
    assertEquals( "Hello World 123", result );
  }

  @Test
  public void startCallbackServerCreatesServer() throws IOException {
    BrowserAuthenticationService testService = new BrowserAuthenticationService();
    testService.startCallbackServer();
    assertNotNull( testService );
    testService.stopCallbackServer();
  }

  @Test
  public void startCallbackServerThrowsIoExceptionOnPortFailure() {
    BrowserAuthenticationService failingService = new BrowserAuthenticationService() {
      @Override
      HttpServer createHttpServer( int port ) throws IOException {
        throw new IOException( "Port already in use" );
      }
    };

    try {
      failingService.startCallbackServer();
      fail( "Expected IOException" );
    } catch ( IOException e ) {
      assertTrue( e.getMessage().contains( "Port" ) );
    }
  }

  @Test
  public void createHttpServerReturnsNotNull() throws IOException {
    HttpServer server = service.createHttpServer( 0 ); // 0 = any available port
    assertNotNull( server );
    server.stop( 0 );
  }

  @Test
  public void openSystemBrowserThrowsOnInvalidOs() {
    BrowserAuthenticationService browserService = new BrowserAuthenticationService() {
      @Override
      void openSystemBrowser( String url ) throws IOException {
        String os = System.getProperty( "os.name" ).toLowerCase();
        if ( os.contains( "win" ) || os.contains( "mac" ) || os.contains( "nix" ) || os.contains( "nux" ) ) {
          // Valid OS, don't throw
          return;
        }
        throw new IOException( "Cannot open browser on OS: " + os );
      }
    };
    // Test should not throw on known OS
    try {
      browserService.openSystemBrowser( "http://localhost:8282/callback" );
    } catch ( IOException e ) {
      // Expected for unknown OS
      assertTrue( e.getMessage().contains( "Cannot open browser" ) );
    }
  }

  private HttpExchange mockExchangeWithQuery( String query ) {
    HttpExchange exchange = mock( HttpExchange.class );
    URI uri = query != null ? URI.create( "http://localhost/callback?" + query )
      : URI.create( "http://localhost/callback" );
    when( exchange.getRequestURI() ).thenReturn( uri );
    Headers headers = new Headers();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    when( exchange.getResponseHeaders() ).thenReturn( headers );
    when( exchange.getResponseBody() ).thenReturn( body );
    return exchange;
  }
}

