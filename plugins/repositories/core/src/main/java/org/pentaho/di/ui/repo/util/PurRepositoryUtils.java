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

package org.pentaho.di.ui.repo.util;

import org.pentaho.di.core.logging.KettleLogStore;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.repository.RepositoryMeta;

import java.lang.reflect.Method;

/**
 * Utility class for PUR (Pentaho Unified Repository) operations.
 * Provides common methods for checking repository types and extracting URLs.
 */
public class PurRepositoryUtils {

  private static final LogChannelInterface log =
    KettleLogStore.getLogChannelInterfaceFactory().create( PurRepositoryUtils.class );

  private static final String PUR_REPOSITORY_ID = "PentahoEnterpriseRepository";
  private static final String AUTH_METHOD_USERNAME_PASSWORD = "USERNAME_PASSWORD";

  /**
   * Private constructor to hide the implicit public one.
   * This is a utility class with only static methods.
   */
  private PurRepositoryUtils() {
    // Utility class, should not be instantiated
  }

  /**
   * Check if repository is a PUR repository that supports browser authentication
   *
   * @param repositoryMeta Repository metadata to check
   * @return true if this is a PUR repository
   */
  public static boolean isPurRepository( RepositoryMeta repositoryMeta ) {
    return repositoryMeta != null &&
      PUR_REPOSITORY_ID.equals( repositoryMeta.getId() );
  }

  /**
   * Extract server URL from PUR repository metadata using reflection
   *
   * @param repositoryMeta Repository metadata containing the URL
   * @return Server URL string, or null if not available
   */
  public static String getServerUrl( RepositoryMeta repositoryMeta ) {
    try {
      if ( repositoryMeta == null ) {
        return null;
      }

      // Use reflection to get URL from PurRepositoryMeta
      java.lang.reflect.Method getRepositoryLocation =
        repositoryMeta.getClass().getMethod( "getRepositoryLocation" );
      Object location = getRepositoryLocation.invoke( repositoryMeta );

      if ( location != null ) {
        java.lang.reflect.Method getUrl = location.getClass().getMethod( "getUrl" );
        return (String) getUrl.invoke( location );
      }
    } catch ( Exception e ) {
      log.logDebug( "Failed to extract repository URL", e );
    }
    return null;
  }

  /**
   * Check if repository has a valid server URL configured
   *
   * @param repositoryMeta Repository metadata to check
   * @return true if a non-empty URL is configured
   */
  public static boolean hasServerUrl( RepositoryMeta repositoryMeta ) {
    String url = getServerUrl( repositoryMeta );
    return url != null && !url.trim().isEmpty();
  }

  /**
   * Check if repository supports browser authentication.
   * Requires the repository to be a PUR repository, have a configured server URL,
   * and have its authentication method set to {@code "SSO"}.
   *
   * @param repositoryMeta Repository metadata to check
   * @return true if browser (SSO) authentication is supported
   */
  public static boolean supportsBrowserAuth( RepositoryMeta repositoryMeta ) {
    return isPurRepository( repositoryMeta )
      && hasServerUrl( repositoryMeta )
      && "SSO".equals( getAuthMethod( repositoryMeta ) );
  }

  /**
   * Extract the authentication method from PUR repository metadata using reflection.
   * Returns {@code "USERNAME_PASSWORD"} for any non-PUR repository or when the
   * {@code getAuthMethod} method is not present on the metadata object.
   *
   * @param repositoryMeta Repository metadata to inspect
   * @return the auth-method string (e.g. {@code "SSO"}, {@code "USERNAME_PASSWORD"})
   */
  public static String getAuthMethod( RepositoryMeta repositoryMeta ) {
    if ( !isPurRepository( repositoryMeta ) ) {
      return AUTH_METHOD_USERNAME_PASSWORD;
    }
    try {
      Method m = repositoryMeta.getClass().getMethod( "getAuthMethod" );
      String authMethod = (String) m.invoke( repositoryMeta );
      return authMethod != null ? authMethod : AUTH_METHOD_USERNAME_PASSWORD;
    } catch ( Exception e ) {
      log.logDebug( "Failed to get authMethod from repository metadata", e );
    }
    return AUTH_METHOD_USERNAME_PASSWORD;
  }
}

