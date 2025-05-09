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


package org.pentaho.di.core.database;

import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pentaho.di.core.KettleEnvironment;

/**
 * Tests the value returned from org.pentaho.di.core.database.DatabaseInterface.getSelectCountStatement for the database
 * the interface is fronting.
 *
 * As this release, Hive uses the following to select the number of rows:
 *
 * SELECT COUNT(1) FROM ....
 *
 * All other databases use:
 *
 * SELECT COUNT(*) FROM ....
 */
public class SelectCountIT {

  /**
     *
     */
  private static final String NonHiveSelect = "select count(*) from ";
  private static final String TableName = "NON_EXISTANT";

  public static final String h2DatabaseXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    + "<connection>" + "<name>H2</name>" + "<server>127.0.0.1</server>" + "<type>H2</type>"
    + "<access>Native</access>" + "<database>mem:db</database>" + "<port></port>" + "<username>sa</username>"
    + "<password></password>" + "</connection>";

  public static final String OracleDatabaseXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    + "<connection>" + "<name>Oracle</name>" + "<server>127.0.0.1</server>" + "<type>Oracle</type>"
    + "<access>Native</access>" + "<database>test</database>" + "<port>1024</port>"
    + "<username>scott</username>" + "<password>tiger</password>" + "</connection>";

  public static final String MySQLDatabaseXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    + "<connection>" + "<name>MySQL</name>" + "<server>127.0.0.1</server>" + "<type>MySQL</type>"
    + "<access></access>" + "<database>test</database>" + "<port>3306</port>" + "<username>sa</username>"
    + "<password></password>" + "</connection>";

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    KettleEnvironment.init( false );
  }

  @Test
  public void testH2Database() {
    try {
      String expectedSQL = NonHiveSelect + TableName;
      DatabaseMeta databaseMeta = new DatabaseMeta( h2DatabaseXML );
      String sql = databaseMeta.getDatabaseInterface().getSelectCountStatement( TableName );
      assertTrue( sql.equalsIgnoreCase( expectedSQL ) );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  @Test
  public void testOracleDatabase() {
    try {
      String expectedSQL = NonHiveSelect + TableName;
      DatabaseMeta databaseMeta = new DatabaseMeta( OracleDatabaseXML );
      String sql = databaseMeta.getDatabaseInterface().getSelectCountStatement( TableName );
      assertTrue( sql.equalsIgnoreCase( expectedSQL ) );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  @Test
  public void testMySQLDatabase() {
    try {
      String expectedSQL = NonHiveSelect + TableName;
      DatabaseMeta databaseMeta = new DatabaseMeta( MySQLDatabaseXML );
      String sql = databaseMeta.getDatabaseInterface().getSelectCountStatement( TableName );
      assertTrue( sql.equalsIgnoreCase( expectedSQL ) );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }
}
