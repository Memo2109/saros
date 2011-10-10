/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universitaet Berlin - Fachbereich Mathematik und Informatik - 2006
 * (c) Riad Djemili - 2006
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 1, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package de.fu_berlin.inf.dpp.project;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.progress.IProgressConstants;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.XMPPException;
import org.joda.time.DateTime;
import org.picocontainer.annotations.Inject;
import org.picocontainer.annotations.Nullable;

import de.fu_berlin.inf.dpp.FileList;
import de.fu_berlin.inf.dpp.FileListFactory;
import de.fu_berlin.inf.dpp.Saros;
import de.fu_berlin.inf.dpp.SarosContext;
import de.fu_berlin.inf.dpp.User;
import de.fu_berlin.inf.dpp.activities.ProjectExchangeInfo;
import de.fu_berlin.inf.dpp.annotations.Component;
import de.fu_berlin.inf.dpp.communication.muc.negotiation.MUCSessionPreferences;
import de.fu_berlin.inf.dpp.communication.muc.negotiation.MUCSessionPreferencesNegotiatingManager;
import de.fu_berlin.inf.dpp.editor.internal.EditorAPI;
import de.fu_berlin.inf.dpp.exceptions.LocalCancellationException;
import de.fu_berlin.inf.dpp.exceptions.RemoteCancellationException;
import de.fu_berlin.inf.dpp.invitation.IncomingProjectNegotiation;
import de.fu_berlin.inf.dpp.invitation.IncomingSessionNegotiation;
import de.fu_berlin.inf.dpp.invitation.OutgoingProjectNegotiation;
import de.fu_berlin.inf.dpp.invitation.OutgoingSessionNegotiation;
import de.fu_berlin.inf.dpp.invitation.ProcessTools.CancelOption;
import de.fu_berlin.inf.dpp.net.ConnectionState;
import de.fu_berlin.inf.dpp.net.IConnectionListener;
import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.RosterTracker;
import de.fu_berlin.inf.dpp.net.business.DispatchThreadContext;
import de.fu_berlin.inf.dpp.net.internal.XMPPTransmitter;
import de.fu_berlin.inf.dpp.net.internal.discoveryManager.DiscoveryManager;
import de.fu_berlin.inf.dpp.observables.InvitationProcessObservable;
import de.fu_berlin.inf.dpp.observables.ProjectNegotiationObservable;
import de.fu_berlin.inf.dpp.observables.SarosSessionObservable;
import de.fu_berlin.inf.dpp.observables.SessionIDObservable;
import de.fu_berlin.inf.dpp.preferences.PreferenceConstants;
import de.fu_berlin.inf.dpp.preferences.PreferenceManager;
import de.fu_berlin.inf.dpp.project.internal.SarosSession;
import de.fu_berlin.inf.dpp.synchronize.StopManager;
import de.fu_berlin.inf.dpp.ui.ImageManager;
import de.fu_berlin.inf.dpp.ui.SarosUI;
import de.fu_berlin.inf.dpp.ui.views.SarosView;
import de.fu_berlin.inf.dpp.ui.wizards.InvitationWizard;
import de.fu_berlin.inf.dpp.util.StackTrace;
import de.fu_berlin.inf.dpp.util.Utils;
import de.fu_berlin.inf.dpp.util.VersionManager;
import de.fu_berlin.inf.dpp.util.VersionManager.VersionInfo;

/**
 * The SessionManager is responsible for initiating new Saros sessions and for
 * reacting to invitations. The user can be only part of one session at most.
 * 
 * @author rdjemili
 */
@Component(module = "core")
public class SarosSessionManager implements IConnectionListener,
    ISarosSessionManager {

    private static Logger log = Logger.getLogger(SarosSessionManager.class
        .getName());

    @Inject
    protected SarosSessionObservable sarosSessionObservable;

    @Inject
    protected DiscoveryManager discoveryManager;

    @Inject
    protected XMPPTransmitter transmitter;

    @Inject
    protected SessionIDObservable sessionID;

    @Inject
    // FIXME dependency of OIP
    protected StopManager stopManager;

    @Inject
    // FIXME dependency of other classes
    protected InvitationProcessObservable invitationProcesses;

    @Inject
    // FIXME dependency of other classes
    protected VersionManager versionManager;

    @Inject
    protected MUCSessionPreferencesNegotiatingManager comNegotiatingManager;

    @Inject
    // FIXME dependency of other class
    protected RosterTracker rosterTracker;

    @Inject
    // FIXME dependency of other class
    protected DispatchThreadContext dispatchThreadContext;

    @Inject
    protected ProjectNegotiationObservable projectExchangeProcesses;

    @Inject
    protected SarosContext sarosContext;

    private final List<ISarosSessionListener> sarosSessionListeners = new CopyOnWriteArrayList<ISarosSessionListener>();

    protected Saros saros;

    /**
     * Should invitations send the project archive via StreamSession?
     */
    protected IPreferenceStore prefStore;

    protected boolean doStreamingInvitation = false;

    public SarosSessionManager(Saros saros) {
        this.saros = saros;
        this.prefStore = saros.getPreferenceStore();
        saros.getSarosNet().addListener(this);
    }

    protected static final Random sessionRandom = new Random();

    /**
     * @JTourBusStop 3, Invitation Process:
     * 
     *               This class manages the current Saros session.
     * 
     *               Saros makes a distinction between a session and a shared
     *               project. A session is an on-line collaboration between
     *               users which allows users to carry out activities. The main
     *               activity is to share projects. Hence, before you share a
     *               project, a session has to be started and all users added to
     *               it.
     * 
     *               (At the moment, this separation is invisible to the user.
     *               He/she must share a project in order to start a session.)
     * 
     */
    public void startSession(
        HashMap<IProject, List<IResource>> projectResourcesMapping)
        throws XMPPException {
        if (!saros.getSarosNet().isConnected()) {
            throw new XMPPException(Messages.SarosSessionManager_no_connection);
        }

        this.sessionID.setValue(String.valueOf(sessionRandom
            .nextInt(Integer.MAX_VALUE)));

        SarosSession sarosSession = new SarosSession(this.transmitter,
            dispatchThreadContext, new DateTime(), sarosContext);

        this.sarosSessionObservable.setValue(sarosSession);

        notifySarosSessionStarting(sarosSession);
        sarosSession.start();
        notifySarosSessionStarted(sarosSession);

        for (Entry<IProject, List<IResource>> mapEntry : projectResourcesMapping
            .entrySet()) {
            IProject iProject = mapEntry.getKey();
            List<IResource> resourcesList = mapEntry.getValue();
            if (!iProject.isOpen()) {
                try {
                    iProject.open(null);
                } catch (CoreException e1) {
                    log.debug("An error occur while opening project", e1); //$NON-NLS-1$
                    continue;
                }
            }
            String projectID = String.valueOf(sessionRandom
                .nextInt(Integer.MAX_VALUE));
            sarosSession.addSharedResources(iProject, projectID, resourcesList);
            notifyProjectAdded(iProject);
        }

        SarosSessionManager.log.info("Session started"); //$NON-NLS-1$
    }

    /**
     * {@inheritDoc}
     */
    public ISarosSession joinSession(JID host, int colorID,
        DateTime sessionStart) {

        SarosSession sarosSession = new SarosSession(transmitter,
            dispatchThreadContext, host, colorID, sessionStart, sarosContext);

        this.sarosSessionObservable.setValue(sarosSession);

        log.info("Saros session joined"); //$NON-NLS-1$

        return sarosSession;
    }

    /**
     * Used to make stopSharedProject reentrant
     */
    private Lock stopSharedProjectLock = new ReentrantLock();

    /**
     * @nonSWT
     */
    public void stopSarosSession() {

        if (Utils.isSWT()) {
            log.warn("StopSharedProject should not be called from SWT", //$NON-NLS-1$
                new StackTrace());
        }

        if (!stopSharedProjectLock.tryLock()) {
            log.debug("stopSharedProject() couldn't acquire " //$NON-NLS-1$
                + "stopSharedProjectLock."); //$NON-NLS-1$
            return;
        }

        try {
            SarosSession sarosSession = (SarosSession) sarosSessionObservable
                .getValue();

            if (sarosSession == null) {
                return;
            }

            notifySessionEnding(sarosSession);

            this.transmitter.sendLeaveMessage(sarosSession);
            log.debug("Leave message sent."); //$NON-NLS-1$
            if (!sarosSession.isStopped()) {
                try {
                    sarosSession.stop();
                } catch (RuntimeException e) {
                    log.error("Error stopping project: ", e); //$NON-NLS-1$
                }
            }
            sarosSession.dispose();

            this.sarosSessionObservable.setValue(null);

            notifySessionEnd(sarosSession);

            clearSessionID();
            log.info("Session left"); //$NON-NLS-1$
        } finally {
            stopSharedProjectLock.unlock();
        }
    }

    public void clearSessionID() {
        sessionID.setValue(SessionIDObservable.NOT_IN_SESSION);
    }

    public ISarosSession getSarosSession() {
        return this.sarosSessionObservable.getValue();
    }

    public void invitationReceived(JID from, String sessionID, int colorID,
        VersionInfo versionInfo, DateTime sessionStart, final SarosUI sarosUI,
        String invitationID, MUCSessionPreferences comPrefs, String description) {

        this.sessionID.setValue(sessionID);

        final IncomingSessionNegotiation process = new IncomingSessionNegotiation(
            this, transmitter, from, colorID, invitationProcesses,
            versionManager, versionInfo, sessionStart, sarosUI, invitationID,
            saros, description, sarosContext);
        comNegotiatingManager.setSessionPreferences(comPrefs);

        Utils.runSafeSWTAsync(log, new Runnable() {
            public void run() {
                process.acknowledgeInvitation();
                sarosUI.showIncomingInvitationUI(process);
                sarosUI.openSarosView();
            }
        });
    }

    /**
     * This method is called when a new project was added to the session
     * 
     * @param from
     *            The one who added the project.
     * @param projectInfos
     *            what projects where added ({@link FileList}, projectName etc.)
     *            see: {@link ProjectExchangeInfo}
     * @param processID
     *            ID of the exchanging process
     * @param doStream
     *            If <code>true</code>, the files of the projects will be
     *            streamed.
     */
    public void incomingProjectReceived(JID from, final SarosUI sarosUI,
        List<ProjectExchangeInfo> projectInfos, String processID,
        boolean doStream) {
        final IncomingProjectNegotiation process = new IncomingProjectNegotiation(
            from, processID, projectInfos, doStream, sarosContext);

        Utils.runSafeSWTAsync(log, new Runnable() {

            public void run() {
                sarosUI.showIncomingProjectUI(process);

            }

        });
    }

    public void connectionStateChanged(Connection connection,
        ConnectionState newState) {

        if (newState == ConnectionState.DISCONNECTING) {
            stopSarosSession();
        }
    }

    public void onReconnect(Map<JID, Integer> expectedSequenceNumbers) {

        ISarosSession sarosSession = sarosSessionObservable.getValue();

        if (sarosSession == null) {
            return;
        }

        this.transmitter.sendRemainingFiles();
        this.transmitter.sendRemainingMessages();

        /*
         * ask for next expected activityDataObjects (in case I missed something
         * while being not available)
         */

        // TODO this is currently disabled
        this.transmitter.sendRequestForActivity(sarosSession,
            expectedSequenceNumbers, true);
    }

    public void openInviteDialog(final @Nullable List<JID> toInvite) {
        final ISarosSession sarosSession = sarosSessionObservable.getValue();

        Utils.runSafeSWTAsync(log, new Runnable() {
            public void run() {
                // Instantiates and initializes the wizard
                InvitationWizard wizard = new InvitationWizard(saros
                    .getSarosNet(), sarosSession, rosterTracker,
                    discoveryManager, SarosSessionManager.this, versionManager,
                    invitationProcesses);

                // Instantiates the wizard container with the wizard and opens
                // it
                Shell dialogShell = EditorAPI.getShell();
                if (dialogShell == null)
                    dialogShell = new Shell();
                WizardDialog dialog = new WizardDialog(dialogShell, wizard);
                dialog.create();
                dialog.open();
            }
        });

    }

    /**
     * Invites a user to the shared project.
     * 
     * @param toInvite
     *            the JID of the user that is to be invited.
     */
    public void invite(JID toInvite, String description) {
        ISarosSession sarosSession = sarosSessionObservable.getValue();

        OutgoingSessionNegotiation result = new OutgoingSessionNegotiation(
            toInvite, sarosSession.getFreeColor(), sarosSession, description,
            sarosContext);

        OutgoingInvitationJob outgoingInvitationJob = new OutgoingInvitationJob(
            result);
        outgoingInvitationJob.setPriority(Job.SHORT);
        outgoingInvitationJob.schedule();
    }

    public void invite(Collection<JID> jidsToInvite, String description) {
        for (JID jid : jidsToInvite)
            invite(jid, description);
    }

    /**
     * 
     * OutgoingInvitationJob wraps the instance of
     * {@link OutgoingSessionNegotiation} and cares about handling the
     * exceptions like local or remote cancellation.
     * 
     * It notifies the user about the progress using the Eclipse Jobs API and
     * interrupts the process if the session closes.
     * 
     */
    protected class OutgoingInvitationJob extends Job {

        protected OutgoingSessionNegotiation process;
        protected String peer;
        protected ISarosSessionListener cancelListener = new AbstractSarosSessionListener() {

            @Override
            public void sessionEnded(ISarosSession oldSharedProject) {
                process.localCancel(null, CancelOption.NOTIFY_PEER);
            }

        };

        public OutgoingInvitationJob(OutgoingSessionNegotiation process) {
            super(MessageFormat.format(
                Messages.SarosSessionManager_inviting_user, process.getPeer()
                    .getBase()));
            this.process = process;
            this.peer = process.getPeer().getBase();
            this.setUser(true);
            setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
            setProperty(IProgressConstants.ICON_PROPERTY,
                ImageManager
                    .getImageDescriptor("/icons/elcl16/project_share_tsk.png")); //$NON-NLS-1$
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            try {

                registerCancelListener();
                process.start(SubMonitor.convert(monitor));

            } catch (LocalCancellationException e) {

                return Status.CANCEL_STATUS;

            } catch (RemoteCancellationException e) {

                if (e.getMessage() == null) { // buddy canceled purposely

                    SarosView
                        .showNotification(
                            Messages.SarosSessionManager_canceled_invitation,
                            MessageFormat
                                .format(
                                    Messages.SarosSessionManager_canceled_invitation_text,
                                    peer));

                    return new Status(
                        IStatus.CANCEL,
                        Saros.SAROS,
                        MessageFormat
                            .format(
                                Messages.SarosSessionManager_canceled_invitation_text,
                                peer));

                } else {

                    SarosView
                        .showNotification(
                            Messages.SarosSessionManager_error_during_invitation,
                            MessageFormat
                                .format(
                                    Messages.SarosSessionManager_error_during_invitation_text,
                                    peer, e.getMessage()));

                    return new Status(
                        IStatus.ERROR,
                        Saros.SAROS,
                        MessageFormat
                            .format(
                                Messages.SarosSessionManager_error_during_invitation_text2,
                                peer, e.getMessage()));
                }

            } catch (Exception e) {

                log.error("This exception is not expected here: ", e); //$NON-NLS-1$
                return new Status(IStatus.ERROR, Saros.SAROS, e.getMessage(), e);

            } finally {

                releaseCancelListener();

            }

            return Status.OK_STATUS;
        }

        protected void registerCancelListener() {
            Utils.runSafeSWTSync(log, new Runnable() {

                public void run() {
                    SarosSessionManager.this
                        .addSarosSessionListener(cancelListener);
                }

            });
        }

        protected void releaseCancelListener() {
            Utils.runSafeSWTSync(log, new Runnable() {

                public void run() {
                    SarosSessionManager.this
                        .removeSarosSessionListener(cancelListener);
                }

            });
        }
    }

    /**
     * Adds project resources to an existing session.
     * 
     * @param projectResourcesMapping
     * 
     */
    public void addResourcesToSession(
        HashMap<IProject, List<IResource>> projectResourcesMapping) {
        for (Entry<IProject, List<IResource>> mapEntry : projectResourcesMapping
            .entrySet()) {
            IProject iProject = mapEntry.getKey();
            List<IResource> resourcesList = mapEntry.getValue();
            if (!iProject.isOpen()) {
                try {
                    iProject.open(null);
                } catch (CoreException e1) {
                    log.debug("An error occur while opening project", e1); //$NON-NLS-1$
                    continue;
                }
            }

            if (!this.getSarosSession().isCompletelyShared(iProject)) {
                String projectID = String.valueOf(sessionRandom
                    .nextInt(Integer.MAX_VALUE));
                this.getSarosSession().addSharedResources(iProject, projectID,
                    resourcesList);
                notifyProjectAdded(iProject);
            }
        }
        boolean doStream = prefStore
            .getBoolean(PreferenceConstants.STREAM_PROJECT);

        for (User user : this.getSarosSession().getRemoteUsers()) {
            OutgoingProjectNegotiation out = new OutgoingProjectNegotiation(
                user.getJID(), this.getSarosSession(), projectResourcesMapping,
                doStream, sarosContext, null);

            OutgoingProjectJob job = new OutgoingProjectJob(out);
            job.setPriority(Job.SHORT);
            job.schedule();
        }
    }

    /**
     * Will start sharing all projects in session with a participant. This
     * should be called after a the invitation to a session was completed
     * successfully.
     * 
     * @param user
     *            JID of session participant to share projects with
     * @param projectExchangeInfos
     *            List of ProjectExchangeInfo containing the project dependent
     *            FileList
     */
    public void startSharingProjects(JID user,
        List<ProjectExchangeInfo> projectExchangeInfos) {
        boolean doStream = prefStore
            .getBoolean(PreferenceConstants.STREAM_PROJECT);
        HashMap<IProject, List<IResource>> projectResourcesMapping = this
            .getSarosSession().getProjectResourcesMapping();

        if (!projectResourcesMapping.isEmpty()
            && !projectExchangeInfos.isEmpty()) {
            OutgoingProjectNegotiation out = new OutgoingProjectNegotiation(
                user, this.getSarosSession(), projectResourcesMapping,
                doStream, sarosContext, projectExchangeInfos);
            OutgoingProjectJob job = new OutgoingProjectJob(out);
            job.setPriority(Job.SHORT);
            job.schedule();
        }

    }

    /**
     * Method to create list of ProjectExchangeInfo.
     * 
     * @param projectsToShare
     *            List of projects initially to share
     * @param subMonitor
     *            Show progress
     * @return
     * @throws LocalCancellationException
     */
    public List<ProjectExchangeInfo> createProjectExchangeInfoList(
        List<IProject> projectsToShare, SubMonitor subMonitor)
        throws LocalCancellationException {
        subMonitor.setTaskName(Messages.SarosSessionManager_creating_file_list);
        subMonitor.setWorkRemaining(100);
        List<ProjectExchangeInfo> pInfos = new ArrayList<ProjectExchangeInfo>(
            projectsToShare.size());
        for (IProject iProject : projectsToShare) {
            if (subMonitor.isCanceled())
                throw new LocalCancellationException(null,
                    CancelOption.DO_NOT_NOTIFY_PEER);
            try {
                String projectID = this.getSarosSession()
                    .getProjectID(iProject);
                String projectName = iProject.getName();
                FileList projectFileList = FileListFactory.createFileList(
                    iProject,
                    this.getSarosSession().getSharedResources(iProject), this
                        .getSarosSession().useVersionControl(), subMonitor
                        .newChild(0));
                projectFileList.setProjectID(projectID);
                boolean partial = !this.getSarosSession().isCompletelyShared(
                    iProject);

                ProjectExchangeInfo pInfo = new ProjectExchangeInfo(projectID,
                    "", projectName, partial, projectFileList); //$NON-NLS-1$
                pInfos.add(pInfo);
            } catch (CoreException e) {
                throw new LocalCancellationException(e.getMessage(),
                    CancelOption.DO_NOT_NOTIFY_PEER);
            }
            subMonitor.worked(100 / projectsToShare.size());
        }
        subMonitor.subTask(""); //$NON-NLS-1$
        subMonitor.done();
        return pInfos;
    }

    protected class OutgoingProjectJob extends Job {

        protected OutgoingProjectNegotiation process;
        protected String peer;
        protected ISarosSessionListener cancelListener = new AbstractSarosSessionListener() {

            @Override
            public void sessionEnded(ISarosSession oldSharedProject) {
                process.localCancel(null, CancelOption.NOTIFY_PEER);
            }

        };

        public OutgoingProjectJob(
            OutgoingProjectNegotiation outgoingProjectNegotiation) {
            super(MessageFormat.format(
                Messages.SarosSessionManager_sharing_project,
                outgoingProjectNegotiation.getProjectNames()));
            this.process = outgoingProjectNegotiation;
            this.peer = process.getPeer().getBase();
            this.setUser(true);
            setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
            setProperty(IProgressConstants.ICON_PROPERTY,
                ImageManager.getImageDescriptor("/icons/invites.png")); //$NON-NLS-1$
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            try {

                registerCancelListener();
                process.start(SubMonitor.convert(monitor));

            } catch (LocalCancellationException e) {

                return Status.CANCEL_STATUS;

            } catch (RemoteCancellationException e) {

                if (e.getMessage() == null) { // remote user canceled purposely
                    String message = MessageFormat
                        .format(
                            Messages.SarosSessionManager_project_sharing_cancelled_text,
                            peer);

                    SarosView.showNotification(
                        Messages.SarosSessionManager_project_sharing_cancelled,
                        message);

                    return new Status(IStatus.ERROR, Saros.SAROS, message);

                } else {
                    String message = MessageFormat
                        .format(
                            Messages.SarosSessionManager_sharing_project_cancelled_remotely,
                            peer, e.getMessage());
                    SarosView
                        .showNotification(
                            Messages.SarosSessionManager_sharing_project_cancelled_remotely_text,
                            message);

                    return new Status(IStatus.ERROR, Saros.SAROS, message);
                }

            } catch (Exception e) {

                log.error("This exception is not expected here: ", e); //$NON-NLS-1$
                return new Status(IStatus.ERROR, Saros.SAROS, e.getMessage(), e);

            } finally {

                releaseCancelListener();

            }

            return Status.OK_STATUS;
        }

        protected void registerCancelListener() {
            Utils.runSafeSWTSync(log, new Runnable() {

                public void run() {
                    SarosSessionManager.this
                        .addSarosSessionListener(cancelListener);
                }

            });
        }

        protected void releaseCancelListener() {
            Utils.runSafeSWTSync(log, new Runnable() {

                public void run() {
                    SarosSessionManager.this
                        .removeSarosSessionListener(cancelListener);
                }

            });
        }

    }

    public void addSarosSessionListener(ISarosSessionListener listener) {
        if (!this.sarosSessionListeners.contains(listener)) {
            /*
             * HACK PreferencesManager relies on the fact that a project is
             * added only when a session is started, and it might create a new
             * file ".settings/org.eclipse.core.resources.prefs" for the project
             * specific settings. Adding PreferencesManager as the last listener
             * makes sure that the file creation is registered by the
             * SharedResourcesManager.
             */
            if (listener instanceof PreferenceManager) {
                this.sarosSessionListeners.add(listener);
            } else {
                this.sarosSessionListeners.add(0, listener);
            }
        }
    }

    public void removeSarosSessionListener(ISarosSessionListener listener) {
        this.sarosSessionListeners.remove(listener);
    }

    public void notifyPreIncomingInvitationCompleted(SubMonitor subMonitor) {
        try {
            for (ISarosSessionListener sarosSessionListener : this.sarosSessionListeners) {
                sarosSessionListener.preIncomingInvitationCompleted(subMonitor);
            }
        } catch (RuntimeException e) {
            log.error("Internal error in notifying listener" //$NON-NLS-1$
                + " of an incoming invitation: ", e); //$NON-NLS-1$
        }
    }

    public void notifyPostOutgoingInvitationCompleted(SubMonitor subMonitor,
        User user) {
        try {
            for (ISarosSessionListener sarosSessionListener : this.sarosSessionListeners) {
                sarosSessionListener.postOutgoingInvitationCompleted(
                    subMonitor, user);
            }
        } catch (RuntimeException e) {
            log.error("Internal error in notifying listener" //$NON-NLS-1$
                + " of an outgoing invitation: ", e); //$NON-NLS-1$
        }
    }

    public void notifySarosSessionStarting(ISarosSession sarosSession) {
        try {
            for (ISarosSessionListener sarosSessionListener : this.sarosSessionListeners) {
                sarosSessionListener.sessionStarting(sarosSession);
            }
        } catch (RuntimeException e) {
            log.error("Internal error in notifying listener" //$NON-NLS-1$
                + " of SarosSession starting: ", e); //$NON-NLS-1$
        }
    }

    public void notifySarosSessionStarted(ISarosSession sarosSession) {
        for (ISarosSessionListener sarosSessionListener : this.sarosSessionListeners) {
            try {
                sarosSessionListener.sessionStarted(sarosSession);
            } catch (RuntimeException e) {
                log.error("Internal error in notifying listener" //$NON-NLS-1$
                    + " of SarosSession start: ", e); //$NON-NLS-1$
            }
        }
    }

    public void notifySessionEnding(ISarosSession sarosSession) {
        for (ISarosSessionListener saroSessionListener : this.sarosSessionListeners) {
            try {
                saroSessionListener.sessionEnding(sarosSession);
            } catch (RuntimeException e) {
                log.error("Internal error in notifying listener" //$NON-NLS-1$
                    + " of SarosSession ending: ", e); //$NON-NLS-1$
            }
        }
    }

    public void notifySessionEnd(ISarosSession sarosSession) {
        for (ISarosSessionListener listener : this.sarosSessionListeners) {
            try {
                listener.sessionEnded(sarosSession);
            } catch (RuntimeException e) {
                log.error("Internal error in notifying listener" //$NON-NLS-1$
                    + " of SarosSession end: ", e); //$NON-NLS-1$
            }
        }
    }

    public void notifyProjectAdded(IProject project) {
        for (ISarosSessionListener listener : this.sarosSessionListeners) {
            try {
                listener.projectAdded(getSarosSession().getProjectID(project));
            } catch (RuntimeException e) {
                log.error("Internal error in notifying listener" //$NON-NLS-1$
                    + " of an added project: ", e); //$NON-NLS-1$
            }
        }
    }
}
