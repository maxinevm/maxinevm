/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.debug;

import java.awt.*;

import javax.swing.*;

import com.sun.max.ins.*;
import com.sun.max.ins.InspectionSettings.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.gui.TableColumnVisibilityPreferences.*;
import com.sun.max.program.*;
import com.sun.max.tele.debug.*;

/**
 * Singleton inspector that displays information about all kinds of breakpoints that might be set in the VM.
 * Wrappers with extra information about each breakpoint are kept in a model.
 *
 * @author Mick Jordan
 * @author Michael Van De Vanter
 */
public final class BreakpointsInspector extends Inspector implements TableColumnViewPreferenceListener {

    private static final int TRACE_VALUE = 1;

    // Set to null when inspector closed.
    private static BreakpointsInspector breakpointsInspector;

    /**
     * Displays the (singleton) breakpoints inspector.
     * @return  The breakpoints inspector, possibly newly created.
     */
    public static BreakpointsInspector make(Inspection inspection) {
        if (breakpointsInspector == null) {
            breakpointsInspector = new BreakpointsInspector(inspection);
        }
        return breakpointsInspector;
    }

    private final SaveSettingsListener saveSettingsListener = createGeometrySettingsClient(this, "breakpointsInspectorGeometry");

    // This is a singleton viewer, so only use a single level of view preferences.
    private final BreakpointsViewPreferences viewPreferences;

    private BreakpointsTable table;

    private BreakpointsInspector(Inspection inspection) {
        super(inspection);
        Trace.begin(1,  tracePrefix() + " initializing");
        viewPreferences = BreakpointsViewPreferences.globalPreferences(inspection());
        viewPreferences.addListener(this);
        final InspectorFrame frame = createFrame();

        frame.makeMenu(MenuKind.DEFAULT_MENU).add(defaultMenuItems(MenuKind.DEFAULT_MENU));

        final InspectorMenu editMenu = frame.makeMenu(MenuKind.EDIT_MENU);
        editMenu.add(inspection().actions().removeSelectedBreakpoint());

        final InspectorMenu memoryMenu = frame.makeMenu(MenuKind.MEMORY_MENU);
        memoryMenu.add(defaultMenuItems(MenuKind.MEMORY_MENU));
        final JMenuItem viewMemoryRegionsMenuItem = new JMenuItem(actions().viewMemoryRegions());
        viewMemoryRegionsMenuItem.setText("View Memory Regions");
        memoryMenu.add(viewMemoryRegionsMenuItem);

        final InspectorMenu debugMenu = frame.makeMenu(MenuKind.DEBUG_MENU);
        debugMenu.addSeparator();
        debugMenu.add(actions().genericBreakpointMenuItems());
        if (maxVM().watchpointsEnabled()) {
            debugMenu.add(actions().genericWatchpointMenuItems());
            final JMenuItem viewWatchpointsMenuItem = new JMenuItem(actions().viewWatchpoints());
            viewWatchpointsMenuItem.setText("View Watchpoints");
            debugMenu.add(viewWatchpointsMenuItem);
        }

        frame.makeMenu(MenuKind.VIEW_MENU).add(defaultMenuItems(MenuKind.VIEW_MENU));
        Trace.end(1,  tracePrefix() + " initializing");
    }

    @Override
    protected Rectangle defaultFrameBounds() {
        return inspection().geometry().breakpointsFrameDefaultBounds();
    }

    @Override
    protected void createView() {
        table = new BreakpointsTable(inspection(), viewPreferences);
        frame().setContentPane(new InspectorScrollPane(inspection(), table));
    }

    @Override
    protected SaveSettingsListener saveSettingsListener() {
        return saveSettingsListener;
    }

    @Override
    protected InspectorTable getTable() {
        return table;
    }

    @Override
    public String getTextForTitle() {
        return "Breakpoints";
    }

    @Override
    public InspectorAction getViewOptionsAction() {
        return new InspectorAction(inspection(), "View Options") {
            @Override
            public void procedure() {
                new TableColumnVisibilityPreferences.ColumnPreferencesDialog<BreakpointsColumnKind>(inspection(), "Breakpoints View Options", viewPreferences);
            }
        };
    }

    @Override
    public InspectorAction getPrintAction() {
        return getDefaultPrintAction();
    }

    @Override
    protected boolean refreshView(boolean force) {
        table.refresh(force);
        super.refreshView(force);
        return true;
    }

    public void viewConfigurationChanged() {
        reconstructView();
    }

    @Override
    public void breakpointStateChanged() {
        refreshView(true);
    }

    @Override
    public  void breakpointFocusSet(TeleBreakpoint oldTeleBreakpoint, TeleBreakpoint teleBreakpoint) {
        if (table != null) {
            table.updateFocusSelection();
        }
    }

    public void tableColumnViewPreferencesChanged() {
        reconstructView();
    }

    @Override
    public void inspectorClosing() {
        Trace.line(TRACE_VALUE, tracePrefix() + " closing");
        breakpointsInspector = null;
        viewPreferences.removeListener(this);
        super.inspectorClosing();
    }

}
