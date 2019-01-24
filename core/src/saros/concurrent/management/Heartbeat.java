package saros.concurrent.management;

import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import saros.activities.JupiterActivity;
import saros.activities.SPath;
import saros.concurrent.jupiter.internal.Jupiter;
import saros.concurrent.jupiter.internal.text.NoOperation;
import saros.repackaged.picocontainer.Startable;
import saros.session.AbstractActivityProducer;
import saros.session.ISarosSession;
import saros.synchronize.UISynchronizer;
import saros.util.NamedThreadFactory;

public class Heartbeat extends AbstractActivityProducer implements Startable {
  private ScheduledThreadPoolExecutor jupiterHeartbeat;
  private final ISarosSession sarosSession;
  private final UISynchronizer uiSynchronizer;
  private final Map<SPath, Jupiter> clientDocs;

  Heartbeat(
      ISarosSession sarosSession, UISynchronizer uiSynchronizer, Map<SPath, Jupiter> clientDocs) {
    this.sarosSession = sarosSession;
    this.uiSynchronizer = uiSynchronizer;
    this.clientDocs = clientDocs;
  }

  public void start() {
    jupiterHeartbeat =
        new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("dpp-jupiter-heartbeat"));
    jupiterHeartbeat.scheduleWithFixedDelay(this::dispatchHeartbeats, 1, 1, TimeUnit.MINUTES);
  }

  private void dispatchHeartbeats() {
    uiSynchronizer.asyncExec(
        () ->
            clientDocs.forEach(
                (path, jupiter) -> {
                  JupiterActivity heartbeat =
                      jupiter.generateJupiterActivity(
                          new NoOperation(), sarosSession.getLocalUser(), path);
                  fireActivity(heartbeat);
                }));
  }

  @Override
  public void stop() {
    jupiterHeartbeat.shutdown();
  }
}
