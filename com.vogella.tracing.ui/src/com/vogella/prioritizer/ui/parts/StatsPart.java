
package com.vogella.prioritizer.ui.parts;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.e4.core.commands.ECommandService;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.bindings.EBindingService;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.ConfigRegistry;
import org.eclipse.nebula.widgets.nattable.config.DefaultNatTableStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.data.ListDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.grid.layer.ColumnHeaderLayer;
import org.eclipse.nebula.widgets.nattable.layer.CompositeLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;
import org.eclipse.nebula.widgets.nattable.layer.cell.ColumnLabelAccumulator;
import org.eclipse.nebula.widgets.nattable.reorder.ColumnReorderLayer;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.sort.config.SingleClickSortConfiguration;
import org.eclipse.nebula.widgets.nattable.viewport.ViewportLayer;
import org.eclipse.swt.widgets.Composite;

import com.vogella.prioritizer.core.events.Events;
import com.vogella.prioritizer.ui.domain.CommandStats;
import com.vogella.prioritizer.ui.nattable.CommandStatsColumnPropertyAccessor;
import com.vogella.prioritizer.ui.nattable.CommandStatsHeaderDataProvider;
import com.vogella.tracing.core.event.CommandListenerEvents;
import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.SortedList;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

@SuppressWarnings("restriction")
public class StatsPart {

	@Inject
	private MeterRegistry meterRegistry;

	@Inject
	private EBindingService bindingService;

	@Inject
	private ECommandService commandService;

	private NatTable natTable;

	private SortedList<CommandStats> sortedList;

	@PostConstruct
	public void postConstruct(Composite parent) {
		BasicEventList<CommandStats> eventList = new BasicEventList<>(500);
		sortedList = new SortedList<>(eventList, (o1, o2) -> Double.compare(o2.getInvocations(), o1.getInvocations()));

		ListDataProvider<CommandStats> dataProvider = new ListDataProvider<CommandStats>(sortedList,
				new CommandStatsColumnPropertyAccessor());
		DataLayer dataLayer = new DataLayer(dataProvider);
		dataLayer.setColumnPercentageSizing(true);
		dataLayer.setColumnWidthPercentageByPosition(0, 40);
		dataLayer.setColumnWidthPercentageByPosition(1, 30);
		dataLayer.setColumnWidthPercentageByPosition(2, 10);
		dataLayer.setColumnWidthPercentageByPosition(3, 20);
		ColumnReorderLayer columnReorderLayer = new ColumnReorderLayer(dataLayer);
		ColumnLabelAccumulator columnLabelAccumulator = new ColumnLabelAccumulator(dataProvider);
		dataLayer.setConfigLabelAccumulator(columnLabelAccumulator);

		ViewportLayer viewportLayer = new ViewportLayer(columnReorderLayer);

		IDataProvider headerDataProvider = new CommandStatsHeaderDataProvider();
		DataLayer headerDataLayer = new DataLayer(headerDataProvider);
		ILayer columnHeaderLayer = new ColumnHeaderLayer(headerDataLayer, viewportLayer, (SelectionLayer) null);

		CompositeLayer compositeLayer = new CompositeLayer(1, 2);
		compositeLayer.setChildLayer(GridRegion.COLUMN_HEADER, columnHeaderLayer, 0, 0);
		compositeLayer.setChildLayer(GridRegion.BODY, viewportLayer, 0, 1);

		ConfigRegistry configRegistry = new ConfigRegistry();

		natTable = new NatTable(parent, compositeLayer, false);
		natTable.setConfigRegistry(configRegistry);
		natTable.addConfiguration(new DefaultNatTableStyleConfiguration());
		natTable.addConfiguration(new SingleClickSortConfiguration());

		GridDataFactory.fillDefaults().grab(true, true).applyTo(natTable);

		natTable.configure();

		refreshCommandStats(true);
	}

	@Inject
	@Optional
	public void trackCommandCalls(@UIEventTopic(CommandListenerEvents.TOPIC_COMMAND_PRE_EXECUTE) String commandId) {
		refreshCommandStats(true);
	}

	@Inject
	@Optional
	public void refreshCommandStats(@UIEventTopic(Events.REFRESH) boolean refresh) {
		List<Meter> meters = meterRegistry.getMeters();
		List<CommandStats> list = meters.stream().filter(meter -> "command.calls.pre".equals(meter.getId().getName()))
				.flatMap(meter -> {
					return StreamSupport.stream(meter.measure().spliterator(), false).map(measurement -> {
						String commandId = meter.getId().getTag("commandId");
						double invocations = measurement.getValue();

						ParameterizedCommand command = commandService.createCommand(commandId, null);
						return new CommandStats(commandId, getCommandName(command), invocations,
								getKeybinding(command));
					});
				}).collect(Collectors.toList());

		sortedList.clear();
		sortedList.addAll(list);
		natTable.refresh();
	}

	private String getKeybinding(ParameterizedCommand command) {
		TriggerSequence bestSequenceFor = bindingService.getBestSequenceFor(command);
		if (bestSequenceFor != null) {
			return bestSequenceFor.format();
		}
		return "No keybinding definied";
	}

	private String getCommandName(ParameterizedCommand command) {
		try {
			return command.getName();
		} catch (NotDefinedException e) {
			// unlikely to happen
			return "Command does not have a name";
		}
	}
}