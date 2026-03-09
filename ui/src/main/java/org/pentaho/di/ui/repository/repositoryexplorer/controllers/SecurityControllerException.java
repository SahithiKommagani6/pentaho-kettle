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

/**
 * Dedicated unchecked exception for {@link SecurityController} failures,
 * replacing generic {@link RuntimeException} usage.
 */
public class SecurityControllerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SecurityControllerException( final String message ) {
    super( message );
  }

  public SecurityControllerException( final Throwable cause ) {
    super( cause );
  }

}

