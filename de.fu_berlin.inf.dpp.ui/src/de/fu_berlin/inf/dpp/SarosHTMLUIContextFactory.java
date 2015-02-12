package de.fu_berlin.inf.dpp;

import de.fu_berlin.inf.dpp.account.XMPPAccountLocator;
import de.fu_berlin.inf.dpp.communication.connection.ConnectionHandlerCore;
import de.fu_berlin.inf.dpp.ui.browser_functions.ContactListCoreService;
import de.fu_berlin.inf.dpp.ui.manager.ContactListManager;
import de.fu_berlin.inf.dpp.ui.view_parts.AddAccountWizard;
import de.fu_berlin.inf.dpp.ui.view_parts.AddContactWizard;
import de.fu_berlin.inf.dpp.ui.view_parts.SarosMainPage;
import org.picocontainer.MutablePicoContainer;

import java.util.Arrays;

/**
 * This is the HTML UI core factory for Saros. All components that are created by
 * this factory <b>must</b> be working on any platform the application is
 * running on.
 */
public class SarosHTMLUIContextFactory extends AbstractSarosContextFactory {

    private final ISarosContextFactory additionalFactory;

    public SarosHTMLUIContextFactory(ISarosContextFactory additionalFactory) {
        this.additionalFactory = additionalFactory;
    }

    @Override
    public void createComponents(MutablePicoContainer container) {
        if (additionalFactory != null) {
            additionalFactory.createComponents(container);
        }

        Component[] components = new Component[] {
            Component.create(XMPPAccountLocator.class),
            Component.create(SarosMainPage.class),
            Component.create(AddAccountWizard.class),
            Component.create(AddContactWizard.class),
            Component.create(ContactListCoreService.class),
            Component.create(ContactListManager.class),
            Component.create(ConnectionHandlerCore.class) };

        for (Component component : Arrays.asList(components)) {
            container.addComponent(component.getBindKey(),
                component.getImplementation());
        }
    }
}