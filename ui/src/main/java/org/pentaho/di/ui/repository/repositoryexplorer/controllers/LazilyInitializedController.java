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

import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.pentaho.di.core.logging.LogChannel;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.ui.repository.exception.RepositoryExceptionUtils;
import org.pentaho.di.ui.repository.repositoryexplorer.ControllerInitializationException;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;

public abstract class LazilyInitializedController extends AbstractXulEventHandler {

  private static Class<?> PKG = RepositoryExplorer.class; // for i18n purposes, needed by Translator2!!
  private static final LogChannelInterface log = LogChannel.GENERAL;

  protected Repository repository;

  protected boolean initialized;

  public void init( Repository repository ) throws ControllerInitializationException {
    this.repository = repository;
  }

  protected synchronized void lazyInit() {
    if ( !initialized ) {
      try {
        boolean succeeded = doLazyInit();
        if ( succeeded ) {
          initialized = true;
        } else {
          showErrorDialog( null );
        }
      } catch ( Exception e ) {
        if ( RepositoryExceptionUtils.isSessionExpired( e ) ) {
          handleSessionExpiry( e );
        } else {
          log.logError( "Error during lazy initialization", e );
          showErrorDialog( e );
        }
      }
    }
  }

  /**
   * Handles session expiry by delegating to MainController.
   * If reconnection succeeds, retries doLazyInit() and marks the controller as
   * initialized so subsequent tab clicks do not re-enter the init loop.
   */
  protected void handleSessionExpiry( Exception e ) {
    try {
      MainController mainController = (MainController) this.getXulDomContainer().getEventHandler( "mainController" );
      if ( mainController != null && mainController.handleSessionExpiry( e ) ) {
        // Reconnection succeeded — retry initialization with the fresh session.
        retryLazyInitAfterSessionRecovery();
      }
    } catch ( Exception handleException ) {
      log.logError( "Failed to get MainController for session expiry handling", handleException );
    }
  }

  /**
   * Retries doLazyInit() after session recovery and updates initialized flag.
   */
  private void retryLazyInitAfterSessionRecovery() {
    try {
      if ( doLazyInit() ) {
        initialized = true;
      }
    } catch ( Exception retryEx ) {
      log.logError( "Failed to re-initialize after session recovery", retryEx );
      showErrorDialog( retryEx );
    }
  }

  private void showErrorDialog( final Exception e ) {
    XulMessageBox messageBox = null;
    try {
      messageBox = (XulMessageBox) document.createElement( "messagebox" );
    } catch ( XulException xe ) {
      throw new RuntimeException( xe );
    }
    messageBox.setTitle( BaseMessages.getString( PKG, "Dialog.Error" ) );
    messageBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
    if ( e != null ) {
      messageBox.setMessage( BaseMessages.getString(
        PKG, "LazilyInitializedController.Message.UnableToInitWithParam", e.getLocalizedMessage() ) );
    } else {
      messageBox.setMessage( BaseMessages.getString( PKG, "LazilyInitializedController.Message.UnableToInit" ) );
    }
    messageBox.open();
  }

  protected abstract boolean doLazyInit();

  protected void doWithBusyIndicator( final Runnable r ) {
    BusyIndicator.showWhile( Display.getCurrent() != null ? Display.getCurrent() : Display.getDefault(), r );
  }

  protected void doInEventThread( final Runnable r ) {
    if ( Display.getCurrent() != null ) {
      r.run();
    } else {
      Display.getDefault().syncExec( r );
    }

  }

}
