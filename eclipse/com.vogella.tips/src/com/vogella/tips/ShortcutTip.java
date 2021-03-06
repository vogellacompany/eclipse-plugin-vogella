package com.vogella.tips;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tips.core.IHtmlTip;
import org.eclipse.tips.core.Tip;
import org.eclipse.tips.core.TipAction;
import org.eclipse.tips.core.TipImage;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vogella.common.ui.dialog.ReportDialog;
import com.vogella.common.ui.dialog.ReportModel;
import com.vogella.services.innosensr.InnoSensrService;
import com.vogella.services.innosensr.InnoSensrStatus;

import reactor.core.scheduler.Schedulers;

public class ShortcutTip extends Tip implements IHtmlTip {

	private static final Logger LOG = LoggerFactory.getLogger(ShortcutTip.class);

	private String commandName;
	private String shortcut;

	private InnoSensrService innoSensrService;

	public ShortcutTip(String providerId, String commandName, String shortcut, UISynchronize uiSync, InnoSensrService innoSensrService) {
		super(providerId);
		this.commandName = commandName;
		this.shortcut = shortcut;
		this.innoSensrService = innoSensrService;

		if (null == shortcut) {
			Bundle bundle = FrameworkUtil.getBundle(getClass());
			URL innosensrIcon = FileLocator.find(bundle, new Path("icons/16/innosensr-logo.png"));
			try {
				TipAction reportBugTipAction = new TipAction("Report Bug", "Create a bug report in OpenReq Live",
						() -> uiSync.asyncExec(this::reportBug),
						new TipImage(innosensrIcon));
				getActions().add(reportBugTipAction);
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}
	}

	private void reportBug() {
		ReportModel reportModel = new ReportModel();
		reportModel.setTitle("No default shortcut for the " + commandName + " command.");
		reportModel.setDescription("The " + commandName + " should have a default shortcut");
		Shell shell = Display.getDefault().getActiveShell();
		ReportDialog reportDialog = new ReportDialog(shell, reportModel, "Report bug in OpenReq Live",
				"Open a new issue in OpenReq Live");
		if (Window.OK == reportDialog.open()) {
			LOG.debug("Sending bug report to InnoSensr.");
			innoSensrService.createRequirement("bLMk11Jc", reportModel.getTitle(), reportModel.getDescription(),
					InnoSensrStatus.NEW).subscribeOn(Schedulers.elastic()).subscribe(System.out::println, e -> {
						Status errorStatus = new Status(IStatus.ERROR, "com.vogella.tips", e.getMessage(), e);
						ErrorDialog.openError(shell, "Error creating issue", e.getMessage(), errorStatus);
					});
		}
	}

	@Override
	public Date getCreationDate() {
		return Date.from(LocalDate.of(2018, 6, 21).atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	@Override
	public String getSubject() {
		return "Using shortcuts instead of menus";
	}

	@Override
	public String getHTML() {
		return shortcut != null ? "<html><head><title>Shortcut advice</title></head><body><p>The <i>" + commandName
				+ "</i> command has been used quite often.</p><p>Did you know about the <i>" + shortcut
				+ "</i> shortcut to invoke this command.</p><p>Shortcuts allow you to invoke commands faster and be more efficient.</p></body></html>"
				: "<html><head><title>Shortcut advice</title></head><body><p>The <i>" + commandName
						+ "</i> command has been used quite often.</p><p>But there is no keybinding defined for it, but you can specify one here <button onclick=\"openKeysPreferences()\"><b>Keys</b> preferences</button></p><p>Shortcuts allow you to invoke commands faster and be more efficient.</p></body></html>";
	}

	@Override
	public TipImage getImage() {
		Bundle bundle = FrameworkUtil.getBundle(getClass());
		try {
			return new TipImage(bundle.getEntry("icons/48/keyboard.png"));
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		return null;
	}

}
