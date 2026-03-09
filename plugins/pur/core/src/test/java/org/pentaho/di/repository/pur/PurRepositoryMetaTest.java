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

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.simple.JSONObject;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.pentaho.di.repository.RepositoriesMeta;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.junit.Test;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.repository.RepositoryMeta;

@RunWith( MockitoJUnitRunner.class )
public class PurRepositoryMetaTest {

  @Mock
  private RepositoriesMeta repositoriesMeta;

  private static final String URL_WITHOUT_TRAILING = "http://host:0000/pentaho-di";
  private static final String EXAMPLE_RESOURCES =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><repositories><repository><id>PentahoEnterpriseRepository</id><name>pentaho-di</name><description>pentaho-di</description><repository_location_url>http://host:0000/pentaho-di</repository_location_url><version_comment_mandatory>N</version_comment_mandatory></repository><repository><id>PentahoEnterpriseRepository</id><name>pentaho-di</name><description>pentaho-di</description><repository_location_url>http://host:0000/pentaho-di/</repository_location_url><version_comment_mandatory>N</version_comment_mandatory></repository></repositories>";

  /**
   * check URL trailing slash load throw exception when internal test resource unavailable
   * 
   * @throws ParserConfigurationException
   * @throws IOException
   * @throws SAXException
   * @throws KettleException
   * @see <a href="http://jira.pentaho.com/browse/PDI-10401">http://jira.pentaho.com/browse/PDI-10401</a>
   */
  @Test
  public void testURLTralingSlashTolerante() throws ParserConfigurationException, SAXException, KettleException,
    IOException {

    InputStream stream = new ByteArrayInputStream( EXAMPLE_RESOURCES.getBytes( StandardCharsets.UTF_8 ) );
    DocumentBuilder db;
    Document doc;
    db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    doc = db.parse( stream );
    Node repsnode = XMLHandler.getSubNode( doc, "repositories" );
    Node repnode = XMLHandler.getSubNodeByNr( repsnode, RepositoryMeta.XML_TAG, 0 );

    @SuppressWarnings( "unchecked" )
    List<DatabaseMeta> databases = mock( List.class );

    // check with trailing
    PurRepositoryMeta repositoryMeta = new PurRepositoryMeta();
    repositoryMeta.loadXML( repnode, databases );
    assertEquals( repositoryMeta.getRepositoryLocation().getUrl(), URL_WITHOUT_TRAILING );

    // check without trailing
    Node repnode2 = XMLHandler.getSubNodeByNr( repsnode, RepositoryMeta.XML_TAG, 1 );
    repositoryMeta = new PurRepositoryMeta();
    repositoryMeta.loadXML( repnode2, databases );

    assertEquals( repositoryMeta.getRepositoryLocation().getUrl(), URL_WITHOUT_TRAILING );
  }

  @Test
  public void testPopulate() throws Exception {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put( "displayName", "Display Name" );
    properties.put( "url", "URL" );
    properties.put( "description", "Description" );
    properties.put( "isDefault", true );

    PurRepositoryMeta purRepositoryMeta = new PurRepositoryMeta();
    purRepositoryMeta.populate( properties, repositoriesMeta );

    assertEquals( "Display Name", purRepositoryMeta.getName() );
    assertEquals( "URL", purRepositoryMeta.getRepositoryLocation().getUrl() );
    assertEquals( "Description", purRepositoryMeta.getDescription() );
    assertEquals( true, purRepositoryMeta.isDefault() );
  }

  // --- Tests for the newly added authMethod field ---

  @Test
  public void testGetAuthMethod_DefaultsToUsernamePassword_WhenNull() {
    PurRepositoryMeta meta = new PurRepositoryMeta();
    // authMethod is not set, should default to USERNAME_PASSWORD
    assertEquals( "USERNAME_PASSWORD", meta.getAuthMethod() );
  }

  @Test
  public void testSetAndGetAuthMethod() {
    PurRepositoryMeta meta = new PurRepositoryMeta();
    meta.setAuthMethod( "BROWSER" );
    assertEquals( "BROWSER", meta.getAuthMethod() );
  }

  @Test
  public void testSetAuthMethod_Null_DefaultsToUsernamePassword() {
    PurRepositoryMeta meta = new PurRepositoryMeta();
    meta.setAuthMethod( "BROWSER" );
    assertEquals( "BROWSER", meta.getAuthMethod() );
    meta.setAuthMethod( null );
    assertEquals( "USERNAME_PASSWORD", meta.getAuthMethod() );
  }

  @Test
  public void testGetXML_ContainsAuthMethodTag() {
    PurRepositoryMeta meta = new PurRepositoryMeta(
      PurRepositoryMeta.REPOSITORY_TYPE_ID, "testName", "testDesc",
      new PurRepositoryLocation( "http://localhost:8080/pentaho" ), false );
    meta.setAuthMethod( "BROWSER" );

    String xml = meta.getXML();
    assertTrue( "XML should contain auth_method tag", xml.contains( "<auth_method>" ) );
    assertTrue( "XML should contain BROWSER value", xml.contains( "BROWSER" ) );
  }

  @Test
  public void testGetXML_ContainsAuthMethodTag_WhenUsernamePassword() {
    PurRepositoryMeta meta = new PurRepositoryMeta(
      PurRepositoryMeta.REPOSITORY_TYPE_ID, "testName", "testDesc",
      new PurRepositoryLocation( "http://localhost:8080/pentaho" ), false );
    meta.setAuthMethod( "USERNAME_PASSWORD" );

    String xml = meta.getXML();
    assertTrue( "XML should contain auth_method tag", xml.contains( "<auth_method>" ) );
    assertTrue( "XML should contain USERNAME_PASSWORD value", xml.contains( "USERNAME_PASSWORD" ) );
  }

  @Test
  public void testLoadXML_WithAuthMethod() throws Exception {
    String xmlWithAuthMethod =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><repositories>"
        + "<repository><id>PentahoEnterpriseRepository</id>"
        + "<name>test</name><description>test</description>"
        + "<repository_location_url>http://localhost:8080/pentaho</repository_location_url>"
        + "<version_comment_mandatory>N</version_comment_mandatory>"
        + "<auth_method>BROWSER</auth_method>"
        + "</repository></repositories>";

    InputStream stream = new ByteArrayInputStream( xmlWithAuthMethod.getBytes( StandardCharsets.UTF_8 ) );
    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = db.parse( stream );
    Node repsnode = XMLHandler.getSubNode( doc, "repositories" );
    Node repnode = XMLHandler.getSubNodeByNr( repsnode, RepositoryMeta.XML_TAG, 0 );

    @SuppressWarnings( "unchecked" )
    List<DatabaseMeta> databases = mock( List.class );
    PurRepositoryMeta meta = new PurRepositoryMeta();
    meta.loadXML( repnode, databases );

    assertEquals( "BROWSER", meta.getAuthMethod() );
  }

  @Test
  public void testLoadXML_WithoutAuthMethod_DefaultsToUsernamePassword() throws Exception {
    String xmlWithoutAuthMethod =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?><repositories>"
        + "<repository><id>PentahoEnterpriseRepository</id>"
        + "<name>test</name><description>test</description>"
        + "<repository_location_url>http://localhost:8080/pentaho</repository_location_url>"
        + "<version_comment_mandatory>N</version_comment_mandatory>"
        + "</repository></repositories>";

    InputStream stream = new ByteArrayInputStream( xmlWithoutAuthMethod.getBytes( StandardCharsets.UTF_8 ) );
    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = db.parse( stream );
    Node repsnode = XMLHandler.getSubNode( doc, "repositories" );
    Node repnode = XMLHandler.getSubNodeByNr( repsnode, RepositoryMeta.XML_TAG, 0 );

    @SuppressWarnings( "unchecked" )
    List<DatabaseMeta> databases = mock( List.class );
    PurRepositoryMeta meta = new PurRepositoryMeta();
    meta.loadXML( repnode, databases );

    assertEquals( "USERNAME_PASSWORD", meta.getAuthMethod() );
  }

  @Test
  public void testPopulate_WithAuthMethod() {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put( "displayName", "Test" );
    properties.put( "url", "http://localhost:8080/pentaho" );
    properties.put( "description", "Description" );
    properties.put( "isDefault", false );
    properties.put( PurRepositoryMeta.AUTH_METHOD, "BROWSER" );

    PurRepositoryMeta meta = new PurRepositoryMeta();
    meta.populate( properties, repositoriesMeta );

    assertEquals( "BROWSER", meta.getAuthMethod() );
  }

  @Test
  public void testPopulate_WithoutAuthMethod_DefaultsToUsernamePassword() {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put( "displayName", "Test" );
    properties.put( "url", "http://localhost:8080/pentaho" );
    properties.put( "description", "Description" );
    properties.put( "isDefault", false );
    // authMethod not set in properties

    PurRepositoryMeta meta = new PurRepositoryMeta();
    meta.populate( properties, repositoriesMeta );

    assertEquals( "USERNAME_PASSWORD", meta.getAuthMethod() );
  }

  @Test
  public void testPopulate_WithNullAuthMethod_DefaultsToUsernamePassword() {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put( "displayName", "Test" );
    properties.put( "url", "http://localhost:8080/pentaho" );
    properties.put( "description", "Description" );
    properties.put( "isDefault", false );
    properties.put( PurRepositoryMeta.AUTH_METHOD, null );

    PurRepositoryMeta meta = new PurRepositoryMeta();
    meta.populate( properties, repositoriesMeta );

    assertEquals( "USERNAME_PASSWORD", meta.getAuthMethod() );
  }

  @Test
  public void testToJSONObject_ContainsAuthMethod() {
    PurRepositoryMeta meta = new PurRepositoryMeta(
      PurRepositoryMeta.REPOSITORY_TYPE_ID, "testName", "testDesc",
      new PurRepositoryLocation( "http://localhost:8080/pentaho" ), false );
    meta.setAuthMethod( "BROWSER" );

    JSONObject json = meta.toJSONObject();
    assertEquals( "BROWSER", json.get( PurRepositoryMeta.AUTH_METHOD ) );
  }

  @Test
  public void testToJSONObject_ContainsUrl() {
    PurRepositoryMeta meta = new PurRepositoryMeta(
      PurRepositoryMeta.REPOSITORY_TYPE_ID, "testName", "testDesc",
      new PurRepositoryLocation( "http://localhost:8080/pentaho" ), false );
    meta.setAuthMethod( "USERNAME_PASSWORD" );

    JSONObject json = meta.toJSONObject();
    assertEquals( "http://localhost:8080/pentaho", json.get( PurRepositoryMeta.URL ) );
    assertEquals( "USERNAME_PASSWORD", json.get( PurRepositoryMeta.AUTH_METHOD ) );
  }

  @Test
  public void testToJSONObject_DefaultAuthMethod_WhenNotSet() {
    PurRepositoryMeta meta = new PurRepositoryMeta(
      PurRepositoryMeta.REPOSITORY_TYPE_ID, "testName", "testDesc",
      new PurRepositoryLocation( "http://localhost:8080/pentaho" ), false );
    // authMethod not set explicitly

    JSONObject json = meta.toJSONObject();
    assertEquals( "USERNAME_PASSWORD", json.get( PurRepositoryMeta.AUTH_METHOD ) );
  }

  @Test
  public void testAuthMethodConstant() {
    assertEquals( "authMethod", PurRepositoryMeta.AUTH_METHOD );
  }

  @Test
  public void testGetXML_RoundTrip_PreservesAuthMethod() throws Exception {
    // Create meta with BROWSER auth method
    PurRepositoryMeta original = new PurRepositoryMeta(
      PurRepositoryMeta.REPOSITORY_TYPE_ID, "testName", "testDesc",
      new PurRepositoryLocation( "http://localhost:8080/pentaho" ), true );
    original.setAuthMethod( "BROWSER" );

    // Serialize to XML
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><repositories>" + original.getXML() + "</repositories>";

    // Parse back
    InputStream stream = new ByteArrayInputStream( xml.getBytes( StandardCharsets.UTF_8 ) );
    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = db.parse( stream );
    Node repsnode = XMLHandler.getSubNode( doc, "repositories" );
    Node repnode = XMLHandler.getSubNodeByNr( repsnode, RepositoryMeta.XML_TAG, 0 );

    @SuppressWarnings( "unchecked" )
    List<DatabaseMeta> databases = mock( List.class );
    PurRepositoryMeta loaded = new PurRepositoryMeta();
    loaded.loadXML( repnode, databases );

    assertEquals( "BROWSER", loaded.getAuthMethod() );
    assertEquals( "http://localhost:8080/pentaho", loaded.getRepositoryLocation().getUrl() );
    assertTrue( loaded.isVersionCommentMandatory() );
  }
}
