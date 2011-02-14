package de.fu_berlin.inf.dpp.stf.client.testProject.testsuits.invitation;

import static org.junit.Assert.assertTrue;

import java.rmi.RemoteException;

import org.junit.BeforeClass;
import org.junit.Test;

import de.fu_berlin.inf.dpp.stf.client.testProject.testsuits.STFTest;

public class TestEditDuringInvitation extends STFTest {

    /**
     * Preconditions:
     * <ol>
     * <li>alice (Host, Write Access), alice share a java project with bob and
     * carl.</li>
     * <li>bob (Read-Only Access)</li>
     * <li>carl (Read-Only Access)</li>
     * </ol>
     * 
     * @throws RemoteException
     * 
     */
    @BeforeClass
    public static void runBeforeClass() throws RemoteException {
        initTesters(TypeOfTester.ALICE, TypeOfTester.BOB, TypeOfTester.CARL);
        setUpWorkbench();
        setUpSaros();
        alice.fileM.newJavaProjectWithClasses(PROJECT1, PKG1, CLS1);
    }

    /**
     * 
     * Steps:
     * <ol>
     * <li>Alice invites Bob.</li>
     * <li>Bob accepts the invitation</li>
     * <li>Alice grants Bob write access</li>
     * <li>Alice invites Carl</li>
     * <li>Bob changes data during the running invtiation of Carl.</li>
     * </ol>
     * 
     * 
     * Expected Results:
     * <ol>
     * <li>All changes that Bob has done should be on Carl's side. There should
     * not be an inconsistency.</li>.
     * </ol>
     * 
     * @throws RemoteException
     */
    @Test
    public void testEditDuringInvitation() throws RemoteException {
        log.trace("starting testEditDuringInvitation, alice.buildSession");
        buildSessionSequentially(VIEW_PACKAGE_EXPLORER, PROJECT1,
            TypeOfShareProject.SHARE_PROJECT, TypeOfCreateProject.NEW_PROJECT,
            alice, bob);

        assertTrue(bob.sarosSessionV.hasWriteAccessNoGUI());

        log.trace("alice.inviteUser(carl");
        alice.sarosSessionV.openInvitationInterface(carl.getBaseJid());

        log.trace("carl.confirmSessionInvitationWindowStep1");
        carl.sarosC.confirmShellSessionnInvitation();

        log.trace("bob.setTextInJavaEditor");
        bob.openC.openClass(VIEW_PACKAGE_EXPLORER, PROJECT1, PKG1, CLS1);
        bob.bot().editor(CLS1_SUFFIX).setTextInEditorWithSave(CP1);

        log.trace("carl.confirmSessionInvitationWindowStep2UsingNewproject");
        carl.sarosC.confirmShellAddProjectWithNewProject(PROJECT1);

        log.trace("getTextOfJavaEditor");
        String textFromCarl = carl.editor.getTextOfJavaEditor(PROJECT1, PKG1,
            CLS1);
        String textFormAlice = alice.editor.getTextOfJavaEditor(PROJECT1, PKG1,
            CLS1);

        String textFormBob = bob.editor.getTextOfJavaEditor(PROJECT1, PKG1,
            CLS1);
        assertTrue(textFromCarl.equals(textFormAlice));
        assertTrue(textFromCarl.equals(textFormBob));
        // assertTrue(carl.sessionV.isInconsistencyDetectedEnabled());

        log.trace("testEditDuringInvitation done");
    }
}
