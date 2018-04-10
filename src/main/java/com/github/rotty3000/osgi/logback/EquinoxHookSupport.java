package com.github.rotty3000.osgi.logback;

import org.eclipse.osgi.internal.hookregistry.ActivatorHookFactory;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.osgi.framework.BundleActivator;

public class EquinoxHookSupport implements ActivatorHookFactory, HookConfigurator {

	@Override
	public BundleActivator createActivator() {
		return new Activator();
	}

	@Override
	public void addHooks(HookRegistry hookRegistry) {
		hookRegistry.addActivatorHookFactory(this);
	}

}
