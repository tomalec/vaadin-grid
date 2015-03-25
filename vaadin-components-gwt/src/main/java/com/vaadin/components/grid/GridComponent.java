package com.vaadin.components.grid;

import static com.google.gwt.query.client.GQuery.$;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.js.JsExport;
import com.google.gwt.core.client.js.JsNamespace;
import com.google.gwt.core.client.js.JsNoExport;
import com.google.gwt.core.client.js.JsType;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableElement;
import com.google.gwt.query.client.Function;
import com.google.gwt.query.client.js.JsUtils;
import com.google.gwt.query.client.plugins.widgets.WidgetsUtils;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.vaadin.client.data.AbstractRemoteDataSource;
import com.vaadin.client.data.DataSource;
import com.vaadin.client.widget.grid.selection.SelectionEvent;
import com.vaadin.client.widget.grid.selection.SelectionHandler;
import com.vaadin.client.widget.grid.selection.SelectionModelMulti;
import com.vaadin.client.widget.grid.selection.SelectionModelNone;
import com.vaadin.client.widget.grid.sort.SortEvent;
import com.vaadin.client.widget.grid.sort.SortHandler;
import com.vaadin.client.widget.grid.sort.SortOrder;
import com.vaadin.client.widgets.Grid;
import com.vaadin.client.widgets.Grid.Column;
import com.vaadin.client.widgets.Grid.SelectionMode;
import com.vaadin.components.common.js.JS;
import com.vaadin.components.common.js.JSArray;
import com.vaadin.components.common.js.JSEnums;
import com.vaadin.components.common.js.JSValidate;
import com.vaadin.components.common.util.DOMUtils;
import com.vaadin.components.common.util.Elements;
import com.vaadin.components.grid.config.JSCellClassName;
import com.vaadin.components.grid.config.JSColumn;
import com.vaadin.components.grid.config.JSRowClassName;
import com.vaadin.components.grid.config.JSSortOrder;
import com.vaadin.components.grid.data.GridDataSource;
import com.vaadin.components.grid.data.GridDomTableDataSource;
import com.vaadin.components.grid.data.GridJsFuncDataSource;
import com.vaadin.components.grid.head.GridColumn;
import com.vaadin.components.grid.head.GridDomTableHead;
import com.vaadin.components.grid.utils.GridCellStyleGenerator;
import com.vaadin.components.grid.utils.GridRowStyleGenerator;
import com.vaadin.components.grid.utils.Redraw;
import com.vaadin.shared.data.sort.SortDirection;
import com.vaadin.shared.ui.grid.HeightMode;
import com.vaadin.shared.ui.grid.ScrollDestination;

/**
 * Class to export Vaadin Grid to JS.
 */
@JsNamespace(Elements.VAADIN_JS_NAMESPACE)
@JsExport
@JsType
public class GridComponent implements SelectionHandler<Object>,
        EventListener, SortHandler<Object> {

    private final Grid<Object> grid;
    private JSArray<JSSortOrder> jsSort;

    private boolean updating = false;
    private GridDomTableHead head;
    private final Redraw redrawer;
    private final GridEditor editor;

    private Element container;
    private JSArray<JSColumn> cols;

    public GridComponent() {
        grid = new Grid<Object>();
        grid.addSelectionHandler(this);
        grid.addSortHandler(this);
        cols = JS.createArray();
        observeColumnArray();
        redrawer = new Redraw(this);
        editor = new GridEditor(this);
    }

    public GridEditor getEditor() {
        return editor;
    }

    public Element getGridElement() {
        return grid.getElement();
    }

    public JSArray<JSSortOrder> getSortOrder() {
        return jsSort;
    }

    public void setSortOrder(JSArray<JSSortOrder> jsOrders) {
        List<SortOrder> order = new ArrayList<SortOrder>();
        for (JSSortOrder jsOrder : jsOrders.asList()) {
            Column<?, ?> column = grid.getColumn(jsOrder.getColumn());
            SortDirection direction = JSEnums.Direction.val(jsOrder
                    .getDirection());
            jsOrder.setDirection(JSEnums.Direction.val(direction));
            order.add(new SortOrder(column, direction));
        }
        grid.setSortOrder(order);
        this.jsSort = jsOrders;
    }

    public Grid<Object> getGrid() {
        return grid;
    }

    public void init(Element container, TableElement lightDomElement,
            Element gridContainer) {
        this.container = container;

        if (head == null) {
            head = new GridDomTableHead(lightDomElement, this);
        } else {
            head.setLightDom(lightDomElement);
        }
        if (lightDomElement != null) {
            setColumns(head.loadHeaders());
        }

        gridContainer.appendChild(grid.getElement());
        WidgetsUtils.attachWidget(grid, null);

        if (lightDomElement != null) {
            // If the wrapped DOM table has TR elements, we use it as data
            // source
            DataSource<Object> dataSource = GridDomTableDataSource
                    .createInstance(lightDomElement, this);
            if (dataSource != null) {
                grid.setDataSource(dataSource);
            }
        }

        redrawer.setContainer(container);
        editor.setContainer(container);
    }

    public JSColumn addColumn(JSColumn jsColumn, String beforeColumn) {
        int index = grid.getColumnCount();
        if (beforeColumn != null) {
            index = getColumnIndexByIndexOrName(beforeColumn);
        }
        cols.add(jsColumn, index);

        return jsColumn;
    }

    private int getColumnIndexByIndexOrName(String indexOrName) {
        if (indexOrName.matches("[+-]?\\d+")) {
            int parsedInt = Integer.parseInt(indexOrName);
            if (parsedInt >= 0 && parsedInt < cols.length()) {
                return parsedInt;
            }
        } else {
            String idString = String.valueOf(indexOrName);
            for (int i = 0; i < cols.length(); i++) {
                JSColumn jsColumn = cols.get(i);
                if (idString.equals(jsColumn.name())) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Column not found.");
    }

    public void removeColumn(String id) {
        int index = getColumnIndexByIndexOrName(id);
        cols.remove(cols.get(index));
    }

    public void onMutation() {
        setColumns(head.loadHeaders());
        refresh();
    }

    public void setDisabled(boolean disabled) {
        grid.setEnabled(!disabled);
    }

    public void setFrozenColumns(String frozenColumn) {
        grid.setFrozenColumnCount(JSValidate.Integer.val(frozenColumn));
    }

    public void scrollToRow(int index, String scrollDestination) {
        if (scrollDestination != null) {
            grid.scrollToRow(index,
                    ScrollDestination.valueOf(scrollDestination.toUpperCase()));
        } else {
            grid.scrollToRow(index);
        }
    }

    public void scrollToStart() {
        grid.scrollToStart();
    }

    public void scrollToEnd() {
        grid.scrollToEnd();
    }

    public double getScrollTop() {
        return grid.getScrollTop();
    }

    public void setScrollTop(double px) {
        grid.setScrollTop(px);
    }

    public JSArray<JSColumn> getColumns() {
        return cols;
    }

    @JsNoExport
    @Override
    public void onSelect(SelectionEvent<Object> ev) {
        if (!updating) {
            $(container).trigger("select");
        }
    }

    public void setColumnWidth(int column, int widht) {
        grid.getColumn(column).setWidth(widht);
    }

    public String getHeightMode() {
        return grid.getHeightMode().toString();
    }

    public void setHeightMode(String mode) {
        grid.setHeightMode(HeightMode.valueOf(mode));
    }

    public void setHeight(String height) {
        grid.setHeight(height);
    }

    public void setDataSource(JavaScriptObject data) {
        if (JsUtils.isFunction(data)) {
            grid.setDataSource(new GridJsFuncDataSource(data, this));
            redrawer.redraw(true);
        } else {
            throw new RuntimeException("Unknown data source type: " + data
                    + ". Arrays and Functions are supported only.");
        }
    }

    public DataSource<Object> getDataSource() {
        return grid.getDataSource();
    }

    public void refresh() {
        final JsArrayInteger a = getSelectedRows();
        ((GridDataSource) grid.getDataSource()).refresh();
        redrawer.redraw(true);
        if (a.length() > 0) {
            $(container).delay(5, new Function() {
                @Override
                public void f() {
                    setSelectedRows(a);
                }
            });
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setColumns(JSArray<JSColumn> columns) {
        if (this.cols != null) {
            DOMUtils.unobserve(this.cols);
        }

        Collection<JSColumn> gCols = new ArrayList<JSColumn>();
        for (Column<?, Object> gCol : getDataColumns()) {
            gCols.add(((GridColumn) gCol).getJsColumn());
        }

        // Add all missing columns to grid
        for (JSColumn column : columns.asList()) {
            if (!gCols.contains(column)) {
                GridColumn.addColumn(column, this);
            }
        }
        // Remove all non-included columns from grid
        for (Column<?, Object> column : getDataColumns()) {
            if (columns.indexOf(((GridColumn) column).getJsColumn()) == -1) {
                grid.removeColumn(column);
            }
        }

        // Fix column order
        Column[] array = getDataColumns().toArray(new Column[0]);
        Arrays.sort(array, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                return columns.indexOf(((GridColumn) o1).getJsColumn()) > columns
                        .indexOf(((GridColumn) o2).getJsColumn()) ? 1 : -1;
            }
        });
        grid.setColumnOrder(array);
        this.cols = columns;
        observeColumnArray();
    }

    /*
     * This method is needed internally for listing all the columns that display
     * data. On multi-select mode grid.getColumns() will contain the selection
     * column as the first item.
     */
    private List<Column<?, Object>> getDataColumns() {
        List<Column<?, Object>> result = grid.getColumns();
        if (grid.getSelectionModel() instanceof SelectionModelMulti) {
            result = result.subList(1, result.size());
        }
        return result;
    }

    private void observeColumnArray() {
        DOMUtils.observe(this.cols, new EventListener() {
            @Override
            public void onBrowserEvent(Event event) {
                setColumns(GridComponent.this.cols);
            }
        });
    }

    public void setSelectionMode(String selectionMode) {
        SelectionMode mode = JSEnums.Selection.val(selectionMode);
        grid.setSelectionMode(mode);
        if (mode == SelectionMode.MULTI) {
            // Remove check-box for selecting all in the header
            grid.getDefaultHeaderRow().getCell(grid.getColumn(0)).setText("");
        }
    }

    @Override
    public void onBrowserEvent(Event event) {
        refresh();
    }

    public void setSelectedRows(JsArrayInteger selectedJso) {
        if (grid.getSelectionModel() instanceof SelectionModelNone) {
            return;
        }
        updating = true;
        grid.getSelectionModel().reset();
        for (int i = 0, l = selectedJso.length(); i < l; i++) {
            int selectedIndex = selectedJso.get(i);
            if (selectedIndex >= 0
                    && selectedIndex < grid.getDataSource().size()) {
                grid.select(grid.getDataSource().getRow(selectedIndex));
            }
        }
        updating = false;
        onSelect(null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public JsArrayInteger getSelectedRows() {
        JsArrayInteger selectedJso = JsArrayInteger.createArray().cast();
        selectedJso.setLength(0);
        Collection<Object> c = grid.getSelectedRows();
        for (Iterator<Object> i = c.iterator(); i.hasNext();) {
            selectedJso.push(((AbstractRemoteDataSource) grid.getDataSource())
                    .indexOf(i.next()));
        }
        return selectedJso;
    }

    public JSRowClassName getRowClassName() {
        JSRowClassName result = null;
        if (grid.getRowStyleGenerator() != null) {
            result = ((GridRowStyleGenerator) grid.getRowStyleGenerator())
                    .getRowClassName();
        }
        return result;
    }

    public void setRowClassName(JSRowClassName rowClassName) {
        if (rowClassName == null) {
            grid.setRowStyleGenerator(null);
        } else {
            grid.setRowStyleGenerator(new GridRowStyleGenerator(rowClassName,
                    container));
        }
    }

    public JSCellClassName getCellClassName() {
        JSCellClassName result = null;
        if (grid.getCellStyleGenerator() != null) {
            result = ((GridCellStyleGenerator) grid.getCellStyleGenerator())
                    .getCellClassName();
        }
        return result;
    }

    public void setCellClassName(JSCellClassName cellClass) {
        if (cellClass == null) {
            grid.setCellStyleGenerator(null);
        } else {
            grid.setCellStyleGenerator(new GridCellStyleGenerator(cellClass,
                    container));
        }
    }

    // TODO: remove this when grid resizes appropriately on container
    // and data changes.
    public void redraw() {
        redrawer.redraw(false);
    }

    // TODO: we are using String instead of int because polymer passes a string
    // instead of the number when using the 'bind:' directive, when the value
    // is set as an attribute, the 'published:' works as expected though.
    public void setRows(String rows) {
        redrawer.setSize(JSValidate.Integer.val(rows));
    }

    @Override
    public void sort(SortEvent<Object> event) {
        if (jsSort == null) {
            jsSort = JSArray.createArray().cast();
        }
        jsSort.setLength(0);
        for (SortOrder order: event.getOrder()) {
            int idx = grid.getColumns().indexOf(order.getColumn());
            jsSort.push(JS.createJsType(JSSortOrder.class).setColumn(idx)
                    .setDirection(JSEnums.Direction.val(order.getDirection())));
        }
        $(container).trigger("sort");
        refresh();
    }
}