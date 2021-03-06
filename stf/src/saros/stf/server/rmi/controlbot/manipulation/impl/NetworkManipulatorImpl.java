package saros.stf.server.rmi.controlbot.manipulation.impl;

import java.rmi.RemoteException;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.VoidResult;
import org.eclipse.swtbot.swt.finder.widgets.TimeoutException;
import org.jivesoftware.smack.packet.PacketExtension;
import saros.activities.IActivity;
import saros.activities.NOPActivity;
import saros.net.IPacketInterceptor;
import saros.net.ITransmitter;
import saros.net.internal.BinaryXMPPExtension;
import saros.net.internal.XMPPReceiver;
import saros.net.xmpp.JID;
import saros.session.IActivityConsumer;
import saros.session.IActivityListener;
import saros.session.IActivityProducer;
import saros.session.ISarosSession;
import saros.session.ISessionLifecycleListener;
import saros.session.SessionEndReason;
import saros.stf.server.StfRemoteObject;
import saros.stf.server.bot.SarosSWTBotPreferences;
import saros.stf.server.rmi.controlbot.manipulation.INetworkManipulator;

/** @author Stefan Rossbach */
public final class NetworkManipulatorImpl extends StfRemoteObject
    implements INetworkManipulator,
        IActivityConsumer,
        IActivityProducer,
        ISessionLifecycleListener {

  private static final Logger log = Logger.getLogger(NetworkManipulatorImpl.class);

  private static final Random RANDOM = new Random();

  private static final INetworkManipulator INSTANCE = new NetworkManipulatorImpl();

  public static INetworkManipulator getInstance() {
    return NetworkManipulatorImpl.INSTANCE;
  }

  private static class OutgoingPacketHolder {
    public String connectionId;
    public PacketExtension extension;
  }

  private ConcurrentHashMap<Integer, CountDownLatch> synchronizeRequests =
      new ConcurrentHashMap<Integer, CountDownLatch>();

  private ConcurrentHashMap<JID, Boolean> discardIncomingSessionPackets =
      new ConcurrentHashMap<JID, Boolean>();
  private ConcurrentHashMap<JID, Boolean> discardOutgoingSessionPackets =
      new ConcurrentHashMap<JID, Boolean>();

  /** map that contains the current blocking state for incoming packets for the given JID */
  private ConcurrentHashMap<JID, Boolean> blockIncomingSessionPackets =
      new ConcurrentHashMap<JID, Boolean>();

  /** map that contains the current blocking state for outgoing packets for the given JID */
  private ConcurrentHashMap<JID, Boolean> blockOutgoingSessionPackets =
      new ConcurrentHashMap<JID, Boolean>();

  /**
   * map that contains the current incoming packets for the given JID that are currently not
   * dispatched
   */
  private ConcurrentHashMap<JID, ConcurrentLinkedQueue<BinaryXMPPExtension>>
      blockedIncomingSessionPackets =
          new ConcurrentHashMap<JID, ConcurrentLinkedQueue<BinaryXMPPExtension>>();

  /**
   * map that contains the current incoming packets for the given JID that are currently not send
   */
  private ConcurrentHashMap<JID, ConcurrentLinkedQueue<OutgoingPacketHolder>>
      blockedOutgoingSessionPackets =
          new ConcurrentHashMap<JID, ConcurrentLinkedQueue<OutgoingPacketHolder>>();

  private volatile boolean blockAllOutgoingSessionPackets;
  private volatile boolean blockAllIncomingSessionPackets;

  private volatile ISarosSession session;

  private volatile IActivityListener listener;

  private IPacketInterceptor sessionPacketInterceptor =
      new IPacketInterceptor() {

        @Override
        public boolean receivedPacket(BinaryXMPPExtension object) {

          JID jid = object.getTransferDescription().getSender();
          log.trace("intercepting incoming packet from: " + jid);

          discardIncomingSessionPackets.putIfAbsent(jid, false);

          boolean discard = discardIncomingSessionPackets.get(jid);

          if (discard) {
            log.trace("discarding incoming packet: " + object);
            return false;
          }

          blockIncomingSessionPackets.putIfAbsent(jid, false);

          boolean blockIncomingPackets = blockIncomingSessionPackets.get(jid);

          if (blockIncomingPackets || blockAllIncomingSessionPackets) {

            blockedIncomingSessionPackets.putIfAbsent(
                jid, new ConcurrentLinkedQueue<BinaryXMPPExtension>());

            log.trace("queuing incoming packet: " + object);
            blockedIncomingSessionPackets.get(jid).add(object);
            return false;
          }

          return true;
        }

        @Override
        public boolean sendPacket(
            final String connectionId, final JID recipient, final PacketExtension extension) {

          if (!ISarosSession.SESSION_CONNECTION_ID.equals(connectionId)) return true;

          log.trace("intercepting outgoing packet to: " + recipient);

          discardOutgoingSessionPackets.putIfAbsent(recipient, false);

          boolean discard = discardOutgoingSessionPackets.get(recipient);

          if (discard) {
            log.trace("discarding outgoing packet: " + extension);
            return false;
          }

          blockOutgoingSessionPackets.putIfAbsent(recipient, false);

          boolean blockOutgoingPackets = blockOutgoingSessionPackets.get(recipient);

          if (blockOutgoingPackets || blockAllOutgoingSessionPackets) {

            blockedOutgoingSessionPackets.putIfAbsent(
                recipient, new ConcurrentLinkedQueue<OutgoingPacketHolder>());

            OutgoingPacketHolder holder = new OutgoingPacketHolder();
            holder.connectionId = connectionId;
            holder.extension = extension;

            log.trace("queuing outgoing packet: " + extension);
            blockedOutgoingSessionPackets.get(recipient).add(holder);
            return false;
          }

          return true;
        }
      };

  private NetworkManipulatorImpl() {
    this.getSessionManager().addSessionLifecycleListener(this);
    this.getReceiver().addPacketInterceptor(sessionPacketInterceptor);
    this.getTransmitter().addPacketInterceptor(sessionPacketInterceptor);
  }

  @Override
  public void blockIncomingXMPPPackets(JID jid) throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void blockOutgoingXMPPPackets(JID jid) throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void unblockIncomingXMPPPackets(JID jid) throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void unblockOutgoingXMPPPackets(JID jid) throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void unblockIncomingXMPPPackets() throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void unblockOutgoingXMPPPackets() throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void setDiscardIncomingXMPPPackets(JID jid, boolean discard) throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void setDiscardOutgoingXMPPPackets(JID jid, boolean discard) throws RemoteException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void blockIncomingSessionPackets(JID jid) throws RemoteException {
    log.trace("blocking incoming packet transfer from " + jid);
    blockIncomingSessionPackets.put(jid, true);
  }

  @Override
  public void blockOutgoingSessionPackets(JID jid) throws RemoteException {
    log.trace("blocking outgoing packet transfer to " + jid);
    blockOutgoingSessionPackets.put(jid, true);
  }

  @Override
  public void unblockIncomingSessionPackets(JID jid) throws RemoteException {
    log.trace("unblocking incoming packet transfer from " + jid);

    if (blockAllIncomingSessionPackets) {
      log.warn(
          "cannot unblock incoming packet transfer from "
              + jid
              + ", because all incoming packet traffic is locked");
      return;
    }

    blockIncomingSessionPackets.put(jid, false);

    blockedIncomingSessionPackets.putIfAbsent(
        jid, new ConcurrentLinkedQueue<BinaryXMPPExtension>());

    Queue<BinaryXMPPExtension> pendingIncomingPackets = blockedIncomingSessionPackets.get(jid);

    /*
     * HACK: short sleep as it is possible that a packet arrive, thread is
     * suspended, queue is cleared and then the packet is put into the queue
     */

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    if (pendingIncomingPackets.isEmpty()) {
      log.trace("no packets where intercepted during blocking state");
      return;
    }

    while (!pendingIncomingPackets.isEmpty()) {
      try {
        BinaryXMPPExtension object = pendingIncomingPackets.remove();

        log.trace("dispatching blocked packet: " + object);

        ((XMPPReceiver) getReceiver()).receive(object);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  @Override
  public void unblockOutgoingSessionPackets(JID jid) throws RemoteException {
    log.trace("unblocking outgoing packet transfer to " + jid);

    if (blockAllOutgoingSessionPackets) {
      log.warn(
          "cannot unblock outgoing packet transfer to "
              + jid
              + ", because all outgoing packet traffic is locked");
      return;
    }

    blockOutgoingSessionPackets.put(jid, false);

    blockedOutgoingSessionPackets.putIfAbsent(
        jid, new ConcurrentLinkedQueue<OutgoingPacketHolder>());

    Queue<OutgoingPacketHolder> pendingOutgoingPackets = blockedOutgoingSessionPackets.get(jid);

    /*
     * HACK: short sleep as it is possible that a packet arrive, thread is
     * suspended, queue is cleared and then the packet is put into the queue
     */

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    if (pendingOutgoingPackets.isEmpty()) {
      log.trace("no packets where intercepted during blocking state");
      return;
    }

    final ITransmitter transmitter = getTransmitter();

    while (!pendingOutgoingPackets.isEmpty()) {
      try {
        OutgoingPacketHolder holder = pendingOutgoingPackets.remove();

        log.trace(
            "sending blocked packet: "
                + holder.extension
                + ", connection id: "
                + holder.connectionId
                + ", recipient: "
                + jid);

        transmitter.send(holder.connectionId, jid, holder.extension);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  @Override
  public void unblockIncomingSessionPackets() throws RemoteException {
    if (!blockAllIncomingSessionPackets) return;

    log.trace("unblocking all incoming packet transfer");
    blockAllIncomingSessionPackets = false;

    for (JID jid : blockIncomingSessionPackets.keySet()) unblockIncomingSessionPackets(jid);
  }

  @Override
  public void unblockOutgoingSessionPackets() throws RemoteException {
    if (!blockAllOutgoingSessionPackets) return;

    log.trace("unblocking all outgoing packet transfer");
    blockAllOutgoingSessionPackets = false;

    for (JID jid : blockOutgoingSessionPackets.keySet()) unblockOutgoingSessionPackets(jid);
  }

  @Override
  public void blockIncomingSessionPackets() throws RemoteException {
    log.trace("blocking all incoming packet transfer");
    blockAllIncomingSessionPackets = true;
  }

  @Override
  public void blockOutgoingSessionPackets() throws RemoteException {
    log.trace("blocking all outgoing packet transfer");
    blockAllOutgoingSessionPackets = true;
  }

  @Override
  public void setDiscardIncomingSessionPackets(JID jid, boolean discard) throws RemoteException {
    discardIncomingSessionPackets.put(jid, discard);
  }

  @Override
  public void setDiscardOutgoingSessionPackets(JID jid, boolean discard) throws RemoteException {
    discardOutgoingSessionPackets.put(jid, discard);
  }

  @Override
  public void synchronizeOnActivityQueue(JID jid, long timeout) throws RemoteException {

    ISarosSession session = this.session;
    IActivityListener listener = this.listener;

    // this is too lazy, but ok for testing purposes

    if (session == null) throw new IllegalStateException("no session running");

    if (listener == null) throw new IllegalStateException("no session running");

    final CountDownLatch swtThreadSync = new CountDownLatch(1);

    UIThreadRunnable.asyncExec(
        new VoidResult() {
          @Override
          public void run() {
            swtThreadSync.countDown();
          }
        });

    try {
      if (!swtThreadSync.await(
          SarosSWTBotPreferences.SAROS_DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)) {
        log.warn("could not synchronize on the SWT EDT");
      }
    } catch (InterruptedException e1) {
      Thread.currentThread().interrupt();
    }

    int id = RANDOM.nextInt();

    NOPActivity activity = new NOPActivity(session.getLocalUser(), session.getUser(jid), id);

    CountDownLatch latch = new CountDownLatch(1);

    synchronizeRequests.put(id, latch);
    listener.created(activity);

    try {
      if (!latch.await(timeout, TimeUnit.MILLISECONDS))
        throw new TimeoutException("no reply from " + jid);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      synchronizeRequests.remove(id);
    }
  }

  // IActivityConsumer interface implementation

  @Override
  public void exec(IActivity activity) {
    if (!(activity instanceof NOPActivity)) {
      return;
    }

    NOPActivity nop = (NOPActivity) activity;

    ISarosSession session = this.session;
    IActivityListener listener = this.listener;

    if (session == null) return;

    if (listener == null) return;

    /*
     * if we have not send this message to ourself then reply back , do not
     * use our JID as source as this would cause a infinite loop
     */
    if (!nop.getSource().equals(session.getLocalUser())) {
      NOPActivity response = new NOPActivity(nop.getSource(), nop.getSource(), nop.getID());

      listener.created(response);

      return;
    }

    int id = nop.getID();

    CountDownLatch latch = synchronizeRequests.get(id);

    if (latch != null) latch.countDown();
  }

  // IActivityProducer interface implementation (not perfectly conform)

  @Override
  public void addActivityListener(IActivityListener listener) {
    this.listener = listener;
  }

  @Override
  public void removeActivityListener(IActivityListener listener) {
    this.listener = null;
  }

  @Override
  public void sessionStarting(ISarosSession session) {
    this.session = session;
    this.session.addActivityProducer(this);
    this.session.addActivityConsumer(this, Priority.ACTIVE);
  }

  @Override
  public void sessionStarted(ISarosSession session) {
    // NOP
  }

  @Override
  public void sessionEnding(ISarosSession session) {
    if (this.session != null) {
      this.session.removeActivityProducer(this);
      this.session.removeActivityConsumer(this);
    }
    this.session = null;
  }

  @Override
  public void sessionEnded(ISarosSession session, SessionEndReason reason) {
    // NOP

  }
}
