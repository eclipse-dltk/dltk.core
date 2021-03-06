package org.eclipse.dltk.validators.internal.externalchecker.core;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.dltk.validators.internal.externalchecker.core.messages"; //$NON-NLS-1$
	public static String ExternalChecker_checkingWithExternalExecutable;
	public static String ExternalChecker_error;
	public static String ExternalChecker_sourceModuleResourceIsNull;
	public static String ExternalChecker_warning;
	public static String ExternalCheckerType_externalChecker;
	public static String ExternalCheckerWildcardManager_fileName;
	public static String ExternalCheckerWildcardManager_lineNumber;
	public static String ExternalCheckerWildcardManager_message;
	public static String ExternalChecker_resourceIsNull;
	
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
