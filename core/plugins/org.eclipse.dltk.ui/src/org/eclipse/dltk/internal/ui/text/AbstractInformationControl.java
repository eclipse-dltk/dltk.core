/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 *******************************************************************************/
package org.eclipse.dltk.internal.ui.text;

import org.eclipse.core.commands.Command;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.IParent;
import org.eclipse.dltk.internal.ui.actions.OpenActionUtil;
import org.eclipse.dltk.internal.ui.util.StringMatcher;
import org.eclipse.dltk.ui.DLTKUIPlugin;
import org.eclipse.dltk.ui.actions.CustomFiltersActionGroup;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlExtension;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.LegacyHandlerSubmissionExpression;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.keys.IBindingService;

/**
 * Abstract class for Show hierarchy in light-weight controls.
 *
 *
 */
public abstract class AbstractInformationControl extends PopupDialog
		implements IInformationControl, IInformationControlExtension, IInformationControlExtension2, DisposeListener {

	/**
	 * The NamePatternFilter selects the elements which match the given string
	 * patterns.
	 */
	protected class NamePatternFilter extends ViewerFilter {

		public NamePatternFilter() {
		}

		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			StringMatcher matcher = getMatcher();
			if (matcher == null || !(viewer instanceof TreeViewer))
				return true;
			TreeViewer treeViewer = (TreeViewer) viewer;

			String matchName = ((ILabelProvider) treeViewer.getLabelProvider()).getText(element);
			if (matchName != null && matcher.match(matchName))
				return true;

			return hasUnfilteredChild(treeViewer, element);
		}

		private boolean hasUnfilteredChild(TreeViewer viewer, Object element) {
			if (element instanceof IParent) {
				Object[] children = ((ITreeContentProvider) viewer.getContentProvider()).getChildren(element);
				for (int i = 0; i < children.length; i++)
					if (select(viewer, element, children[i]))
						return true;
			}
			return false;
		}
	}

	/** The control's text widget */
	private Text fFilterText;
	/** The control's tree widget */
	private TreeViewer fTreeViewer;
	/** The current string matcher */
	protected StringMatcher fStringMatcher;
	private Command fInvokingCommand;
	private TriggerSequence[] fInvokingCommandKeySequences;

	/**
	 * Fields that support the dialog menu
	 *
	 *
	 */
	private Composite fViewMenuButtonComposite;

	private CustomFiltersActionGroup fCustomFiltersActionGroup;

	private String[] fKeyBindingScopes;
	private IAction fShowViewMenuAction;
	private IHandlerActivation fShowViewMenuHandlerSubmission;

	/**
	 * Field for tree style since it must be remembered by the instance.
	 *
	 *
	 */
	private int fTreeStyle;

	/**
	 * Creates a tree information control with the given shell as parent. The given
	 * styles are applied to the shell and the tree widget.
	 *
	 * @param parent            the parent shell
	 * @param shellStyle        the additional styles for the shell
	 * @param treeStyle         the additional styles for the tree widget
	 * @param invokingCommandId the id of the command that invoked this control or
	 *                          <code>null</code>
	 * @param showStatusField   <code>true</code> iff the control has a status field
	 *                          at the bottom
	 */
	public AbstractInformationControl(Shell parent, int shellStyle, int treeStyle, String invokingCommandId,
			boolean showStatusField) {
		super(parent, shellStyle, true, true, true, true, true, null, null);
		if (invokingCommandId != null) {
			ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
			fInvokingCommand = commandService.getCommand(invokingCommandId);
			if (fInvokingCommand != null && !fInvokingCommand.isDefined())
				fInvokingCommand = null;
			else
				// Pre-fetch key sequence - do not change because scope will
				// change later.
				getInvokingCommandKeySequences();
		}
		fTreeStyle = treeStyle;
		// Title and status text must be set to get the title label created, so
		// force empty values here.
		if (hasHeader())
			setTitleText(""); //$NON-NLS-1$
		setInfoText(""); // //$NON-NLS-1$

		// Create all controls early to preserve the life cycle of the original
		// implementation.
		if (isEarlyCreate()) {
			create();
		}

	}

	@Override
	public void create() {
		super.create();
		// Status field text can only be computed after widgets are created.
		setInfoText(getStatusFieldText());
	}

	protected boolean isEarlyCreate() {
		return true;
	}

	/**
	 * Create the main content for this information control.
	 *
	 * @param parent The parent composite
	 * @return The control representing the main content.
	 *
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		fTreeViewer = createTreeViewer(parent, fTreeStyle);

		fCustomFiltersActionGroup = new CustomFiltersActionGroup(getId(), fTreeViewer);

		final Tree tree = fTreeViewer.getTree();
		tree.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.character == 0x1B) // ESC
					dispose();
			}
		});

		tree.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				gotoSelectedElement();
			}
		});

		tree.addMouseMoveListener(new MouseMoveListener() {
			TreeItem fLastItem = null;

			@Override
			public void mouseMove(MouseEvent e) {
				if (tree.equals(e.getSource())) {
					Object o = tree.getItem(new Point(e.x, e.y));
					if (o instanceof TreeItem) {
						if (!o.equals(fLastItem)) {
							fLastItem = (TreeItem) o;
							tree.setSelection(new TreeItem[] { fLastItem });
						} else if (e.y < tree.getItemHeight() / 4) {
							// Scroll up
							Point p = tree.toDisplay(e.x, e.y);
							Item item = fTreeViewer.scrollUp(p.x, p.y);
							if (item instanceof TreeItem) {
								fLastItem = (TreeItem) item;
								tree.setSelection(new TreeItem[] { fLastItem });
							}
						} else if (e.y > tree.getBounds().height - tree.getItemHeight() / 4) {
							// Scroll down
							Point p = tree.toDisplay(e.x, e.y);
							Item item = fTreeViewer.scrollDown(p.x, p.y);
							if (item instanceof TreeItem) {
								fLastItem = (TreeItem) item;
								tree.setSelection(new TreeItem[] { fLastItem });
							}
						}
					}
				}
			}
		});

		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {

				if (tree.getSelectionCount() < 1)
					return;

				if (e.button != 1)
					return;

				if (tree.equals(e.getSource())) {
					Object o = tree.getItem(new Point(e.x, e.y));
					TreeItem selection = tree.getSelection()[0];
					if (selection.equals(o))
						gotoSelectedElement();
				}
			}
		});

		installFilter();

		addDisposeListener(this);
		return fTreeViewer.getControl();
	}

	/**
	 * Creates a tree information control with the given shell as parent. The given
	 * styles are applied to the shell and the tree widget.
	 *
	 * @param parent     the parent shell
	 * @param shellStyle the additional styles for the shell
	 * @param treeStyle  the additional styles for the tree widget
	 */
	public AbstractInformationControl(Shell parent, int shellStyle, int treeStyle) {
		this(parent, shellStyle, treeStyle, null, false);
	}

	protected abstract TreeViewer createTreeViewer(Composite parent, int style);

	/**
	 * Returns the name of the dialog settings section.
	 *
	 * @return the name of the dialog settings section
	 */
	protected abstract String getId();

	protected TreeViewer getTreeViewer() {
		return fTreeViewer;
	}

	/**
	 * Returns <code>true</code> if the control has a header, <code>false</code>
	 * otherwise.
	 * <p>
	 * The default is to return <code>false</code>.
	 * </p>
	 *
	 * @return <code>true</code> if the control has a header
	 */
	protected boolean hasHeader() {
		// default is to have no header
		return false;
	}

	protected Text getFilterText() {
		return fFilterText;
	}

	protected Text createFilterText(Composite parent) {
		fFilterText = new Text(parent, SWT.NONE);

		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		GC gc = new GC(parent);
		gc.setFont(parent.getFont());
		FontMetrics fontMetrics = gc.getFontMetrics();
		gc.dispose();

		data.heightHint = Dialog.convertHeightInCharsToPixels(fontMetrics, 1);
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.CENTER;
		fFilterText.setLayoutData(data);

		fFilterText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == 0x0D) // return
					gotoSelectedElement();
				if (e.keyCode == SWT.ARROW_DOWN)
					fTreeViewer.getTree().setFocus();
				if (e.keyCode == SWT.ARROW_UP)
					fTreeViewer.getTree().setFocus();
				if (e.character == 0x1B) // ESC
					dispose();
			}
		});

		return fFilterText;
	}

	protected void createHorizontalSeparator(Composite parent) {
		Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL | SWT.LINE_DOT);
		separator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	}

	protected void updateStatusFieldText() {
		setInfoText(getStatusFieldText());
	}

	/**
	 * Handles click in status field.
	 * <p>
	 * Default does nothing.
	 * </p>
	 */
	protected void handleStatusFieldClicked() {
	}

	protected String getStatusFieldText() {
		return ""; //$NON-NLS-1$
	}

	private void installFilter() {
		fFilterText.setText(""); //$NON-NLS-1$

		fFilterText.addModifyListener(e -> {
			String text = ((Text) e.widget).getText();
			int length = text.length();
			if (length > 0 && text.charAt(length - 1) != '*') {
				text = text + '*';
			}
			setMatcherString(text, true);
		});
	}

	/**
	 * The string matcher has been modified. The default implementation refreshes
	 * the view and selects the first matched element
	 */
	protected void stringMatcherUpdated() {
		// refresh viewer to re-filter
		fTreeViewer.getControl().setRedraw(false);
		fTreeViewer.refresh();
		fTreeViewer.expandAll();
		selectFirstMatch();
		fTreeViewer.getControl().setRedraw(true);
	}

	/**
	 * Sets the patterns to filter out for the receiver.
	 * <p>
	 * The following characters have special meaning: ? => any character * => any
	 * string
	 * </p>
	 *
	 * @param pattern the pattern
	 * @param update  <code>true</code> if the viewer should be updated
	 */
	protected void setMatcherString(String pattern, boolean update) {
		if (pattern.length() == 0) {
			fStringMatcher = null;
		} else {
			boolean ignoreCase = pattern.toLowerCase().equals(pattern);
			fStringMatcher = new StringMatcher(pattern, ignoreCase, false);
		}

		if (update)
			stringMatcherUpdated();
	}

	protected StringMatcher getMatcher() {
		return fStringMatcher;
	}

	/**
	 * Implementers can modify
	 *
	 * @return the selected element
	 */
	protected Object getSelectedElement() {
		if (fTreeViewer == null)
			return null;

		return fTreeViewer.getStructuredSelection().getFirstElement();
	}

	private void gotoSelectedElement() {
		Object selectedElement = getSelectedElement();
		if (selectedElement != null) {
			try {
				dispose();
				OpenActionUtil.open(selectedElement, true);
			} catch (CoreException ex) {
				DLTKUIPlugin.log(ex);
			}
		}
	}

	/**
	 * Selects the first element in the tree which matches the current filter
	 * pattern.
	 */
	protected void selectFirstMatch() {
		Tree tree = fTreeViewer.getTree();
		Object element = findElement(tree.getItems());
		if (element != null)
			fTreeViewer.setSelection(new StructuredSelection(element), true);
		else
			fTreeViewer.setSelection(StructuredSelection.EMPTY);
	}

	private IModelElement findElement(TreeItem[] items) {
		ILabelProvider labelProvider = (ILabelProvider) fTreeViewer.getLabelProvider();
		for (int i = 0; i < items.length; i++) {
			IModelElement element = (IModelElement) items[i].getData();
			if (fStringMatcher == null)
				return element;

			if (element != null) {
				String label = labelProvider.getText(element);
				if (fStringMatcher.match(label))
					return element;
			}

			element = findElement(items[i].getItems());
			if (element != null)
				return element;
		}
		return null;
	}

	@Override
	public void setInformation(String information) {
		// this method is ignored, see IInformationControlExtension2
	}

	@Override
	public abstract void setInput(Object information);

	/**
	 * Fills the view menu. Clients can extend or override.
	 *
	 * @param viewMenu the menu manager that manages the menu
	 *
	 */
	protected void fillViewMenu(IMenuManager viewMenu) {
		fCustomFiltersActionGroup.fillViewMenu(viewMenu);
	}

	/*
	 * Overridden to call the old framework method.
	 *
	 * @see org.eclipse.jface.dialogs.PopupDialog#fillDialogMenu(IMenuManager)
	 */
	@Override
	protected void fillDialogMenu(IMenuManager dialogMenu) {
		super.fillDialogMenu(dialogMenu);
		fillViewMenu(dialogMenu);
	}

	protected void inputChanged(Object newInput, Object newSelection) {
		fFilterText.setText(""); //$NON-NLS-1$
		fTreeViewer.setInput(newInput);
		if (newSelection != null) {
			fTreeViewer.setSelection(new StructuredSelection(newSelection));
		}
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible) {
			addHandlerAndKeyBindingSupport();
			open();
		} else {
			removeHandlerAndKeyBindingSupport();
			saveDialogBounds(getShell());
			getShell().setVisible(false);
			removeHandlerAndKeyBindingSupport();
		}
	}

	@Override
	public final void dispose() {
		close();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param event can be null
	 *              <p>
	 *              Subclasses may extend.
	 *              </p>
	 */
	@Override
	public void widgetDisposed(DisposeEvent event) {
		removeHandlerAndKeyBindingSupport();
		fTreeViewer = null;
		fFilterText = null;
	}

	/**
	 * Adds handler and key binding support.
	 *
	 *
	 */
	protected void addHandlerAndKeyBindingSupport() {

		if (fShowViewMenuHandlerSubmission == null) {
			IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
			Expression expression = new LegacyHandlerSubmissionExpression(null, getShell(), null);
			fShowViewMenuHandlerSubmission = handlerService.activateHandler(fShowViewMenuAction.getActionDefinitionId(),
					new ActionHandler(fShowViewMenuAction), expression);
		}
	}

	/**
	 * Removes handler and key binding support.
	 *
	 *
	 */
	protected void removeHandlerAndKeyBindingSupport() {
		if (fShowViewMenuHandlerSubmission != null) {
			IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
			handlerService.deactivateHandler(fShowViewMenuHandlerSubmission);
			fShowViewMenuHandlerSubmission = null;
		}
	}

	@Override
	public boolean hasContents() {
		return fTreeViewer != null && fTreeViewer.getInput() != null;
	}

	@Override
	public void setSizeConstraints(int maxWidth, int maxHeight) {
		// ignore
	}

	@Override
	public Point computeSizeHint() {
		// return the shell's size - note that it already has the persisted size
		// if persisting
		// is enabled.
		return getShell().getSize();
	}

	@Override
	public void setLocation(Point location) {
		/*
		 * If the location is persisted, it gets managed by PopupDialog - fine.
		 * Otherwise, the location is computed in Window#getInitialLocation, which will
		 * center it in the parent shell / main monitor, which is wrong for two reasons:
		 * - we want to center over the editor / subject control, not the parent shell -
		 * the center is computed via the initalSize, which may be also wrong since the
		 * size may have been updated since via min/max sizing of
		 * AbstractInformationControlManager. In that case, override the location with
		 * the one computed by the manager. Note that the call to constrainShellSize in
		 * PopupDialog.open will still ensure that the shell is entirely visible.
		 */
		if (!(getPersistLocation() && getPersistSize()) || getDialogSettings() == null)
			getShell().setLocation(location);
	}

	@Override
	public void setSize(int width, int height) {
		getShell().setSize(width, height);
	}

	@Override
	public void addDisposeListener(DisposeListener listener) {
		getShell().addDisposeListener(listener);
	}

	@Override
	public void removeDisposeListener(DisposeListener listener) {
		getShell().removeDisposeListener(listener);
	}

	@Override
	public void setForegroundColor(Color foreground) {
		applyForegroundColor(foreground, getContents());
	}

	@Override
	public void setBackgroundColor(Color background) {
		applyBackgroundColor(background, getContents());
	}

	@Override
	public boolean isFocusControl() {
		return fTreeViewer.getControl().isFocusControl() || fFilterText.isFocusControl();
	}

	@Override
	public void setFocus() {
		getShell().forceFocus();
		fFilterText.setFocus();
	}

	@Override
	public void addFocusListener(FocusListener listener) {
		getShell().addFocusListener(listener);
	}

	@Override
	public void removeFocusListener(FocusListener listener) {
		getShell().removeFocusListener(listener);
	}

	final protected Command getInvokingCommand() {
		return fInvokingCommand;
	}

	final protected TriggerSequence[] getInvokingCommandKeySequences() {
		if (fInvokingCommandKeySequences == null) {
			if (getInvokingCommand() != null) {
				IBindingService bindingService = PlatformUI.getWorkbench().getAdapter(IBindingService.class);
				fInvokingCommandKeySequences = bindingService.getActiveBindingsFor(getInvokingCommand().getId());
				return fInvokingCommandKeySequences;
			}
		}
		return fInvokingCommandKeySequences;
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		String sectionName = getId();

		IDialogSettings settings = DLTKUIPlugin.getDefault().getDialogSettings().getSection(sectionName);
		if (settings == null)
			settings = DLTKUIPlugin.getDefault().getDialogSettings().addNewSection(sectionName);

		return settings;
	}

	/*
	 * Overridden to insert the filter text into the title and menu area.
	 */
	@Override
	protected Control createTitleMenuArea(Composite parent) {
		fViewMenuButtonComposite = (Composite) super.createTitleMenuArea(parent);

		// If there is a header, then the filter text must be created
		// underneath the title and menu area.

		if (hasHeader()) {
			fFilterText = createFilterText(parent);
		}

		// Create show view menu action
		fShowViewMenuAction = new Action("showViewMenu") { //$NON-NLS-1$
			/*
			 * @see org.eclipse.jface.action.Action#run()
			 */
			@Override
			public void run() {
				showDialogMenu();
			}
		};
		fShowViewMenuAction.setEnabled(true);
		fShowViewMenuAction.setActionDefinitionId("org.eclipse.ui.window.showViewMenu"); //$NON-NLS-1$

		addHandlerAndKeyBindingSupport();

		return fViewMenuButtonComposite;
	}

	/*
	 * Overridden to insert the filter text into the title control if there is no
	 * header specified.
	 */
	@Override
	protected Control createTitleControl(Composite parent) {
		if (hasHeader()) {
			return super.createTitleControl(parent);
		}
		fFilterText = createFilterText(parent);
		return fFilterText;
	}

	@Override
	protected void setTabOrder(Composite composite) {
		if (hasHeader()) {
			composite.setTabList(new Control[] { fFilterText, fTreeViewer.getTree() });
		} else {
			fViewMenuButtonComposite.setTabList(new Control[] { fFilterText });
			composite.setTabList(new Control[] { fViewMenuButtonComposite, fTreeViewer.getTree() });
		}
	}
}
