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

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.core.logging.KettleLogStore;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.repository.KettleRepositoryLostException;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.repository.dialog.RepositoryExplorerDialog;
import org.pentaho.di.ui.repository.exception.RepositoryExceptionUtils;
import org.pentaho.di.ui.repository.repositoryexplorer.RepositoryExplorerCallback;
import org.pentaho.di.ui.spoon.SharedObjectSyncUtil;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.containers.XulDialog;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.stereotype.Bindable;
import org.pentaho.ui.xul.swt.SwtBindingFactory;
import org.pentaho.ui.xul.swt.tags.SwtDialog;
import org.pentaho.ui.xul.util.DialogController;

/**
 *
 * This is the main XulEventHandler for the dialog. It sets up the main bindings for the user interface and responds to
 * some of the main UI events such as closing and accepting the dialog.
 *
 */
public class MainController extends AbstractXulEventHandler implements DialogController<Object> {

  private static Class<?> PKG = RepositoryExplorerDialog.class; // for i18n purposes, needed by Translator2!!
  private static final LogChannelInterface log =
    KettleLogStore.getLogChannelInterfaceFactory().create( MainController.class );

  private RepositoryExplorerCallback callback;

  public static final int CANCELLED = 0;
  public static final int OK = 1;

  private int lastClicked = CANCELLED;

  // private XulButton acceptButton;

  private XulDialog dialog;
  private List<DialogListener<Object>> listeners = new ArrayList<DialogListener<Object>>();

  private Shell shell;

  private Repository repository = null;

  BindingFactory bf;

  private boolean aborting = false;

  /**
   * Prevents re-entrant session-expiry dialogs. If reconnection is already in progress
   * (e.g. a retry lambda fires another 401) this flag causes the second call to return
   * false immediately so no second dialog is shown.
   */
  private final AtomicBoolean isHandlingSessionExpiry = new AtomicBoolean( false );

  private SharedObjectSyncUtil sharedObjectSyncUtil;

  public MainController() {
  }

  public boolean getOkClicked() {
    return lastClicked == OK;
  }

  public void init() {
    bf = new SwtBindingFactory();
    bf.setDocument( this.getXulDomContainer().getDocumentRoot() );
    createBindings();

    if ( dialog != null && repository != null ) {
      dialog.setTitle( BaseMessages.getString( PKG, "RepositoryExplorerDialog.DevTitle", repository.getName() ) );
    }
  }

  public void showDialog() {
    dialog.show();
  }

  private void createBindings() {

    dialog = (XulDialog) document.getElementById( "repository-explorer-dialog" );
    shell = ( (SwtDialog) document.getElementById( "repository-explorer-dialog" ) ).getShell();
    // acceptButton = (XulButton) document.getElementById("repository-explorer-dialog_accept");
  }

  public RepositoryExplorerCallback getCallback() {
    return callback;
  }

  public void setCallback( RepositoryExplorerCallback callback ) {
    this.callback = callback;
  }

  public void setRepository( Repository rep ) {
    this.repository = rep;
  }

  public String getName() {
    return "mainController";
  }

  @Bindable
  public void closeDialog() {
    lastClicked = CANCELLED;
    this.dialog.hide();
    Spoon.getInstance().forceRefreshTree();

    // listeners may remove themselves, old-style iteration
    for ( int i = 0; i < listeners.size(); i++ ) {
      listeners.get( i ).onDialogCancel();
    }
  }

  public void addDialogListener( DialogListener<Object> listener ) {
    if ( listeners.contains( listener ) == false ) {
      listeners.add( listener );
    }
  }

  public void removeDialogListener( DialogListener<Object> listener ) {
    if ( listeners.contains( listener ) ) {
      listeners.remove( listener );
    }
  }

  public void hideDialog() {
    closeDialog();

  }

  private synchronized boolean isAborting() {
    if ( !aborting ) {
      aborting = true;
      return false;
    } else {
      return true;
    }
  }

  public boolean handleLostRepository( Throwable e ) {
    // First check if this is a session expiry - handle it differently
    if ( isSessionExpired( e ) ) {
      return handleSessionExpiry( e );
    }

    KettleRepositoryLostException repLost = KettleRepositoryLostException.lookupStackStrace( e );
    try {
      if ( repLost != null ) {
        if ( !isAborting() ) {
          new ErrorDialog(
                shell,
                BaseMessages.getString( PKG, "RepositoryExplorer.Dialog.Error.Title" ),
                repLost.getPrefaceMessage(),
                repLost );
          if ( callback != null && callback.error( null ) ) {
            closeDialog();
          }
        }

        return true;
      }
    } catch ( Exception ex ) {
      return true;
    }

    return false;
  }

  /**
   * Handles session expiry by prompting for re-authentication
   * Does NOT close the dialog or disconnect repository
   * @param e The session expiry exception
   * @return true if session expiry was handled, false otherwise
   */
  public boolean handleSessionExpiry( Throwable e ) {
    if ( !isSessionExpired( e ) ) {
      return false;
    }
    // If we are already handling a session expiry (e.g. a retry lambda inside retryOrThrow
    // fires another 401) return false so no second dialog is opened.
    if ( !isHandlingSessionExpiry.compareAndSet( false, true ) ) {
      return false;
    }
    try {
      log.logBasic( "Session expired detected: " + e.getMessage() );
      final Spoon spoon = Spoon.getInstance();
      if ( spoon == null ) {
        log.logBasic( "Spoon instance not available, closing dialog" );
        closeDialogIfCallback();
        return true;
      }
      if ( tryReconnectOnUiThread( spoon ) ) {
        onReconnectSuccess( spoon );
      } else {
        log.logBasic( "User cancelled reconnection, closing repository explorer" );
        closeDialogIfCallback();
      }
      return true;
    } catch ( Exception ex ) {
      log.logError( "Error handling session expiry: " + ex.getMessage() );
      return false;
    } finally {
      isHandlingSessionExpiry.set( false );
    }
  }

  private boolean tryReconnectOnUiThread( Spoon spoon ) {
    final boolean[] reconnected = { false };
    shell.getDisplay().syncExec( () -> {
      try {
        reconnected[0] = spoon.handleSessionExpiryWithRelogin();
      } catch ( Exception ex ) {
        log.logError( "Error during re-authentication: " + ex.getMessage() );
      }
    } );
    return reconnected[0];
  }

  private void onReconnectSuccess( Spoon spoon ) {
    log.logBasic( "Successfully reconnected, repository explorer can continue" );
    if ( spoon.getRepository() != null ) {
      log.logBasic( "Updating repository reference in MainController" );
      this.repository = spoon.getRepository();
    }
  }

  private void closeDialogIfCallback() throws Exception {
    if ( callback != null && callback.error( null ) ) {
      closeDialog();
    }
  }

  /**
   * Checks if an exception indicates that the session has expired.
   * Delegates to centralized RepositoryExceptionUtils for consistent exception handling.
   */
  private boolean isSessionExpired( Throwable throwable ) {
    return RepositoryExceptionUtils.isSessionExpired( throwable );
  }

  public SharedObjectSyncUtil getSharedObjectSyncUtil() {
    return sharedObjectSyncUtil;
  }

  public void setSharedObjectSyncUtil( SharedObjectSyncUtil sharedObjectSyncUtil ) {
    this.sharedObjectSyncUtil = sharedObjectSyncUtil;
  }

}
