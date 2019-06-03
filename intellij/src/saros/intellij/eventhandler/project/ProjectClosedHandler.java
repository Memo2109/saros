package saros.intellij.eventhandler.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import saros.repackaged.picocontainer.Disposable;
import saros.session.ISarosSession;
import saros.session.ISarosSessionManager;
import saros.session.SessionEndReason;
import saros.util.ThreadUtils;

/**
 * Ends the current session when a project containing shared modules is closed. This is done to
 * avoid calls on disposed project objects.
 *
 * <p>The handler is enabled by default after instantiation.
 */
public class ProjectClosedHandler implements Disposable {

  private static final Logger log = Logger.getLogger(ProjectClosedHandler.class);

  private final ISarosSessionManager sarosSessionManager;
  private final ISarosSession sarosSession;

  private final MessageBusConnection messageBusConnection;

  @SuppressWarnings("FieldCanBeLocal")
  private final ProjectManagerListener projectManagerListener =
      new ProjectManagerListener() {
        @Override
        public void projectClosing(@NotNull Project project) {
          /*
           * TODO correctly filter whether current session runs on shared project
           *  The current check works, but relies on the default equals implementation.
           *  I could not find any documentation on guarantees that the each project has a singleton
           *  object representation, which means I can't ensure that the Object.equals check always
           *  works correctly.
           *  A check using the characteristics of the module (like the name and module file
           *  location) would be better. This could be adjusted right now, but as this has to be
           *  changed for the application level plugin refactoring, I don't think it is worth it to
           *  do so at this moment. This change won't be merged beforehand anyways.
           */
          if (sarosSession.getComponent(Project.class).equals(project)) {
            ThreadUtils.runSafeSync(
                log, () -> sarosSessionManager.stopSession(SessionEndReason.LOCAL_USER_LEFT));
          }
        }
      };

  /**
   * Instantiates the <code>ProjectClosedHandler</code>. Registers the held <code>
   * ProjectManagerListener</code> with the application <code>MessageBus</code>.
   *
   * @param sarosSessionManager the <code>SarosSessionManager</code> instance
   * @param sarosSession the current <code>SarosSession</code>
   * @see MessageBusConnection
   * @see ProjectManagerListener
   */
  public ProjectClosedHandler(
      ISarosSessionManager sarosSessionManager, ISarosSession sarosSession) {
    this.sarosSessionManager = sarosSessionManager;
    this.sarosSession = sarosSession;

    messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    messageBusConnection.subscribe(ProjectManager.TOPIC, projectManagerListener);
  }

  /** Disconnects from the message bus when the plugin context is disposed. */
  @Override
  public void dispose() {
    messageBusConnection.disconnect();
  }
}
